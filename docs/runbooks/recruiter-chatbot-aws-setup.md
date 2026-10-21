# Recruiter chatbot: one-time AWS setup

The chatbot's AWS resources are defined in code with the **AWS CDK** (`infra/`). This runbook is
what you do **once** to get from an empty account to a working, verified deployment. After that,
`mcp-server/deploy-mcp.sh` is the only command you need.

- **Part A**: the MCP server on Lambda (this page).
- **Part B**: model access, guardrails and the budget alarm (added later).

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

## Part B: model access, guardrails, budget

Added later.