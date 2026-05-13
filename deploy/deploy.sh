#!/usr/bin/env bash
#
# deploy/deploy.sh — Upload backend jar + frontend bundle to the configured
# EC2 host and restart the backend service.
#
# Run from anywhere on your laptop (the script resolves the repo root itself):
#     bash deploy/deploy.sh
#
# Prerequisites:
#   1. Artefacts already built: deploy/build.sh has produced
#        deploy/artefacts/finnish-backend.jar
#        deploy/artefacts/dist/index.html
#   2. EC2 host already provisioned: deploy/provision-ec2.sh + kafka/install-kafka.sh
#      + backend/install-backend.sh + nginx/install-nginx.sh have all run on the host.
#   3. deploy/.deploy.env present (copy from .deploy.env.example).
#
# What it does (each step is idempotent — re-running redeploys cleanly):
#   1. Resolve repo root, load .deploy.env, verify required variables.
#   2. Verify artefacts exist on the laptop.
#   3. Expand `~` in SSH_KEY, verify the key file is readable.
#   4. Upload the jar to /tmp/app.jar on the host, then sudo-install it to
#      /opt/finnish/app.jar with owner finnish:finnish and mode 0644.
#   5. Rsync the SPA bundle into /tmp/dist on the host, then sudo-rsync it into
#      /var/www/finnish/dist with owner nginx:nginx and the correct SELinux label
#      (restorecon -R re-applies httpd_sys_content_t from the rule that was set previously).
#   6. Restart the finnish-backend systemd unit.
#   7. Smoke-test http://127.0.0.1:8080/api-docs from the host (up to ~20s).
#      Dump the unit's journal if the smoke test fails.
#   8. Print the public URL.
#
# Exit codes:
#   0  deploy succeeded
#   1  precondition failed (missing env var, missing artefact, unreadable key)
#   2  remote command failed (scp, rsync, ssh, sudo, systemctl, smoke test)

set -euo pipefail

# ──────────────────────────────────────────────────────────────────────────────
# Logging helpers — same style as deploy/build.sh and deploy/kafka/install-kafka.sh.
# ──────────────────────────────────────────────────────────────────────────────
log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31m[err]\033[0m %s\n' "$*" >&2; exit "${2:-1}"; }

# ──────────────────────────────────────────────────────────────────────────────
# 1. Resolve repo root and load .deploy.env.
#
# `git rev-parse --show-toplevel` returns the absolute path to the repo root,
# so the script works regardless of where the operator invokes it from.
# `set -a` makes every variable assigned in the sourced file automatically
# exported — equivalent to writing `export EC2_HOST=...` in the file itself.
# ──────────────────────────────────────────────────────────────────────────────
REPO_ROOT="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel 2>/dev/null \
              || fail "Could not locate repo root. Run inside the monorepo.")"

DEPLOY_ENV="$REPO_ROOT/deploy/.deploy.env"
ARTEFACTS_DIR="$REPO_ROOT/deploy/artefacts"

if [[ ! -f "$DEPLOY_ENV" ]]; then
  fail "$DEPLOY_ENV not found. Copy deploy/.deploy.env.example and fill it in."
fi

log "Loading $DEPLOY_ENV"
set -a
# shellcheck disable=SC1090
source "$DEPLOY_ENV"
set +a

# All three are required. We check explicitly so the error is friendlier than
# `set -u` complaining about an unbound variable mid-script.
: "${EC2_HOST:?EC2_HOST not set in $DEPLOY_ENV}"
: "${SSH_KEY:?SSH_KEY not set in $DEPLOY_ENV}"
: "${SSH_USER:?SSH_USER not set in $DEPLOY_ENV (use ec2-user for Amazon Linux 2023)}"

# ──────────────────────────────────────────────────────────────────────────────
# 2. Verify artefacts.
#
# The jar is the single source-of-truth artefact for the backend. The SPA
# bundle is a directory — checking index.html is enough to confirm a real Vite
# build produced files (an empty dist/ would have no index.html).
# ──────────────────────────────────────────────────────────────────────────────
JAR="$ARTEFACTS_DIR/finnish-backend.jar"
DIST="$ARTEFACTS_DIR/dist"

[[ -f "$JAR" ]]            || fail "Missing $JAR. Run deploy/build.sh first."
[[ -f "$DIST/index.html" ]] || fail "Missing $DIST/index.html. Run deploy/build.sh first."

log "Backend jar:  $(ls -lh "$JAR" | awk '{print $5,$9}')"
log "Frontend dir: $(du -sh "$DIST" | awk '{print $1,$2}')"

# ──────────────────────────────────────────────────────────────────────────────
# 3. Resolve ~ in SSH_KEY; verify the key file.
#
# Bash does NOT expand ~ inside a variable that was assigned by `source` —
# it only expands ~ at the moment of an unquoted literal. So we expand it
# manually here. Without this, SSH_KEY=~/.ssh/finnish-demo.pem stays as the
# literal string "~/.ssh/finnish-demo.pem" and scp would error "no such file".
# ──────────────────────────────────────────────────────────────────────────────
SSH_KEY="${SSH_KEY/#\~/$HOME}"

[[ -r "$SSH_KEY" ]] || fail "SSH key not readable: $SSH_KEY"

# SSH refuses to use a private key with permissions broader than 0600. If the
# key was accidentally chmod'd looser, ssh prints a long WARNING that's easy
# to miss and then exits. Fail fast here with a clearer error.
KEY_MODE="$(stat -c '%a' "$SSH_KEY" 2>/dev/null || stat -f '%Lp' "$SSH_KEY")"
if [[ "$KEY_MODE" != "600" && "$KEY_MODE" != "400" ]]; then
  fail "SSH key has loose permissions ($KEY_MODE). Run: chmod 600 $SSH_KEY"
fi

# Common SSH options we reuse on every invocation. -o BatchMode=yes disables
# password prompts (we want a clean failure, not a stuck process if the key
# is rejected). StrictHostKeyChecking=accept-new auto-trusts on first connect
# but refuses to silently accept a CHANGED host key (which would suggest a MITM).
SSH_OPTS=(-i "$SSH_KEY"
          -o BatchMode=yes
          -o StrictHostKeyChecking=accept-new
          -o ConnectTimeout=10)

REMOTE="$SSH_USER@$EC2_HOST"

# ──────────────────────────────────────────────────────────────────────────────
# 4. Upload the backend jar (laptop → /tmp/app.jar → /opt/finnish/app.jar).
#
# The two-step is unavoidable: the SSH user is unprivileged and cannot write
# to /opt/finnish directly. `install -o finnish -g finnish -m 644` is one
# syscall — there is no window where the file exists with the wrong owner.
#
# We do NOT stop the service before installing the new jar. Spring Boot reads
# the jar at JVM startup and keeps the classloader resident; overwriting the
# file on disk does not affect the running process. The restart in step 6
# is when the new bytes take effect.
# ──────────────────────────────────────────────────────────────────────────────
log "Uploading jar to $REMOTE:/tmp/app.jar"
scp "${SSH_OPTS[@]}" "$JAR" "$REMOTE:/tmp/app.jar"

log "Installing /tmp/app.jar -> /opt/finnish/app.jar (owner finnish:finnish, mode 0644)"
ssh "${SSH_OPTS[@]}" "$REMOTE" 'bash -se' <<'REMOTE_SCRIPT'
set -euo pipefail
sudo install -o finnish -g finnish -m 644 /tmp/app.jar /opt/finnish/app.jar
rm -f /tmp/app.jar
REMOTE_SCRIPT

# ──────────────────────────────────────────────────────────────────────────────
# 5. Sync the SPA bundle (laptop → /tmp/dist → /var/www/finnish/dist).
#
# Trailing slashes matter to rsync: "$DIST/" copies the CONTENTS of dist/,
# not the dist/ directory itself. Same convention on the remote side.
#
# --delete on both rsync calls so removed files (e.g. an old hashed JS chunk)
# don't linger.
#
# --chown=nginx:nginx is an rsync feature, not a system one — it sets the
# owner in the destination during the copy (root sudo'ing into the remote
# rsync, so the chown succeeds).
#
# restorecon -R re-applies the SELinux label /var/www/finnish was registered
# with (The provisioning step ran `semanage fcontext -a -t httpd_sys_content_t '/var/www/finnish(/.*)?'`).
# Without it, freshly written files inherit the default `var_t` label and
# nginx returns 403.
# ──────────────────────────────────────────────────────────────────────────────
log "Uploading dist/ to $REMOTE:/tmp/dist/"
rsync -az --delete -e "ssh ${SSH_OPTS[*]}" "$DIST/" "$REMOTE:/tmp/dist/"

log "Syncing /tmp/dist/ -> /var/www/finnish/dist/ (owner nginx:nginx, SELinux relabel)"
ssh "${SSH_OPTS[@]}" "$REMOTE" 'bash -se' <<'REMOTE_SCRIPT'
set -euo pipefail
sudo rsync -a --delete --chown=nginx:nginx /tmp/dist/ /var/www/finnish/dist/
sudo restorecon -R /var/www/finnish/dist
rm -rf /tmp/dist
REMOTE_SCRIPT

# ──────────────────────────────────────────────────────────────────────────────
# 6. Restart the backend.
#
# `systemctl restart` returns immediately after the unit transitions to
# `activating` — NOT after the JVM has bound :8080. That's what step 7
# verifies.
# ──────────────────────────────────────────────────────────────────────────────
log "Restarting finnish-backend.service"
ssh "${SSH_OPTS[@]}" "$REMOTE" "sudo systemctl restart finnish-backend"

# ──────────────────────────────────────────────────────────────────────────────
# 7. Smoke test.
#
# Poll /api-docs (SpringDoc OpenAPI JSON; always available, no auth) for up to
# 20 seconds. On a t3.micro under swap pressure the JVM can take ~15 s.
#
# On failure: dump the last 50 lines of the unit's journal so the operator
# can see the actual error without SSHing in.
# ──────────────────────────────────────────────────────────────────────────────
log "Smoke-testing http://127.0.0.1:8080/api-docs (up to 20s)"
if ! ssh "${SSH_OPTS[@]}" "$REMOTE" 'bash -se' <<'REMOTE_SCRIPT'; then
set -euo pipefail
for i in $(seq 1 10); do
  if curl -fsS http://127.0.0.1:8080/api-docs >/dev/null 2>&1; then
    echo "Backend is up (attempt $i)."
    exit 0
  fi
  sleep 2
done
echo "Backend did NOT respond on :8080 within 20s. Last 50 log lines:" >&2
sudo journalctl -u finnish-backend -n 50 --no-pager >&2
exit 1
REMOTE_SCRIPT
  fail "Smoke test failed. See journal output above." 2
fi

# ──────────────────────────────────────────────────────────────────────────────
# 8. Done.
# ──────────────────────────────────────────────────────────────────────────────
cat <<EOF

============================================================
  Deployed.
============================================================

URL:        http://$EC2_HOST/
Backend:    restarted, /api-docs responding
Frontend:   /var/www/finnish/dist synced (owner nginx, SELinux relabelled)

Tail logs from the laptop:
    ssh -i $SSH_KEY $REMOTE 'sudo journalctl -u finnish-backend -f'

Re-deploy after the next build:
    bash deploy/build.sh && bash deploy/deploy.sh
EOF