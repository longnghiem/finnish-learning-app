#!/usr/bin/env bash
#
# Bring a freshly launched Amazon Linux 2023 EC2 host
# to a state where the Finnish Learning backend, Kafka, and nginx can run.
#
# Run ONCE per instance, as root:
#     sudo bash deploy/provision-ec2.sh
#
# Idempotent: re-running on an already-provisioned host is a no-op for stateful
# steps (swapfile, user creation, Postgres role/db, pg_hba edits, SELinux
# booleans). Safe to invoke after a partial failure.
#
# What it does (in order):
#   1. Refuse to run if not root or not on Amazon Linux 2023.
#   2. Install dnf packages: java-21-amazon-corretto-headless, postgresql15,
#      postgresql15-server, nginx, helpers, policycoreutils-python-utils
#      (needed for `semanage`).
#   3. Initialise the Postgres data directory if missing, then enable + start.
#   4. Rewrite pg_hba.conf so TCP localhost auth uses scram-sha-256 instead of
#      the default `ident` (which would reject the Spring Boot JDBC connection).
#   5. Create a 4 GB /swapfile (required for Kafka + Spring on 1 GB RAM).
#   6. Create system user `finnish` and application directories with correct owners.
#   7. Create Postgres role `finnish` and database `finnish_learning_app` if missing.
#      (Password is NOT set here — operator sets it after the script finishes.)
#   8. SELinux: allow nginx to proxy to localhost (httpd_can_network_connect) and
#      label /var/www/finnish so nginx may serve files from it.
#   9. Print a summary of what to do next.
#
# Exit codes:
#   0  success (host ready, or already provisioned)
#   1  precondition failed (not root, not AL2023, etc.)
#   2  dnf install failed
#   3  postgres bootstrap failed
#

set -euo pipefail

# ──────────────────────────────────────────────────────────────────────────────
# Logging helpers. Plain printf so the script has no external deps.
# ──────────────────────────────────────────────────────────────────────────────
log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31m[err]\033[0m %s\n' "$*" >&2; exit "${2:-1}"; }

# ──────────────────────────────────────────────────────────────────────────────
# 1. Preconditions.
#
# Why root: dnf, useradd, swapon, writing to /etc and /var/lib all need it.
# `sudo bash` is the contract — invoking via `bash deploy/provision-ec2.sh`
# (no sudo) fails fast here instead of later with a confusing permissions error.
# ──────────────────────────────────────────────────────────────────────────────
if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  fail "Must run as root. Invoke: sudo bash deploy/provision-ec2.sh"
fi

if ! grep -q '^ID="amzn"' /etc/os-release 2>/dev/null; then
  fail "This script targets Amazon Linux 2023. Detected: $(. /etc/os-release && echo "$PRETTY_NAME")"
fi

AL_VERSION="$(. /etc/os-release && echo "$VERSION_ID")"
log "Detected Amazon Linux $AL_VERSION"

# ──────────────────────────────────────────────────────────────────────────────
# 2. DNF installs.
#
# `dnf install -y` is naturally idempotent: already-installed packages are a
# no-op. We do not need DEBIAN_FRONTEND-style tricks; dnf is non-interactive
# with `-y` by default and does not prompt for service restarts.
#
# Package notes:
#   java-21-amazon-corretto-headless   Amazon's tuned JDK 21 build. `headless`
#                                       strips X11/GUI deps (saves ~150 MB).
#                                       We use the runtime-only flavour because
#                                       compilation happens on the laptop, not here.
#   postgresql15-server, postgresql15  Postgres 15 server + client tools. AL2023
#                                       ships PG15 in the default repo; PG16
#                                       requires extra repo configuration and
#                                       buys us nothing the app needs.
#                                       Binds to 127.0.0.1:5432 by default
#                                       (different from the dev compose stack
#                                       which uses 5532). The backend env file
#                                       sets SPRING_DATASOURCE_URL to point at 5432.
#   nginx                              Reverse proxy + static file server.
#   curl, tar, ca-certificates         Used by the Kafka tarball install in.
#   git                                Convenience — operator usually clones the
#                                       repo on the box.
#   policycoreutils-python-utils       Provides `semanage` for SELinux file
#                                       context labeling (step 8).
# ──────────────────────────────────────────────────────────────────────────────
log "Installing system packages"
dnf install -y \
  java-21-amazon-corretto-headless \
  postgresql15-server \
  postgresql15 \
  nginx \
  curl \
  tar \
  ca-certificates \
  git \
  policycoreutils-python-utils \
  || fail "dnf install failed. See output above." 2

# ──────────────────────────────────────────────────────────────────────────────
# 3. Initialise Postgres if not already initialised.
#
# Unlike Ubuntu's postgresql package, AL2023 does NOT auto-initdb. You get the
# binaries and an empty /var/lib/pgsql/. You must call `postgresql-setup
# --initdb` once before the server will start.
#
# The /var/lib/pgsql/data/PG_VERSION file exists if (and only if) initdb has
# already run, so we use that as the idempotency guard.
# ──────────────────────────────────────────────────────────────────────────────
if [[ ! -f /var/lib/pgsql/data/PG_VERSION ]]; then
  log "Initialising PostgreSQL data directory (/var/lib/pgsql/data)"
  /usr/bin/postgresql-setup --initdb \
    || fail "postgresql-setup --initdb failed." 3
else
  log "PostgreSQL data directory already initialised — skipping initdb"
fi

systemctl enable --now postgresql

# ──────────────────────────────────────────────────────────────────────────────
# 4. pg_hba.conf — switch TCP localhost auth from `ident` to `scram-sha-256`.
#
# AL2023's stock pg_hba.conf for host (TCP) lines uses `ident`, which maps
# the connecting OS user to a same-named Postgres role. Our backend connects
# over TCP from the `finnish` Linux user, but the `finnish` Postgres role only
# accepts password auth from a JDBC driver. With `ident`, the connection is
# rejected with "Ident authentication failed for user \"finnish\"".
#
# Fix: rewrite the two host lines (IPv4 + IPv6) to `scram-sha-256`. The
# `local` line for the postgres superuser stays as `peer`, so we can still
# `sudo -u postgres psql` for admin tasks below.
#
# `sed -i` with a guard pattern is idempotent — re-runs will find scram-sha-256
# already in place and do nothing.
# ──────────────────────────────────────────────────────────────────────────────
PG_HBA=/var/lib/pgsql/data/pg_hba.conf
if grep -qE '^host\s+all\s+all\s+(127\.0\.0\.1/32|::1/128)\s+ident' "$PG_HBA"; then
  log "Switching pg_hba TCP localhost auth from ident → scram-sha-256"
  sed -i -E \
    -e 's|^(host\s+all\s+all\s+127\.0\.0\.1/32\s+)ident|\1scram-sha-256|' \
    -e 's|^(host\s+all\s+all\s+::1/128\s+)ident|\1scram-sha-256|' \
    "$PG_HBA"
  systemctl reload postgresql
else
  log "pg_hba already uses scram-sha-256 for TCP localhost — skipping"
fi

# ──────────────────────────────────────────────────────────────────────────────
# 5. Swap.
#
# Why 4 GB on a 1 GB RAM box: memory budget projects ~1.14 GB RSS
# across Postgres + Kafka + Spring + nginx + OS. ~140 MB lives in swap under
# steady-state. 4 GB gives headroom for GC compaction spikes and the occasional
# `dnf upgrade` running in parallel.
#
# Guard: skip if /swapfile already in use (idempotency). Without the guard,
# `swapon /swapfile` errors when the file is already active.
#
# /etc/fstab line makes the swapfile survive reboots. Without it, every reboot
# would leave the host swapless and prone to OOM-killer events.
# ──────────────────────────────────────────────────────────────────────────────
if ! swapon --show 2>/dev/null | grep -q '/swapfile'; then
  log "Creating 4 GB /swapfile"
  fallocate -l 4G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
  if ! grep -q '^/swapfile ' /etc/fstab; then
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
  fi
  log "Swap active: $(swapon --show --noheadings | awk '{print $1, $3}')"
else
  log "Swapfile already active — skipping"
fi

# ──────────────────────────────────────────────────────────────────────────────
# 6. Application user and directories.
#
# Why a dedicated user:
#   The backend runs as `finnish`, not root. If the jar is ever compromised
#   (RCE, file traversal, etc.), the blast radius is one unprivileged user.
#
# Why --system and --shell /sbin/nologin:
#   `--system` creates a UID in the system range (<1000) and skips the home-dir
#   skeleton. `nologin` means nobody can `su - finnish` interactively — the user
#   only exists for systemd to drop privileges into.
#   (AL2023 uses /sbin/nologin; Ubuntu uses /usr/sbin/nologin. Same binary,
#   different path.)
#
# Directory map:
#   /opt/finnish              Place for the jar (uploaded by deploy.sh).
#   /var/lib/finnish/uploads  Mutable data: card images. The env var
#                             IMAGE_STORAGE_LOCATION points here.
#   /var/www/finnish/dist     Static SPA bundle; nginx reads it directly.
#                             Owned by `nginx` (the AL2023 nginx user — Ubuntu's
#                             equivalent is `www-data`) so nginx can read
#                             without extra permissions.
# ──────────────────────────────────────────────────────────────────────────────
if ! id -u finnish >/dev/null 2>&1; then
  log "Creating system user 'finnish'"
  useradd --system --home-dir /opt/finnish --shell /sbin/nologin finnish
else
  log "User 'finnish' already exists — skipping"
fi

log "Creating application directories"
mkdir -p /opt/finnish /var/lib/finnish/uploads /var/www/finnish/dist
chown -R finnish:finnish /opt/finnish /var/lib/finnish
chown -R nginx:nginx /var/www/finnish
chmod 750 /var/lib/finnish/uploads

# ──────────────────────────────────────────────────────────────────────────────
# 7. PostgreSQL role and database.
#
# Why we don't set a password here:
#   Embedding a password in a checked-in script (even an .example) makes leaks
#   too easy. Operator runs a single `ALTER USER` after this script — the same
#   password then lives in /etc/finnish-backend.env (mode 0600, root-only).
#
# The role is created with no LOGIN restrictions disabled (createuser defaults).
# The database is owned by `finnish` so Flyway migrations (run by the backend
# at startup) have full DDL rights without needing the postgres superuser.
#
# Both creations are guarded by `SELECT 1 FROM pg_roles WHERE rolname=...`
# style probes so re-running the script is a no-op.
# ──────────────────────────────────────────────────────────────────────────────
log "Bootstrapping PostgreSQL role and database"

# Wait briefly for postgres to be ready (socket may not be listening yet).
for _ in 1 2 3 4 5; do
  sudo -u postgres psql -c '\q' >/dev/null 2>&1 && break
  sleep 1
done
sudo -u postgres psql -c '\q' >/dev/null 2>&1 \
  || fail "PostgreSQL is installed but not responding on the local socket." 3

if ! sudo -u postgres psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='finnish'" | grep -q 1; then
  sudo -u postgres createuser finnish
  log "Created role 'finnish'"
else
  log "Role 'finnish' already exists — skipping"
fi

if ! sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='finnish_learning_app'" | grep -q 1; then
  sudo -u postgres createdb finnish_learning_app -O finnish
  log "Created database 'finnish_learning_app' owned by 'finnish'"
else
  log "Database 'finnish_learning_app' already exists — skipping"
fi

# ──────────────────────────────────────────────────────────────────────────────
# 8. SELinux: two policy adjustments.
#
# Why this exists at all:
#   AL2023 ships SELinux in enforcing mode. Two things our setup does that
#   would otherwise be blocked:
#
#   (a) nginx connects out to 127.0.0.1:8080 (reverse proxy → Spring Boot).
#       Without `httpd_can_network_connect=on`, nginx logs:
#         connect() to 127.0.0.1:8080 failed (13: Permission denied)
#
#   (b) nginx serves static files from /var/www/finnish/dist. /var/www is not
#       a standard SELinux-labelled directory on AL2023, so files there have
#       the default `var_t` type, which httpd is not allowed to read. Result:
#         "403 Forbidden" or "Permission denied" in nginx error.log.
#
# `setsebool -P` makes the change persistent across reboots. `semanage fcontext`
# records a permanent label rule that `restorecon` then applies. Without the
# semanage entry, a future `restorecon /` (security audit, etc.) would
# silently reset the labels and break the site.
#
# Both commands are naturally idempotent (semanage refuses to add a duplicate
# rule with a non-zero exit; the `|| true` swallows that case).
# ──────────────────────────────────────────────────────────────────────────────
log "Configuring SELinux for nginx reverse proxy + static file serving"
setsebool -P httpd_can_network_connect 1
semanage fcontext -a -t httpd_sys_content_t '/var/www/finnish(/.*)?' 2>/dev/null || true
restorecon -R /var/www/finnish

# ──────────────────────────────────────────────────────────────────────────────
# 9. Default nginx server block.
#
# AL2023's /etc/nginx/nginx.conf includes a default `server { listen 80
# default_server; ... }` block that serves the AL2023 placeholder page. The
# `default_server` flag would conflict with our finnish.conf if both
# tried to claim it. We comment out the entire default server block.
#
# Use a sentinel comment so re-runs detect that the block is already disabled.
# ──────────────────────────────────────────────────────────────────────────────
NGINX_MAIN=/etc/nginx/nginx.conf
if ! grep -q '# disabled-by-provision-ec2' "$NGINX_MAIN"; then
  log "Disabling default nginx server block"
  cp "$NGINX_MAIN" "$NGINX_MAIN.bak.$(date +%s)"
  # Comment every line between `server {` and the matching `}` only inside the
  # http {} stanza. Awk-based so we don't mis-handle nested braces.
  awk '
    BEGIN { in_http=0; in_server=0; depth=0 }
    /^[[:space:]]*http[[:space:]]*\{/        { in_http=1 }
    in_http && /^[[:space:]]*server[[:space:]]*\{/ && !in_server { in_server=1; depth=1; print "    # disabled-by-provision-ec2 " $0; next }
    in_server {
      n_open  = gsub(/\{/, "&"); depth += n_open
      n_close = gsub(/\}/, "&"); depth -= n_close
      print "    # disabled-by-provision-ec2 " $0
      if (depth == 0) { in_server=0 }
      next
    }
    { print }
  ' "$NGINX_MAIN" > "$NGINX_MAIN.new"
  mv "$NGINX_MAIN.new" "$NGINX_MAIN"
else
  log "Default nginx server block already disabled — skipping"
fi

# Validate but do not start nginx yet — later we will install-nginx.sh adds the
# finnish.conf site and reloads.
nginx -t

# ──────────────────────────────────────────────────────────────────────────────
# 10. Summary + operator next steps.
# ──────────────────────────────────────────────────────────────────────────────
cat <<'EOF'

============================================================
  Provisioning complete.
============================================================

Next steps (in this order — see deploy/README.md for full runbook):

  1. Set the Postgres password for the `finnish` role:
       sudo -u postgres psql -c "ALTER USER finnish WITH PASSWORD '<strong-password>';"

  2. Install Kafka (single-node KRaft):
       sudo bash deploy/kafka/install-kafka.sh

  3. Drop in the backend systemd unit and env file:
       sudo bash deploy/backend/install-backend.sh
       sudo $EDITOR /etc/finnish-backend.env
         - paste the Postgres password from step 1 into SPRING_DATASOURCE_PASSWORD
         - generate JWT_SECRET with: openssl rand -base64 32

  4. Drop in the nginx site config:
       sudo bash deploy/nginx/install-nginx.sh

  5. From your local env, build artefacts and upload:
       bash deploy/build.sh
       bash deploy/deploy.sh

  6. Open  http://<this-ec2-public-dns>/  in a browser.

EOF