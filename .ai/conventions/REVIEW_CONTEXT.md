# Review Context — finnish-learning-monorepo

Guidance for automated code review. Condensed from `.ai/conventions/`;
`internal.instructions.md` has the highest priority and overrides any generic
best-practice advice. On conflict, `standards/*` beats `language/*`.

## Stack — do not suggest patterns from other ecosystems

- **backend/** — Kotlin 2.2, Java 21, Spring Boot 4, PostgreSQL 16, jOOQ, Flyway,
  Spring Kafka, JJWT, Spring Security, SpringDoc OpenAPI.
  Tests: JUnit 5, Testcontainers, Mockito-Kotlin.
- **frontend/** — React 19, TypeScript, Vite, React Router 7, TanStack Query 5,
  Tailwind 4, Zod 4. **No test runner is configured.**
- **android/** — Kotlin, Jetpack Compose, Material 3, Hilt, Retrofit 2 + Moshi, KSP.

The three projects are independent builds sharing no code. Never propose a
refactor that spans them.

## Numbered rules — flag violations explicitly and name the rule

1. **Immutable collections.** Prefer `map` / `filter` / `flatMap` over
   accumulating into a `mutableListOf` inside a loop.
2. **Validation.** `require` for function arguments and preconditions; `check`
   for internal state and invariants; `requireNotNull` with a clear message when
   enforcing non-nullity.
3. **No inline `style` in reusable React components.** Inline styles cannot be
   overridden by application CSS.
4. **One class at the top level.** Apply a CSS class to the top-level component;
   style nested elements via selectors or `data-*` attributes, not one class per
   element.
5. **Nav matching compares path SEGMENTS.** `pathname.startsWith(linkTo)` is a
   defect — `/pick_tasks` must not match `/pick`. Split on `/` and compare
   segment by segment.
6. **Logging.** Application logging goes through `createLogger` from
   `@react-commons/utils/logging.ts`. Never `console.log` / `warn` / `error` —
   console output is not persisted and cannot be submitted for debugging.
7. **JVM file I/O.** Use `java.nio.file.Path` with `kotlin.io.path` extensions.
   `java.io.File` is forbidden except where a third-party API demands it, and
   then only via `.toFile()` at the call site.
8. **jOOQ optional filters are conditional CTEs.** Build them with
   `DSL.selectDistinct` + `nullIfEmpty()?.let`, collect with `listOfNotNull`,
   and `innerJoin` each non-null CTE. A direct join to the child table
   multiplies parent rows and is a defect.
9. **`useState`, not `useRef`, for component-owned values.** `useRef` is for DOM
   nodes, timer ids, and previous-render tracking only. A component must never
   be defined inside a hook or inside another component — that causes
   unmount/remount on every render and silently resets state and focus.
10. **Test naming.** Unit tests use `testMethod_Condition`, e.g.
    `testRegister_DuplicateUsername`. Sentence-style backtick names are wrong.
11. **No synchronous `setState` in `useEffect`.** Reset state during render via
    previous-prop comparison, derive values inline, or remount the subtree with
    a `key`. Effects synchronize with external systems (DOM, timers,
    subscriptions) — not with other state.

## Repository invariants

- **Never modify a checked-in Flyway migration** under
  `backend/src/main/resources/db/migration/`. Schema changes require a NEW `V*` file.
- A schema change must come with regenerated jOOQ types (`./gradlew generateJooq`).
- **Never hand-edit generated jOOQ sources** under `build/generated-sources/`.
- No secrets, keys, or tokens in committed files. `.env` stays untracked.
  Never log secrets or personal data.
- **Spring:** constructor injection only, never field injection. `@Transactional`
  belongs on the service layer, never on a controller. Validate input at the
  boundary; map errors centrally in `GlobalExceptionHandler`. Keep domain logic
  out of controllers and repositories.
- **Outbound HTTP and Kafka calls need explicit timeouts.** Retries must be
  intentional, documented, and safe to repeat.
- **TypeScript:** no `any`. Parse untrusted external data (API responses,
  `localStorage`, query params) with Zod at the boundary rather than casting
  with `as`.
- **New dependencies must be justified.** Prefer boring, stable choices.

## Testing expectations

- New logic requires unit tests; a bug fix requires a regression test.
- API or contract changes require integration tests.
- Mock boundaries, not internals. Tests must be deterministic and independent.
- **`frontend/` has no test runner — never ask for frontend tests.**