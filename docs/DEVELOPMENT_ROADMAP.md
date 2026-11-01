# Development Roadmap

## Purpose
Define a dependency-aware sequence for delivery. A phase is not complete merely because its code exists; its exit criteria and verification evidence must be met.

## Scope and sequencing principles
- Deliver a modular monolith first; avoid premature distributed infrastructure.
- Establish reproducible repository/revision handling before analysis.
- Build deterministic analysis before adding scoring sophistication.
- Isolate execution before enabling arbitrary repository test runs.
- Integrate GitHub and dashboard after the core run lifecycle is stable.
- Do not begin a later phase without explicit authorization and prior-phase exit review.

## Phase 0 — Documentation and architecture baseline
**Status:** This documentation package defines the planned baseline; consistency and stakeholder approval remain to be confirmed.

Deliverables:
- Product requirements and scope.
- System boundaries and technical decisions.
- Data/security model.
- Analysis design and limitations.
- Engineering and operational conventions.

Exit criteria:
- Open decisions have owners or explicit accepted defaults.
- Architecture and requirements are approved.
- Initial supported repository cohort is defined.

## Phase 1 — Repository and diff vertical slice
Dependencies: Phase 0 approval.

Deliverables:
- Spring Boot backend skeleton and modular boundaries.
- Repository/run domain model and PostgreSQL/Flyway baseline.
- Secure repository/revision input abstraction.
- Git diff ingestion for a controlled local fixture.
- Run lifecycle and structured errors.

Exit criteria:
- A fixture repository can be analyzed for a fixed base/head pair.
- Revision identity and diff output are reproducible.
- Tests cover invalid revisions, missing paths, and ingestion failure.
- No untrusted code is executed.

## Phase 2 — Java parsing and symbol extraction
Dependencies: Phase 1.

Deliverables:
- JavaParser integration.
- Maven module/source discovery for supported layouts.
- Symbol inventory and source locations.
- Parser diagnostics and coverage metadata.

Exit criteria:
- Fixture corpus covers supported Java constructs and known unsupported patterns.
- Parse failures are visible and do not become false clean results.
- Extraction is deterministic for identical inputs and tool versions.

## Phase 3 — Dependency graph and impact analysis
Dependencies: Phase 2.

Deliverables:
- Method/type graph model.
- Supported call/reference edge extraction.
- Changed-symbol mapping and bounded traversal.
- Evidence-bearing impact paths.

Exit criteria:
- Graph fixtures validate expected edges and unresolved cases.
- Cycles and large graphs are bounded safely.
- Edge provenance and resolution quality are persisted or reproducibly generated.

## Phase 4 — API and database impact
Dependencies: Phase 2; graph integration from Phase 3 where relevant.

Deliverables:
- Supported Spring REST endpoint extraction.
- Endpoint contract-change detection.
- Supported migration/schema change inspection.
- Explicit unsupported-pattern reporting.

Exit criteria:
- Contract and migration fixtures produce explainable findings.
- Raw/unsupported constructs are marked unknown or partial.
- No unsupported inference is represented as a deterministic fact.

## Phase 5 — Dependency-aware test selection
Dependencies: Phases 2 and 3; test discovery may proceed in parallel after Phase 2.

Deliverables:
- Maven/JUnit 5 test discovery.
- Test-to-symbol association.
- Candidate test selection and rationale.
- Coverage-quality and fallback policy.

Exit criteria:
- Labeled fixtures measure selected-test relevance and missed-impact cases.
- Each selected test has evidence and a reason.
- Low graph quality triggers documented fallback behavior.

## Phase 6 — Isolated test execution
Dependencies: Phase 1 and Phase 5; security review of `../04-INTEGRATIONS/DOCKER_EXECUTION.md`.

Deliverables:
- Docker execution adapter.
- Resource/time limits and controlled mounts/network.
- Test result normalization and bounded logs.
- Cancellation and cleanup behavior.

Exit criteria:
- Malicious and resource-exhaustion fixtures are safely contained within the approved threat model.
- Timeouts, infrastructure failures, and assertion failures are distinct.
- No host secrets or privileged host resources are exposed.

## Phase 7 — Risk assessment and report
Dependencies: Phases 3–6.

Deliverables:
- Versioned, evidence-based risk model.
- Risk factors, explanation, and limitations.
- Stable report API/domain representation.

Exit criteria:
- Labeled evaluation set and scoring calibration method are documented.
- No claim of guaranteed safety.
- Unknown evidence reduces confidence or yields an indeterminate assessment rather than an unjustified low-risk result.

## Phase 8 — GitHub App integration
Dependencies: stable run lifecycle and report interface from Phases 1 and 7.

Deliverables:
- GitHub App installation and permission flow.
- Webhook validation and idempotency.
- Pull-request revision resolution and status/comment publishing.

Exit criteria:
- Duplicate/out-of-order webhook tests pass.
- Permission and token handling reviewed.
- Installation removal and access revocation are handled.

## Phase 9 — SaaS dashboard and hosted operations
Dependencies: stable API, authentication, tenant model, and report model.

Deliverables:
- Next.js/TypeScript dashboard.
- User/organization/repository access controls.
- Hosted deployment, monitoring, backups, retention, and incident procedures.

Exit criteria:
- Tenant-isolation tests pass.
- Operational runbooks and restore test exist.
- Security, privacy, and retention review completed.

## Future options — Not committed
Kafka, Redis, Kubernetes, LLM-assisted explanations, additional languages/build systems, and distributed service decomposition may be evaluated only when measured scale, latency, or product requirements justify them. Each requires a technical decision record and migration plan.

## Cross-phase gates
- Security review before executing repository code or enabling external installations.
- Evaluation corpus and measurement methodology before publishing quality claims.
- Database migration and rollback review before schema changes.
- Documentation and `AGENTS.md` updated when architecture changes.

## Open questions
- Who approves each phase gate?
- What fixture corpus and labeled benchmark will be maintained?
- What measurable thresholds define acceptable precision, recall, execution isolation, and latency?
