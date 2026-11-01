# System Architecture

## Purpose
Describe the planned logical architecture, module boundaries, data flow, and trust boundaries for AI ChangeGuard.

## Scope
Initial architecture targets Java 21/Spring Boot backend, JavaParser analysis, Maven/JUnit 5 orchestration, PostgreSQL/Flyway persistence, Docker test isolation, GitHub App integration, and Next.js/TypeScript dashboard.

## Architectural style
Begin with a **modular monolith** for the control plane and analysis orchestration. Separate modules by responsibility and enforce interfaces internally. Do not introduce microservices, Kafka, Redis, or Kubernetes until a measured requirement justifies them.

## Logical components
1. **Web application:** Next.js dashboard; presents repositories, runs, findings, evidence, and test outcomes.
2. **API/control plane:** Spring Boot; authentication integration, repository configuration, run lifecycle, report retrieval, and orchestration.
3. **GitHub adapter:** App installation, webhook validation, revision resolution, repository fetch, and PR publication.
4. **Repository workspace adapter:** obtains a bounded snapshot for immutable base/head revisions.
5. **Diff analyzer:** identifies changed paths and hunks.
6. **Java analysis module:** JavaParser extraction, symbol indexing, and diagnostics.
7. **Dependency graph module:** method/type nodes, typed edges, provenance, and bounded impact traversal.
8. **Contract analysis module:** supported REST endpoint and database migration analysis.
9. **Test selection module:** Maven/JUnit discovery and evidence-based candidate selection.
10. **Execution adapter:** submits approved test commands to constrained Docker workers.
11. **Risk/report module:** aggregates evidence, uncertainty, and execution results into a versioned assessment.
12. **Persistence module:** PostgreSQL repositories and Flyway-managed schema.

These are logical boundaries, not a requirement to deploy each component independently.

## Main analysis flow
1. Receive a user request or validated GitHub event.
2. Create or deduplicate an analysis run keyed by repository and revision context.
3. Resolve immutable base/head commit SHAs.
4. Fetch only authorized repository content into a controlled workspace.
5. Compute the diff and record changed files/hunks.
6. Parse supported Java source and extract symbols.
7. Build a repository graph with explicit edge provenance and unresolved references.
8. Determine potential API/database impacts.
9. Discover tests and select candidates using graph and direct evidence.
10. Execute permitted tests in an isolated Docker environment.
11. Calculate the risk assessment from available evidence and completeness metadata.
12. Persist a report and expose it to dashboard/PR adapters.

A stage failure must produce a typed run/stage outcome. Later stages may be skipped when their prerequisites are unavailable; skipped work must not be interpreted as a successful clean result.

## Module boundaries
- Domain modules must not depend on web framework controllers.
- GitHub-specific payloads must be translated into internal commands/events at the adapter boundary.
- Analysis modules should consume immutable repository snapshot metadata and return versioned results.
- Execution code must not receive control-plane secrets or unrestricted database credentials.
- Risk assessment consumes normalized evidence; it must not silently re-run parsing or infer unsupported facts.
- Dashboard access is through documented APIs, not direct database access.

## Data ownership
PostgreSQL is the authoritative store for users/organizations, repository connections, analysis run metadata, normalized findings, execution summaries, and audit metadata. Source snapshots and bulky logs should be treated as temporary artifacts with explicit storage and retention policies; their final storage backend is unresolved.

## Trust boundaries
- External GitHub events and repository content are untrusted.
- Repository checkout and build/test execution are untrusted workloads.
- Dashboard clients are untrusted; authorization is enforced server-side.
- Docker is a containment mechanism, not an absolute security guarantee.
- Secrets are isolated from repository execution and excluded from logs.

See `SECURITY_ARCHITECTURE.md` and `../04-INTEGRATIONS/DOCKER_EXECUTION.md`.

## Reliability model
Analysis runs are asynchronous at the product level even if early execution is implemented with a simple local worker. Persist state transitions, use idempotency keys, and make stage retries explicit. Do not assume in-memory state survives process restart.

## Technology decisions
The approved direction is recorded in `TECHNICAL_DECISIONS.md`. Database entities and relationships are specified in `DATABASE_DESIGN.md`.

## Dependencies
This architecture depends on the product boundary in `../00-PROJECT/PRODUCT_REQUIREMENTS.md` and analysis semantics in `../03-ANALYSIS-ENGINE/`.

## Acceptance criteria
- Logical components and boundaries are clear.
- Untrusted repository execution is separated from the control plane.
- No future technology is represented as mandatory.
- Stage failures and incomplete analysis remain visible.

## Open questions
- Should analysis workers run in the same deployment initially or as separately scaled worker processes?
- Where will source snapshots and large logs be stored, and with what retention?
- Which authentication provider and tenant identity model will be used?
- What are the first production deployment and network-boundary assumptions?
