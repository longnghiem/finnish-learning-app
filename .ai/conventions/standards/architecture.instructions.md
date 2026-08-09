# Architecture Standards

These standards guide system design and evolution.

## Principles

- Keep boundaries explicit
- Design for change by limiting coupling
- Prefer simple architectures that fit the problem

## Boundaries

- Define clear module/service ownership
- Avoid circular dependencies between modules
- Keep domain logic separate from infrastructure concerns

## Contracts

- APIs/events must have explicit contracts and examples
- Contract changes must be backward compatible unless explicitly approved
- Version contracts when compatibility cannot be maintained

## State & Data

- Avoid shared mutable state across boundaries
- Prefer clear data ownership
- Document data flow and lifecycle for persisted data

## Resilience

- Timeouts and retries must be intentional and documented
- Failures must be observable
- Prefer idempotency for external side effects where feasible

## Observability

- Critical operations must have:
  - logs with correlation identifiers
  - meaningful error reporting
  - metrics/tracing where applicable

## ADR Requirement

Architectural shifts require ADRs:

- new core patterns
- new infrastructure components
- new communication style (events, queues)
- major dependency additions
