---
description: "React instructions: component patterns, hooks, state management, effects, performance basics, and accessibility baseline."
applyTo: "**/*.tsx,**/package.json"
---

# React Instructions

These instructions extend `/standards/*`. If conflicting guidance exists, company-wide standards win unless an ADR overrides.

## Component design

- Prefer function components and hooks.
- Keep components small and focused; extract reusable logic into hooks.
- Prefer composition over inheritance patterns.
- Avoid prop drilling for widely shared state; use context or a state library intentionally.

## State and data fetching

- Handle loading/error/empty states explicitly.
- Avoid duplicating server state into local component state unless necessary and documented.
- Keep a single consistent server-state approach per app/module (document the chosen tool/pattern).

## Side effects (`useEffect`)

- Keep effects minimal and well-scoped.
- Avoid effects that “sync derived state”; compute derived values from state/props instead.
- Effects that perform external calls must handle cancellation/cleanup where relevant.

## Performance (baseline)

- Do not prematurely memoize everything.
- Use memoization intentionally when:
  - renders are expensive, or
  - referential stability matters for child components/hooks
- Avoid creating large objects in render paths when it causes unnecessary renders.

## Accessibility baseline

- Use semantic HTML first.
- Interactive elements must be keyboard accessible.
- Inputs must have accessible labels.
- Avoid “div button” patterns unless fully accessible (role, tabIndex, key handlers, ARIA as needed).

## Error handling

- Ensure user-facing failures are understandable (no raw stack traces).
- Prefer centralized error boundaries for unexpected rendering failures if the app needs it.

## Testing

- Test behavior, not implementation details.
- Avoid snapshots as the primary signal unless explicitly standardized.
- Prioritize critical flows and edge cases.

## References

- `/standards/coding-standards.md`
- `/standards/testing-standards.md`
- `/standards/security-standards.md`
- `/standards/documentation-standards.md`
- `/standards/architecture-standards.md`
