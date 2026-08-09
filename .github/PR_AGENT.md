# AI code review

Pull requests in this repository are reviewed automatically by [PR-Agent](https://docs.pr-agent.ai/), running as a GitHub Action ([`workflows/pr_agent.yml`](./workflows/pr_agent.yml)) 
on the `gpt-4o-mini` model. Opening a PR gets you a generated description and a code review; everything else is on request.

The reviewer is given this repository's own conventions — the numbered rules in [`.ai/conventions/REVIEW_CONTEXT.md`](../.ai/conventions/REVIEW_CONTEXT.md) —
so feedback is specific to this codebase rather than generic advice. Model, ignore rules, and reviewer behaviour are configured in [`.pr_agent.toml`](../.pr_agent.toml).

### Running a command

Commands are **comments on the pull request**, posted in the PR's **Conversation** tab on GitHub. Write the command as the whole comment body, starting with `/`:

```
/improve
```

One command per comment. Arguments can be appended:

```
/review --pr_reviewer.extra_instructions="Focus on the Kafka consumer"
/improve --pr_code_suggestions.num_code_suggestions=3
/ask Does this migration break the existing user_topic_stats aggregation?
```

From a terminal, the same thing via the GitHub CLI:

```
gh pr comment 42 --body "/improve"
```

Three things that trip people up:

- **Inline review comments do not work.** A command left on a specific line inside a file diff fires a different GitHub event than the one this workflow listens for. Post it in the Conversation tab.
- **Nothing runs outside a pull request.** There is no local CLI in this repo, and commands in commit messages or issues are ignored.
- **Comments posted by bots are ignored**, by design — it stops the agent replying to itself.

### Commands

| Command | What it does | Automatic? |
|---|---|---|
| `/describe` | Rewrites the PR title, description, and changed-files walkthrough | ✅ on open |
| `/review` | Posts a code review — findings, security concerns, effort estimate | ✅ on open |
| `/improve` | Suggests concrete code improvements as committable diffs | ❌ on request |
| `/ask <question>` | Answers a question about the diff | ❌ on request |
| `/add_docs` | Generates docstrings for changed functions | ❌ on request |
| `/generate_labels` | Proposes PR labels | ❌ on request |
| `/update_changelog` | Appends the change to a changelog file | ❌ on request |
| `/help` | Lists available commands | ❌ on request |

### Behaviour worth knowing

- **Auto-review fires on PR open, reopen, and ready-for-review — not on later pushes.** This keeps cost and noise down on a small repo. After pushing new commits, comment `/review` to get a fresh review.
- **`/improve` is never automatic.** It produces the most output of any tool, so it is on request only.
- **Fork PRs are not auto-reviewed.** The workflow uses `pull_request` rather than `pull_request_target`, which would grant elevated permissions to code from forks. A maintainer can still comment `/review` on a fork PR.
- **Convention changes take effect only after merging to `main`.** Context files are read from the default branch, so a PR cannot alter the guidance used to review itself.
- Generated code, build output, lockfiles, and DB backups are excluded from analysis — see the `[ignore]` block in `.pr_agent.toml`.