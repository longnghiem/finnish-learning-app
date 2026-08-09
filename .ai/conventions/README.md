# AI Agent Conventions

This directory is a copy of my personal AI-agent instructions — the coding conventions, standards, and
agreements I want any AI agent to follow when working in this repository. It is checked in so that the
rules travel with the code and apply identically across agents, machines, and sessions.

## Precedence

**`internal.instructions.md` has the highest priority.** It overrides any conflicting guidance from any
other document, including everything in `standards/` and `language/`. When it contradicts another file,
follow `internal.instructions.md`.

Below that, the `language/*` files each state that they extend `standards/*`: where the two conflict,
the company-wide standard in `standards/` wins, unless an ADR explicitly overrides it.

Resolution order, highest to lowest:

1. `internal.instructions.md`
2. An ADR that explicitly overrides a standard
3. `standards/*.instructions.md`
4. `language/*.instructions.md`

## What PR-Agent reads

The automated reviewer reads **only `REVIEW_CONTEXT.md`** — it is the single file listed under
`repo_context_files` in `.pr_agent.toml`. The full instruction files (`internal.instructions.md`,
`standards/*`, `language/*`) are *not* read by the reviewer: together they exceed the 500-line
context budget PR-Agent allows for repo context.

Consequence: editing a rule in `internal.instructions.md` does **not** change review behaviour.
A rule only reaches the reviewer once `REVIEW_CONTEXT.md` is updated to reflect it. Treat the two
as a pair — change a rule, then propagate the reviewable part of it into `REVIEW_CONTEXT.md`,
keeping that file inside the line budget.

## Maintenance

Rules are additive: when a new convention is found, add it rather than rewriting an existing entry,
so that prior decisions stay traceable. `internal.instructions.md` is numbered and includes placeholder
slots for rules not yet written.