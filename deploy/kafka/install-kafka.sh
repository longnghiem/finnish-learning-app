#!/usr/bin/env bash
#
# Install and start a single-node KRaft Kafka
# broker on an Amazon Linux 2023 EC2 host that has been provisioned by
# deploy/provision-ec2.sh.
#
# Run as root:
#     sudo bash deploy/kafka/install-install-kafka.sh
#
# Idempotent: re-running on an already-installed host is a no-op for stateful
# steps (download skipped if /opt/kafka is populated; storage format skipped
# if /var/lib/kafka/meta.properties already exists).
#
# What it does:
#   1. Preconditions: root + Amazon Linux 2023.
#   2. Download Apache Kafka 3.9.0 (Scala 2.13 build) tarball if not already
#      extracted to /opt/kafka.
#   3. Create system user `kafka` and data dir /var/lib/kafka if missing.
#   4. Copy the sibling server.properties into /opt/kafka/config/kraft/.
#   5. Format the KRaft storage with cluster id bcyctWRKT921wWzPY1JhaA if not
#      already formatted.
#   6. Install the sibling kafka.service unit, enable, and start.
#   7. Wait briefly for the broker to come up; smoke-test with
#      kafka-broker-api-versions.sh; print final status.
#
# Exit codes:
#   0  success (or already installed)
#   1  precondition failed
#   2  download / extract failed
#   3  storage format failed
#   4  broker did not come up within timeout
#

set -euo pipefail

KAFKA_VERSION="3.9.0"
SCALA_VERSION="2.13"
KAFKA_TARBALL="kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz"
# archive.apache.org is the stable archive — older versions remain reachable
# here even after they roll off the active downloads.apache.org mirrors.
KAFKA_URL="https://archive.apache.org/dist/kafka/${KAFKA_VERSION}/${KAFKA_TARBALL}"
KAFKA_HOME="/opt/kafka"
KAFKA_DATA="/var/lib/kafka"
CLUSTER_ID="bcyctWRKT921wWzPY1JhaA"

# ──────────────────────────────────────────────────────────────────────────────
# Logging helpers.
# ──────────────────────────────────────────────────────────────────────────────
log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31m[err]\033[0m %s\n' "$*" >&2; exit "${2:-1}"; }

# ──────────────────────────────────────────────────────────────────────────────
# 1. Preconditions.
# ──────────────────────────────────────────────────────────────────────────────
if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  fail "Must run as root. Invoke: sudo bash deploy/kafka/install-kafka.sh"
fi

if ! grep -q '^ID="amzn"' /etc/os-release 2>/dev/null; then
  fail "This script targets Amazon Linux 2023. Detected: $(. /etc/os-release && echo "$PRETTY_NAME")"
fi

command -v java >/dev/null \
  || fail "java not on PATH. Run deploy/provision-ec2.sh first."

# Resolve sibling files relative to this script so the script works regardless
# of cwd (e.g. when invoked from /home/ec2-user/finnish-learning-monorepo).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_PROPERTIES_SRC="$SCRIPT_DIR/server.properties"
KAFKA_SERVICE_SRC="$SCRIPT_DIR/kafka.service"

[[ -f "$SERVER_PROPERTIES_SRC" ]] \
  || fail "Missing sibling file: $SERVER_PROPERTIES_SRC"
[[ -f "$KAFKA_SERVICE_SRC" ]] \
  || fail "Missing sibling file: $KAFKA_SERVICE_SRC"

# ──────────────────────────────────────────────────────────────────────────────
# 2. Download + extract Kafka.
#
# Idempotency: if /opt/kafka/bin/kafka-server-start.sh already exists, assume
# this step has run. We do not version-check the extracted tree — if the
# operator wants to upgrade Kafka, they should remove /opt/kafka first.
#
# `tar --strip-components=1` removes the top-level kafka_2.13-3.9.0/ directory
# so files land directly under /opt/kafka/ (cleaner than /opt/kafka/kafka_.../).
# ──────────────────────────────────────────────────────────────────────────────
if [[ ! -x "$KAFKA_HOME/bin/kafka-server-start.sh" ]]; then
  log "Downloading $KAFKA_TARBALL"
  curl -fsSL "$KAFKA_URL" -o "/tmp/$KAFKA_TARBALL" \
    || fail "Download failed from $KAFKA_URL" 2

  mkdir -p "$KAFKA_HOME"
  log "Extracting to $KAFKA_HOME"
  tar -xzf "/tmp/$KAFKA_TARBALL" -C "$KAFKA_HOME" --strip-components=1 \
    || fail "Extract failed." 2
  rm -f "/tmp/$KAFKA_TARBALL"
else
  log "Kafka already extracted at $KAFKA_HOME — skipping download"
fi

# ──────────────────────────────────────────────────────────────────────────────
# 3. kafka system user + data directory.
#
# /sbin/nologin (not /usr/sbin/nologin — Ubuntu convention) so nobody can SSH
# into the kafka user directly. systemd drops to this user via the unit's
# `User=kafka` directive.
# ──────────────────────────────────────────────────────────────────────────────
if ! id -u kafka >/dev/null 2>&1; then
  log "Creating system user 'kafka'"
  useradd --system --home-dir "$KAFKA_DATA" --shell /sbin/nologin kafka
else
  log "User 'kafka' already exists — skipping"
fi

mkdir -p "$KAFKA_DATA"
chown -R kafka:kafka "$KAFKA_HOME" "$KAFKA_DATA"

# ──────────────────────────────────────────────────────────────────────────────
# 4. Drop server.properties into Kafka's config dir.
#
# /opt/kafka/config/kraft/ ships with a stock server.properties from the
# tarball; we overwrite it with our loopback-only single-node config every
# time. Safe to overwrite — config-as-code lives in this repo, not on the host.
# ──────────────────────────────────────────────────────────────────────────────
log "Installing server.properties"
install -o kafka -g kafka -m 644 \
  "$SERVER_PROPERTIES_SRC" \
  "$KAFKA_HOME/config/kraft/server.properties"

# ──────────────────────────────────────────────────────────────────────────────
# 5. Format storage (KRaft prerequisite).
#
# kafka-storage.sh format writes a `meta.properties` file into log.dirs
# containing the cluster id and node id. Without it, kafka-server-start.sh
# refuses to boot with:
#   "No `meta.properties` found in /var/lib/kafka (have you run
#    `kafka-storage.sh format` first?)"
#
# We guard on the file's presence so re-running this script does not wipe
# existing topic data.
# ──────────────────────────────────────────────────────────────────────────────
if [[ ! -f "$KAFKA_DATA/meta.properties" ]]; then
  log "Formatting KRaft storage with cluster id $CLUSTER_ID"
  sudo -u kafka "$KAFKA_HOME/bin/kafka-storage.sh" format \
    -t "$CLUSTER_ID" \
    -c "$KAFKA_HOME/config/kraft/server.properties" \
    || fail "kafka-storage.sh format failed." 3
else
  log "KRaft storage already formatted (cluster id: $(awk -F= '/^cluster.id/ {print $2}' "$KAFKA_DATA/meta.properties")) — skipping"
fi

# ──────────────────────────────────────────────────────────────────────────────
# 6. Install systemd unit and start.
# ──────────────────────────────────────────────────────────────────────────────
log "Installing kafka.service"
install -m 644 "$KAFKA_SERVICE_SRC" /etc/systemd/system/kafka.service
systemctl daemon-reload
systemctl enable --now kafka

# ──────────────────────────────────────────────────────────────────────────────
# 7. Smoke test.
#
# kafka-broker-api-versions.sh is the lightest end-to-end probe — it speaks
# the actual Kafka protocol on :9092, so a passing response proves the broker
# is up and listening. Try up to 30 seconds (the JVM takes ~10s on t3.micro).
# ──────────────────────────────────────────────────────────────────────────────
log "Waiting for broker on 127.0.0.1:9092"
for _ in $(seq 1 15); do
  if sudo -u kafka "$KAFKA_HOME/bin/kafka-broker-api-versions.sh" \
       --bootstrap-server 127.0.0.1:9092 >/dev/null 2>&1; then
    log "Broker is up."
    break
  fi
  sleep 2
done

if ! sudo -u kafka "$KAFKA_HOME/bin/kafka-broker-api-versions.sh" \
     --bootstrap-server 127.0.0.1:9092 >/dev/null 2>&1; then
  warn "Broker did not respond within 30s."
  warn "Investigate with: journalctl -u kafka -n 100 --no-pager"
  exit 4
fi

cat <<'EOF'

============================================================
  Kafka installed and running.
============================================================

Verify any time with:
    systemctl status kafka --no-pager
    sudo -u kafka /opt/kafka/bin/kafka-broker-api-versions.sh \
        --bootstrap-server 127.0.0.1:9092

Listener is loopback-only (127.0.0.1:9092). Confirm with:
    ss -tlnp | grep ':9092'

Next: sudo bash deploy/backend/install-backend.sh

EOF