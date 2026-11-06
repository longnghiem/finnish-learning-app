# mcp-server

A small, read-only **MCP server** that gives the opisuomea.org recruiter chatbot ("Ask Himmel")
access to a curated developer profile, and nothing else.

- The **Spring Boot backend** is the MCP host and client. It runs the chat with Claude on AWS
  Bedrock and calls this server when the model wants to look something up.
- This server runs on **AWS Lambda**. It only knows one bundled file, `index.json`,
  built from markdown profile entries by the script in this project.
- Recruiters never talk to this server directly.

## Requirements

- Node.js 22 (the Lambda runtime is `nodejs22.x`)
- npm

```bash
npm ci               # install the exact versions from package-lock.json
npm run typecheck    # TypeScript type check, no output files
npm test             # unit tests (Vitest)
npm run format       # format the TypeScript files with Prettier (markdown is left alone)
npm run format:check # only report unformatted files
```

## The profile

A profile is a folder of markdown files, one file per **entry**:

```
<profile-dir>/
├── about.md                  # exactly one; type: about
├── education/<name>.md       # one per degree, certificate or course; type: education
├── experience/<name>.md      # one per job; type: experience
├── faq/<name>.md             # one per common recruiter question (title = the question); type: faq
├── projects/<name>.md        # one per project, talk or article; type: project
└── stories/<name>.md         # one per anecdote (STAR style); type: story
```

### Where the real profile lives

**The real profile must never be committed to this repository.** Keep it in a private folder
outside the repo (for example `~/private/recruiter-profile/`) and pass its path with
`--profile-dir`. `/profile/` is listed in `.gitignore` as a safety net in case a copy ends up
inside this folder.

### Entry format

Each file starts with a **frontmatter** block (YAML between two `---` lines), followed by the
**body** in markdown:

```markdown
---
id: experience/acme-backend        # unique; equals the path without .md
type: experience                   # about | education | experience | faq | project | story
title: Backend developer, Acme Oy
tags: [kotlin, spring-boot, kafka]
summary: One or two sentences, ≤300 chars. In the index (every prompt) and in list_entries.
period: 2022-03 – 2024-08          # optional, education and experience only
skills:                            # optional; proficiency is what fit-check honesty is based on
  - { name: Kotlin, level: strong }      # strong | working | familiar
  - { name: AWS Lambda, level: familiar }
notDone: [Kubernetes in production]   # optional; explicit gaps (about entry mainly)
---
Body markdown. The full text returned by get_entry.
```

| Field | Required | Meaning |
|---|---|---|
| `id` | yes | The file path without `.md`, e.g. `projects/finnish-learning-app`. Only `a-z`, `0-9`, `-`, `/`; at most 100 characters. |
| `type` | yes | Must match the folder: `about.md` → `about`, `education/` → `education`, `experience/` → `experience`, `faq/` → `faq`, `projects/` → `project`, `stories/` → `story`. |
| `title` | yes | Short human-readable name. |
| `tags` | yes | Keywords (technologies, topics). May be empty: `[]`. |
| `summary` | yes | 1–2 sentences, at most 300 characters. The model sees only this before deciding to open the entry, so make it specific. |
| `period` | no | When it happened, free text. Only allowed on `education` and `experience` entries. |
| `skills` | no | `{ name, level }` items. `strong` = used a lot, could lead; `working` = productive without help; `familiar` = basics only. Be honest: the fit check uses these levels to avoid overselling. |
| `notDone` | no | Things you have explicitly **not** done. Gives the fit check real gaps to report. Mostly used in `about.md`. |
| body | yes | The full text in markdown. Not empty, at most 6,000 characters: the backend passes at most 8,000 characters of an entry to the model, so a longer body would lose its end. Fit-check quotes must come from the title, summary or body. |

Unknown fields are rejected, so a typo such as `sumary:` fails the build instead of being ignored.

Hidden files and folders (names starting with `.`, such as `.git/`, `.obsidian/` or `.trash/`) are
skipped, so the profile folder can be a git repository or a notes vault.

The build fails, listing **every** problem with its file path, when:

- the frontmatter is missing, isn't valid YAML, or breaks a field rule above;
- `id` isn't the file path, or `type` doesn't match the folder;
- a file is somewhere else (e.g. `notes.md` at the root, or `projects/sub/x.md`);
- a file uses an upper-case extension (`.MD`);
- two files declare the same `id`;
- there isn't exactly one `about` entry;
- the body is empty or longer than 6,000 characters.

## Building the index

```bash
npm run build:index -- --profile-dir <path-to-profile> --out build/index.json

# with the example profile
npm run build:index -- --profile-dir profile.example --out build/index.json
```

## The MCP server

The server is read-only and stateless. It answers every request with one plain JSON reply
(never an SSE stream).

### Tools

| Tool | Input | Output | Errors (as tool error results, `isError: true`) |
|---|---|---|---|
| `list_entries` | `{ type?: "about" \| "education" \| "experience" \| "faq" \| "project" \| "story" }`; no other fields | JSON array of `{ id, type, title, tags, summary }`, **no body**, in index order (by type, then id) | Unknown `type` or extra fields: `Input validation error: …` |
| `get_entry` | `{ id: string }`: at most 100 characters of `a-z`, `0-9`, `-`, `/`; no other fields | One complete entry as JSON: all frontmatter fields plus `body` | Invalid id or extra fields: `Input validation error: …`. Unknown id: `Unknown entry id "<id>". Call list_entries to see the available ids.` |

### Resource `profile://index`

Plain text (`text/plain`), one line per entry, fields separated by ` | `:

```
id | type | title | tags | summary
```

For example:

```
about | about | About Example Person | kotlin, typescript, spring-boot, react, aws | Fictional full-stack developer used as sample data. …
experience/acme-backend | experience | Backend developer, Acme Oy | kotlin, spring-boot, kafka, postgresql | Built and ran Kotlin/Spring Boot services …
```

Inside a value, line breaks become spaces (so each entry is exactly one line) and `|` becomes `/`
(so `|` only separates fields). The backend reads this resource and puts it into the model's prompt.

### Running locally

**Development only: no authentication, listens on 127.0.0.1 only. Never deploy it.**

```bash
npm run build:index -- --profile-dir profile.example --out build/index.json
npm run dev                                   # http://localhost:3001/mcp, index from build/index.json
npm run dev -- --index path/to/index.json     # another index file
```

Try it from another terminal (`jq .` pretty-prints the reply):

```bash
curl -s http://localhost:3001/mcp -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' -H 'MCP-Protocol-Version: 2025-11-25' -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"list_entries","arguments":{}}}' | jq .
```

### Security

- **Only the backend can call it.** In production the Lambda function URL uses `AWS_IAM` auth: AWS
  rejects any request not signed by the backend's EC2 instance role before our code runs.
- **Browsers are refused.** Any request with an `Origin` header gets 403. Browsers send one; the
  backend never does.
- **Only POST.** Anything else gets 405, before the MCP SDK sees it. This matters on Lambda: the SDK
  would hold a GET open until the timeout, and the backend's MCP client sends one GET per connection.
- **Small requests only.** Bodies over 64 KB get 413.
- **Read-only, no other data.** No tool changes anything. The server has no database, makes no
  network calls, and its Lambda role has no AWS permissions beyond writing logs.
- **No conversation content in logs.** One log line per request: the JSON-RPC method, the tool name
  and the latency. Never the request body.

## Deploy

The server runs on AWS Lambda behind a function URL with IAM auth. The AWS resources are defined
with the AWS CDK in [`infra/`](../infra/README.md).

- **One-time setup:** [docs/runbooks/recruiter-chatbot-aws-setup.md](../docs/runbooks/recruiter-chatbot-aws-setup.md), Part A.
- **After every change** to the profile or the code:

  ```bash
  bash deploy-mcp.sh --profile-dir <path-to-your-profile>
  ```

  It validates the profile, bundles the code (`npm run bundle` → `dist/lambda/`), runs
  `cdk deploy`, and checks that the function URL refuses unsigned requests. `--help` lists all flags.
- **Rehearsal:** `DRY_RUN=1 bash deploy-mcp.sh --profile-dir …` builds everything and writes the
  CloudFormation template to `infra/cdk.out/`, without any AWS access.
- **Calling the deployed server from your laptop:** `bash allow-local-invoke.sh` allows your IAM
  user to call the function URL (`--remove` takes that back). Then `bash verify-function-url.sh`
  checks that an unsigned request gets 403 and a signed one gets 200, and shows the logs.

Everything in the deployed profile is effectively public: the chatbot can be made to repeat it.