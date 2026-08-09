---
description: "Kotlin instructions: idiomatic Kotlin style, null-safety, immutability, result modeling, and interoperability conventions."
applyTo: "**/*.kt,**/*.kts,**/pom.xml,**/build.gradle,**/build.gradle.kts"
---

# Kotlin Instructions

These instructions extend `/standards/*`. If conflicting guidance exists, company-wide standards win unless an ADR overrides.

## Kotlin style

- Prefer immutability (`val`) by default.
- Prefer data classes for DTOs/value objects.
- Prefer expressions over statements where it improves clarity (but avoid clever one-liners).

## Null-safety

- Avoid `!!` except at strict boundaries with explicit justification.
- Model nullability in types, not in comments.
- Prefer `requireNotNull()` with a clear message when enforcing invariants.

## Result modeling

- Prefer sealed classes / explicit result types for multi-outcome domain operations when it improves clarity.
- Don’t use exceptions for routine branching outcomes (e.g., validation failures) unless consistent with existing architecture.

## Java interop

- Be careful with platform types; add annotations or wrappers at boundaries if needed.
- Avoid leaking Kotlin-specific constructs across public Java APIs unless intentionally designed.

## Collections & mutability

- Prefer persistent/immutable collections when appropriate.
- Avoid exposing mutable collections from public APIs.

## References

- `/standards/coding.instructions.md`
- `/standards/testing.instructions.md`
- `/standards/security.instructions.md`
- `/standards/documentation.instructions.md`
- `/standards/architecture.instructions.md`
