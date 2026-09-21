#!/usr/bin/env bash
#
# deploy/patch-os.sh — patch the Amazon Linux 2023 OS on the Finnish Learning EC2
# host, report what changed, and verify the app is still healthy afterwards.
#
# Run as root:  sudo bash deploy/patch-os.sh
#
# THIS SCRIPT NEVER REBOOTS. It only detects whether a reboot is needed.
#
# Configuration (override by exporting before invoking):
#   MIN_FREE_MB      minimum free MB on / before a transaction   (default 3072)
#   PATCH_LOG        transcript log path    (default /var/log/finnish-patch.log)
#   ASSUME_YES=1     skip the --release confirmation prompt
#
# Exit codes:
#   0  success — INCLUDING the reboot-required case. Read the banner.
#   1  precondition failed (not root, not AL2023, unknown argument, no dnf)
#   2  the dnf transaction failed; the system may be partially patched
#   3  insufficient free disk space on / — nothing was attempted
#   4  invalid --release value (malformed, or 'latest')
#   5  verification failed — a service is down RIGHT NOW

set -euo pipefail

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31m[err]\033[0m %s\n' "$*" >&2; exit "${2:-1}"; }

MIN_FREE_MB="${MIN_FREE_MB:-3072}"
PATCH_LOG="${PATCH_LOG:-/var/log/finnish-patch.log}"

# Declared once so the pre-flight dump and the verification cannot drift apart.
UNITS=(postgresql kafka finnish-backend nginx)

# State carried between steps. Initialised here because `set -u` is on.
MODE="patch"
TARGET_RELEASE=""
DRY_RUN=0
REBOOT_REQUIRED=0
VERIFY_FAILED=0
CURRENT_RELEASE=""
NEW_RELEASE=""
RUNNING_KERNEL=""
NEWEST_KERNEL=""
AVAIL_MB=0

usage() {
  cat <<'USAGE'
Usage: sudo bash deploy/patch-os.sh [MODE] [--dry-run]

Modes (mutually exclusive; default is --patch):
  --patch                 Upgrade all packages within the currently pinned release.
  --security-only         As --patch, but security updates only.
  --list-releases         Show available AL2023 releases and exit. Changes nothing.
  --release=<version>     Upgrade to a specific AL2023 release. Asks to confirm.
                          Example: --release=2023.12.20260914
  --verify-only           Run only the health checks. Changes nothing.
                          Use this after a manual reboot.

Flags:
  --dry-run               Preview only: print the full transaction and decline it.
  --help                  This message.

This script NEVER reboots. When a reboot is needed it says so and stops.

Typical use:
  sudo bash deploy/patch-os.sh --dry-run     # what would change?
  sudo bash deploy/patch-os.sh               # routine patch
  sudo reboot                                # if the banner says to
  sudo bash deploy/patch-os.sh --verify-only # after it comes back
USAGE
}

# 1. Parse arguments. Before the root check so --help works for any user.
while [[ $# -gt 0 ]]; do
  case "$1" in
    --patch)         MODE="patch" ;;
    --security-only) MODE="security" ;;
    --list-releases) MODE="list" ;;
    --verify-only)   MODE="verify" ;;
    --dry-run)       DRY_RUN=1 ;;
    --release=*)     MODE="release"; TARGET_RELEASE="${1#*=}" ;;
    --release)
      fail "--release needs a version attached, e.g. --release=2023.12.20260914
       To see what is available:  sudo bash deploy/patch-os.sh --list-releases" 1
      ;;
    -h|--help)       usage; exit 0 ;;
    *)               usage >&2; fail "Unknown argument: $1" 1 ;;
  esac
  shift
done

# 2. Preconditions. Root is needed even for --verify-only: reading unit state and
#    running `nginx -t` both require it.
if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  fail "Must run as root. Invoke: sudo bash deploy/patch-os.sh"
fi

if ! grep -q '^ID="amzn"' /etc/os-release 2>/dev/null; then
  fail "This script targets Amazon Linux 2023. Detected: $(. /etc/os-release && echo "$PRETTY_NAME")"
fi

command -v dnf >/dev/null \
  || fail "dnf not found. This does not look like an Amazon Linux 2023 host."

# 3. Start the transcript log — the only durable record that a patch happened.
#    If it is not writable, degrade to terminal-only rather than refusing to patch.
if touch "$PATCH_LOG" 2>/dev/null; then
  exec > >(tee -a "$PATCH_LOG") 2>&1
else
  warn "Cannot write $PATCH_LOG — continuing with terminal output only."
fi
printf '\n===== %s | mode=%s %s%s =====\n' \
  "$(date -Is)" "$MODE" "$TARGET_RELEASE" "$( ((DRY_RUN)) && echo ' [dry-run]' || true)"

# 4. Confirm a release upgrade: it replaces every package that changed between
#    two releases, so it does not run unattended.
if [[ "$MODE" == "release" ]] && (( DRY_RUN == 0 )) && [[ "${ASSUME_YES:-0}" != "1" ]]; then
  # log() rather than `read -p`: stdout is a tee pipe here and a `read -p` prompt
  # can sit on an unflushed buffer and never appear. `|| true` matters too — under
  # `set -e` a read that hits EOF (no TTY, e.g. `ssh host 'sudo bash ...'`) would
  # abort with exit 1 instead of taking the clean "aborted" path.
  log "About to upgrade the OS release to: $TARGET_RELEASE"
  log "This replaces every package that changed between releases. Type y to continue."
  read -r reply || true
  if [[ ! "${reply:-}" =~ ^[Yy]$ ]]; then
    log "Aborted by operator. Nothing was changed."
    exit 0
  fi
fi

# 5. Capture pre-flight state. Steps 9 and 11 diff against these.
#
#    `rpm -q system-release` is the authoritative read of the release pin. Do NOT
#    read this from /etc/os-release, which carries the major version, not the
#    dated release.
CURRENT_RELEASE="$(rpm -q system-release --qf '%{VERSION}\n')"
NEW_RELEASE="$CURRENT_RELEASE"
RUNNING_KERNEL="$(uname -r)"
AVAIL_MB="$(df --output=avail -m / | tail -1 | tr -d ' ')"

log "Pre-flight state"
log "  AL2023 release : $CURRENT_RELEASE"
log "  running kernel : $RUNNING_KERNEL"
log "  free on /      : ${AVAIL_MB} MB"
for unit in "${UNITS[@]}"; do
  log "  $unit: $(systemctl is-active "$unit" 2>/dev/null || true)"
done

# 6. Disk-space guard. dnf downloads every package into /var/cache/dnf before
#    installing anything; running / out of space mid-transaction is the worst
#    possible outcome.
if [[ "$MODE" != "verify" && "$MODE" != "list" ]] && (( AVAIL_MB < MIN_FREE_MB )); then
  fail "Only ${AVAIL_MB} MB free on / — need ${MIN_FREE_MB} MB.
       Reclaim with 'dnf clean all' / 'journalctl --vacuum-time=7d', or
       override the threshold with MIN_FREE_MB=<n>." 3
fi

# 7. --list-releases. Output is printed raw, never parsed: picking the version is
#    the operator's decision. `dnf check-release-update` exits non-zero when a
#    newer release exists — the normal case — hence `|| true`.
if [[ "$MODE" == "list" ]]; then
  log "Currently pinned to: $CURRENT_RELEASE"
  log "Available releases (dnf check-release-update):"
  dnf check-release-update || true
  log "Preview one with:  --release=<version> --dry-run, then drop --dry-run."
  log "Do NOT use 'latest' — it tests the OS update in production."
  exit 0
fi

# 8. Run the dnf transaction. The --release check below validates only the SHAPE
#    of the version; dnf is the authority on whether it exists, and a typo
#    surfaces as a metadata download error.
if [[ "$MODE" != "verify" ]]; then

  if [[ "$MODE" == "release" ]]; then
    if [[ "$TARGET_RELEASE" == "latest" ]]; then
      fail "--release=latest is refused: it is not reproducible. Pick a dated
       version from:  sudo bash deploy/patch-os.sh --list-releases" 4
    fi

    if [[ ! "$TARGET_RELEASE" =~ ^20[0-9]{2}\.[0-9]+\.[0-9]{8}$ ]]; then
      fail "'$TARGET_RELEASE' is not a valid release. Expected e.g. 2023.12.20260914.
       See:  sudo bash deploy/patch-os.sh --list-releases" 4
    fi
  fi

  case "$MODE" in
    security) DNF_ARGS=(upgrade --security) ;;
    patch)    DNF_ARGS=(upgrade) ;;
    release)  DNF_ARGS=(upgrade --releasever="$TARGET_RELEASE") ;;
    *)        fail "Internal error: unhandled mode '$MODE'" 1 ;;
  esac

  # --assumeno makes dnf resolve and print the full transaction, then decline it.
  # It exits non-zero because the operation was declined, hence `|| true`.
  if (( DRY_RUN == 1 )); then
    DNF_ARGS+=(--assumeno)
    log "DRY RUN: dnf ${DNF_ARGS[*]}"
    dnf "${DNF_ARGS[@]}" || true
    log "Dry run complete. NOTHING was changed. Re-run without --dry-run to apply."
    exit 0
  fi

  DNF_ARGS+=(-y)
  log "Running: dnf ${DNF_ARGS[*]}"
  dnf "${DNF_ARGS[@]}" || fail "dnf transaction failed. See the output above.
       With --release, the usual cause is a version that does not exist.
       The system may be partially patched; re-running is safe. To roll back:
         sudo dnf history info last  &&  sudo dnf history undo last" 2
fi

# 9. Report what changed. After a --release run the host is pinned to the NEW
#    value, because the transaction bumped the system-release package.
if [[ "$MODE" != "verify" ]]; then
  NEW_RELEASE="$(rpm -q system-release --qf '%{VERSION}\n')"

  if [[ "$NEW_RELEASE" != "$CURRENT_RELEASE" ]]; then
    log "Release: $CURRENT_RELEASE -> $NEW_RELEASE   (host is now pinned to $NEW_RELEASE)"
  else
    log "Release unchanged: $CURRENT_RELEASE"
  fi

  log "Transaction summary:"
  dnf history info last 2>/dev/null | head -40 || true

  warn "Kafka is NOT managed by dnf — /opt/kafka is a tarball install (pinned in"
  warn "deploy/kafka/install-kafka.sh) and was not touched by this run."
fi

# 10. Config-file collisions. The highest-value check here.
#
#     Some nginx point releases reinstate /etc/nginx/conf.d/default.conf with its
#     own `listen 80 default_server`, which collides with finnish.conf and makes
#     `nginx -t` fail. dnf restarts nothing, so the running nginx keeps serving
#     from its loaded config and the breakage stays invisible until the next
#     reboot — by which point the site is down and the cause is an hour behind you.
log "Checking for package-manager config collisions"

if [[ -f /etc/nginx/conf.d/default.conf ]]; then
  warn "An nginx upgrade has reinstated /etc/nginx/conf.d/default.conf."
  warn "Its 'listen 80 default_server' collides with finnish.conf and will make"
  warn "nginx -t fail at the next restart or reboot."
  warn "Fix:  sudo mv /etc/nginx/conf.d/default.conf /etc/nginx/conf.d/default.conf.disabled"
  VERIFY_FAILED=1
fi

# The escaped parentheses matter: without them -o binds loosely and find returns
# the wrong set.
RPM_NEW="$(find /etc \( -name '*.rpmnew' -o -name '*.rpmsave' \) 2>/dev/null || true)"
if [[ -n "$RPM_NEW" ]]; then
  warn "The package manager left config files needing manual review:"
  # shellcheck disable=SC2086  # deliberate word-splitting: one path per line
  printf '         %s\n' $RPM_NEW >&2
fi

if command -v nginx >/dev/null && ! nginx -t >/dev/null 2>&1; then
  warn "nginx -t FAILS right now. The running nginx is unaffected (it serves from"
  warn "memory), but it will NOT come back after a restart. Fix BEFORE rebooting:"
  warn "    sudo nginx -t"
  VERIFY_FAILED=1
fi

# 11. Reboot detection: running kernel vs newest installed, plus needs-restarting
#     (which also catches glibc/openssl-class updates). needs-restarting comes
#     from dnf-utils and may be absent — degrade rather than install it silently.
NEWEST_KERNEL="$(rpm -q kernel --qf '%{VERSION}-%{RELEASE}.%{ARCH}\n' | sort -V | tail -1)"

if [[ "$RUNNING_KERNEL" != "$NEWEST_KERNEL" ]]; then
  REBOOT_REQUIRED=1
fi

if command -v needs-restarting >/dev/null 2>&1; then
  needs-restarting -r >/dev/null 2>&1 || REBOOT_REQUIRED=1
  log "Services running outdated libraries:"
  needs-restarting -s 2>/dev/null || true
else
  warn "needs-restarting is not installed, so only the kernel was checked."
  warn "Userspace services may still be running pre-patch code in memory."
  warn "For a fuller picture:  sudo dnf install -y dnf-utils"
fi

# 12. Verify services.
#
#     /api-docs matches the smoke test deploy/deploy.sh already uses. For nginx
#     both 200 and 404 pass: 404 only means the SPA bundle is not uploaded yet.
#     000 means nothing answered at all, which is a real failure.
check_backend() {
  curl -sS -o /dev/null -w '%{http_code}' --max-time 10 \
    http://127.0.0.1:8080/api-docs 2>/dev/null || true
}

log "Verifying services"
for unit in "${UNITS[@]}"; do
  if systemctl is-active --quiet "$unit"; then
    log "  $unit: active"
  else
    warn "  $unit: $(systemctl is-active "$unit" 2>/dev/null || echo unknown)"
    VERIFY_FAILED=1
  fi
done

BACKEND_CODE="$(check_backend)"

# Retry ONLY after a reboot: finnish-backend legitimately takes up to ~180s to come
# up (migrations at startup, and kafka.service is Type=simple so systemd marks it
# active ~30s before the broker listens; the backend restart-loops across that gap
# by design). On the post-patch path nothing was stopped, so a failure is real
# immediately and must not be masked by a retry.
if [[ "$MODE" == "verify" && "$BACKEND_CODE" != "200" ]]; then
  log "  backend: HTTP $BACKEND_CODE — retrying, it can take ~180s after a reboot"
  for attempt in {1..12}; do
    sleep 10
    BACKEND_CODE="$(check_backend)"
    log "    attempt $attempt/12: HTTP $BACKEND_CODE"
    if [[ "$BACKEND_CODE" == "200" ]]; then
      break
    fi
  done
fi

if [[ "$BACKEND_CODE" == "200" ]]; then
  log "  backend /api-docs: HTTP 200"
else
  warn "  backend /api-docs: HTTP $BACKEND_CODE (expected 200)"
  warn "  Investigate with:  sudo journalctl -u finnish-backend -n 50 --no-pager"
  VERIFY_FAILED=1
fi

NGINX_CODE="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 \
  http://127.0.0.1/ 2>/dev/null || true)"

case "$NGINX_CODE" in
  200) log "  nginx /: HTTP 200" ;;
  404) log "  nginx /: HTTP 404 (SPA bundle not uploaded — not a patching problem)" ;;
  *)   warn "  nginx /: HTTP $NGINX_CODE (expected 200 or 404)"
       VERIFY_FAILED=1 ;;
esac

# A renewed certificate nginx never reloads, or a timer that quietly stopped, is
# the classic silent failure on a TLS host.
if systemctl list-unit-files 2>/dev/null | grep -q certbot-renew.timer; then
  if systemctl is-active --quiet certbot-renew.timer; then
    log "  certbot-renew.timer: active"
  else
    warn "  certbot-renew.timer is NOT active. TLS renewal will not happen automatically."
    warn "  Re-arm with:  sudo systemctl enable --now certbot-renew.timer"
  fi
fi

# 13. Final banner. The exit-5 check comes AFTER it on purpose: even when
#     verification failed, the operator still needs the reboot guidance.
if (( REBOOT_REQUIRED == 1 )); then
  cat <<EOF

============================================================
  Patching complete.  ACTION REQUIRED: REBOOT
============================================================
  release:  $CURRENT_RELEASE -> $NEW_RELEASE
  kernel:   running $RUNNING_KERNEL / installed $NEWEST_KERNEL

The new kernel is on disk but NOT running.

    sudo nginx -t        # must pass, or the site will not come back
    sudo reboot          # you decide when

Then wait ~2 minutes and verify:
    sudo bash deploy/patch-os.sh --verify-only

Expect 1-3 minutes of 502s on /api/ after the reboot — the backend retries
until Kafka is listening. That is normal.

Transcript: $PATCH_LOG
EOF
else
  cat <<EOF

============================================================
  Patching complete.  No reboot required.
============================================================
  release:  $CURRENT_RELEASE -> $NEW_RELEASE
  kernel:   $RUNNING_KERNEL (current)

Transcript: $PATCH_LOG
EOF
fi

if [[ "$NEW_RELEASE" == "$CURRENT_RELEASE" && "$MODE" != "verify" ]]; then
  log "The release pin did not move — a plain patch only installs updates from"
  log "inside the pinned snapshot. Check for newer ones:  --list-releases"
fi

if (( VERIFY_FAILED == 1 )); then
  warn "One or more verification checks failed. See the warnings above."
  exit 5
fi
