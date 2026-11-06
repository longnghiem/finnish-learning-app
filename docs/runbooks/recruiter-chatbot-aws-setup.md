# Recruiter chatbot: one-time AWS setup

The chatbot's AWS resources are defined in code with the **AWS CDK** (`infra/`). This runbook is
what you do **once** to get from an empty account to a working, verified deployment. After that,
`mcp-server/deploy-mcp.sh` is the only command you need.

- **Part A**: the MCP server on Lambda.
- **Part B**: production permissions, configuration and go-live: what the chatbot needs on the
  EC2 instance.
- **Part C**: cost: where the chatbot shows up on the bill, and the budget alerts.
- **Part D**: known gaps on the host, out of scope for the chatbot.

Every step has four parts: **What** it does, **Why** it's needed, the **Command**, and how to
**Verify** it worked. Run the steps in order, from the repository root. `<ACCOUNT_ID>` is a
placeholder; the commands fill in your real account id from a variable.

**Pasting commands.** Every command here is a single line, and paste one line at a time.
Anything longer is a script in `mcp-server/`. Terminals can split a long pasted line or add
spaces in front of pasted lines, and that breaks commands in ways that are hard to see. One
example: a line break inside a signed `curl` request's header makes AWS answer "The request
signature we calculated does not match…", even though your keys are fine.

## Part A: the MCP server on Lambda

### What you'll have at the end

```
CloudFormation stack CDKToolkit (A1, once per account + region: where the CDK uploads files)
CloudFormation stack FinAppProfileMcp (A2, defined in infra/lib/profile-mcp-stack.ts):

  EC2 role fin-app-ec2-bedrock ──(policy added by the stack: may invoke, only via the URL)──┐
  (existing, created by hand for Bedrock)                                                   ▼
                                                              function URL (AWS_IAM, BUFFERED)
                                                                          │
                                                                          ▼
                                 Lambda function fin-app-profile-mcp (nodejs22.x, arm64, 256 MB,
                                 10 s, at most 5 copies at once)
                                      │ runs as                     │ writes to
                                      ▼                             ▼
                                 execution role (logs only)    log group /aws/lambda/fin-app-profile-mcp (14 days)
```

### A0. Prepare your terminal

**What:** shell variables used by the later commands, and two read-only checks.
**Why:** the names are typed once, so later commands can be pasted unchanged.

```bash
export AWS_PROFILE=fin-learn-app
export AWS_REGION=eu-north-1
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
echo "$ACCOUNT_ID"
```

**Verify:** a 12-digit account id. If you open a new terminal later, run this block again.

**Check the account's concurrency limit:**

```bash
aws lambda get-account-settings --query 'AccountLimit.[ConcurrentExecutions,UnreservedConcurrentExecutions]' --output text
```

The stack reserves 5 copies of the function. AWS always keeps a minimum of the account's
concurrency unreserved for all other functions (100 on a normal account). If the first number is
**1000**, you're fine. If it's low (new accounts sometimes start at **10**), the deploy in A2 will
fail on the reservation. Then either ask for an increase first (Service Quotas console → AWS
Lambda → "Concurrent executions", free, usually approved within a day), or remove the
`reservedConcurrentExecutions` line in `infra/lib/profile-mcp-stack.ts` for now (a limit of 10
already caps the cost on its own) and put it back once the limit is raised.

### A1. Bootstrap the CDK (once per account and region)

**What:** `cdk bootstrap` deploys a CloudFormation stack named **`CDKToolkit`** that the CDK needs
before it can deploy anything. It contains:

| Resource | Used for |
|---|---|
| An S3 bucket `cdk-hnb659fds-assets-<ACCOUNT_ID>-eu-north-1` | The CDK uploads the Lambda zip here; CloudFormation picks it up from there |
| An ECR repository | Container images (not used by us; empty costs nothing) |
| Five IAM roles | The CDK CLI **assumes** these to deploy: one to start deployments, one to upload files, one to upload images, one to look things up, and one that **CloudFormation itself** uses to create the resources |

```bash
cd infra
npm ci
npx cdk bootstrap "aws://$ACCOUNT_ID/$AWS_REGION"
cd ..
```

**Verify:**

```bash
aws cloudformation describe-stacks --stack-name CDKToolkit --query 'Stacks[0].StackStatus' --output text   # CREATE_COMPLETE
```

### A2. First deploy, with the fictional example

**What:** create the stack `FinAppProfileMcp` with the **example profile**.

```bash
bash mcp-server/deploy-mcp.sh --profile-dir mcp-server/profile.example --allow-example
```

**Verify:**

```bash
aws cloudformation describe-stacks --stack-name FinAppProfileMcp --query 'Stacks[0].[StackStatus,Outputs]' --output text
aws lambda get-function-configuration --function-name fin-app-profile-mcp --query '[State,Runtime,Architectures[0],MemorySize,Timeout,Handler]' --output text   # Active nodejs22.x arm64 256 10 index.handler
aws lambda get-function-url-config --function-name fin-app-profile-mcp --query '[AuthType,InvokeMode]' --output text   # AWS_IAM BUFFERED
aws lambda get-function-concurrency --function-name fin-app-profile-mcp                # "ReservedConcurrentExecutions": 5
aws lambda get-policy --function-name fin-app-profile-mcp   # error "ResourceNotFoundException": good, no resource policy, nothing public
```

Copy the `FunctionUrl` output (`https://<random-id>.lambda-url.eu-north-1.on.aws/`). The backend
needs it as `ASK_MCP_URL`, with `mcp` appended.

### A3. Local development (pick one)

**Option 1, recommended: no AWS at all.** Run the server locally (`npm run dev` in `mcp-server/`)
and point the backend at `http://localhost:3001/mcp` with signing off.

**Option 2: call the real Lambda from your laptop.** Your laptop has no EC2 role; it signs with
your own IAM identity, which needs the same two permissions. This is **not** in the CDK stack,
because your personal identity isn't part of the application.

```bash
bash mcp-server/allow-local-invoke.sh
```

It asks AWS which IAM user the profile `fin-learn-app` belongs to, then adds the inline policy
`fin-app-profile-mcp-invoke` to that user. The policy has the same two statements the stack gives
the backend's role: `lambda:InvokeFunctionUrl` only while the URL uses `AWS_IAM`, and
`lambda:InvokeFunction` only through the URL.

**Verify:** the script ends by listing `lambda:InvokeFunctionUrl lambda:InvokeFunction`.

### A4. Verify the whole chain

**What:** proof that AWS itself refuses unsigned calls and accepts signed ones.

This needs A3 option 2.

```bash
bash mcp-server/verify-function-url.sh
```

It reads the function URL from the stack's outputs, then:

1. **Unsigned → 403.** A POST to `<FunctionUrl>mcp` without a signature. The deploy script runs
   the same check.
2. **Signed → 200.** `curl --aws-sigv4` signs a `tools/call list_entries` request with your
   credentials, the same way the backend will sign with its EC2 role. The credentials are
   exported inside the script only, so they don't stay in your terminal.
3. **The logs.** It shows the last 15 minutes: one JSON line per request, plus Lambda's own
   `START`/`END`/`REPORT` lines. The first `REPORT` line has an `Init Duration`, which means that
   call was a cold start. CloudWatch delivers logs a few seconds late, so lines from this run can
   be missing. Run `aws logs tail /aws/lambda/fin-app-profile-mcp --since 15m` again a minute
   later.

**Verify:** "Refused with 403, as expected", the JSON reply listing the deployed entries'
summaries, then "Accepted with 200 and an MCP result, as expected". If either check fails, the
script stops with a red `[err]` line (exit code 2 for the unsigned check, 3 for the signed one).

**4. Later, once the backend calls the server:** the backend's connection shows up as three
requests: `initialize` (200), **one `GET` answered 405 within milliseconds**, and
`notifications/initialized` (202). The fast 405 proves IAM let the backend in and the "POST only"
rule works; without it, that GET would run for the full 10 seconds.

**If a signed call gets 403:**

| Message in the reply | Meaning | Fix |
|---|---|---|
| `Signature expired` / `not yet current` | The caller's clock is off by more than 15 minutes | Fix the clock (`timedatectl` on Linux) |
| `The request signature we calculated does not match` | The request changed after signing, or the wrong region/service | Sign with region `eu-north-1` and service `lambda` |
| `Forbidden` with no detail | The signer lacks permission | The stack's policy on `fin-app-ec2-bedrock` (A2), or A3 option 2 for your user |

### A5. Deploy the real profile

```bash
bash mcp-server/deploy-mcp.sh --profile-dir <path-to-your-profile>
```

Only the code changes, so the CDK doesn't ask for approval. From now on, this is the only command
you need after editing the profile or the server code.

**Verify:** `bash mcp-server/verify-function-url.sh` now lists your entries instead of the
example ones.

### Everyday CDK commands (run in `infra/`)

| Command | What it does | Touches AWS? |
|---|---|---|
| `npx cdk synth` | Turn the TypeScript into a CloudFormation template (`cdk.out/`) | No (needs a built bundle) |
| `npx cdk diff` | Show what a deploy would change, compared with what's deployed | Reads only |
| `npx cdk deploy` | Apply the changes (use `deploy-mcp.sh` instead, it builds the bundle first) | Yes |
| `npx cdk drift` | Find settings someone changed by hand in the console | Reads only |
| `npx cdk destroy` | Delete the whole stack | Yes |
| `npm test` | Check the stack's rules (IAM-only URL, logs-only role, …) without AWS | No |

**Never change these resources in the console.** CloudFormation doesn't notice, the next deploy
may or may not overwrite your change, and the code no longer describes what's really there.
Change `infra/lib/profile-mcp-stack.ts` and deploy instead.

### Undo everything (Part A)

```bash
(cd infra && npx cdk destroy)
```

This deletes the function, its URL, its role, its log group, and the policy the stack added to
`fin-app-ec2-bedrock` (the role itself stays; it was never the stack's). Then, if you did A3
option 2:

```bash
bash mcp-server/allow-local-invoke.sh --remove
```

The `CDKToolkit` stack stays, ready for the next deploy. To remove it too: empty the
`cdk-hnb659fds-assets-…` bucket in the S3 console, then
`aws cloudformation delete-stack --stack-name CDKToolkit`.

## Part B: production permissions, configuration and go-live

Part A put the MCP server on Lambda. Part B makes the backend on the EC2 instance ready to run the
chatbot: one AWS permission, three settings, two host files, then the deploy and its checks.

```
visitor ── nginx (/api/ask/: 10 requests/min per IP, B5) ── backend on EC2, signs as the role
                                                                fin-app-ec2-bedrock (B3)
                                                                   │
              ┌────────────────────────────────────────────────────┼────────────────────────┐
              ▼                                                    ▼                        ▼
   Bedrock InvokeGuardrailChecks                       Bedrock Converse,             Lambda function URL
   (checks every input first)                          Claude Haiku 4.5              (profile MCP server)
   permission: the CDK stack (B2)                      permission: the hand-made     permission: the CDK
                                                       policy fin-app-bedrock-invoke stack (Part A)
                                                       (B1, already there)
```

The commands with `aws` run on your laptop with the variables from A0. The commands marked
**(host)** run on the EC2 instance, over SSH. Run the steps in order. B1 to B3 change nothing on the
running site; B4 to B6 do.

**What's there already** (checked on 2026-11-03):

| Piece | State |
|---|---|
| Role `fin-app-ec2-bedrock` attached to the instance | ✅ |
| Model permission (`fin-app-bedrock-invoke`, Haiku 4.5) | ✅, used by the essay feature |
| Lambda invoke permission (stack `FinAppProfileMcp`) | ✅ |
| Guardrail permission (`bedrock:InvokeGuardrailChecks`) | ❌, B2 adds it |
| Claude Haiku 4.5 in Bedrock | `ACTIVE`, no end-of-life date |

### B1. Model permission: verify only

**What:** confirm that the instance role may call Claude Haiku 4.5, the chatbot's model.
**Why:** the chatbot calls Bedrock's **Converse** API. Converse needs the same IAM action as the
essay feature's calls, `bedrock:InvokeModel`, on the same model. So the hand-made policy
`fin-app-bedrock-invoke` (deploy/README.md §4.7.2) already covers it: nothing to add.

The model id `eu.anthropic.claude-haiku-4-5-…` is an **inference profile**: Bedrock may run each
request in any of six EU regions. IAM checks the profile **and** the model in the region that
answers, so the policy must list the model in all six. The first command compares the policy
with AWS's own list of regions:

```bash
diff <(aws iam get-policy-version --policy-arn "arn:aws:iam::$ACCOUNT_ID:policy/fin-app-bedrock-invoke" --version-id "$(aws iam get-policy --policy-arn "arn:aws:iam::$ACCOUNT_ID:policy/fin-app-bedrock-invoke" --query Policy.DefaultVersionId --output text)" --query 'PolicyVersion.Document.Statement[].Resource[]' --output text | tr '\t' '\n' | grep foundation-model | sort) <(aws bedrock get-inference-profile --inference-profile-identifier eu.anthropic.claude-haiku-4-5-20251001-v1:0 --query 'models[].modelArn' --output text | tr '\t' '\n' | sort) && echo "same model ARNs"
aws iam simulate-principal-policy --policy-source-arn "arn:aws:iam::$ACCOUNT_ID:role/fin-app-ec2-bedrock" --action-names bedrock:InvokeModel --resource-arns "arn:aws:bedrock:eu-north-1:$ACCOUNT_ID:inference-profile/eu.anthropic.claude-haiku-4-5-20251001-v1:0" --query 'EvaluationResults[0].EvalDecision' --output text
```

**Verify:** `same model ARNs`, then `allowed`.

`simulate-principal-policy` is IAM's **policy simulator**: it evaluates the role's policies for
an action and answers `allowed` or `implicitDeny` (no statement allows it), without calling
Bedrock and without changing anything. It's free.

#### If you ever change `ASK_MODEL_ID`

The backend's message for a refused model call points here. A model change is a config change
plus these steps, **in this order**:

1. List the new profile's model ARNs:
   `aws bedrock get-inference-profile --inference-profile-identifier <new profile id> --query 'models[].modelArn' --output text`.
2. Add a statement for the new profile ARN and all of those model ARNs to
   `fin-app-bedrock-invoke` (IAM console → Policies → the policy → Edit → JSON; this saves a new
   version and makes it the default). **Keep the Haiku 4.5 statement**: the essay feature uses it.
3. Repeat the simulator command above with the new profile ARN: `allowed`.
4. Set `ASK_MODEL_ID` and the four `ASK_PRICE_*` values for the new model. For a model that
   thinks by default, e.g. Haiku 5.5, also set
   `ASK_ADDITIONAL_MODEL_FIELDS={"thinking":{"type":"disabled"}}` (see the comment in
   `application.properties`), and check the new model's prompt-caching minimum. Restart the
   backend.
5. Run the red-team suite once (`./gradlew redTeam`, `backend/src/test/resources/redteam/README.md`).
6. Change the model's name in the "How this works" panel (`howSteps` in
   `frontend/src/lang/translations.ts`, both languages).

If Bedrock then says the model **isn't enabled** (not an IAM denial), open the Bedrock console in
`eu-north-1` → **Model catalog** → the model, and request access. Anthropic models need the
one-time use-case form, which this account has already filled in for Haiku 4.5.

### B2. Guardrail permission, through the CDK

**What:** allow the instance role to call `bedrock:InvokeGuardrailChecks`, in `eu-north-1` only.
**Why:** the chatbot checks every message and job description with Bedrock Guardrails before
the model sees it, and **fails closed**: without this permission, every chat ends with "The
assistant is unavailable right now".

The permission is defined in `infra/lib/profile-mcp-stack.ts`, next to the Lambda invoke
permission, so it is reviewed in git and tested (`npm test` in `infra/`). The API has no resource
of its own (the checks to run are sent with each request), so the statement's resource is `*`, and
a condition limits it to the region:

```json
{
  "Sid": "AskInputGuardrailChecks",
  "Effect": "Allow",
  "Action": "bedrock:InvokeGuardrailChecks",
  "Resource": "*",
  "Condition": { "StringEquals": { "aws:RequestedRegion": "eu-north-1" } }
}
```

**Before:** the simulator says the role can't do it yet:

```bash
aws iam simulate-principal-policy --policy-source-arn "arn:aws:iam::$ACCOUNT_ID:role/fin-app-ec2-bedrock" --action-names bedrock:InvokeGuardrailChecks --context-entries ContextKeyName=aws:RequestedRegion,ContextKeyValues=eu-north-1,ContextKeyType=string --query 'EvaluationResults[0].EvalDecision' --output text
```

`implicitDeny`.

**Look first, then deploy** (with your real profile, as in A5):

```bash
DRY_RUN=1 bash mcp-server/deploy-mcp.sh --profile-dir <path-to-your-profile>
(cd infra && npx cdk diff FinAppProfileMcp)
bash mcp-server/deploy-mcp.sh --profile-dir <path-to-your-profile>
```

`cdk diff` lists one change: the policy `BackendRolePolicy…` gets the statement above. The policy
keeps its name, so CloudFormation updates it in place. Because the change **adds** a permission,
`deploy-mcp.sh` stops at an "IAM Statement Changes" table and asks `Do you wish to deploy these
changes (y/n)?`. Check that the table has exactly one `+` row: `Allow`,
`bedrock:InvokeGuardrailChecks`, resource `*`, the region condition, and the role
`fin-app-ec2-bedrock`. Then answer `y`.

**Verify:** run the "Before" command again: `allowed`. The same command with
`ContextKeyValues=eu-west-1` must still say `implicitDeny`. IAM changes can take a few seconds to
show up.

**Undo:** remove the statement from the stack and deploy again. `cdk destroy` also removes it,
together with the rest of Part A.

### B3. The backend must sign as the instance role, not as a Bedrock API key

**What:** make sure nothing on the server gives the backend AWS credentials other than the role
`fin-app-ec2-bedrock`.
**Why:** the AWS SDK picks credentials in a fixed order, and the instance role comes last:

```
1. AWS_BEARER_TOKEN_BEDROCK          (Bedrock API key; Bedrock clients only)
2. AWS_ACCESS_KEY_ID + AWS_SECRET_ACCESS_KEY (+ AWS_SESSION_TOKEN)
3. AWS_PROFILE / ~/.aws/credentials of the user the backend runs as (finnish)
4. the instance role (fin-app-ec2-bedrock), from the instance metadata service
```

If an earlier one is set, the SDK uses it and never reaches the role. A Bedrock API key's IAM user
(`BedrockAPIKey-…`) may call the model, so the essay feature would still work, but it may not
call `bedrock:InvokeGuardrailChecks`: every chat would end at once. This happened on a laptop on
2026-10-30.

**Command (host)**, read-only:

```bash
sudo grep -nE '^\s*(export\s+)?(AWS_BEARER_TOKEN_BEDROCK|AWS_ACCESS_KEY_ID|AWS_SECRET_ACCESS_KEY|AWS_SESSION_TOKEN|AWS_PROFILE)=' /etc/finnish-backend.env
systemctl show finnish-backend -p Environment
sudo -u finnish sh -c 'ls -a "$HOME/.aws"'
sudo -u finnish aws sts get-caller-identity --query Arn --output text
```

**Verify:** the `grep` prints nothing; the `Environment=` line names none of those variables;
`ls` says `No such file or directory`; the last line is
`arn:aws:sts::<ACCOUNT_ID>:assumed-role/fin-app-ec2-bedrock/i-…`. If one of the variables is
there, delete its line and restart the backend (`sudo systemctl restart finnish-backend`).

### B4. Configure the backend

**What:** three new settings in the server's env file, and the public Turnstile key in the
frontend build.
**Why:** the env file on the host is yours: `install-backend.sh` copies the template only when
the file doesn't exist yet, so new variables never arrive by themselves.

| Setting | Where | What it is, and where it comes from |
|---|---|---|
| `ASK_IP_HASH_KEY` | `/etc/finnish-backend.env` | **Required, or the backend doesn't start at all** (the whole site goes down, not only the chatbot). The secret key of the IP hash. Generate it once with `openssl rand -hex 32` (64 characters) and never change it: a new key resets the per-IP daily counts and ends every open chat session |
| `TURNSTILE_SECRET_KEY` | `/etc/finnish-backend.env` | The human check's secret. Cloudflare dashboard → **Turnstile** → **Add widget**: name `opisuomea-ask`, hostname `opisuomea.org`, mode **Managed**. Turnstile is free and works without moving DNS to Cloudflare: confirm both on the widget page. **Never** a test secret (`1x…AA` accepts a public dummy token) |
| `ASK_MCP_URL` | `/etc/finnish-backend.env` | The function URL from Part A plus `mcp`. Print it: `echo "$(aws cloudformation describe-stacks --stack-name FinAppProfileMcp --query "Stacks[0].Outputs[?OutputKey=='FunctionUrl'].OutputValue" --output text)mcp"` |
| `VITE_TURNSTILE_SITE_KEY` | `frontend/.env.production` (laptop) | The same widget's **site key**. It is public (the browser needs it), so it lives in the repo; the line is waiting there |

Everything else keeps its default (`application.properties`), including `ASK_MCP_SIGN=true`. If
you change `ASK_CHAT_PER_IP_PER_DAY` or `ASK_FIT_CHECKS_PER_IP_PER_DAY`, also change the
constants that mirror them in `frontend/src/api/chatbot/ask.ts` (`CHAT_MESSAGES_PER_DAY`,
`FIT_CHECKS_PER_DAY`): the page shows them.

**Command (host):** `sudoedit /etc/finnish-backend.env`, then add the three lines (or fill them
in, if your file already has them). `sudoedit` keeps the file's owner and mode (`root:finnish`,
`600`).

**Verify (host):**

```bash
sudo grep -cE '^(ASK_IP_HASH_KEY|TURNSTILE_SECRET_KEY|ASK_MCP_URL)=.+' /etc/finnish-backend.env
sudo awk -F= '/^ASK_IP_HASH_KEY=/{print length($2)}' /etc/finnish-backend.env
sudo stat -c '%U:%G %a' /etc/finnish-backend.env
```

`3`, then `64`, then `root:finnish 600`. Nothing reads the new values until the deploy in B6.

### B5. Install the host-side files

**What:** two files in `deploy/` changed for the chatbot, and both are installed by a script on
the host, not by `deploy.sh`:

| File | Change | Installed by |
|---|---|---|
| `deploy/nginx/finnish-tls.conf` | `/api/ask/` gets a rate limit: 10 requests per minute per IP, bursts of 6, then `429` with code `BUSY`. Explained in the file's comments | `install-tls.sh` |
| `deploy/backend/finnish-backend.service` | `TimeoutStopSec` 30 → 75 s, so a deploy lets running chats finish (the backend waits up to 60 s for them) | `install-backend.sh` |

**Command (host),** from the repository folder on the instance, after getting the new `deploy/`
there (`git pull`, or copy it as in deploy/README.md §5.1):

```bash
sudo bash deploy/backend/install-backend.sh
sudo CERTBOT_EMAIL=<your-email> bash deploy/nginx/install-tls.sh
```

`install-backend.sh` replaces the unit, leaves `/etc/finnish-backend.env` alone, and reloads
systemd; the running backend keeps its old timeout until the next restart (B6).
`install-tls.sh` finds the existing certificate and doesn't request a new one. It backs up the
live vhost, installs the new one, and runs `nginx -t`; if that fails, it **puts the backup back**
and stops before reloading, so a broken file never reaches the running nginx.

**Verify (host):**

```bash
sudo nginx -T 2>/dev/null | grep -c 'zone=ask'
systemctl show finnish-backend -p TimeoutStopUSec
```

`2` (the zone and the limit), then `TimeoutStopUSec=1min 15s`.

### B6. Deploy the backend and the frontend

**What:** the usual build and deploy (deploy/README.md §6–7). The first start runs the database
migration `V9` (the chatbot's three tables).
**Why the extra care:** this deploy is the first with the chatbot, so take a backup first, and
keep to **one** backend: at startup it releases every budget reservation and marks every
unfinished turn as an error, which is only right when no other copy is answering requests. Never
run a second copy (blue/green, or a `java -jar` by hand) against the same database.

1. Back up the database (laptop): the `pg_dump` command in `db_backup/note.md`.
2. Run the red-team suite on both profiles (laptop, about $0.15 for the example profile and about
   $0.30 with `-PrealProfile`): `backend/src/test/resources/redteam/README.md`.
3. Build and deploy (laptop): `bash deploy/build.sh`, then `bash deploy/deploy.sh`. The build
   picks up `VITE_TURNSTILE_SITE_KEY` from `frontend/.env.production`.

**Verify (host):** the startup log of the new backend.

```bash
sudo journalctl -u finnish-backend --since "15 min ago" | grep -E 'Migrating schema|Successfully applied'
sudo journalctl -u finnish-backend --since "15 min ago" | grep -cE 'is not set|sign-requests is false|startup cleanup: requests cut off|must be at least 32'
```

The first prints the migration to `V9`; the second prints `0`.

### B7. Check it on the live site

1. Open `https://opisuomea.org/ask`, pass the human check, ask one question. The progress steps
   include the input check, and Himmel answers from the profile.
2. Log in as admin → **Admin** → **Chatbot**: the turn is there, with guardrail `Passed`, a
   cost, and a tool trace. This also proves nginx passes `/api/admin/`.
3. **(host)** The backend signed as the role, never as an API key:
   `sudo journalctl -u finnish-backend --since today | grep -c BedrockAPIKey` → `0`.
4. The MCP server saw the backend: `aws logs tail /aws/lambda/fin-app-profile-mcp --since 15m`
   shows `initialize`, one `GET` answered `405`, then the tool calls (A4, point 4).
5. The rate limit (laptop): `for i in $(seq 8); do curl -s -o /dev/null -w '%{http_code} ' https://opisuomea.org/api/ask/stats; done; echo`
   → six `200`, then `429 429` (fewer `200`s if you used the chat in the last minute). Wait a
   minute before using the chat again.
6. **(host)** The privacy page says nginx's access log, which records IP addresses, is "deleted
   automatically after a short time". `cat /etc/logrotate.d/nginx` shows how long: for example,
   `daily` with `rotate 10` keeps ten days of old logs. If it keeps much longer, shorten `rotate`, or write the real period
   into `privacyLogs` (`frontend/src/lang/translations.ts`, both languages).

### Troubleshooting

| Message in the backend log | Meaning | Fix |
|---|---|---|
| `app.ask.ip-hash-key (env ASK_IP_HASH_KEY) must be at least 32 characters` (and the service doesn't start) | B4 was skipped | Set the key (B4), `sudo systemctl restart finnish-backend` |
| `Bedrock denied the guardrail check` with `User: arn:aws:iam::…:user/BedrockAPIKey-… is not authorized to perform: bedrock:InvokeGuardrailChecks` | The backend signed with a Bedrock API key (`AWS_BEARER_TOKEN_BEDROCK`), not with the role | Server: remove the variable (B3). Local `bootRun`: comment it out in `backend/.env`, then `eval "$(aws configure export-credentials --profile fin-learn-app --format env)"` in the same terminal |
| The same, with `…assumed-role/fin-app-ec2-bedrock/… is not authorized` | The right identity, but the role lacks the permission | B2 |
| `Bedrock denied the chat model` | The role may not call `ASK_MODEL_ID` | B1, "If you ever change `ASK_MODEL_ID`" |
| `Bedrock didn't find the chat model` | A wrong `ASK_MODEL_ID`, or the model isn't enabled | B1, the last paragraph |
| `MCP initialize failed after … ms: HTTP 403` | `ASK_MCP_URL` is wrong, or the role lacks the Lambda permission | B4; Part A's 403 table |
| `MCP initialize failed after … ms: connection failed` | `ASK_MCP_URL` is unset: the default is the local dev server | B4 |
| `app.ask.turnstile.secret-key (env TURNSTILE_SECRET_KEY) is not set` | No human check: no chat session can start | B4 |
| `Ask startup cleanup: requests cut off by a restart were found` | A deploy or crash cut off running requests; they were marked as errors and their reservations released | Nothing, if it's rare. Check B5's `TimeoutStopUSec` |
| `Actual cost above the reservation` | A request cost more than its worst-case estimate | Part C, C2 |
| `Chat answer hit the output limit` | An answer reached `app.ask.agent.chat-max-tokens`; the visitor got it without its unfinished sentence | Nothing, if it's rare. If it's common, raise the setting |
| `Fit check runs without the about entry` | The backend couldn't read the `about` entry, so the check has no skill levels and may rate a skill too high | Check the MCP lines above, and that the profile has `about.md` |

nginx writes `limiting requests, excess: … by zone "ask"` (level `warn`) to
`/var/log/nginx/finnish.error.log` for each refused request. A few are normal; many from one
address are a script.

## Part C: cost

### C1. Where the chatbot shows up on the bill

| Line in Cost Explorer (service) | What it is | Shared? |
|---|---|---|
| **Claude Haiku 4.5 (Amazon Bedrock Edition)** | The model's tokens. Billed through AWS Marketplace, so it is **not** under "Amazon Bedrock" | Yes: the essay feature uses the same model |
| **Amazon Bedrock** | The guardrail checks (`InvokeGuardrailChecks`, per 1,000 characters) | No: chatbot only |
| AWS Lambda | The MCP server | Within the free tier (1 million requests a month) |

Gross cost (credits excluded) of the two Bedrock lines so far:

| Month | Claude Haiku 4.5 | Amazon Bedrock | Mostly |
|---|---|---|---|
| 2026-08 | $0.034 | – | essays |
| 2026-09 | $0.004 | – | essays |
| 2026-10 | $0.197 | $0.010 | chatbot development and red-team runs |
| 2026-11 (to the 3rd) | $0.101 | $0.004 | chatbot development |

So the essay feature costs a few cents a month; nearly all Bedrock cost from now on will be the
chatbot. The credits have covered both lines so far (each charge has an equal `Credit` line):
Marketplace-billed models are covered by this account's credits. Credits end around November
2026; after that, these numbers are paid.

### C2. Compare the app's own numbers with the bill

**What:** after the first week of real use, and again after the first month, compare what the
backend recorded with what AWS charged.
**Why:** the backend stops the chatbot at its caps ($0.50 a day, $5 a month) using its **own**
price table (`ASK_PRICE_*`). If those prices are too low, the real bill is higher than the caps
suggest.

**The app's numbers (host):**

```bash
sudo -u postgres psql finnish_learning_app -c "select day, round(spent_usd, 4) as spent_usd, round(reserved_usd, 4) as reserved_usd from ask_daily_stats order by day desc limit 14"
```

The same numbers are in the admin page's **Chatbot** tab ("Daily totals").

**The bill (laptop),** gross, per day. Each Cost Explorer API call costs **$0.01**:

```bash
aws ce get-cost-and-usage --time-period Start=<YYYY-MM-DD>,End=<YYYY-MM-DD> --granularity DAILY --metrics UnblendedCost --filter '{"And":[{"Dimensions":{"Key":"SERVICE","Values":["Claude Haiku 4.5 (Amazon Bedrock Edition)","Amazon Bedrock"]}},{"Dimensions":{"Key":"RECORD_TYPE","Values":["Usage"]}}]}' --query 'ResultsByTime[].[TimePeriod.Start,Total.UnblendedCost.Amount]' --output text
```

`End` is exclusive. Cost Explorer's days are UTC and the app's are Helsinki days, so compare
**week totals**, not single days. AWS updates the data a few times a day, so leave out the last
day.

**Verify:** the two week totals differ by less than about 10 %, plus a few cents for essays. If
the bill is clearly higher, check the current Haiku 4.5 price for the EU profile on the Bedrock
pricing page, and update the four `ASK_PRICE_*` values. Also count, on the host:
`sudo journalctl -u finnish-backend --since "7 days ago" | grep -c 'Actual cost above the reservation'`.
More than a handful means requests regularly cost more than their worst-case reservation (most
likely a long profile entry); raise `app.ask.budget.tool-result-tokens`, or keep the entries
shorter.

### C3. Budget alerts

Two budgets exist (2026-11-03):

| Budget | Measures | Alerts | Fires today? |
|---|---|---|---|
| `Monthly cap 15 USD` | **Gross** cost: credits and refunds excluded | Actual above 85 % and 100 %, forecast above 100 % | Forecast for November: $13.60 |
| `My Zero-Spend Budget` | **Net** cost: credits included | Above $1 | Never while credits last; every month after they end |

The gross budget is the right kind: it sees real usage while credits hide it. But the instance
alone costs about $14.30 a month, and the chatbot may add up to $5, so its 85 % alert ($12.75)
would fire every month and stop meaning anything.

**Recommended** (your choice; console, Billing and Cost Management → **Budgets**):

1. Edit `Monthly cap 15 USD`: amount **$20**. It stays the alarm for "the whole account costs
   more than expected".
2. Add a budget that watches only the AI spend: select `Monthly cap 15 USD` → **Actions** →
   **Copy** (or create one with the same settings: monthly, unblended cost, credits and refunds
   excluded). Name it `AI services 6 USD`, amount **$6** (the chatbot's $5 cap plus the essays),
   add the filter **Service** = `Claude Haiku 4.5 (Amazon Bedrock Edition)` and `Amazon Bedrock`,
   and an alert at **100 % actual**. If it fires, the app's own cap didn't hold or its prices are
   wrong: C2.
3. Optional: delete `My Zero-Spend Budget` once the credits end, or it emails you every month.

**Verify:**

```bash
aws budgets describe-budgets --account-id "$ACCOUNT_ID" --query 'Budgets[].[BudgetName,BudgetLimit.Amount,CalculatedSpend.ActualSpend.Amount,CalculatedSpend.ForecastedSpend.Amount]' --output text
```

Budget alerts come by email and are based on billing data that can be up to a day late: they
are a check on the app's caps, not a replacement for them.

### C4. Optional, not recommended now: separate cost for the chatbot

An **application inference profile** is your own copy of the `eu.` Haiku 4.5 profile with a tag
(e.g. `app=recruiter-chatbot`). With `ASK_MODEL_ID` set to its ARN, Cost Explorer can split the
chatbot's tokens from the essays'. It needs the profile (`aws bedrock create-inference-profile
--inference-profile-name ask-haiku45 --model-source copyFrom=<the eu. profile's ARN> --tags
key=app,value=recruiter-chatbot`), an IAM statement for the new ARN (B1's model-change steps),
and the tag activated as a cost allocation tag in the Billing console (it applies from then on,
not to the past). Not worth it today: the essays cost a few cents a month (C1).

### C5. Optional, not recommended: an automatic kill switch

An **AWS Budgets action** can detach `fin-app-bedrock-invoke` from the role when a budget is
reached. That stops the chatbot **and the essay feature** until you attach it again by hand, and
it acts on billing data that is up to a day late. The backend's own caps already stop the
chatbot within one request of $0.50 a day and $5 a month, and C3's budget tells you if they
fail. Add it only if you want a second, blunt lock: Budgets → the budget → **Actions** →
**IAM policy**, policy `fin-app-bedrock-invoke`, role `fin-app-ec2-bedrock`.

## Part D: known gaps on the host (out of scope)

Not part of the chatbot, but the chatbot makes them matter more: the database now holds
conversations and job descriptions. Checked on 2026-11-03:

- **SSH (port 22) is open to the whole internet** (`0.0.0.0/0`). Limit it to your own IP in the
  security group, and update the rule when your IP changes.
- **PostgreSQL (port 5432) is reachable from one outside IP address.** Close it and use an SSH
  tunnel for remote access (`ssh -L 5432:localhost:5432 …`).
- **There are no EBS snapshots and no snapshot schedule.** A lost disk loses the database. A Data
  Lifecycle Manager policy can take daily snapshots and keep a few. Remember that snapshots
  contain the conversations, which the privacy page promises to delete after 30 days: keep at
  most 30 days of snapshots.