#!/usr/bin/env bash
#
# mcp-server/verify-function-url.sh — Check the deployed MCP function URL end to end: AWS must
# refuse an unsigned request and accept a signed one, and the request must show up in the logs.
#
# Run from anywhere on your laptop, after a deploy:
#     bash mcp-server/verify-function-url.sh
#
# Flags:
#   --aws-profile <name>   AWS CLI profile to sign with. Default: fin-learn-app
#   -h, --help             Show this help.
#
# Prerequisites:
#   1. The stack FinAppProfileMcp is deployed (mcp-server/deploy-mcp.sh).
#   2. Your IAM user may call the URL (mcp-server/allow-local-invoke.sh).
#   3. curl 7.75+ (for --aws-sigv4) and the AWS CLI v2 (for `configure export-credentials`).
#
# What it does:
#   1. Read the function URL from the stack's outputs.
#   2. Unsigned POST to <FunctionUrl>mcp: must get 403.
#   3. Signed POST, `tools/call list_entries`: must get 200 and an MCP result. The credentials are
#      exported inside this script only, so they never stay in your terminal.
#   4. Show the function's logs from the last 15 minutes (they can lag a few seconds behind).
#
# Exit codes:
#   0  both checks passed
#   1  bad flags, a missing tool, or the function URL could not be read
#   2  the unsigned request did NOT get 403
#   3  the signed request did NOT get 200 with an MCP result

set -euo pipefail

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31m[err]\033[0m %s\n' "$1" >&2; exit "${2:-1}"; }

usage() { sed -n '3,/^# Exit codes:/p' "${BASH_SOURCE[0]}" | sed '$d; s/^# \{0,1\}//'; }

AWS_PROFILE_NAME="fin-learn-app"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --aws-profile) AWS_PROFILE_NAME="${2:-}"; shift 2 ;;
    -h|--help)     usage; exit 0 ;;
    *)             fail "Unknown flag: $1 (see --help)" ;;
  esac
done

for tool in aws curl; do
  command -v "$tool" >/dev/null || fail "$tool is not installed"
done

REGION="eu-north-1"
STACK_NAME="FinAppProfileMcp"
LOG_GROUP="/aws/lambda/fin-app-profile-mcp"

# ──────────────────────────────────────────────────────────────────────────────
# 1. The function URL, from the stack's outputs.
# ──────────────────────────────────────────────────────────────────────────────
FUNCTION_URL="$(aws cloudformation describe-stacks --profile "$AWS_PROFILE_NAME" --region "$REGION" \
    --stack-name "$STACK_NAME" --query "Stacks[0].Outputs[?OutputKey=='FunctionUrl'].OutputValue" \
    --output text)" || fail "Could not read the stack $STACK_NAME. Is it deployed?"
[[ "$FUNCTION_URL" == https://* ]] || fail "The stack $STACK_NAME has no FunctionUrl output (got: $FUNCTION_URL)"
MCP_URL="${FUNCTION_URL}mcp"

# ──────────────────────────────────────────────────────────────────────────────
# 2. Unsigned request: AWS itself must refuse it, before our code runs.
# ──────────────────────────────────────────────────────────────────────────────
log "Test 1: unsigned request to $MCP_URL (expect 403)"
STATUS="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$MCP_URL" \
    -H 'Content-Type: application/json' -d '{}')"
[[ "$STATUS" == "403" ]] \
  || fail "The unsigned request got HTTP $STATUS instead of 403. Check the function URL auth type NOW." 2
log "Refused with 403, as expected"

# ──────────────────────────────────────────────────────────────────────────────
# 3. Signed request. curl signs with SigV4 for the service `lambda`; the session token is only
#    there for temporary credentials (SSO, assumed roles) and must be sent as a header.
# ──────────────────────────────────────────────────────────────────────────────
log "Test 2: signed request, tools/call list_entries (expect 200)"
eval "$(aws configure export-credentials --profile "$AWS_PROFILE_NAME" --format env)" \
  || fail "aws configure export-credentials failed for the profile $AWS_PROFILE_NAME"

BODY='{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"list_entries","arguments":{}}}'
RESPONSE_FILE="$(mktemp)"
trap 'rm -f "$RESPONSE_FILE"' EXIT

STATUS="$(curl -s -o "$RESPONSE_FILE" -w '%{http_code}' "$MCP_URL" \
    --aws-sigv4 "aws:amz:$REGION:lambda" \
    --user "$AWS_ACCESS_KEY_ID:$AWS_SECRET_ACCESS_KEY" \
    ${AWS_SESSION_TOKEN:+-H "x-amz-security-token: $AWS_SESSION_TOKEN"} \
    -H 'Content-Type: application/json' \
    -H 'Accept: application/json, text/event-stream' \
    -d "$BODY")"
cat "$RESPONSE_FILE"; echo
[[ "$STATUS" == "200" ]] \
  || fail "The signed request got HTTP $STATUS instead of 200. The runbook's A4 table lists the usual causes." 3
grep -q '"result"' "$RESPONSE_FILE" \
  || fail "HTTP 200, but the reply has no MCP result (see above)" 3
log "Accepted with 200 and an MCP result, as expected"

# ──────────────────────────────────────────────────────────────────────────────
# 4. Logs. Informational only: CloudWatch delivers them a few seconds late, so the newest lines
#    may still be missing.
# ──────────────────────────────────────────────────────────────────────────────
log "Logs from the last 15 minutes ($LOG_GROUP)"
aws logs tail "$LOG_GROUP" --profile "$AWS_PROFILE_NAME" --region "$REGION" --since 15m \
  || warn "Could not read the logs (the checks above passed anyway)"
log "Done. Lines from this run still missing? Run: aws logs tail $LOG_GROUP --profile $AWS_PROFILE_NAME --region $REGION --since 15m"
