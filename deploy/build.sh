#!/usr/bin/env bash
#
# deploy/build.sh — Build backend fat jar + frontend production bundle on the
# operator's laptop and stage them under deploy/artefacts/ for upload to EC2.
#
# Why build locally rather than on EC2:
#   The target EC2 is t3.micro (1 GB RAM). Running Gradle + jOOQ codegen + a live
#   Postgres alongside the deployed services would OOM. Build on a beefier laptop,
#   ship only the runnable jar + static bundle.
#
# Prerequisites:
#   - JDK 21 (`java -version` reports 21).
#   - Node ≥ 18 + npm (`npm ci` runs in frontend/).
#   - rsync (used to mirror frontend/dist/).
#   - PostgreSQL reachable on 127.0.0.1:5532 — required by jOOQ codegen at Gradle
#     configuration time. Start it with `(cd backend && docker compose up -d postgres)`.
#
# Output (deterministic names for deploy.sh to consume):
#   deploy/artefacts/finnish-backend.jar
#   deploy/artefacts/dist/         (frontend production bundle, same-origin)
#
# Exit codes:
#   0  success
#   1  prerequisite missing or Postgres unreachable
#   2  backend build failed
#   3  frontend build failed
#

set -euo pipefail

# ──────────────────────────────────────────────────────────────────────────────
# Pretty logging — single source of truth so the rest of the script stays terse.
# ──────────────────────────────────────────────────────────────────────────────
log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31m[err]\033[0m %s\n' "$*" >&2; exit "${2:-1}"; }

# ──────────────────────────────────────────────────────────────────────────────
# Locate the repo root regardless of where the script was invoked from.
# Prefer git; fall back to the script's own directory so the script still works
# inside a checkout that is not a git repo (e.g. a tarball export).
# ──────────────────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel 2>/dev/null)"; then
  :
else
  REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
fi

BACKEND_DIR="$REPO_ROOT/backend"
FRONTEND_DIR="$REPO_ROOT/frontend"
ARTEFACTS_DIR="$REPO_ROOT/deploy/artefacts"

PG_HOST="${PG_HOST:-127.0.0.1}"
PG_PORT="${PG_PORT:-5532}"

# ──────────────────────────────────────────────────────────────────────────────
# Prerequisite checks. Each check exits 1 with a copy-pasteable remediation hint.
# ──────────────────────────────────────────────────────────────────────────────
log "Checking prerequisites"

command -v java  >/dev/null || fail "java not on PATH. Install JDK 21 (https://adoptium.net/)."
command -v node  >/dev/null || fail "node not on PATH. Install Node ≥ 18 (https://nodejs.org/)."
command -v npm   >/dev/null || fail "npm not on PATH. Comes with Node."
command -v rsync >/dev/null || fail "rsync not on PATH. Install via your package manager."

# Java major version. `java -version` writes to stderr; parse defensively.
JAVA_MAJOR="$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F. '{print $1}')"
if [[ -z "$JAVA_MAJOR" || "$JAVA_MAJOR" -lt 21 ]]; then
  fail "JDK 21 required (detected: ${JAVA_MAJOR:-unknown}). Set JAVA_HOME to a JDK 21 install."
fi

# Postgres reachability. Prefer pg_isready when available (proper protocol check);
# fall back to a /dev/tcp probe so the script works on minimal laptop installs.
if command -v pg_isready >/dev/null 2>&1; then
  if ! pg_isready -h "$PG_HOST" -p "$PG_PORT" -q; then
    fail "PostgreSQL not reachable at $PG_HOST:$PG_PORT (pg_isready). \
Run: (cd backend && docker compose up -d postgres)"
  fi
else
  if ! (exec 3<>"/dev/tcp/$PG_HOST/$PG_PORT") 2>/dev/null; then
    fail "PostgreSQL not reachable at $PG_HOST:$PG_PORT (tcp probe). \
Run: (cd backend && docker compose up -d postgres)"
  fi
  exec 3>&- 3<&- || true
fi

log "Prerequisites OK (java=$JAVA_MAJOR, node=$(node --version), postgres reachable at $PG_HOST:$PG_PORT)"

# ──────────────────────────────────────────────────────────────────────────────
# Stage a clean artefacts directory. Removing it (rather than rsync --delete only)
# guarantees no stale files survive a rename/refactor in the source tree.
# ──────────────────────────────────────────────────────────────────────────────
log "Resetting $ARTEFACTS_DIR"
rm -rf "$ARTEFACTS_DIR"
mkdir -p "$ARTEFACTS_DIR"

# ──────────────────────────────────────────────────────────────────────────────
# Backend: produce the Spring Boot fat jar.
#
# `--no-daemon` keeps the build hermetic — no leftover daemon caching env state
# between runs, which matters because build.gradle.kts reads `.env` at
# configuration time.
# ──────────────────────────────────────────────────────────────────────────────
log "Building backend (./gradlew clean bootJar)"
(
  cd "$BACKEND_DIR"
  ./gradlew --no-daemon --console=plain clean bootJar
) || fail "Backend build failed. See Gradle output above." 2

# bootJar emits TWO artefacts in build/libs/:
#   *-SNAPSHOT.jar         ← runnable fat jar (we want this)
#   *-SNAPSHOT-plain.jar   ← thin classes-only jar (Spring Boot side artefact)
# Filter explicitly so we never ship the wrong one.
FAT_JAR="$(find "$BACKEND_DIR/build/libs" -maxdepth 1 -type f -name '*.jar' \
            ! -name '*-plain.jar' | head -n 1)"

[[ -n "$FAT_JAR" ]] || fail "No fat jar found under $BACKEND_DIR/build/libs/." 2

cp "$FAT_JAR" "$ARTEFACTS_DIR/finnish-backend.jar"
log "Backend jar staged: $(basename "$FAT_JAR") -> finnish-backend.jar"

# ──────────────────────────────────────────────────────────────────────────────
# Frontend: install pinned deps and run the production build.
#
# `npm ci` (not `install`) so package-lock.json is honoured — reproducible bundles.
# Vite auto-loads frontend/.env.production (added in TASK_02), which sets
# VITE_API_BASE_URL="" so the bundle issues same-origin requests behind nginx.
# ──────────────────────────────────────────────────────────────────────────────
log "Building frontend (npm ci && npm run build)"
(
  cd "$FRONTEND_DIR"
  npm ci --no-audit --no-fund
  npm run build
) || fail "Frontend build failed. See npm/vite output above." 3

# Guard: production bundle must NOT contain the dev base URL. If it does, either
# .env.production is missing or VITE_API_BASE_URL leaked from another env source.
if grep -RIn "http://localhost:8080" "$FRONTEND_DIR/dist" >/dev/null 2>&1; then
  warn "Frontend bundle contains 'http://localhost:8080' — production base URL \
not applied. Check frontend/.env.production (TASK_02)."
  warn "Bundle will be staged anyway; fix and rebuild before running deploy.sh."
fi

rsync -a --delete "$FRONTEND_DIR/dist/" "$ARTEFACTS_DIR/dist/"
log "Frontend bundle staged at $ARTEFACTS_DIR/dist/"

# ──────────────────────────────────────────────────────────────────────────────
# Summary — operator can eyeball the sizes before uploading.
# ──────────────────────────────────────────────────────────────────────────────
log "Build complete. Artefacts:"
ls -lh "$ARTEFACTS_DIR/finnish-backend.jar"
du -sh "$ARTEFACTS_DIR/dist"