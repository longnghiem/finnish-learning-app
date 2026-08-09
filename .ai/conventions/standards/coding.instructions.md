## Principles

- Clarity over cleverness
- Explicit is better than implicit
- Consistency beats personal preference
- Prefer boring solutions and stable dependencies

## Structure & Readability

- Keep functions small and single-purpose
- Avoid deep nesting; refactor early
- Prefer composition over inheritance unless a clear base type exists
- Avoid “utility dumping grounds”

## Naming

- Use meaningful names
- Avoid unclear abbreviations
- Name by intent, not by type (e.g., `orderRepository` not `orderRepoImpl2`)

## Error Handling

- Never swallow exceptions silently
- Errors must be actionable: include context
- Validate inputs at boundaries (API layer, message consumer, CLI entrypoint)
- Use structured error types where applicable

## Logging

- Log at boundaries and failure points
- Logs must not include secrets or sensitive personal data
- Prefer structured logging fields (requestId, transactionId, correlationId) when available

## Dependencies

- New dependencies must be justified and documented
- Prefer company-approved libraries
- Avoid adding dependencies for small convenience
- Assign an owner for significant dependencies

## Configuration

- No hardcoded environment-specific values
- Configuration must be documented and discoverable
- Provide examples/defaults where appropriate

## Testing

All code must comply with:

- `standards/testing-standards.md`

## Prohibited Practices

- Committing secrets
- Undocumented breaking changes
- Copy-paste architecture without understanding
- Flaky tests left unfixed
