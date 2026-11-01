# Project Overview

## Purpose
Define the product vision, users, operating model, initial support boundary, and success criteria for AI ChangeGuard.

## Product definition
AI ChangeGuard evaluates code changes for potential production risks before deployment. It analyzes a proposed change in the context of its repository, estimates affected behavior, selects relevant tests, optionally executes those tests in an isolated environment, and presents a traceable assessment.

The product is a decision-support system. It does not guarantee detection of all defects, prove a change safe, or replace human review and existing CI/CD controls.

## Problem
A code diff often understates its true impact. A small method change can affect callers, REST endpoints, persistence behavior, configuration, or downstream workflows. Running every test may be expensive, while running only tests adjacent to the edited file can miss indirect effects. Teams need a transparent way to connect changed code to potentially affected behavior and relevant tests.

## Target users
- Backend engineers reviewing or authoring Java/Spring Boot changes.
- Engineering leads responsible for release confidence.
- Platform and quality engineers improving test effectiveness.
- Organizations using GitHub pull requests and Maven-based Java repositories.

## Initial scope
Supported repository profile:
- Java 21 source code.
- Spring Boot application conventions.
- Maven build structure.
- JUnit 5 tests.

Core planned capabilities:
1. Repository ingestion.
2. Git diff analysis.
3. Java AST analysis.
4. Method-level dependency mapping.
5. REST API impact analysis.
6. Database change analysis.
7. Dependency-aware test selection.
8. Isolated test execution.
9. Evidence-based production risk assessment.
10. GitHub pull-request integration.
11. SaaS dashboard.

See `PRODUCT_REQUIREMENTS.md` for normative requirements and exclusions.

## Product principles
1. **Evidence before assertion:** findings must link to source or execution evidence.
2. **Deterministic by default:** use parsers, graph algorithms, explicit rules, and measured test outcomes for core decisions.
3. **Explain uncertainty:** unsupported syntax, missing edges, incomplete tests, and execution failures must be visible.
4. **Least privilege:** repository access and test execution are constrained.
5. **Incremental delivery:** build a vertical slice before adding distributed infrastructure.
6. **Human decision remains central:** the platform informs review; it does not approve deployment.

## Conceptual workflow
Repository selection → revision resolution → snapshot and diff → AST extraction → symbol/dependency graph → API/database impact → test selection → isolated execution → risk assessment → dashboard/PR result.

The system architecture is described in `../01-ARCHITECTURE/SYSTEM_ARCHITECTURE.md`.

## Success measures
Initial product validation should measure:
- Repository ingestion completion rate for supported repositories.
- Analysis completion rate and parser coverage.
- Precision and recall of changed-symbol and impact identification on a labeled evaluation set.
- Selected-test relevance and missed-affected-test rate.
- Test execution success, duration, and resource consumption.
- Finding traceability and reviewer usefulness.
- GitHub installation and pull-request feedback success.

No numerical target is asserted until a baseline dataset and measurement method are agreed. Do not present targets as achieved results.

## Constraints and limitations
- Static analysis cannot fully resolve runtime reflection, dynamic proxies, generated code, dependency injection behavior, runtime configuration, or external system behavior.
- Method-level graph completeness depends on parser support, type resolution, build metadata, and supported language/framework patterns.
- Database impact analysis may not infer all effects of arbitrary SQL, ORM behavior, triggers, stored procedures, or production data.
- A passing selected test suite is not proof that unselected tests or production behavior are safe.
- Test execution of untrusted repositories requires strict isolation and resource controls.

## Dependencies on other documents
- Requirements: `PRODUCT_REQUIREMENTS.md`
- Delivery order: `DEVELOPMENT_ROADMAP.md`
- Architecture: `../01-ARCHITECTURE/SYSTEM_ARCHITECTURE.md`
- Risk semantics: `../03-ANALYSIS-ENGINE/RISK_ASSESSMENT_MODEL.md`

## Acceptance criteria
- Product scope and non-goals are explicit.
- Initial supported ecosystem is consistent across documentation.
- Planned capabilities are not represented as implemented.
- Success measures are measurable and not fabricated.

## Open questions
- Which repository sizes and Maven layouts define the first supported test cohort?
- Will the initial product be single-tenant for internal validation or multi-tenant SaaS from the first hosted release?
- What retention period will apply to source snapshots, logs, and reports?
- Which GitHub event types are required for the first integration?
