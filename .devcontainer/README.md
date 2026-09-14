# Dev Container — Claude Code Sandbox

A containerized, network-restricted environment for running **Claude Code** against this
monorepo. The point is isolation: Claude Code runs with `--dangerously-skip-permissions`-style
autonomy inside a box where a firewall limits what it can reach, so an agent session can't
touch arbitrary hosts on the network or files outside the mounted workspace.

### Mounts

- Repo → `/workspace` (bind, the working directory).
- `claude-code-config-${devcontainerId}` → `/home/node/.claude` (volume — Claude Code config
  persists per container).
- `claude-code-bashhistory-${devcontainerId}` → `/commandhistory` (volume — shell history
  survives rebuilds).
- `~/.claude/skills` → `/home/node/.claude/skills` (bind — host skills are shared in).
- `~/devcontainer-tmp` → `/tmp` (bind — so `/tmp/RESEARCH.md`, `/tmp/PLAN.md`, `TASK_XX_*.md`
  written by the planner/researcher skills are visible on the host).

> `~/devcontainer-tmp` must exist on the host before the container starts, or the bind mount
> fails.

### Firewall allowlist

Everything outbound is rejected except: DNS, SSH, loopback, the host `/24` subnet, GitHub's
published IP ranges (fetched from `api.github.com/meta`), and these domains —
`registry.npmjs.org`, `api.anthropic.com`, `sentry.io`, `statsig.com`,
`marketplace.visualstudio.com`, `vscode.blob.core.windows.net`,
`update.code.visualstudio.com`, `knowledge-mcp.global.api.aws`.

The host subnet is allowed, so services you run on the host (Postgres on `5532`, Kafka on
`9092`, backend on `8080`) remain reachable from inside the container.

## Commands

From the repo root:
```bash
devcontainer up --workspace-folder .
devcontainer exec --workspace-folder . zsh
```

### Rebuild after editing the Dockerfile or devcontainer.json

```bash
devcontainer build --workspace-folder . --no-cache
devcontainer up --workspace-folder . --remove-existing-container
```

Inside the container:
```bash
claude                          # start Claude Code
```

To allow another host, add it to the `for domain in ...` list in `init-firewall.sh` and re-run
the script (no rebuild needed — the script is copied into the image, so a rebuild is only
required to make the change permanent).
