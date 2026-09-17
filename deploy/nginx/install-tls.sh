#!/usr/bin/env bash
#
# deploy/nginx/install-tls.sh — Phase 2 of the nginx install: obtain a Let's Encrypt
# certificate and swap the HTTP-only bootstrap vhost for the TLS vhost.
#
# Run as root, AFTER install-nginx.sh:
#     sudo bash deploy/nginx/install-tls.sh
#
# PREREQUISITE THAT IS NOT CHECKABLE FROM THIS SCRIPT: the domain's DNS A record
# must already point at THIS instance's public IP. The HTTP-01 challenge is
# fetched by Let's Encrypt over the public internet — if the A record still points
# at an old instance, the challenge is served by that host and issuance fails with
# "Invalid response from http://<domain>/.well-known/acme-challenge/...".
#
# Idempotent. Re-running with a valid certificate in place skips issuance (so it
# cannot burn a Let's Encrypt rate-limit slot) and simply re-installs the vhost.
#
# Why certonly --webroot and not --nginx:
#   The --nginx plugin edits /etc/nginx/conf.d/finnish.conf IN PLACE. That is the
#   single behaviour that produced the config drift this two-phase split exists to
#   fix: the :443 block lived only on the host and was wiped by the next
#   install-nginx.sh run. certonly --webroot obtains the certificate and touches no
#   nginx config at all, leaving this repo as the only source of truth for the vhost.
#
# What it does (in order):
#   1. Preconditions: root, AL2023, nginx installed AND running, sibling conf present.
#   2. Assert the cert lineage in finnish-tls.conf matches $DOMAINS. Fail loudly if not.
#   3. Require an email for expiry notices (or an explicit opt-out).
#   4. dnf install certbot.
#   5. Create + SELinux-label the ACME webroot.
#   6. certbot certonly --webroot (skipped if the lineage already exists).
#   7. Install the renewal deploy hook that reloads nginx.
#   8. Back up the live vhost, install finnish-tls.conf over it.
#   9. nginx -t — on failure, RESTORE the backup and abort without reloading.
#  10. systemctl reload nginx.
#  11. Verify renewal: certbot renew --dry-run + the systemd timer.
#  12. Print verification commands.
#
# Configuration (override by exporting before invoking):
#   DOMAINS        space-separated; the FIRST one names the certificate lineage
#                  and MUST match the ssl_certificate path in finnish-tls.conf
#   CERTBOT_EMAIL  where Let's Encrypt sends expiry warnings
#   ACME_WEBROOT   directory certbot writes challenge tokens into
#
# Exit codes:
#   0  success
#   1  precondition failed (not root, not AL2023, nginx down, lineage mismatch,
#      missing email)
#   2  nginx -t failed — previous config RESTORED, nginx NOT reloaded
#   3  certbot failed to obtain the certificate

set -euo pipefail

# ──────────────────────────────────────────────────────────────────────────────
# Logging helpers — same style as install-nginx.sh and install-kafka.sh.
# ──────────────────────────────────────────────────────────────────────────────
log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31m[err]\033[0m %s\n' "$*" >&2; exit "${2:-1}"; }

# ──────────────────────────────────────────────────────────────────────────────
# Configuration.
# ──────────────────────────────────────────────────────────────────────────────
DOMAINS="${DOMAINS:-opisuomea.org www.opisuomea.org}"
CERTBOT_EMAIL="${CERTBOT_EMAIL:-}"
ACME_WEBROOT="${ACME_WEBROOT:-/var/www/letsencrypt}"

# read -a splits on IFS into an array. Quoting the expansion later keeps each
# domain a single argv entry even if one ever contains something exotic.
read -r -a DOMAIN_LIST <<< "$DOMAINS"
PRIMARY_DOMAIN="${DOMAIN_LIST[0]}"
LIVE_DIR="/etc/letsencrypt/live/$PRIMARY_DOMAIN"

# ──────────────────────────────────────────────────────────────────────────────
# 1. Preconditions.
#
# The nginx-is-RUNNING check is not pedantry: the HTTP-01 challenge is served by
# nginx on :80. If phase 1 was skipped or nginx died, certbot fails with a
# connection error that reads like a DNS or firewall problem, costing an hour.
# ──────────────────────────────────────────────────────────────────────────────
if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  fail "Must run as root. Invoke: sudo bash deploy/nginx/install-tls.sh"
fi

if ! grep -q '^ID="amzn"' /etc/os-release 2>/dev/null; then
  fail "This script targets Amazon Linux 2023. Detected: $(. /etc/os-release && echo "$PRETTY_NAME")"
fi

command -v nginx >/dev/null \
  || fail "nginx is not installed. Run deploy/provision-ec2.sh first."

systemctl is-active --quiet nginx \
  || fail "nginx is not running. Run deploy/nginx/install-nginx.sh (phase 1) first — \
the HTTP-01 challenge needs a live listener on :80."

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TLS_CONF_SRC="$SCRIPT_DIR/finnish-tls.conf"

[[ -f "$TLS_CONF_SRC" ]] \
  || fail "Missing sibling file: $TLS_CONF_SRC"

# ──────────────────────────────────────────────────────────────────────────────
# 2. Assert the lineage matches.
#
# certbot names the certificate directory after the FIRST -d argument. If
# $DOMAINS is reordered or changed without editing finnish-tls.conf, nginx would
# point ssl_certificate at a path that does not exist and nginx -t would fail in
# step 9 — after the certificate had already been issued. Catching it here turns
# a confusing late failure into a one-line message before anything is spent.
# ──────────────────────────────────────────────────────────────────────────────
if ! grep -q "ssl_certificate .*/etc/letsencrypt/live/$PRIMARY_DOMAIN/" "$TLS_CONF_SRC"; then
  fail "Lineage mismatch. DOMAINS starts with '$PRIMARY_DOMAIN', but $TLS_CONF_SRC does not
       reference /etc/letsencrypt/live/$PRIMARY_DOMAIN/.
       Edit the ssl_certificate / ssl_certificate_key paths in that file, or reorder DOMAINS
       so the lineage host comes first."
fi

log "Domains:       ${DOMAIN_LIST[*]}"
log "Lineage:       $LIVE_DIR"
log "ACME webroot:  $ACME_WEBROOT"

# ──────────────────────────────────────────────────────────────────────────────
# 3. Email.
#
# Without a registered address Let's Encrypt cannot warn you about an expiring
# certificate — and a silent expiry is the exact failure mode this whole task
# exists to prevent. We therefore require an explicit decision rather than
# defaulting to the unsafe option.
# ──────────────────────────────────────────────────────────────────────────────
if [[ -z "$CERTBOT_EMAIL" ]]; then
  fail "CERTBOT_EMAIL is not set. Let's Encrypt sends expiry warnings there.
       Run:  sudo CERTBOT_EMAIL=you@example.com bash deploy/nginx/install-tls.sh
       To register without an address anyway (NOT recommended):
             sudo CERTBOT_EMAIL=none bash deploy/nginx/install-tls.sh"
fi

if [[ "$CERTBOT_EMAIL" == "none" ]]; then
  warn "Registering without an email. You will get NO expiry warnings."
  EMAIL_ARGS=(--register-unsafely-without-email)
else
  EMAIL_ARGS=(-m "$CERTBOT_EMAIL")
fi

# ──────────────────────────────────────────────────────────────────────────────
# 4. Install certbot.
#
# `certbot` alone — NOT python3-certbot-nginx. The nginx plugin exists to edit
# nginx config, which is the behaviour we are eliminating. The webroot
# authenticator ships in certbot core.
#
# dnf install -y is idempotent: an already-installed package is a no-op.
# ──────────────────────────────────────────────────────────────────────────────
log "Installing certbot"
dnf install -y certbot || fail "dnf install certbot failed. See output above." 3

# ──────────────────────────────────────────────────────────────────────────────
# 5. ACME webroot.
#
# A dedicated directory, NOT /var/www/finnish/dist. deploy.sh runs
# `rsync -a --delete` over the SPA directory, which would sweep away anything
# ACME left behind. Nothing else touches /var/www/letsencrypt.
#
# SELinux: AL2023 runs enforcing. Files under /var/www are var_t by default,
# which httpd may not read — the same trap provision-ec2.sh step 8 handles for
# /var/www/finnish. semanage records the rule permanently so a future
# `restorecon /` cannot silently break renewal; restorecon applies it now.
# semanage exits non-zero on a duplicate rule, hence the `|| true`.
# ──────────────────────────────────────────────────────────────────────────────
log "Creating ACME webroot $ACME_WEBROOT"
mkdir -p "$ACME_WEBROOT/.well-known/acme-challenge"
chown -R nginx:nginx "$ACME_WEBROOT"
chmod 755 "$ACME_WEBROOT"

if command -v semanage >/dev/null 2>&1; then
  semanage fcontext -a -t httpd_sys_content_t "${ACME_WEBROOT}(/.*)?" 2>/dev/null || true
fi
restorecon -R "$ACME_WEBROOT" 2>/dev/null || true

# ──────────────────────────────────────────────────────────────────────────────
# 6. Renewal deploy hook — INSTALL THIS BEFORE ISSUANCE.
#
# THE most important step in this script.
#
# `certbot --nginx` reloads nginx itself after a renewal. `certbot certonly` does
# NOT. Without a hook, the certificate renews on disk at ~day 60 while the running
# nginx keeps serving the expiring one from memory until it actually expires at
# day 90 — a silent failure that surfaces as a browser warning on a live site.
#
# We use the GLOBAL hook directory rather than a --deploy-hook CLI flag. The flag
# is persisted into renewal/<domain>.conf at ISSUANCE time only, so on a re-run
# where step 7 skips issuance nothing would register it. certbot executes every
# executable in /etc/letsencrypt/renewal-hooks/deploy/ after any successful
# renewal, which is correct whichever path this script took.
# ──────────────────────────────────────────────────────────────────────────────
HOOK_DIR=/etc/letsencrypt/renewal-hooks/deploy
HOOK_FILE="$HOOK_DIR/reload-nginx.sh"

log "Installing renewal deploy hook $HOOK_FILE"
mkdir -p "$HOOK_DIR"
cat > "$HOOK_FILE" <<'HOOK'
#!/bin/sh
# Installed by deploy/nginx/install-tls.sh — do not edit by hand.
#
# certbot runs every executable in this directory after a successful renewal.
# nginx caches the certificate in memory at startup, so without this reload a
# renewed certificate would sit unused on disk until the old one expired.
systemctl reload nginx
HOOK
chmod 0755 "$HOOK_FILE"

# ──────────────────────────────────────────────────────────────────────────────
# 7. Obtain the certificate.
#
# Guarded on the lineage directory so a re-run does not request a new certificate
# — Let's Encrypt rate-limits duplicate certificates to 5 per week, and burning
# that on repeated runs is a real way to lock yourself out for days.
#
# --non-interactive + --agree-tos because this runs unattended.
# --keep-until-expiring is belt-and-braces: even if the guard is bypassed,
# certbot declines to reissue a certificate that is not near expiry.
# ──────────────────────────────────────────────────────────────────────────────
CERT_ARGS=()
for d in "${DOMAIN_LIST[@]}"; do
  CERT_ARGS+=(-d "$d")
done

if [[ -d "$LIVE_DIR" ]]; then
  log "Certificate already exists at $LIVE_DIR — skipping issuance"
else
  log "Requesting certificate from Let's Encrypt (HTTP-01 via $ACME_WEBROOT)"
  certbot certonly \
    --webroot -w "$ACME_WEBROOT" \
    "${CERT_ARGS[@]}" \
    "${EMAIL_ARGS[@]}" \
    --non-interactive \
    --agree-tos \
    --keep-until-expiring \
    || fail "certbot failed to obtain the certificate.
       Most common causes, in order:
         1. DNS A record does not point at this instance's public IP.
         2. Port 80 is not open to 0.0.0.0/0 in the Security Group.
         3. nginx is not serving $ACME_WEBROOT at /.well-known/acme-challenge/
            (re-run phase 1 — finnish.conf must contain that location block)." 3

  [[ -d "$LIVE_DIR" ]] \
    || fail "certbot reported success but $LIVE_DIR does not exist. Check: certbot certificates" 3
fi

# ──────────────────────────────────────────────────────────────────────────────
# 8. Install the TLS vhost.
#
# Destination filename is finnish.conf, NOT finnish-tls.conf — two files both
# claiming `default_server` would fail nginx -t with "a duplicate default server".
# This overwrites the phase-1 bootstrap vhost, which is the intended transition.
#
# cp -a preserves mode/owner/timestamps on the backup so a restore in step 9 is
# byte-for-byte. The epoch suffix matches the backup convention provision-ec2.sh
# already uses for nginx.conf.
# ──────────────────────────────────────────────────────────────────────────────
LIVE_CONF=/etc/nginx/conf.d/finnish.conf
BACKUP=""

if [[ -f "$LIVE_CONF" ]]; then
  BACKUP="$LIVE_CONF.bak.$(date +%s)"
  cp -a "$LIVE_CONF" "$BACKUP"
  log "Backed up existing vhost to $BACKUP"
fi

log "Installing $TLS_CONF_SRC -> $LIVE_CONF"
install -m 644 "$TLS_CONF_SRC" "$LIVE_CONF"

# ──────────────────────────────────────────────────────────────────────────────
# 9. Validate — and roll back on failure.
#
# This differs from phase 1 on purpose. install-nginx.sh can leave a bad config
# on disk because nothing is serving yet. Here a working site is being replaced,
# so a failed nginx -t must restore the previous file before aborting. The
# running nginx keeps serving its already-loaded config throughout.
# ──────────────────────────────────────────────────────────────────────────────
log "Validating nginx config (nginx -t)"
if ! nginx -t; then
  if [[ -n "$BACKUP" ]]; then
    cp -a "$BACKUP" "$LIVE_CONF"
    warn "Restored previous vhost from $BACKUP"
  fi
  fail "nginx -t failed. Previous config restored; nginx was NOT reloaded." 2
fi

# ──────────────────────────────────────────────────────────────────────────────
# 10. Reload.
#
# SIGHUP — nginx re-reads config and gracefully drains old workers. This is also
# the moment the certificate is first read into memory.
# ──────────────────────────────────────────────────────────────────────────────
log "Reloading nginx"
systemctl reload nginx

# ──────────────────────────────────────────────────────────────────────────────
# 11. Verify renewal.
#
# The step everybody skips, and the one that decides whether the site is still up
# in 90 days. --dry-run exercises the full HTTP-01 round trip against the Let's
# Encrypt staging environment, so it proves the challenge path actually works.
#
# We `warn` rather than `fail` on the timer check: by this point the certificate
# is already installed and serving, so aborting would leave a working site behind
# an angry exit code. A loud warning is the right severity.
#
# ──────────────────────────────────────────────────────────────────────────────
log "Testing renewal (certbot renew --dry-run)"
if certbot renew --dry-run; then
  log "Renewal dry-run passed."
else
  warn "certbot renew --dry-run FAILED. The certificate is installed and serving,"
  warn "but it will NOT renew automatically. Investigate before day 60."
fi

log "Checking the renewal timer"
if systemctl list-timers --all 2>/dev/null | grep -q certbot; then
  systemctl list-timers --all | grep certbot || true
  if ! systemctl is-active --quiet certbot-renew.timer; then
    log "Enabling certbot-renew.timer"
    systemctl enable --now certbot-renew.timer || warn "Could not enable certbot-renew.timer"
  fi
elif ls /etc/cron.d/ 2>/dev/null | grep -qi certbot; then
  log "Renewal is driven by cron (/etc/cron.d), not a systemd timer — that is fine."
else
  warn "No certbot renewal timer or cron entry found. Renewal will NOT happen automatically."
  warn "Investigate with:  systemctl list-unit-files | grep certbot"
fi

# ──────────────────────────────────────────────────────────────────────────────
# 12. Done.
# ──────────────────────────────────────────────────────────────────────────────
cat <<EOF

============================================================
  TLS installed.
============================================================

Certificate:  $LIVE_DIR
Vhost:        $LIVE_CONF  (from deploy/nginx/finnish-tls.conf)
Renew hook:   $HOOK_FILE

Verify from your laptop:
    curl -I https://$PRIMARY_DOMAIN/                 # expect HTTP/1.1 200
    curl -I http://$PRIMARY_DOMAIN/                  # expect 301 -> https://
    curl -s https://$PRIMARY_DOMAIN/api/topics | head -c 200

Inspect the certificate:
    echo | openssl s_client -servername $PRIMARY_DOMAIN -connect $PRIMARY_DOMAIN:443 2>/dev/null \\
      | openssl x509 -noout -subject -issuer -dates

Re-running this script is safe: issuance is skipped while the certificate is valid.

NOTE: do NOT re-run install-nginx.sh on this host afterwards. It installs the
HTTP-only bootstrap vhost and will refuse (exit 3) now that TLS is live.

EOF