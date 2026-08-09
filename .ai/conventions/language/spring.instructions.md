---
description: "Spring instructions: layering, DI, configuration, transactions, persistence boundaries, error handling, and observability. Includes Spring Boot notes where relevant."
applyTo: "**/*.java,**/*.kt,**/application*.properties,**/application*.yml,**/application*.yaml,**/*Context.xml,**/pom.xml,**/build.gradle,**/build.gradle.kts"
---

# Spring Instructions

These instructions extend `/standards/*`. If conflicting guidance exists, company-wide standards win unless an ADR overrides.

## Architecture & layering

- Keep boundaries explicit (API/controller, service/use-case, domain, persistence, integration).
- Avoid circular dependencies between modules/packages.
- Avoid putting domain logic in controllers or repositories.

## Dependency Injection

- Prefer constructor injection.
- Avoid field injection.
- Avoid static access patterns for Spring-managed components.

## Configuration

- No environment-specific hardcoding.
- Configuration must be documented (required properties, defaults, and where they come from).
- Keep configuration explicit for critical behavior (timeouts, retries, thread pools, serialization).

## Transactions

- Transactions belong in the service/use-case layer.
- Avoid `@Transactional` on controllers.
- Use read-only transactions for read paths where applicable.
- Do not rely on implicit session behavior (avoid LazyInitialization surprises).

## Persistence boundaries

- Do not let persistence concerns leak into API DTOs.
- Be intentional about fetch strategies and query performance (N+1, pagination, limits).
- Prefer explicit query methods for critical paths.

## External integrations (HTTP, messaging, etc.)

- Timeouts are mandatory and must be explicit.
- Retries must be intentional and documented (what is safe to retry and why).
- Prefer idempotency for operations that may be retried.

## Error handling

- Validate inputs at boundaries (controllers/message consumers).
- Centralize error mapping at the boundary (e.g., `@ControllerAdvice` for HTTP).
- Errors returned externally must be consistent and actionable.

## Observability

- Log at boundaries and failures.
- Use correlation identifiers where available.
- Do not log secrets or personal data.
- For critical operations: ensure enough telemetry exists to debug production issues.

## Spring Boot notes (only when applicable)

- Be careful with auto-configuration side effects; explicitly configure critical behaviors.
- If Actuator is used: secure sensitive endpoints and document what is enabled.
- Avoid adding starters casually; they introduce transitive behavior.

## References

- `/standards/coding-standards.md`
- `/standards/testing-standards.md`
- `/standards/security-standards.md`
- `/standards/documentation-standards.md`
- `/standards/architecture-standards.md`
