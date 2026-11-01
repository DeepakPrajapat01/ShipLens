# Product Requirements

## Purpose
Specify initial functional and non-functional requirements for AI ChangeGuard. Requirements are planned acceptance criteria, not evidence of implementation.

## Scope
Initial supported ecosystem: Java 21, Spring Boot, Maven, and JUnit 5 repositories hosted on GitHub. The product includes a backend analysis service, PostgreSQL persistence, a Next.js/TypeScript dashboard, Docker-based isolated test execution, and GitHub App integration.

## Functional requirements

### FR-01 Repository connection and ingestion
- A user can authorize access to an eligible GitHub repository through the GitHub App.
- The service records repository identity and the minimum permissions granted.
- An analysis run resolves immutable base and head commit identifiers.
- Repository checkout and metadata collection must be bounded by configured size/time limits.
- Ingestion failures and unsupported layouts must be reported explicitly.

**Acceptance:** a run can identify its repository and exact revisions; incomplete ingestion cannot be reported as complete.

### FR-02 Diff analysis
- Compute a diff between the selected base and head revisions.
- Record changed, added, deleted, and renamed paths where Git can determine them.
- Preserve file-level change metadata and associate source changes with parsed symbols where possible.

**Acceptance:** diff results are reproducible for the same repository and revision pair.

### FR-03 Java AST analysis
- Parse supported Java source using JavaParser.
- Extract packages, types, methods, fields, imports, annotations, and source locations needed by downstream analysis.
- Record parser errors and unresolved symbols.
- Never silently classify unparsed source as having no impact.

**Acceptance:** parser coverage and parse failures are available to the analysis report.

### FR-04 Method-level dependency mapping
- Build a directed graph of supported method/type relationships.
- Record edge type, source evidence, and resolution status.
- Support traversal from changed symbols to candidate callers/callees within configured limits.
- Distinguish resolved edges from heuristic or unresolved relationships.

**Acceptance:** each traversed impact path is explainable and traceable to source evidence.

### FR-05 REST API impact analysis
- Identify supported Spring MVC endpoint declarations and relevant request mappings.
- Associate endpoints with controller methods and downstream analyzed methods where resolvable.
- Flag changed endpoint signatures, mappings, request/response types, and validation annotations as potential contract changes.
- Document unsupported patterns.

**Acceptance:** endpoint findings link to source locations and describe whether the change is direct or inferred.

### FR-06 Database change analysis
- Inspect supported schema migration files and recognized persistence mappings.
- Identify additions, removals, and modifications to supported tables, columns, indexes, and constraints when deterministically parseable.
- Associate application changes with database artifacts only where evidence supports the relationship.
- Report raw SQL or unsupported migration syntax as partially analyzed or unknown.

**Acceptance:** database findings identify the migration/source artifact and the exact recognized change; no unsupported inference is presented as fact.

### FR-07 Dependency-aware test selection
- Discover Maven modules and JUnit 5 tests within supported layouts.
- Map tests to analyzed symbols using explicit references and supported dependency paths.
- Select candidate tests based on changed symbols and impact graph traversal.
- Include selection rationale and confidence/coverage limitations.
- Provide a safe fallback policy when graph quality is insufficient; the policy must not silently imply complete coverage.

**Acceptance:** every selected test has a recorded reason; excluded tests are not described as unaffected unless supported by evidence.

### FR-08 Isolated test execution
- Execute selected Maven/JUnit tests in a Docker-isolated environment.
- Apply resource limits, timeout, restricted filesystem mounts, and controlled network policy.
- Capture exit status, test summaries, bounded logs, and execution metadata.
- Treat infrastructure failure separately from test assertion failure.

**Acceptance:** execution result distinguishes passed, failed, timed out, cancelled, and infrastructure-error states.

### FR-09 Risk assessment
- Combine deterministic findings, graph reachability, API/database impact, test-selection coverage, execution outcomes, and analysis completeness.
- Produce an explainable risk category and supporting evidence.
- Preserve unknowns and confidence limitations.
- Avoid claiming that a low score means a change is safe.

**Acceptance:** every assessment includes contributing factors, evidence references, and explicit limitations.

### FR-10 GitHub pull-request integration
- Receive and validate relevant GitHub App webhooks.
- Resolve pull-request base/head revisions and trigger or update analysis.
- Publish a concise result to the pull request using least-privilege permissions.
- Handle duplicate and out-of-order webhook delivery idempotently.
- Avoid exposing secrets or source content in comments.

**Acceptance:** repeated delivery of the same event does not create uncontrolled duplicate analysis or comments.

### FR-11 SaaS dashboard
- Authenticate users and enforce repository/organization access boundaries.
- Show repositories, analysis runs, status, risk summary, findings, evidence, selected tests, and execution results.
- Clearly label incomplete or unsupported analysis.
- Support navigation from a finding to its evidence and affected symbol.

**Acceptance:** a user cannot view another tenant's repository data; report status and limitations are visible.

## Non-functional requirements
- **Security:** least privilege, secret protection, tenant isolation, secure webhook validation, and sandboxed execution.
- **Reliability:** retryable asynchronous stages, idempotent state transitions, and durable run status.
- **Observability:** structured logs, metrics, correlation IDs, and audit events without secret leakage.
- **Explainability:** traceable evidence for findings and test selection.
- **Maintainability:** modular backend boundaries and versioned database migrations.
- **Performance:** establish baselines before setting numeric service-level targets.
- **Portability:** local development should use documented, reproducible configuration.
- **Data governance:** define retention, deletion, and source-code handling before hosted customer onboarding.

## Out of scope for initial release
- Non-Java languages and non-Maven build systems.
- Automatic code fixes, merges, deployments, or release approvals.
- Guaranteed defect detection or formal proof of correctness.
- LLM-authoritative risk scoring or test selection.
- Mandatory Kafka, Redis, Kubernetes, or microservice decomposition.
- Full dynamic program analysis or production telemetry correlation.

## Dependencies
See `../01-ARCHITECTURE/SYSTEM_ARCHITECTURE.md`, `../03-ANALYSIS-ENGINE/`, and `../04-INTEGRATIONS/`.

## Open questions
- Which Spring MVC annotations and persistence technologies are in the first supported subset?
- What is the minimum supported Maven multi-module configuration?
- What is the fallback test policy when graph coverage is low?
- What are the report retention and deletion requirements?
- What numeric performance and availability targets should be set after baseline measurement?
