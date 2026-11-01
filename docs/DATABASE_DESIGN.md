# Database Design

## Purpose
Define the planned persistence model and data ownership for AI ChangeGuard. This is a logical design; no schema or migration is asserted to exist.

## Scope
PostgreSQL is the primary system of record and Flyway manages schema migrations. This document describes logical entities, relationships, integrity rules, and lifecycle concerns.

## Core entities
- **User:** authenticated human identity and account status.
- **Organization/Tenant:** security and billing boundary; exact hierarchy remains open.
- **Membership:** user-to-tenant relationship and role.
- **RepositoryConnection:** tenant-owned GitHub repository identity, installation reference, selected settings, and connection status. Do not store installation private keys here.
- **AnalysisRun:** repository, base/head SHA, trigger type, status, timestamps, analysis version, and idempotency identity.
- **AnalysisStage:** stage name, status, attempt, timestamps, failure category, and bounded diagnostic reference.
- **ChangedFile:** path, change type, diff metadata, and association to a run.
- **Symbol:** repository/revision-scoped symbol identity, kind, qualified name, source location, and extraction version.
- **DependencyEdge:** source/target symbol references, edge type, resolution status, and provenance.
- **Finding:** normalized finding category, severity/risk contribution, title, explanation, and evidence references.
- **TestCandidate:** test identity, selection status, rationale, and confidence/coverage metadata.
- **TestExecution:** execution state, exit code, duration, normalized test summary, and artifact reference.
- **RiskAssessment:** versioned result, category/score if approved, factors, completeness, and limitations.
- **AuditEvent:** security- and lifecycle-relevant actor/action/resource metadata.

## Relationships
- A tenant owns repository connections.
- A repository connection has many analysis runs.
- A run has many stages, changed files, findings, test candidates, executions, and assessments.
- Symbols and graph edges are scoped to a repository revision or analysis snapshot; avoid accidental cross-revision joins.
- A test candidate may have one or more selection evidence records.
- An execution belongs to a run and may correspond to a selected test batch.
- Audit events reference tenant and actor/resource identifiers where available.

## Integrity and lifecycle rules
- Store immutable Git commit SHAs for analyzed revisions.
- Enforce tenant scoping in every query path; do not rely solely on frontend filtering.
- Use foreign keys and unique constraints for stable identities and idempotency.
- Model run and stage state transitions explicitly; reject invalid transitions.
- Preserve analysis-engine and risk-model versions for reproducibility.
- Use UTC timestamps in persistence and API serialization.
- Avoid storing raw source code or full diffs in relational rows unless justified; prefer bounded references to controlled artifact storage.
- Ensure deletion workflows cover related source artifacts, logs, derived graph data, and reports according to retention policy.

## Suggested indexes
Validate with query plans before finalizing:
- AnalysisRun by tenant/repository and created time.
- AnalysisRun by status and updated time for worker polling.
- Unique idempotency key within an appropriate tenant/repository scope.
- Findings by run and category/severity.
- Changed files by run and normalized path.
- Symbols by snapshot and qualified identity.
- Audit events by tenant and timestamp.

## Data minimization
Persist only information required for analysis, explainability, operations, and product functionality. Treat source code, paths, test names, and logs as confidential customer data. Redact credentials and avoid embedding secrets in evidence payloads.

## Migrations
- Flyway scripts are immutable after release; add a forward migration to correct schema.
- Review indexes, locks, data backfills, and rollback/forward-fix behavior.
- Test migrations against representative prior schema versions.
- Do not assume destructive rollback is safe for data-bearing migrations.

## Dependencies
See `SYSTEM_ARCHITECTURE.md`, `SECURITY_ARCHITECTURE.md`, and `../03-ANALYSIS-ENGINE/CODE_ANALYSIS_DESIGN.md`.

## Acceptance criteria
- Tenant ownership is explicit.
- Runs are reproducible by immutable revisions and analysis versions.
- Execution and analysis states distinguish failure from incomplete work.
- Data retention and deletion include derived artifacts.

## Open questions
- Exact tenant/organization hierarchy and role model.
- Artifact/object storage choice and access model.
- Whether graph snapshots are persisted or recomputed.
- Retention periods for source snapshots, diffs, logs, and reports.
- Whether raw evidence payloads use relational columns, JSONB, or external artifacts.
