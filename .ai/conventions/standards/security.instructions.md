# Security Standards

These are baseline rules. Some projects may require additional controls.

## Secrets

- Never commit secrets
- Use secret managers or environment variables
- Provide `.env.example` or equivalent documentation

## Data Handling

- Do not log sensitive personal data
- Minimize stored data; store only what is needed
- Document any persistent data and retention expectations

## Auth & Access

- Authenticate and authorize at boundaries
- Least privilege for service accounts
- Document permission requirements

## Dependencies

- Avoid unmaintained or low-trust dependencies
- Track critical dependencies and their update strategy
- Security-impacting dependencies require Planner approval

## Vulnerabilities

- Security issues are escalated immediately
- Temporary mitigations must be documented with an owner and follow-up
