# Monitoring Strategy

## Purpose
Define observability requirements for analysis lifecycle, execution workers, integrations, and hosted operations.

## Scope
Applies to logs, metrics, traces, audit events, alerting, and operational dashboards. The monitoring stack is not yet selected.

## Observability principles
- Every request and analysis run should have a correlation identifier.
- Use structured logs with stable event names and bounded fields.
- Never log secrets, access tokens, private keys, or unrestricted source/test output.
- Distinguish platform health from analysis quality/completeness.
- Monitor tenant-aware service behavior without exposing one tenant's data to another.
- Retain operational telemetry according to a documented policy.

## Key metrics
### API/control plane
- Request count, latency, status/error rate.
- Authentication/authorization failures.
- Database connection and query latency.
- Run creation and state-transition failures.

### Analysis pipeline
- Runs by stage and terminal status.
- Stage duration and retry count.
- Repository ingestion size/time and failure category.
- Parser success/partial/failure rate.
- Resolved/unresolved dependency edge counts.
- Analysis completeness and resource-limit events.
- Test discovery and selection counts.
- Fallback selection frequency.

### Execution workers
- Active containers and queue/wait time.
- Execution duration and terminal outcome.
- CPU, memory, disk, and PID limit events.
- Timeout/cancellation rate.
- Container cleanup failures.
- Network-policy violations where observable.

### GitHub integration
- Webhook delivery count and signature rejection.
- Duplicate delivery rate.
- API errors and rate-limit responses.
- Pull-request result publication failures.
- Installation revocation and permission-change events.

### Product quality
- Finding review feedback where collected.
- False-positive/false-negative evaluation results on labeled corpus.
- Selected-test relevance and missed-affected-test rate.
- Report completeness and indeterminate assessment frequency.

## Alerts
Alert on sustained API failures, database unavailability, worker backlog, repeated container cleanup failure, unexpected network access, secret leakage indicators, webhook signature anomalies, high timeout rates, and backup/restore failures. Thresholds must be set from baseline measurements rather than invented in this document.

## Tracing
Trace a run across ingestion, diff, parsing, graph, impact, selection, execution, risk, persistence, and publication. Propagate correlation IDs without placing sensitive payloads in trace attributes.

## Audit events versus logs
Audit events are durable records of security- and lifecycle-relevant actions. Operational logs are diagnostic and may have shorter retention. Do not treat logs as the sole audit record.

## Dependencies
See `../01-ARCHITECTURE/SYSTEM_ARCHITECTURE.md`, `../01-ARCHITECTURE/SECURITY_ARCHITECTURE.md`, and `DEPLOYMENT_STRATEGY.md`.

## Acceptance criteria
- A run can be traced across stages.
- Metrics distinguish failure, partial completion, and success.
- Alerts exist for security and operational failure modes.
- Telemetry does not leak secrets or unnecessary source content.

## Open questions
- Metrics/logging/tracing vendor and instrumentation libraries.
- Retention and access controls for telemetry.
- Baseline thresholds and service-level objectives.
- Customer-visible status versus internal operational metrics.
