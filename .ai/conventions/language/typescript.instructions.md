---
description: "TypeScript instructions: typing rules, module boundaries, runtime safety, linting/formatting, and TS config expectations."
applyTo: "**/*.ts,**/*.tsx,**/tsconfig*.json,**/.eslintrc*,**/.prettierrc*,**/package.json"
---

# TypeScript Instructions

These instructions extend `/standards/*`. If conflicting guidance exists, company-wide standards win unless an ADR overrides.

## Typing rules

- Avoid `any`. If unavoidable, isolate it and justify with a comment.
- Prefer `unknown` for untrusted inputs, then narrow via type guards.
- Add explicit types at module boundaries:
  - exported functions
  - public APIs
  - API client interfaces
  - shared library surfaces
- Prefer discriminated unions for complex state modeling.
- Prefer `readonly` where it clarifies immutability.

## Runtime safety

- Do not trust external data (API responses, localStorage, query params).
- Validate or parse external data at boundaries (schema validation or explicit parsing).
- Avoid “casting your way out” (`as SomeType`) unless you own the data source and can guarantee shape.

## Errors

- Prefer typed error results for expected failures in libraries.
- For app/UI, ensure errors are handled and user-visible errors are understandable.

## Imports and module structure

- Avoid circular dependencies.
- Keep types close to usage; avoid giant global type files.
- Prefer consistent export style per project (document it if needed).

## Tooling expectations

- Linting + formatting must be enforced (CI or pre-commit) for production projects.
- Type-checking must run in CI for production projects.
- Keep `tsconfig` strictness consistent across a repo unless explicitly documented.

## References

- `/standards/coding-standards.md`
- `/standards/testing-standards.md`
- `/standards/security-standards.md`
- `/standards/documentation-standards.md`
- `/standards/architecture-standards.md`
