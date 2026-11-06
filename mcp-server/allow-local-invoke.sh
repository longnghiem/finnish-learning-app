#!/usr/bin/env bash
#
# mcp-server/allow-local-invoke.sh — Allow your own IAM user to call the MCP function URL from
# your laptop (or take that permission away again). Not needed in production: the backend's EC2
# role gets the same permission from the CDK stack.
#
# Run from anywhere on your laptop:
#     bash mcp-server/allow-local-invoke.sh            # add the permission
#     bash mcp-server/allow-local-invoke.sh --remove   # remove it
#
# Flags:
#   --aws-profile <name>   AWS CLI profile to run with. Default: fin-learn-app
#   --remove               Delete the policy instead of adding it.
#   -h, --help             Show this help.
#
# What it does:
#   1. Ask AWS who the profile is (`sts get-caller-identity`). Only IAM users are handled; for a
#      role (e.g. SSO) it stops and explains.
#   2. Add (or delete) the inline policy fin-app-profile-mcp-invoke on that user. It has the same
#      two statements the stack gives the backend's role: invoke the function, only via its URL.
#
# Exit codes:
#   0  done
#   1  bad flags, a missing tool, or the profile is not an IAM user
#   2  the AWS call failed

set -euo pipefail

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[err]\033[0m %s\n' "$1" >&2; exit "${2:-1}"; }

usage() { sed -n '3,/^# Exit codes:/p' "${BASH_SOURCE[0]}" | sed '$d; s/^# \{0,1\}//'; }

AWS_PROFILE_NAME="fin-learn-app"
REMOVE=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --aws-profile) AWS_PROFILE_NAME="${2:-}"; shift 2 ;;
    --remove)      REMOVE=1; shift ;;
    -h|--help)     usage; exit 0 ;;
    *)             fail "Unknown flag: $1 (see --help)" ;;
  esac
done

command -v aws >/dev/null || fail "aws (the AWS CLI) is not installed"

REGION="eu-north-1"
FUNCTION_NAME="fin-app-profile-mcp"
POLICY_NAME="fin-app-profile-mcp-invoke"

CALLER_ARN="$(aws sts get-caller-identity --profile "$AWS_PROFILE_NAME" --query Arn --output text)" \
  || fail "aws sts get-caller-identity failed for the profile $AWS_PROFILE_NAME" 2

# arn:aws:iam::<account>:user/<name>. A role's ARN has `assumed-role/` instead, and a role's
# policy belongs to whoever manages that role, so this script doesn't touch it.
if [[ "$CALLER_ARN" =~ ^arn:aws:iam::([0-9]{12}):user/(.+)$ ]]; then
  ACCOUNT_ID="${BASH_REMATCH[1]}"
  USER_NAME="${BASH_REMATCH[2]##*/}"
else
  fail "The profile $AWS_PROFILE_NAME is $CALLER_ARN, not an IAM user. Add the two statements of this script to that role's policy by hand." 1
fi

if [[ "$REMOVE" == "1" ]]; then
  log "Removing the policy $POLICY_NAME from the IAM user $USER_NAME"
  aws iam delete-user-policy --profile "$AWS_PROFILE_NAME" \
      --user-name "$USER_NAME" --policy-name "$POLICY_NAME" || fail "delete-user-policy failed" 2
  log "Done. Signed calls from this user now get 403."
  exit 0
fi

FUNCTION_ARN="arn:aws:lambda:$REGION:$ACCOUNT_ID:function:$FUNCTION_NAME"

# The same two statements `grantInvokeUrl` puts on the backend's role in the stack: both are
# needed for a call through the URL, and the conditions stop either from working on its own.
POLICY_DOCUMENT="$(cat <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "lambda:InvokeFunctionUrl",
      "Resource": "$FUNCTION_ARN",
      "Condition": { "StringEquals": { "lambda:FunctionUrlAuthType": "AWS_IAM" } }
    },
    {
      "Effect": "Allow",
      "Action": "lambda:InvokeFunction",
      "Resource": "$FUNCTION_ARN",
      "Condition": { "Bool": { "lambda:InvokedViaFunctionUrl": "true" } }
    }
  ]
}
EOF
)"

log "Adding the policy $POLICY_NAME to the IAM user $USER_NAME (function $FUNCTION_ARN)"
aws iam put-user-policy --profile "$AWS_PROFILE_NAME" \
    --user-name "$USER_NAME" --policy-name "$POLICY_NAME" --policy-document "$POLICY_DOCUMENT" \
  || fail "put-user-policy failed" 2

log "Done. Allowed actions:"
aws iam get-user-policy --profile "$AWS_PROFILE_NAME" --user-name "$USER_NAME" \
    --policy-name "$POLICY_NAME" --query 'PolicyDocument.Statement[].Action' --output text
