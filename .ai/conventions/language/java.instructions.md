---
description: "Java instructions: code style, null/optional usage, exceptions, time, concurrency, and build conventions."
applyTo: "**/*.java,**/pom.xml,**/build.gradle,**/build.gradle.kts"
---

# Java Instructions

These instructions extend `/standards/*`. If conflicting guidance exists, company-wide standards win unless an ADR overrides.

## Style & readability

- Prefer small, single-purpose methods.
- Avoid deep nesting; refactor early.
- Prefer clear names over abbreviations.
- Avoid “utility dumping grounds” (`Utils`, `Common`, `Helper`) unless tightly scoped.

## Nulls and Optional

- Prefer explicit results over `null` for “not found” scenarios.
- Use `Optional` for return types where it improves clarity; avoid `Optional` for fields unless deliberately designed.
- Never return `null` inside `Optional`.

## Exceptions

- Throw exceptions for exceptional cases, not control flow.
- Prefer domain-specific exceptions at service boundaries.
- Include actionable context in exception messages (but never secrets or personal data).

## Time

- Use `java.time` (`Instant`, `LocalDate`, `Duration`) instead of legacy `Date`.
- Avoid scattering `Instant.now()` across logic; inject a clock where determinism matters.

## Concurrency

- Avoid ad-hoc threading; use well-defined executors/schedulers.
- Guard shared mutable state; prefer immutability where possible.
- Document concurrency assumptions (thread safety, ordering, retries).

## Build & dependencies

- Keep dependency versions centralized (BOM/dependencyManagement where applicable).
- Adding dependencies requires justification and ownership (per company standards).

## References

- `/standards/coding-standards.md`
- `/standards/testing-standards.md`
- `/standards/security-standards.md`
- `/standards/documentation-standards.md`
- `/standards/architecture-standards.md`
