## Context

The owner wants a chatbot on `opisuomea.org` where recruiters, can ask about the owner's professional background. 
Its main purpose is to build a chatbot with MCP** (Model Context Protocol). On top of a
Q&A chat, a **"Check my fit"** mode lets a recruiter paste a job description and get an honest,
evidence-based assessment that doesn't oversell.

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

| Choice | Value |
|---|---|
| Audience / access | Anonymous, no login (recruiters arrive from the CV or LinkedIn). Visible Cloudflare Turnstile check once per chat session. Per-IP limits plus a global cap. |
| MCP topology | Spring Boot backend = MCP host + client; it runs the agent loop on Bedrock. MCP server = TypeScript on Lambda, stateless Streamable HTTP with plain JSON responses, behind a **function URL with `AWS_IAM` auth** that only the EC2 instance role may invoke. The Lambda role has no permissions beyond writing logs. |
| MCP surface | Tools `list_entries(type?)` (summaries only) and `get_entry(id)`. Resource `profile://index` (one line per entry), injected into the prompt to save a model round trip. |
| Profile data | One markdown file per entry (`about`, `education/*`, `experience/*`, `faq/*`, `projects/*`, `stories/*`) with YAML frontmatter; skills carry a proficiency level and gaps are listed explicitly. A build step validates them and compiles `index.json`, which is **bundled into the Lambda package**. |
| Data location | The real profile lives in a **private folder outside the public repo** and is passed to the deploy script with `--profile-dir`. The repo holds only `mcp-server/profile.example/` with fictional entries. |
| Infrastructure | **AWS CDK** (TypeScript) in `infra/`, stack `FinAppProfileMcp`: the Lambda (`nodejs22.x`, arm64, reserved concurrency 5), its logs-only role, a 14-day log group, the `AWS_IAM` function URL, and the invoke policy on the existing EC2 role (which stays hand-made). Deployed with `mcp-server/deploy-mcp.sh`. Chosen over hand-run CLI commands because the setup becomes code that can be reviewed, tested and recreated. |
| Model | **Claude Haiku 4.5** via the EU inference profile `eu.anthropic.claude-haiku-4-5-20251001-v1:0`, `Converse` API, no extended thinking, prompt caching only where the prefix reaches the 4,096-token minimum. |
| Streaming | **Progress steps only** over SSE (e.g. "Reading experience/…"); the answer is sent whole. Plain `Converse`, no async client and no Netty. |
| Guardrails | `InvokeGuardrailChecks` (detect-only) on **inputs** (chat messages and job descriptions) before the model call: content filter + prompt-attack detection, thresholds per mode, **fail-closed**. `ApplyGuardrail` (Standard tier) only as a fallback. No PII filter (it would redact the owner's own contact details). The model's **answers are not checked**: an accepted risk, because the model can only use the curated profile, and the red-team tests check its answers to attacks. |
| Budget | One shared pool: **$5.00/month** and **$0.25/day** (Europe/Helsinki calendar), computed from the per-turn costs stored in Postgres; a worst-case cost is reserved before each call. Chat: 500-char messages, ≤ 10 messages per conversation, 400 output tokens per call. Fit check: 300 output tokens per research call, 1,500 for the single report call. |
| Fit check | Separate mode and endpoint. Per-requirement verdicts (Strong / Partial / Gap / Not in profile), each backed by an entry id + verbatim quote that the backend verifies; failed quotes are downgraded to "Not in profile". No percentage score. |
| Cap reached | "Try again tomorrow" with the owner's email and LinkedIn; the mascot sleeps. |
| Logging | Conversations, job descriptions and fit reports stored in the existing Postgres for **30 days**, then purged nightly. IPs stored only as keyed hashes. Per-turn tokens, cost, tool trace and guardrail verdict. Daily aggregate stats (no personal data) kept. Owner reads them in an `/admin` tab. No conversation content in application logs. |
| UI | `/ask` page with tabs Chat \| Check my fit, the mascot "Himmel", visible tool-call trace, "How this works" diagram, live stats, starter chips, EN/FI, mobile-first. Entry points from `/`, `/portfolio`, the navbar and a small link on other pages. |
