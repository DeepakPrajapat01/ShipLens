# Error Handling

## Purpose
Define consistent failure classification and propagation across ingestion, analysis, execution, integrations, and APIs.

## Principles
- Fail explicitly; never turn missing evidence into a successful or clean assessment.
- Preserve root cause internally while exposing safe, stable messages externally.
- Separate domain failure, user input failure, external dependency failure, and infrastructure failure.
- Make retries deliberate and bounded.
- Attach correlation identifiers to logs, stages, and API errors.
- Do not log secrets or unrestricted source/test output.

## Failure categories
- **VALIDATION:** malformed request, unsupported repository profile, invalid revision.
- **AUTHORIZATION:** missing permission, revoked installation, tenant access denied.
- **INGESTION:** clone/fetch failure, missing object, repository size/time limit.
- **PARSING:** syntax error, unsupported language level, parser crash, partial parse.
- **RESOLUTION:** unresolved type/method or incomplete dependency metadata.
- **ANALYSIS:** graph limit reached, unsupported API/database construct, internal analyzer failure.
- **SELECTION:** test discovery failure, insufficient mapping, fallback policy applied.
- **EXECUTION:** assertion failure, build failure, timeout, cancellation, worker/container failure.
- **INTEGRATION:** GitHub rate limit, API outage, webhook validation failure.
- **PERSISTENCE:** database unavailable, constraint conflict, migration failure.
- **INTERNAL:** unexpected platform defect.

## Stage outcome model
Each stage should expose one of: `PENDING`, `RUNNING`, `SUCCEEDED`, `PARTIAL`, `FAILED`, `SKIPPED`, `CANCELLED`, or `TIMED_OUT`, subject to final domain naming approval.

A run may complete with partial analysis. The final report must show which stages succeeded, failed, or were skipped and how that affects confidence.

## Retry policy
- Retry transient network and infrastructure failures with bounded exponential backoff and jitter.
- Do not retry deterministic validation, parse, or unsupported-feature failures without changed inputs or analyzer version.
- Make retries idempotent.
- Cap attempts and expose exhausted retries.
- Respect GitHub rate-limit reset information.
- Do not retry destructive or externally visible actions without idempotency protection.

## Exception boundaries
- Adapters translate vendor exceptions into internal error categories.
- Application services decide retryability and stage transitions.
- Controllers map safe categories to HTTP status and error envelope.
- Logs retain sanitized diagnostic context and correlation IDs.
- Errors from untrusted build/test processes are data, not instructions.

## Partial analysis
A parser failure in one file should not erase valid findings from other files, provided the report marks incomplete coverage. If a prerequisite stage fails entirely, dependent stages should be skipped with a reason. A missing dependency edge is not evidence that no dependency exists.

## Acceptance criteria
- All expected failure categories have explicit handling.
- Retryable and non-retryable errors are distinguishable.
- Partial analysis is represented in report state.
- API responses and logs do not leak secrets or sensitive source.

## Dependencies
See `API_CONVENTIONS.md`, `../01-ARCHITECTURE/SYSTEM_ARCHITECTURE.md`, and `../03-ANALYSIS-ENGINE/RISK_ASSESSMENT_MODEL.md`.

## Open questions
- Final enum/code naming and public error-code registry.
- Per-stage retry counts and timeout defaults.
- Which diagnostic artifacts may be retained and for how long?
