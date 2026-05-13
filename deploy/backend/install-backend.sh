#!/usr/bin/env bash
#
# deploy/backend/install-backend.sh — Install the finnish-backend systemd unit
# and seed the environment file on a freshly provisioned Amazon Linux 2023 EC2 host.
#
# Run AFTER:
#   - deploy/provision-ec2.sh — creates the 'finnish' user, directories,
#                                and the Postgres role/database.
#   - deploy/kafka/install-kafka.sh — installs and starts Kafka.
#
# Run BEFORE:
#   - deploy/deploy.sh — uploads the jar and restarts the service.
#
# Usage:
#     sudo bash deploy/backend/install-backend.sh
#
# Idempotent:
#   - Re-running NEVER overwrites a populated /etc/finnish-backend.env.
#   - Re-running ALWAYS refreshes /etc/systemd/system/finnish-backend.service
#     (so edits to the .service file in this repo take effect after re-run).
#
# What it does:
#   1. Refuse to run unless root + Amazon Linux 2023.
#   2. Verify that provision-ec2.sh has run (looks for the 'finnish' user).
#   3. Copy finnish-backend.service → /etc/systemd/system/.
#   4. If /etc/finnish-backend.env does NOT exist, copy the .example to it with
#      mode 600 / root:finnish ownership. Print a loud reminder to fill in the
#      CHANGE_ME values.
#   5. systemctl daemon-reload + systemctl enable finnish-backend.
#   6. Do NOT start the service — the jar is not on disk yet. We starts it in a later step.
#
# Exit codes:
#   0  success
#   1  precondition failed (not root, not AL2023, finnish user missing)
#

set -euo pipefail

# ──────────────────────────────────────────────────────────────────────────────
# Logging helpers.
# ──────────────────────────────────────────────────────────────────────────────
log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31m[err]\033[0m %s\n' "$*" >&2; exit 1; }

# ──────────────────────────────────────────────────────────────────────────────
# 1. Preconditions: root + AL2023.
# ──────────────────────────────────────────────────────────────────────────────
if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  fail "Must run as root. Invoke: sudo bash deploy/backend/install-backend.sh"
fi

if ! grep -q '^ID="amzn"' /etc/os-release 2>/dev/null; then
  fail "This script targets Amazon Linux 2023. Detected: $(. /etc/os-release && echo "$PRETTY_NAME")"
fi

# ──────────────────────────────────────────────────────────────────────────────
# 2. Verify provision-ec2.sh has run (we depend on the 'finnish' user).
# ──────────────────────────────────────────────────────────────────────────────
if ! id -u finnish >/dev/null 2>&1; then
  fail "User 'finnish' not found. Run deploy/provision-ec2.sh first (TASK_04)."
fi

if [[ ! -d /opt/finnish ]]; then
  fail "/opt/finnish missing. Run deploy/provision-ec2.sh first (TASK_04)."
fi

# Resolve the directory this script lives in so it works regardless of cwd.
# `BASH_SOURCE[0]` is the path of the script; `dirname` strips the filename;
# `readlink -f` resolves symlinks and gives an absolute path.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UNIT_SRC="$SCRIPT_DIR/finnish-backend.service"
ENV_SRC="$SCRIPT_DIR/finnish-backend.env.example"

if [[ ! -f "$UNIT_SRC" ]]; then
  fail "Missing $UNIT_SRC — is the deploy/ directory complete?"
fi
if [[ ! -f "$ENV_SRC" ]]; then
  fail "Missing $ENV_SRC — is the deploy/ directory complete?"
fi

# ──────────────────────────────────────────────────────────────────────────────
# 3. Install the systemd unit.
#
# We always overwrite /etc/systemd/system/finnish-backend.service so edits in
# the repo propagate to the host on re-run. Unlike the env file, the unit has
# no secrets and is checked into git, so blowing it away is safe.
# ──────────────────────────────────────────────────────────────────────────────
UNIT_DST=/etc/systemd/system/finnish-backend.service
log "Installing systemd unit → $UNIT_DST"
install -m 0644 -o root -g root "$UNIT_SRC" "$UNIT_DST"

# ──────────────────────────────────────────────────────────────────────────────
# 4. Seed the env file ONLY if absent. Never clobber operator edits.
#
# `install` with -m 0600 sets restrictive perms in one shot (no race window
# between `cp` and `chmod`). Group `finnish` so the backend can read it.
# Owner `root` so only root can edit it.
# ──────────────────────────────────────────────────────────────────────────────
ENV_DST=/etc/finnish-backend.env
if [[ ! -f "$ENV_DST" ]]; then
  log "Seeding $ENV_DST from .example (mode 600, owner root:finnish)"
  install -m 0600 -o root -g finnish "$ENV_SRC" "$ENV_DST"
  cat >&2 <<'WARN'

  ┌──────────────────────────────────────────────────────────────────────┐
  │  ACTION REQUIRED                                                     │
  │                                                                      │
  │  /etc/finnish-backend.env was created from the template.             │
  │  Edit it now and replace every CHANGE_ME value:                      │
  │                                                                      │
  │      sudo $EDITOR /etc/finnish-backend.env                           │
  │                                                                      │
  │  Required values:                                                    │
  │    SPRING_DATASOURCE_PASSWORD — same password you set via            │
  │        sudo -u postgres psql -c "ALTER USER finnish WITH PASSWORD…"  │
  │    JWT_SECRET                 — generate with:                       │
  │        openssl rand -base64 32                                       │
  │                                                                      │
  │  The service will NOT start until both are set.                      │
  └──────────────────────────────────────────────────────────────────────┘

WARN
else
  log "$ENV_DST already exists — leaving it untouched"
fi

# ──────────────────────────────────────────────────────────────────────────────
# 5. Reload systemd's view of unit files, then enable the service.
#
# `daemon-reload` is required after ANY edit to /etc/systemd/system/*.service —
# without it, systemctl still has the old version cached.
#
# `enable` (without --now) only creates the boot-time symlink. We deliberately
# do NOT start the service here because /opt/finnish/app.jar does not exist
# yet — the jar is uploaded by deploy/deploy.sh, which also issues
# the first `systemctl start`.
# ──────────────────────────────────────────────────────────────────────────────
log "Reloading systemd and enabling finnish-backend on boot"
systemctl daemon-reload
systemctl enable finnish-backend.service

# ──────────────────────────────────────────────────────────────────────────────
# 6. Summary.
# ──────────────────────────────────────────────────────────────────────────────
cat <<'EOF'

============================================================
  Backend systemd unit installed.
============================================================

Status now:
    systemctl is-enabled finnish-backend   →  enabled
    systemctl is-active  finnish-backend   →  inactive (no jar yet)

Next steps:

  1. (If you have not already) edit /etc/finnish-backend.env and replace
     every CHANGE_ME value.

  2. From your laptop, build artefacts and upload to this host:
         bash deploy/build.sh
         bash deploy/deploy.sh   # uploads jar + dist, restarts services

  3. Watch the backend come up:
         journalctl -u finnish-backend -f
     Look for: "Started FinnishLearningBackendApplication in N seconds"

  4. Smoke test from the host:
         curl -i http://127.0.0.1:8080/api/topics
     Expect: HTTP/1.1 200 OK with a JSON array (possibly empty).

EOF