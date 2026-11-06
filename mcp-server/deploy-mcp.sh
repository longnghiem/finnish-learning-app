#!/usr/bin/env bash
#
# mcp-server/deploy-mcp.sh — Build the profile index, bundle the MCP server and deploy it with
# the CDK stack in infra/ (Lambda function, function URL, log group, permissions).
#
# Run from anywhere on your laptop (the script resolves its own folder):
#     bash mcp-server/deploy-mcp.sh --profile-dir <path-to-your-profile>
#
# Flags:
#   --profile-dir <path>   Required. Folder with the profile markdown files. Must be OUTSIDE this
#                          git repository, so the real profile can't end up in a commit.
#   --aws-profile <name>   AWS CLI profile the CDK deploy runs with. Default: fin-learn-app
#   --allow-example        Allow --profile-dir mcp-server/profile.example (fictional data), the
#                          only folder inside the repo that may be deployed. For trying things out.
#   -h, --help             Show this help.
#
# Environment:
#   DRY_RUN=1              Build everything and synthesize the CloudFormation template
#                          (infra/cdk.out/), but don't deploy. Runs without any AWS credentials,
#                          so nothing in AWS is read or changed.
#
# Prerequisites:
#   1. The one-time setup is done: docs/runbooks/recruiter-chatbot-aws-setup.md, Part A
#      (in short: `npx cdk bootstrap` once per account and region).
#   2. node 22+, npm and curl are installed, and the AWS profile works
#      (`aws sts get-caller-identity --profile fin-learn-app`).
#
# What it does:
#   1. Check the flags and the profile folder (outside the repo, or the example with --allow-example).
#      Warn if infra/ or mcp-server/ has uncommitted changes: they would be deployed, but not be in git.
#   2. npm ci in mcp-server/: install the exact dependency versions from package-lock.json.
#   3. npm run build:index: validate the profile and write build/index.json.
#   4. npm run bundle: bundle the handler into dist/lambda/index.mjs and copy index.json beside it.
#   5. npm ci in infra/: install the CDK.
#   6. cdk deploy FinAppProfileMcp: CloudFormation creates or updates this one stack (other stacks
#      in the CDK app are left alone). If the change widens any IAM permission, the CDK shows it
#      and asks for confirmation first.
#   7. Smoke test: an unsigned request to the function URL must be refused with 403.
#   8. Remind you that everything deployed is effectively public.
#
# Exit codes:
#   0  deploy succeeded (or DRY_RUN finished)
#   1  bad flags or a precondition failed (missing tool, profile folder inside the repo)
#   2  build failed (npm ci, build:index, bundle, synth)
#   3  cdk deploy failed or was declined
#   4  smoke test failed: the function URL did NOT answer an unsigned request with 403

set -euo pipefail

# ──────────────────────────────────────────────────────────────────────────────
# Logging helpers — same style as deploy/deploy.sh.
# ──────────────────────────────────────────────────────────────────────────────
log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31m[err]\033[0m %s\n' "$1" >&2; exit "${2:-1}"; }

usage() { sed -n '3,/^# Exit codes:/p' "${BASH_SOURCE[0]}" | sed '$d; s/^# \{0,1\}//'; }

# ──────────────────────────────────────────────────────────────────────────────
# 1. Flags and preconditions.
# ──────────────────────────────────────────────────────────────────────────────
PROFILE_DIR=""
AWS_PROFILE_NAME="fin-learn-app"
ALLOW_EXAMPLE=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile-dir)  PROFILE_DIR="${2:-}"; shift 2 ;;
    --aws-profile)  AWS_PROFILE_NAME="${2:-}"; shift 2 ;;
    --allow-example) ALLOW_EXAMPLE=1; shift ;;
    -h|--help)      usage; exit 0 ;;
    *)              fail "Unknown flag: $1 (see --help)" ;;
  esac
done

[[ -n "$PROFILE_DIR" ]] || fail "--profile-dir is required (see --help)"
[[ -d "$PROFILE_DIR" ]] || fail "Profile folder not found: $PROFILE_DIR"

for tool in node npm curl; do
  command -v "$tool" >/dev/null || fail "$tool is not installed"
done

MCP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git -C "$MCP_DIR" rev-parse --show-toplevel 2>/dev/null \
              || fail "Could not locate the repo root. Run the script from inside the monorepo.")"
INFRA_DIR="$REPO_ROOT/infra"
EXAMPLE_DIR="$MCP_DIR/profile.example"

# The CDK stack this script deploys. Named explicitly, so stacks added to the app later are never
# deployed by accident.
STACK_NAME="FinAppProfileMcp"

# `realpath` resolves `..` and symlinks, so a path can't sneak into the repo through a link or a
# relative detour.
PROFILE_REAL="$(realpath "$PROFILE_DIR")"

# The real profile must never be inside the repo. The fictional example is the one exception,
# and only when asked for explicitly.
if [[ "$PROFILE_REAL" == "$REPO_ROOT" || "$PROFILE_REAL" == "$REPO_ROOT"/* ]]; then
  if [[ "$PROFILE_REAL" == "$EXAMPLE_DIR" && "$ALLOW_EXAMPLE" == "1" ]]; then
    warn "Deploying the FICTIONAL example profile ($EXAMPLE_DIR)."
  elif [[ "$PROFILE_REAL" == "$EXAMPLE_DIR" ]]; then
    fail "That's the example profile. Add --allow-example if you really want to deploy it."
  else
    fail "The profile folder is inside the git repository ($PROFILE_REAL). Keep the real profile outside the repo."
  fi
fi

# Uncommitted changes would be deployed but not be in git, so later nobody could see what is
# running. Only a warning: trying out a change before committing it is fine.
UNCOMMITTED="$(git -C "$REPO_ROOT" status --porcelain -- infra mcp-server)"
if [[ -n "$UNCOMMITTED" ]]; then
  warn "Uncommitted changes in infra/ or mcp-server/ will be deployed:"
  printf '%s\n' "$UNCOMMITTED" >&2
fi

if [[ "${DRY_RUN:-0}" == "1" ]]; then
  log "Dry run: build and synthesize only, no AWS access"
else
  log "Deploying with AWS profile $AWS_PROFILE_NAME"
fi

# ──────────────────────────────────────────────────────────────────────────────
# 2–4. Build the deployment package in mcp-server/dist/lambda/.
#
# The index goes to build/index.json inside mcp-server/, which is gitignored. The bundle folder
# holds exactly index.mjs and index.json; the CDK zips that folder and uploads it.
# ──────────────────────────────────────────────────────────────────────────────
cd "$MCP_DIR"

log "Installing mcp-server dependencies (npm ci)"
npm ci --no-audit --no-fund --loglevel=error >/dev/null || fail "npm ci failed in mcp-server/" 2

log "Building the profile index from $PROFILE_REAL"
npm run -s build:index -- --profile-dir "$PROFILE_REAL" --out build/index.json \
  || fail "The profile has problems (listed above). Fix them and run again." 2

log "Bundling the handler"
npm run -s bundle || fail "Bundling failed" 2

# ──────────────────────────────────────────────────────────────────────────────
# 5–6. Deploy with the CDK.
#
# `--require-approval broadening`: if the change adds or widens an IAM permission, the CDK prints
# the difference and waits for "y". Code-only changes (a new profile) go through without asking.
# `--outputs-file` saves the stack outputs (the function URL) for the smoke test.
# ──────────────────────────────────────────────────────────────────────────────
cd "$INFRA_DIR"

log "Installing infra dependencies (npm ci)"
npm ci --no-audit --no-fund --loglevel=error >/dev/null || fail "npm ci failed in infra/" 2

OUTPUTS_FILE="$INFRA_DIR/cdk.out/outputs.json"

if [[ "${DRY_RUN:-0}" == "1" ]]; then
  # No credentials at all, so the dry run can't reach AWS even by accident.
  env -u AWS_PROFILE -u AWS_ACCESS_KEY_ID -u AWS_SECRET_ACCESS_KEY -u AWS_SESSION_TOKEN \
      AWS_CONFIG_FILE=/dev/null AWS_SHARED_CREDENTIALS_FILE=/dev/null AWS_EC2_METADATA_DISABLED=true \
      npx cdk synth "$STACK_NAME" --quiet || fail "cdk synth failed" 2
  log "Template written to infra/cdk.out/ (open $STACK_NAME.template.json to see what would be deployed)"
  printf '\033[1;35m[dry-run]\033[0m npx cdk deploy %s --profile %s --require-approval broadening --outputs-file %s\n' \
    "$STACK_NAME" "$AWS_PROFILE_NAME" "$OUTPUTS_FILE"
  printf '\033[1;35m[dry-run]\033[0m curl -X POST <FunctionUrl>mcp  (expect 403)\n'
else
  log "Deploying the stack $STACK_NAME (cdk deploy)"
  npx cdk deploy "$STACK_NAME" --profile "$AWS_PROFILE_NAME" --require-approval broadening \
      --outputs-file "$OUTPUTS_FILE" \
    || fail "cdk deploy failed or was declined (see above)" 3

  FUNCTION_URL="$(node -e \
    'console.log(JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"))[process.argv[2]].FunctionUrl)' \
    "$OUTPUTS_FILE" "$STACK_NAME")" || fail "Could not read the function URL from $OUTPUTS_FILE" 3

  # ────────────────────────────────────────────────────────────────────────────
  # 7. Smoke test: an unsigned request must be refused by AWS itself (403), before our code runs.
  #
  # The CDK only changes what differs from the last deploy. If someone switched the URL to
  # public in the console, a deploy that doesn't touch the URL wouldn't undo it. This test would.
  # ────────────────────────────────────────────────────────────────────────────
  STATUS="$(curl -s -o /dev/null -w '%{http_code}' -X POST "${FUNCTION_URL}mcp" \
      -H 'Content-Type: application/json' -d '{}')"
  [[ "$STATUS" == "403" ]] \
    || fail "An unsigned request to ${FUNCTION_URL}mcp got HTTP $STATUS instead of 403. Check the function URL auth type NOW." 4
  log "Unsigned request to ${FUNCTION_URL}mcp refused with 403, as expected"
fi

# ──────────────────────────────────────────────────────────────────────────────
# 8. Reminder.
# ──────────────────────────────────────────────────────────────────────────────
warn "Everything in the deployed profile is effectively PUBLIC: a recruiter (or anyone) can make the bot repeat it."
log "Done."
