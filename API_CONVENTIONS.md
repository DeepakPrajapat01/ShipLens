# API Conventions

## Purpose
Define consistent conventions for the planned Spring Boot HTTP API consumed by the dashboard and integration adapters.

## Scope
Applies to public and internal HTTP endpoints. Exact routes and schemas are not finalized; do not invent them during implementation without an API design update.

## General conventions
- Use resource-oriented paths and JSON payloads.
- Use a versioned API namespace, provisionally `/api/v1`.
- Use HTTPS in deployed environments.
- Use ISO 8601 timestamps in UTC.
- Use immutable identifiers rather than display names as resource references.
- Validate request size, format, and authorization at the boundary.
- Paginate potentially large collections.
- Document request/response schemas and error cases.

## HTTP semantics
- `GET` retrieves resources and must not mutate state.
- `POST` creates resources or initiates commands that are not naturally idempotent.
- `PUT` replaces a resource representation where appropriate.
- `PATCH` applies a documented partial update.
- `DELETE` requests deletion subject to retention and authorization policy.
- Use appropriate status codes: 200/201/202/204 for successful operations; 400 for malformed input; 401/403 for authentication/authorization; 404 for inaccessible or missing resources according to disclosure policy; 409 for state conflicts; 422 for semantically invalid input; 429 for rate limits; 5xx for server failures.

## Asynchronous analysis
Analysis creation should return a stable run identifier and a state indicating whether work was accepted or already exists. Clients retrieve status and report resources separately. Do not hold a request open for the full repository analysis.

## Error envelope
Use a stable machine-readable error structure with:
- `code`: stable application error code.
- `message`: safe human-readable summary.
- `correlationId`: request/run correlation identifier.
- `details`: optional field-level or non-sensitive context.
Do not return stack traces, secret values, internal host paths, or raw untrusted command output.

## Pagination
Use a consistent cursor or page-based strategy across list endpoints. The exact mechanism remains open. Enforce maximum page size and stable ordering.

## Idempotency and concurrency
- Run creation and webhook processing require idempotency.
- Use immutable revision identifiers and an idempotency key appropriate to tenant/repository/event context.
- Return or reference the existing run for duplicate requests when semantics match.
- Define conflict behavior for concurrent updates.

## Authentication and authorization
Authentication mechanism is unresolved. Regardless of provider, enforce tenant/repository access on the server for every operation. GitHub App credentials are not substitutes for dashboard user authorization.

## API documentation
Publish an OpenAPI contract once endpoint design begins. Contract changes require compatibility review and documentation updates.

## Dependencies
See `../01-ARCHITECTURE/SYSTEM_ARCHITECTURE.md`, `ERROR_HANDLING.md`, and `../04-INTEGRATIONS/GITHUB_INTEGRATION.md`.

## Acceptance criteria
- API routes and payloads are documented before frontend coupling.
- Error codes are stable and safe.
- Long-running analysis is asynchronous.
- Authorization and idempotency are enforced server-side.

## Open questions
- Final API versioning strategy and route naming.
- Cursor versus offset pagination.
- Authentication provider and session/token model.
- Whether API contracts are maintained code-first or spec-first.
