# Documentation Standards

Documentation ensures that knowledge survives beyond individuals,
projects, and short-term context.

Documentation is considered part of the deliverable, not an afterthought.

---

## Core Principles

- Documentation must enable continuity
- Knowledge must be discoverable by people outside the project
- Internal project docs and external shared docs serve different purposes
- Both must be maintained when relevant

---

## Documentation Types

### 1. Project-Level Documentation

Stored with the codebase.

Used for:

- Understanding the project
- Running and modifying the code
- Understanding local architecture and contracts

Examples:

- README.md
- `/docs` folder
- Inline code documentation
- API contract files
- ADRs

---

### 2. External / Shared Documentation

Stored outside the codebase.

Used for:

- Cross-team knowledge sharing
- System-level understanding
- Operational and onboarding knowledge
- Architectural overviews

Examples:

- Company wiki pages
- Onboarding guides
- System maps and diagrams

---

## When External Documentation Must Be Updated

External documentation MUST be updated when changes:

- Affect shared systems or integrations
- Introduce new services, agents, or responsibilities
- Change public APIs, events, or data contracts
- Modify architecture or data flow visible outside the team
- Introduce new operational procedures
- Change ownership or support boundaries
- Add or remove significant dependencies

If someone outside the team would be impacted or confused,
external documentation is required.

---

## Required Documentation by Change Type

### New Component / Service / Agent

- Project-level documentation
- External system overview or catalog entry

### Contract or API Change

- Updated local contract docs
- Updated external API documentation
- Migration or compatibility notes if applicable

### Architectural Change

- ADR in the repository
- Updated architecture diagrams or wiki pages

### Operational Change

- Updated runbooks or operational wiki pages
- Clear escalation and ownership information

---

## Documentation Ownership

- The author of the change owns documentation updates
- Ownership does not transfer to “someone else” implicitly
- Documentation updates are part of the acceptance criteria

---

## Documentation Review

Changes are not complete until:

- Required project-level docs are updated
- Required external docs are updated or explicitly acknowledged as unchanged

If external documentation is intentionally deferred:

- The reason must be documented
- A follow-up task with an owner must be created

---

## Documentation Quality Bar

Documentation should allow someone unfamiliar with the change to:

- Understand what changed
- Understand why it changed
- Understand how it affects them
- Know where to find more details

---

## Prohibited Practices

- Assuming “someone else will update the wiki”
- Making changes that invalidate shared docs without updating them
- Hiding critical knowledge in chat or commit messages
