# Testing Standards

These standards define how we validate behavior before merge and release.

## Principles

- Tests exist to protect behavior and enable refactoring
- Prefer reliable tests over maximum coverage
- Flaky tests are treated as bugs

## Test Levels

### Unit Tests

Use for:

- pure logic
- transformations
- branching decisions
- error mapping

### Integration Tests

Use for:

- database interactions
- HTTP clients/servers
- serialization/deserialization
- message brokers
- config wiring

### End-to-End Tests (where applicable)

Use for:

- critical flows spanning multiple components
- high-risk changes to user-visible behavior

## Minimum Requirements (Baseline)

- New logic: unit tests required
- Bug fixes: regression tests required
- Public API/contract changes: integration tests + updated examples required
- Critical flows: at least one higher-level test beyond unit tests (integration or e2e)

## Determinism Rules

Tests must be:

- deterministic
- independent (no order dependency)
- isolated (clean state per test)
- fast by default (flag slow suites separately)

## Mocking Guidelines

- Mock boundaries, not internals
- Prefer fakes over deep mocks when practical
- Avoid tests that mirror implementation step-by-step (they break on refactors)
- Do not mock what you don’t own unless needed for isolation

## Data & Fixtures

- Keep fixtures minimal and readable
- Prefer builders/factories over massive JSON blobs
- Version sample payloads if contracts evolve
