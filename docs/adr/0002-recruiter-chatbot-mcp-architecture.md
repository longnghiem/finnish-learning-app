# ADR 0002: Recruiter chatbot ("Ask Himmel") with MCP on AWS Bedrock

**Status:** Accepted, built and live on `opisuomea.org`.

## Context

The owner wants a chatbot on `opisuomea.org` where recruiters can ask about the owner's
professional background. Its main purpose is to build a chatbot with **MCP** (Model Context
Protocol). On top of a Q&A chat, a **"Check my fit"** mode lets a recruiter paste a job description
and get an honest, evidence-based assessment that doesn't oversell.

Constraints:
- **Safe against malicious use:** prompt injection, jailbreaks, off-topic abuse, embarrassing
  output, made-up claims, and attacks on the web page.
- **Access limited to curated data**, so that even a fully manipulated model can't harm the app,
  the server or the AWS account.
- **Minimal token cost:** a hard ceiling, first $1/month, raised to **$5/month** when the model
  changed to Claude Haiku 4.5 (about 4× the price of the planned Haiku 5.5).
- The app runs on a single small EC2 instance (`t3.micro`), so new components must not add much
  memory there.

## Decision

Build the chatbot as a `/ask` page backed by the existing Spring Boot backend, which acts as the
**MCP host and client**, plus a separate, read-only **TypeScript MCP server on AWS Lambda** that
serves a curated profile.

```
Browser ── Turnstile token ──▶ Spring Boot (EC2) ── InvokeGuardrailChecks ──▶ Bedrock Guardrails
   ▲                              │  MCP host + client, agent loop
   └──── SSE: steps, answer ──────┤── Converse ──▶ Claude Haiku 4.5 (eu. inference profile)
                                  └── MCP over HTTPS, SigV4-signed ──▶ Lambda function URL (AWS_IAM)
                                                                        └─ index.json (curated profile)
```

| Choice | Value |
|---|---|
| Audience / access | Anonymous, no login (recruiters arrive from the CV or LinkedIn). A visible Cloudflare Turnstile check creates a chat session (60 minutes, up to 10 messages), bound to the visitor's IP hash. Per IP: 5 new sessions an hour, 30 chat messages and 3 fit checks a day. On top of that, the global spending cap. |
| MCP topology | Spring Boot backend = MCP host + client (the Java MCP SDK, one shared client); it runs the agent loop on Bedrock. MCP server = TypeScript on Lambda, stateless Streamable HTTP with plain JSON responses, behind a **function URL with `AWS_IAM` auth** that only the EC2 instance role may invoke; the backend signs each request with SigV4. The server accepts only POST, refuses any `Origin` header and bodies over 64 KB, and never logs request bodies. The Lambda role has no permissions beyond writing logs. |
| MCP surface | Tools `list_entries(type?)` (summaries only) and `get_entry(id)`. Resource `profile://index` (one line per entry), put into the system prompt to save a model round trip. |
| Profile data | One markdown file per entry (`about`, `education/*`, `experience/*`, `faq/*`, `projects/*`, `stories/*`) with YAML frontmatter; skills carry a proficiency level and gaps are listed explicitly. A build step validates them and compiles `index.json`, which is **bundled into the Lambda package**. |
| Data location | The real profile lives in a **private folder outside the public repo** and is passed to the deploy script with `--profile-dir`. The repo holds only `mcp-server/profile.example/` with fictional entries. |
| Infrastructure | **AWS CDK** (TypeScript) in `infra/`, stack `FinAppProfileMcp`: the Lambda (`nodejs22.x`, arm64, 256 MB, 10 s timeout, reserved concurrency 5), its logs-only role, a 14-day log group, the `AWS_IAM` function URL without CORS, and the invoke policy on the existing EC2 role (which stays hand-made). Deployed with `mcp-server/deploy-mcp.sh`. Chosen over hand-run CLI commands because the setup becomes code that can be reviewed, tested and recreated. |
| Model | **Claude Haiku 4.5** via the EU inference profile `eu.anthropic.claude-haiku-4-5-20251001-v1:0` (a setting, `ASK_MODEL_ID`), `Converse` API, no extended thinking. One prompt-cache point at the end of the latest message; Bedrock only caches a prefix of at least 4,096 tokens, so short conversations aren't cached. |
| Agent loop | Chat: at most 3 tool rounds with up to 4 entry reads each, 1,000 output tokens per call. The prompt asks for at most 120 words, or at most 200 for how and why questions, projects, stories, references and questions about the chatbot, which end with an offer to go deeper. Every request carries the tool list; each tool call is recorded as the turn's tool trace and shown to the visitor as "How I found this". |
| Streaming | **Progress steps only** over SSE (`GUARDRAIL`, `TOOL_CALL` with the tool and its arguments, `COMPOSING`, and `VERIFYING` for the fit check); the answer is sent whole, then `done`. Plain `Converse` on a small thread pool, no async client and no Netty. |
| Guardrails | `InvokeGuardrailChecks` (detect-only, no guardrail resource needed) on **inputs** (chat messages and job descriptions) before any model call: content filter + prompt-attack detection in one call. A score at or above the threshold blocks: content 0.6; prompt attack 0.6 for chat and 0.8 for job descriptions, which are full of instructions. Any prompt-attack category counts, because the category labels proved unreliable on Finnish text. **Fail-closed**, no retries, 10 s timeout. No PII filter (it would redact the owner's own contact details). The model's **answers are not checked**: an accepted risk, because the model can only use the curated profile, and the red-team suite checks its answers to attacks. |
| Budget | One shared pool: **$5.00/month** and **$0.50/day** (Europe/Helsinki calendar, both settings), computed from the per-turn costs stored in Postgres with Bedrock's eu-north-1 prices, guardrail checks included. A worst-case cost is reserved before each request and settled with the real cost afterwards; at startup, reservations and turns left over from a restart are released. Chat messages are at most 500 characters. |
| Fit check | Separate mode and endpoint (`POST /api/ask/fit-check`, SSE), job descriptions up to 6,000 characters. Research calls (≤ 6 rounds, 1,000 output tokens) read entries through MCP; one report call (1,500 tokens, 120 s timeout) must call a strict, forced `submit_fit_report` tool at temperature 0, so Bedrock guarantees the report's shape. Per-requirement verdicts (Strong / Partial / Gap / Not in profile), each backed by an entry id + verbatim quote (from the entry's title, summary or body) that the backend verifies; failed quotes are removed, a requirement without verified evidence becomes "Not in profile", and verdicts are never raised. A lowered requirement is added to the gaps, and the model's summary (written before the check) is replaced by a plain one built from the checked rows, so it never claims what the rows deny. No percentage score. `:keepalive` comments every 15 s keep the stream open behind nginx. |
| Cap reached | "Resting until tomorrow" (or next month) with the owner's email and LinkedIn; the mascot sleeps. |
| Logging | Conversations, job descriptions and fit reports stored in the existing Postgres for **30 days**, then purged every night at 03:15 Helsinki time. IPs stored only as keyed hashes (HMAC-SHA256). Per-turn tokens, cost, tool trace and guardrail scores. Daily aggregate stats (no personal data) kept. The owner reads and deletes sessions through the admin API (`/api/admin/ask/*`, `ROLE_ADMIN`). No conversation content in application logs. |
| UI | `/ask` page with tabs Chat \| Paste job description (the fit check), the mascot "Himmel" (his state follows the stream), a human check, starter questions, live progress steps, the tool-call trace, live stats (`GET /api/ask/stats`: answers, attacks blocked, % of the month's budget), a "How this works" section, EN/FI, mobile-first. A public `/privacy` page, linked from both tabs. Entry points: a recruiter banner on `/` and `/portfolio`, an "Ask Himmel" pill in the navbar, and a peeking-cat link on other pages. |

## Consequences

- The model never touches the database, the server or AWS: its only tools read a static,
  curated profile through a server that only the backend can call. A successful prompt injection
  can at worst produce a bad answer, which the guardrail, the prompts and the red-team suite work
  to prevent.
- Spending has a hard ceiling, enforced before each request; at the cap the chatbot stops
  answering until the next day or month instead of costing more.
- Answers arrive whole after the progress steps, not token by token: simpler code and no async
  client, at the cost of a few seconds of waiting with only the steps to watch.
- The backend must run as **one instance**: the startup cleanup assumes no other instance has
  requests in flight.
- Changing the model means changing its prices (`app.ask.price.*`) and checking its prompt-cache
  minimum.

## See also

- [AWS setup runbook](../runbooks/recruiter-chatbot-aws-setup.md): the MCP server's setup, the
  go-live steps and the cost checks.
- [Red-team suite](../../backend/src/test/resources/redteam/README.md): 33 attack and benign
  cases against the real model and guardrail (`./gradlew redTeam`, not part of `./gradlew test`).
- [`mcp-server/`](../../mcp-server/) and [`infra/`](../../infra/): the MCP server and its CDK
  stack.
- [ADR 0001](0001-aws-bedrock-for-essay-evaluation.md): the first Bedrock feature, whose client
  setup the chatbot shares.
