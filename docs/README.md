# AI ChangeGuard

**An intelligent production-risk assessment platform for code changes.**

AI ChangeGuard is planned as a pre-deployment analysis platform for Java 21 and Spring Boot repositories. It evaluates a proposed change using repository ingestion, Git diff inspection, Java AST analysis, method-level dependency mapping, API and database impact analysis, dependency-aware test selection, isolated test execution, and evidence-based risk assessment. GitHub pull-request integration and a SaaS dashboard are part of the product direction.

> **Project status:** Documentation foundation / pre-implementation. The capabilities described here are planned unless a later implementation report explicitly verifies them. No risk-detection guarantee is made.

## Product goals
- Identify potentially affected code and contracts before deployment.
- Select relevant tests using explainable dependency evidence.
- Execute selected tests in an isolated environment.
- Present findings with evidence, confidence, uncertainty, and limitations.
- Integrate results into a GitHub pull-request workflow.

## Initial supported ecosystem
- Java 21
- Spring Boot
- Maven
- JUnit 5

Other languages, build systems, and frameworks are outside the initial support commitment.

## Technology direction
| Area | Planned technology |
|---|---|
| Backend/API | Java 21, Spring Boot |
| Static code analysis | JavaParser |
| Build/test orchestration | Maven, JUnit 5 |
| Web dashboard | Next.js, TypeScript |
| Primary persistence | PostgreSQL |
| Schema migrations | Flyway |
| Test execution isolation | Docker |
| Source-control integration | GitHub App |

Kafka, Redis, Kubernetes, and LLM integration are future options and are not required for the initial architecture.

## Documentation map
- `docs/00-PROJECT/` — product definition, requirements, roadmap.
- `docs/01-ARCHITECTURE/` — system boundaries, decisions, data, security.
- `docs/02-ENGINEERING/` — coding, testing, API, and error conventions.
- `docs/03-ANALYSIS-ENGINE/` — static analysis, dependency graph, test selection, risk model.
- `docs/04-INTEGRATIONS/` — GitHub and isolated Docker execution.
- `docs/05-OPERATIONS/` — local development, deployment, monitoring.

Start with `docs/00-PROJECT/PROJECT_OVERVIEW.md`, then review `docs/01-ARCHITECTURE/TECHNICAL_DECISIONS.md` and `docs/00-PROJECT/DEVELOPMENT_ROADMAP.md`.

## High-level workflow
1. A user connects or selects a supported GitHub repository.
2. A pull-request event or user action identifies a base and head revision.
3. The backend obtains the permitted repository snapshot and computes the diff.
4. Static analysis identifies changed symbols and possible impact paths.
5. API and database changes are examined where supported.
6. A dependency-aware strategy selects candidate tests.
7. Selected tests are executed in a constrained Docker environment.
8. The assessment service combines evidence and limitations into a risk report.
9. Results are shown in the dashboard and, where configured, on the pull request.

Each stage may be incomplete or unsupported for a particular repository. The report must expose that fact rather than silently treating missing analysis as a clean result.

## Non-goals for the initial release
- Guaranteeing defect-free releases or predicting every production incident.
- Replacing code review, CI, security scanning, integration testing, or staging.
- Supporting arbitrary languages and build systems.
- Automatically merging or deploying code.
- Using an LLM as the authority for deterministic risk or test-selection decisions.

## Development status
This repository begins with documentation only. Follow `AGENTS.md` before any implementation. The roadmap is authoritative for sequencing and phase gates.

## Contributing
Do not begin implementation based only on this README. Read the relevant architecture and engineering documents, inspect the repository state, and obtain authorization for the active roadmap phase.
