# Local Development

## Purpose
Describe the intended local development workflow after implementation is authorized. This document does not instruct the current documentation-only milestone to install or initialize anything.

## Scope
Planned development environment includes Java 21, Maven, Spring Boot, Node.js for Next.js, PostgreSQL, Flyway, and Docker. Exact tool versions and setup scripts must be pinned by the implementation team.

## Prerequisites (planned)
- Java 21 JDK.
- Maven (or project Maven Wrapper once created and reviewed).
- Node.js LTS compatible with the selected Next.js version.
- PostgreSQL version selected during implementation.
- Docker Engine/Desktop with required isolation capabilities.
- Git and access to a controlled fixture repository.
- GitHub App credentials only for integration development; never commit them.

## Configuration
- Provide a committed `.env.example` containing placeholder names only.
- Keep real credentials in ignored local environment files or a secret manager.
- Separate local, test, and production configuration.
- Document required variables, defaults, and sensitivity when implementation begins.
- Do not use production customer repositories or secrets for routine local testing.

## Intended startup sequence
The exact commands are intentionally not specified until actual project modules and scripts exist. The eventual runbook should cover:
1. Start PostgreSQL and apply Flyway migrations.
2. Start the Spring Boot API/control plane.
3. Start the approved analysis/execution worker components.
4. Start the Next.js dashboard.
5. Verify health/readiness endpoints.
6. Run unit, integration, and fixture tests.

Do not invent commands before the corresponding scripts and module structure exist.

## Local test data
Use synthetic repositories and fixtures. Keep test repositories small, deterministic, and free of credentials. Include supported and unsupported Java constructs so limitations are exercised.

## Troubleshooting requirements
Future implementation should document:
- Database connectivity and migration errors.
- Java/Maven version mismatch.
- Node/Next.js build issues.
- Docker daemon and permission failures.
- GitHub webhook forwarding and signature setup.
- Analysis stage diagnostics and correlation IDs.

## Dependencies
See `../01-ARCHITECTURE/TECHNICAL_DECISIONS.md`, `../02-ENGINEERING/TESTING_STRATEGY.md`, and `../04-INTEGRATIONS/DOCKER_EXECUTION.md`.

## Acceptance criteria
- A new developer can reproduce local setup from pinned prerequisites.
- No secrets are committed.
- Startup, migration, test, and shutdown procedures are documented and verified.
- Synthetic fixtures cover supported and unsupported cases.

## Open questions
- Exact JDK, Maven, Node.js, PostgreSQL, and Docker versions.
- Monorepo versus separate frontend/backend repository layout.
- Local service orchestration approach.
- GitHub webhook development tool and secret handling.
