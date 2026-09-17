#!/usr/bin/env bash
#
# Install the nginx site config for the Finnish Learning demo on an
# Amazon Linux 2023 EC2 host that has been provisioned by deploy/provision-ec2.sh.
#
# Run as root:
#     sudo bash deploy/nginx/install-nginx.sh
#
# This is PHASE 1 of a two-phase nginx install. It installs the HTTP-only
# bootstrap vhost (finnish.conf). Phase 2 is deploy/nginx/install-tls.sh, which
# obtains the certificate and swaps in finnish-tls.conf. The site is intentionally
# HTTP-only until phase 2 runs.
#
# Idempotent: re-running is a no-op for state. The site config is normally
# overwritten every time (config-as-code lives in this repo, not on the host), and
# nginx is re-tested + reloaded. The ONE exception is step 3: if the host is
# already serving HTTPS, this script refuses rather than silently downgrading it.
#
# What it does:
#   1. Preconditions: root + Amazon Linux 2023 + nginx installed + finnish.conf present.
#   2. Defensive: rename /etc/nginx/conf.d/default.conf to *.disabled if present
#      so it cannot collide with our default_server claim on :80.
#   3. Refuse to clobber a TLS-enabled vhost (see ALLOW_TLS_DOWNGRADE below).
#   4. Install sibling finnish.conf to /etc/nginx/conf.d/finnish.conf (mode 0644).
#   5. Validate full nginx config with `nginx -t`. Abort with the error message
#      surfaced if the test fails.
#   6. Ensure /var/www/finnish/dist exists. nginx will
#      happily start with an empty doc-root — index.html shows up once
#      deploy.sh has uploaded the SPA bundle.
#   7. Enable + start nginx (`systemctl enable --now nginx`). Idempotent.
#   8. Reload (not restart) to pick up the new config without dropping in-flight
#      connections — irrelevant for a demo but free best practice.
#   9. Print a final hint pointing at the next step.
#
# Exit codes:
#   0  success
#   1  precondition failed
#   2  nginx -t failed (bad config — script printed nginx's exact error)
#   3  refused to overwrite a TLS-enabled vhost (set ALLOW_TLS_DOWNGRADE=1 to force)

set -euo pipefail

# ──────────────────────────────────────────────────────────────────────────────
# Logging helpers — same style as deploy/kafka/install-kafka.sh.
# ──────────────────────────────────────────────────────────────────────────────
log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31m[err]\033[0m %s\n' "$*" >&2; exit "${2:-1}"; }

# Paths this script operates on. Declared once so the guard, the install and the
# doc-root check cannot drift apart.
LIVE_CONF=/etc/nginx/conf.d/finnish.conf
DOC_ROOT=/var/www/finnish/dist

# ──────────────────────────────────────────────────────────────────────────────
# 1. Preconditions.
# ──────────────────────────────────────────────────────────────────────────────
if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  fail "Must run as root. Invoke: sudo bash deploy/nginx/install-nginx.sh"
fi

if ! grep -q '^ID="amzn"' /etc/os-release 2>/dev/null; then
  fail "This script targets Amazon Linux 2023. Detected: $(. /etc/os-release && echo "$PRETTY_NAME")"
fi

command -v nginx >/dev/null \
  || fail "nginx is not installed. Run deploy/provision-ec2.sh first."

# Resolve sibling files relative to this script so it works regardless of cwd.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FINNISH_CONF_SRC="$SCRIPT_DIR/finnish.conf"

[[ -f "$FINNISH_CONF_SRC" ]] \
  || fail "Missing sibling file: $FINNISH_CONF_SRC"

# ──────────────────────────────────────────────────────────────────────────────
# 2. Defensive: rename any stock default.conf to avoid a duplicate
#    default_server collision when nginx -t runs.
#
# Some nginx point-release packages reintroduce /etc/nginx/conf.d/default.conf
# with its own `listen 80 default_server;` block. Our finnish.conf also claims
# default_server, so both being loaded would make nginx -t fail with:
#   "[emerg] a duplicate default server for 0.0.0.0:80"
# Renaming (not deleting) preserves the original in case the operator wants it back.
# ──────────────────────────────────────────────────────────────────────────────
if [[ -f /etc/nginx/conf.d/default.conf ]]; then
  log "Renaming /etc/nginx/conf.d/default.conf -> default.conf.disabled"
  mv /etc/nginx/conf.d/default.conf /etc/nginx/conf.d/default.conf.disabled
fi

# ──────────────────────────────────────────────────────────────────────────────
# 3. Refuse to downgrade a TLS-enabled host.
#
# This script used to overwrite $LIVE_CONF unconditionally and with no backup.
# Once install-tls.sh (or, historically, `certbot --nginx`) has put the :443 block
# there, an unguarded re-run drops the site to plain HTTP with a perfectly valid
# certificate sitting unused on disk. Port 443 simply stops answering — nothing
# errors, nothing logs, and the site still looks fine over HTTP. If HSTS is ever
# added, it is worse: browsers that already visited refuse to connect at all and
# the warning cannot be clicked through.
#
# Detection covers both origins of a TLS vhost:
#   - `listen 443` / `listen [::]:443`  — what finnish-tls.conf writes.
#   - "managed by Certbot"              — the marker certbot's own installer adds.
#
# A backup is written in BOTH branches, so even a deliberate forced downgrade is
# recoverable. The epoch suffix matches the convention provision-ec2.sh already
# uses when it rewrites /etc/nginx/nginx.conf.
# ──────────────────────────────────────────────────────────────────────────────
if [[ -f "$LIVE_CONF" ]] \
   && grep -qE '^[[:space:]]*listen[[:space:]]+(\[::\]:)?443|managed by Certbot' "$LIVE_CONF"; then

  TLS_BACKUP="$LIVE_CONF.bak.$(date +%s)"
  cp -a "$LIVE_CONF" "$TLS_BACKUP"

  if [[ "${ALLOW_TLS_DOWNGRADE:-0}" == "1" ]]; then
    warn "ALLOW_TLS_DOWNGRADE=1 — replacing a TLS-enabled vhost with the HTTP-only bootstrap."
    warn "Backup: $TLS_BACKUP"
  else
    fail "Refusing to overwrite a TLS-enabled vhost at $LIVE_CONF.

       This host is already serving HTTPS. Installing the HTTP-only bootstrap
       config would silently take port 443 down.

       A backup was written to:
         $TLS_BACKUP

       What you probably want instead:
         sudo bash deploy/nginx/install-tls.sh     # reinstall the TLS vhost from the repo

       To force the HTTP-only config anyway (you will lose HTTPS until you
       re-run install-tls.sh):
         sudo ALLOW_TLS_DOWNGRADE=1 bash deploy/nginx/install-nginx.sh" 3
  fi
fi

# ──────────────────────────────────────────────────────────────────────────────
# 4. Install finnish.conf.
#
# `install -m 644` copies the file AND sets permissions in one step. Owner is
# root:root by default — fine, since nginx only needs read access. We don't
# chown to nginx:nginx because nginx reads configs as root before forking
# worker processes.
# ──────────────────────────────────────────────────────────────────────────────
log "Installing $LIVE_CONF"
install -m 644 "$FINNISH_CONF_SRC" "$LIVE_CONF"

# ──────────────────────────────────────────────────────────────────────────────
# 5. Validate the full nginx config tree.
#
# `nginx -t` parses every loaded conf and reports syntax / semantic errors
# without touching the running process. If it fails, the new config is on
# disk but we do NOT reload — that way the running nginx keeps serving the
# previous (valid) config. Operator sees the exact error and can edit.
#
# Note the asymmetry with install-tls.sh, which restores its backup on a failed
# nginx -t. Here there is usually nothing worth restoring: phase 1 runs on a host
# that is not yet serving. The one case where a backup exists is a forced
# ALLOW_TLS_DOWNGRADE run, and there the operator asked for the replacement.
# ──────────────────────────────────────────────────────────────────────────────
log "Validating nginx config (nginx -t)"
if ! nginx -t; then
  fail "nginx -t failed. Fix the error above and re-run. Running nginx (if any) was NOT reloaded." 2
fi

# ──────────────────────────────────────────────────────────────────────────────
# 6. Ensure the document root exists.
#
# provision-ec2.sh creates $DOC_ROOT with the right SELinux label. We do not
# recreate it here — if it's missing, something went wrong in provisioning.
# nginx will start fine with an empty dir; /index.html returns 404 until
# deploy.sh has uploaded the SPA bundle.
# ──────────────────────────────────────────────────────────────────────────────
if [[ ! -d "$DOC_ROOT" ]]; then
  warn "$DOC_ROOT does not exist. Did you run provision-ec2.sh?"
  warn "nginx will start, but / will 404 until deploy.sh uploads the SPA."
fi

# ──────────────────────────────────────────────────────────────────────────────
# 7. Enable + start.
#
# `systemctl enable --now` is shorthand for `systemctl enable` (auto-start on
# boot) + `systemctl start` (start now). Idempotent — running it twice does
# nothing the second time.
# ──────────────────────────────────────────────────────────────────────────────
log "Enabling and starting nginx"
systemctl enable --now nginx

# ──────────────────────────────────────────────────────────────────────────────
# 8. Reload to pick up the new config.
#
# `reload` sends SIGHUP — nginx re-reads configs and gracefully drains old
# worker processes. `restart` would also work but briefly drops connections.
# On the very first invocation, nginx may have already been started in step 7
# with the new config — in that case reload is a no-op, which is fine.
# ──────────────────────────────────────────────────────────────────────────────
log "Reloading nginx"
systemctl reload nginx

# ──────────────────────────────────────────────────────────────────────────────
# 9. Final hint.
# ──────────────────────────────────────────────────────────────────────────────
cat <<'EOF'

============================================================
  nginx installed and reloaded.  (phase 1 of 2 — HTTP only)
============================================================

Verify locally on the host:
    curl -sI http://127.0.0.1/                # 200 if index.html present, else 404
    curl -sI http://127.0.0.1/api/topics       # 200 (if backend up) or 502 (if not)
    systemctl status nginx --no-pager | head

From your laptop (after deploy.sh has uploaded the SPA bundle):
    Open http://<your-EC2-public-DNS>/ in a browser.

If /api/ returns 502 with "Permission denied" in /var/log/nginx/error.log:
    SELinux blocked nginx -> 127.0.0.1:8080. Re-run:
        sudo setsebool -P httpd_can_network_connect 1

If / returns 403:
    SELinux label missing on /var/www/finnish. Re-run:
        sudo restorecon -R /var/www/finnish

------------------------------------------------------------
This host is HTTP-only by design. Phase 1 installs no certificate.
------------------------------------------------------------

Next: sudo bash deploy/backend/install-backend.sh   (if not already done),

then enable HTTPS (phase 2 — the DNS A record must ALREADY point at this host):
    sudo CERTBOT_EMAIL=you@example.com bash deploy/nginx/install-tls.sh

then build + deploy from your laptop:
    bash deploy/build.sh && bash deploy/deploy.sh

EOF