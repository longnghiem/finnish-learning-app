# mcp-server

A small, read-only **MCP server** that gives the opisuomea.org recruiter chatbot ("Ask Himmel")
access to a curated developer profile, and nothing else.

- The **Spring Boot backend** is the MCP host and client. It runs the chat with Claude on AWS
  Bedrock and calls this server when the model wants to look something up.
- This server runs on **AWS Lambda** (TASK_05/06). It only knows one bundled file, `index.json`,
  built from markdown profile entries by the script in this project.
- Recruiters never talk to this server directly.


## Requirements

- Node.js 22 (the Lambda runtime is `nodejs22.x`)
- npm

```bash
npm ci               # install the exact versions from package-lock.json
npm run typecheck    # TypeScript type check, no output files
npm test             # unit tests (Vitest)
```

## The profile

A profile is a folder of markdown files, one file per **entry**:

```
<profile-dir>/
├── about.md                  # exactly one; type: about
├── experience/<name>.md      # one per job; type: experience
├── projects/<name>.md        # one per project; type: project
└── stories/<name>.md         # one per anecdote (STAR style); type: story
```

`profile.example/` is a complete, **fictional** example. Copy it as a starting point.

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
type: experience                   # about | experience | project | story
title: Backend developer, Acme Oy
tags: [kotlin, spring-boot, kafka]
summary: One or two sentences, ≤300 chars. Shown by list_entries.
period: 2022-03 – 2024-08          # optional, experience only
skills:                            # optional; proficiency is what fit-check honesty is based on
  - { name: Kotlin, level: strong }      # strong | working | familiar
  - { name: AWS Lambda, level: familiar }
notDone: [Kubernetes in production]   # optional; explicit gaps (about entry mainly)
---
Body markdown. The full text returned by get_entry. Quotes for fit check are taken from here.
```

| Field | Required | Meaning |
|---|---|---|
| `id` | yes | The file path without `.md`, e.g. `projects/finnish-learning-app`. Only `a-z`, `0-9`, `-`, `/`; at most 100 characters. |
| `type` | yes | Must match the folder: `about.md` → `about`, `experience/` → `experience`, `projects/` → `project`, `stories/` → `story`. |
| `title` | yes | Short human-readable name. |
| `tags` | yes | Keywords (technologies, topics). May be empty: `[]`. |
| `summary` | yes | 1–2 sentences, at most 300 characters. The model sees only this before deciding to open the entry, so make it specific. |
| `period` | no | When it happened, free text. Only allowed on `experience` entries. |
| `skills` | no | `{ name, level }` items. `strong` = used a lot, could lead; `working` = productive without help; `familiar` = basics only. Be honest: the fit check uses these levels to avoid overselling. |
| `notDone` | no | Things you have explicitly **not** done. Gives the fit check real gaps to report. Mostly used in `about.md`. |
| body | yes | The full text in markdown. Must not be empty. Fit-check quotes must come from the title, summary or body. |

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
- the body is empty.

## Building the index

```bash
npm run build:index -- --profile-dir <path-to-profile> --out build/index.json

# with the example profile
npm run build:index -- --profile-dir profile.example --out build/index.json
```

On success it prints `Wrote <n> entries to <file>` and exits with code 0. On any problem it prints
the list and exits with code 1, so scripts can stop before deploying a broken profile.

`index.json` has this shape (entries sorted by `type`, then `id`):

```json
{
  "generatedAt": "2026-10-19T12:00:00.000Z",
  "entries": [
    {
      "id": "about",
      "type": "about",
      "title": "About Example Person",
      "tags": ["kotlin", "typescript"],
      "summary": "…",
      "skills": [{ "name": "Kotlin", "level": "strong" }],
      "notDone": ["Kubernetes in production"],
      "body": "…"
    }
  ]
}
```