# Technical Decisions

## Purpose
Record the current architectural baseline and prevent conflicting technology choices. This file is the index of decisions; material changes should be added as dated decision records.

## Decision status convention
- **Accepted direction:** approved for initial design; implementation still pending.
- **Provisional:** preferred default pending a named validation.
- **Deferred:** intentionally not selected for the initial release.
- **Open:** requires a decision before the dependent phase.

## TD-001 — Backend language and framework
**Status:** Accepted direction  
Use Java 21 and Spring Boot for the backend/control plane.  
**Rationale:** aligns the product backend with its initial Java/Spring Boot analysis domain and supports a cohesive JVM toolchain.  
**Implications:** use Maven for backend builds; framework version and dependency baseline must be pinned when implementation begins.  
**Validation:** confirm supported Java 21 runtime and compatible Spring Boot release at implementation time.

## TD-002 — Build system
**Status:** Accepted direction  
Use Maven for the backend and initial target repository ecosystem.  
**Implications:** initial repository ingestion and test orchestration target Maven layouts. Gradle is out of initial scope.

## TD-003 — Java source analysis
**Status:** Accepted direction  
Use JavaParser as the initial Java AST parsing library.  
**Implications:** AST parsing and symbol extraction are distinct from complete compiler-grade type resolution. Record unresolved symbols and parser limitations.  
**Open:** decide whether and when a compiler-backed symbol solver or Maven compiler metadata integration is required.

## TD-004 — Frontend
**Status:** Accepted direction  
Use Next.js and TypeScript for the SaaS dashboard.  
**Implications:** frontend communicates with backend APIs; it does not access PostgreSQL directly.

## TD-005 — Primary persistence
**Status:** Accepted direction  
Use PostgreSQL with Flyway-managed schema migrations.  
**Implications:** schema changes are version-controlled and applied through migration scripts. JSON fields may hold versioned evidence payloads where appropriate, but relational ownership and query needs must remain explicit.

## TD-006 — Test framework
**Status:** Accepted direction  
Target JUnit 5 discovery and execution for supported repositories.  
**Implications:** other test frameworks may be reported as unsupported or partially recognized.

## TD-007 — Test execution isolation
**Status:** Accepted direction  
Use Docker as the initial execution isolation mechanism, subject to the security controls in `../04-INTEGRATIONS/DOCKER_EXECUTION.md`.  
**Implications:** never execute untrusted repository tests directly on the control-plane host.

## TD-008 — Source-control integration
**Status:** Accepted direction  
Use a GitHub App rather than a broad personal access token model.  
**Implications:** request minimum permissions, validate webhook signatures, handle installation revocation, and scope repository access.

## TD-009 — Initial deployment shape
**Status:** Provisional  
Start with a modular monolith and a separately bounded execution worker process/container where operationally practical. Do not decompose every logical module into a service.  
**Validation:** review resource isolation and worker lifecycle before enabling customer repositories.

## TD-010 — Asynchronous orchestration
**Status:** Provisional  
Use persisted run/stage state and a simple database-backed or in-process worker strategy for the first vertical slice. Select the simplest reliable implementation after workload assumptions are tested.  
**Deferred:** Kafka is not required initially.

## TD-011 — Cache and distributed coordination
**Status:** Deferred  
Redis is not an initial dependency. Introduce only if measured caching, rate limiting, or coordination requirements cannot be adequately met with the initial stack.

## TD-012 — Container orchestration
**Status:** Deferred  
Kubernetes is not an initial requirement. Reassess only when deployment scale, scheduling, or isolation operations justify it.

## TD-013 — LLM usage
**Status:** Deferred  
LLMs must not be authoritative for parsing, dependency edges, test selection, or risk scores. A future optional explanation feature may be evaluated with strict provenance, privacy, and non-authoritative labeling.

## Cross-document invariants
- Java 21 + Spring Boot + Maven is the initial backend/repository direction.
- JavaParser is the initial AST parser; parser success is not equivalent to complete semantic resolution.
- PostgreSQL is the system-of-record database; Flyway manages schema evolution.
- Docker is used for isolated test execution; no host execution of untrusted code.
- GitHub App is the source-control integration model.
- Kafka, Redis, Kubernetes, and LLM integration remain deferred options.

## Change process
A material change must include: context, options considered, decision, consequences, security implications, migration/rollback plan, affected documents, and approval owner/date. Update all affected cross-references in the same change.

## Open decisions
- Spring Boot version and dependency version pinning.
- JavaParser version and symbol-resolution approach.
- Worker scheduling implementation for Phase 1.
- Artifact storage backend and retention.
- Authentication provider and tenant model.
- Deployment platform, secrets manager, and observability stack.
- API versioning and webhook event scope.
