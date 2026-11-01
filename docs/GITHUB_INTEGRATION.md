# GitHub Integration

## Purpose
Specify the planned GitHub App integration for repository authorization, pull-request event intake, revision resolution, and result publication.

## Scope
Initial integration targets GitHub repositories and pull requests. Exact event subscriptions, permissions, and API operations must be validated against current GitHub App capabilities before implementation.

## Integration model
Use a GitHub App installation. Avoid requiring users to provide broad personal access tokens. Request only the permissions needed for repository metadata, contents, pull-request metadata, and publishing the agreed result surface.

## Installation flow
1. User installs the app for an organization or account and selects repositories.
2. GitHub redirects or notifies the platform of installation context.
3. Backend validates installation identity and records tenant/repository mapping.
4. App credentials are used by a narrowly scoped adapter to obtain short-lived installation access tokens.
5. Installation removal or permission changes update connection state and stop future access.

Private key material and webhook secrets must be stored outside the database in a secret manager or protected deployment configuration.

## Webhook handling
- Validate the signature before processing payload data.
- Enforce request size limits.
- Persist delivery identifiers and deduplicate repeated deliveries.
- Acknowledge promptly and process analysis asynchronously.
- Handle out-of-order events by resolving current immutable base/head SHAs before creating or updating a run.
- Treat payload text and repository content as untrusted.
- Record safe audit metadata.

## Pull-request workflow
For supported pull-request events:
1. Resolve repository and installation identity.
2. Confirm tenant authorization and repository selection.
3. Resolve base/head commit SHAs.
4. Create or reuse an idempotent analysis run.
5. Trigger the analysis pipeline.
6. Publish or update a concise status/check/comment using the approved permission model.
7. Link to the dashboard report where available.

The exact GitHub surface (Checks API, commit status, PR comment, or combination) is an open decision. Avoid noisy repeated comments; updates should be idempotent.

## Rate limits and resilience
Respect GitHub rate-limit headers and retry guidance. Use bounded retries for transient failures. Queue or defer work when limits are reached. Do not repeatedly fetch full repository content when a valid snapshot/cache can be safely reused.

## Security
- Minimum permissions and repository selection.
- Signature validation.
- Short-lived installation tokens.
- Secret redaction.
- Installation revocation and token invalidation.
- Tenant authorization independent of GitHub identity.
- No source or secrets in public comments.

## Dependencies
See `../01-ARCHITECTURE/SECURITY_ARCHITECTURE.md`, `../01-ARCHITECTURE/SYSTEM_ARCHITECTURE.md`, and `../02-ENGINEERING/ERROR_HANDLING.md`.

## Acceptance criteria
- Invalid webhook signatures are rejected.
- Duplicate delivery is idempotent.
- Revision SHAs are immutable and recorded.
- Installation removal/revocation is handled.
- Published output is concise and does not expose sensitive source.

## Open questions
- Exact webhook event subscriptions for initial release.
- Minimum GitHub App permissions.
- Checks API versus commit status/PR comment.
- Handling fork pull requests and untrusted contribution builds.
- Private repository data retention and installation deletion behavior.
