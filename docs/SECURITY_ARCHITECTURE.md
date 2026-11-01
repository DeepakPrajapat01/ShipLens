# Security Architecture

## Purpose
Define security principles, threat boundaries, and minimum controls for a platform that ingests and executes untrusted source repositories.

## Scope
Covers GitHub authorization, dashboard/API access, tenant isolation, source handling, Docker test execution, secrets, data retention, and auditability. It is a design baseline, not a completed security assessment.

## Threat model
Assets include GitHub credentials, customer source code, repository metadata, test output, reports, tenant identities, and platform infrastructure.

Threat actors and risks include:
- Malicious repository content attempting to compromise analysis or execution.
- Malicious or forged webhooks.
- Cross-tenant data access.
- Credential theft or accidental secret disclosure.
- Dependency/build scripts attempting network exfiltration.
- Resource exhaustion through oversized repositories, pathological source, or tests.
- Prompt/instruction injection in source files or PR text if future AI features are introduced.
- Compromised worker or container escape.

## Identity and authorization
- Use GitHub App installation permissions with least privilege.
- Validate webhook signatures using the configured secret and reject invalid requests.
- Enforce tenant and repository authorization on every backend request.
- Do not trust tenant identifiers supplied only by clients.
- Support installation revocation and credential rotation.
- Keep authentication-provider selection open until a decision is approved.

## Secret management
- Store private keys, webhook secrets, database credentials, and signing keys in a dedicated secret-management mechanism appropriate to deployment.
- Never commit secrets or place them in repository execution environments.
- Redact secrets from logs, exception messages, traces, and test output.
- Prefer short-lived installation access tokens and restrict their use to the required adapter.
- Define rotation and incident revocation procedures before hosted onboarding.

## Untrusted repository processing
- Treat filenames, source, build files, Maven plugins, wrapper scripts, test code, and output as untrusted.
- Parse source in a bounded process with memory/time limits.
- Do not run Maven or tests on the control-plane host.
- Do not mount host home directories, Docker socket, credentials, or control-plane configuration into execution containers.
- Use non-root execution, read-only base layers where practical, bounded writable workspace, CPU/memory/PID limits, and timeouts.
- Disable network by default during test execution; any exception requires an explicit allowlist and security review.
- Clean up containers, volumes, temporary credentials, and workspaces after completion.
- Docker reduces risk but does not provide an absolute security guarantee; evaluate stronger isolation if threat model or hosting environment requires it.

## Application security
- Validate inputs and enforce size/range limits.
- Use parameterized database access.
- Apply CSRF/CORS/session protections appropriate to the selected authentication model.
- Rate-limit externally reachable endpoints and webhook processing.
- Avoid exposing stack traces, internal paths, or secrets in API errors.
- Keep dependencies patched and scan build artifacts.
- Protect administrative and audit functions with explicit authorization.

## Data protection
- Encrypt network traffic in transit and use encrypted storage where supported by deployment.
- Restrict access to source artifacts and logs.
- Define retention and deletion for source snapshots, diffs, test output, and reports.
- Avoid sending private source to third-party AI services; no LLM integration is in initial scope.
- Ensure logs contain identifiers and summaries, not unnecessary source excerpts.

## Audit and incident readiness
Record installation changes, repository access changes, run creation/cancellation, authorization failures, and sensitive administrative actions. Audit records must not contain secret values. Define incident response, credential revocation, and customer notification procedures before production.

## Dependencies
See `../04-INTEGRATIONS/GITHUB_INTEGRATION.md`, `../04-INTEGRATIONS/DOCKER_EXECUTION.md`, and `DATABASE_DESIGN.md`.

## Acceptance criteria
- A documented threat model exists before external repository execution.
- No untrusted code runs on the control-plane host.
- Tenant authorization is server-side and tested.
- Secrets are absent from execution containers and logs.
- Retention, deletion, and incident procedures are defined before hosted customer use.

## Open questions
- What isolation technology is sufficient for the intended hosting threat model?
- Will test containers have any network access, and if so, which destinations?
- Which secret manager and authentication provider will be selected?
- What data residency, retention, and deletion commitments apply?
- Is an independent penetration test required before general availability?
