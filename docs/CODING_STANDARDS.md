# Coding Standards

## Purpose
Set implementation conventions for maintainable backend, frontend, analysis, and integration code.

## Scope
Applies when implementation is authorized. It does not imply that code currently exists.

## General rules
- Prefer clear, explicit code over clever abstractions.
- Keep modules cohesive and dependencies directed toward domain contracts.
- Use descriptive names and small methods with single responsibilities.
- Avoid speculative frameworks, generic layers, and premature microservices.
- Make state transitions and failure modes explicit.
- Do not swallow exceptions or convert unknown states into success.
- Keep secrets and customer source out of logs.
- Avoid global mutable state and hidden side effects.

## Java and Spring Boot
- Target Java 21; use the approved Spring Boot baseline once pinned.
- Use Maven and keep dependency versions centrally managed where appropriate.
- Use constructor injection; avoid field injection.
- Keep controllers thin: validate/translate transport input and delegate to application services.
- Keep domain logic independent of Spring MVC and persistence frameworks where practical.
- Use immutable records/value objects for stable data-transfer/domain values when appropriate.
- Use `Optional` for return semantics where it improves clarity; do not use it for fields or parameters by default.
- Define explicit transaction boundaries in application services.
- Use typed domain exceptions and centralized error mapping.
- Avoid broad catch blocks that hide root causes.
- Keep JavaParser-specific structures inside the analysis adapter/module boundary.

## Frontend
- Use TypeScript with strict compiler settings.
- Keep API access in a typed client/service layer.
- Keep authorization decisions on the server.
- Represent loading, empty, partial, failed, and completed states distinctly.
- Do not render untrusted repository content as executable HTML.
- Keep components focused and accessible.

## Database
- Use Flyway migrations for schema changes.
- Use explicit constraints and indexes based on query requirements.
- Use parameterized queries or safe ORM APIs.
- Avoid unbounded queries and uncontrolled eager loading.
- Store timestamps in UTC.
- Do not mutate released migration files.

## Analysis engine
- Preserve source locations and provenance for extracted facts.
- Distinguish deterministic, heuristic, unresolved, and unsupported results.
- Make traversal limits and parser diagnostics observable.
- Version extraction and risk algorithms.
- Ensure deterministic outputs for identical inputs and pinned tool versions where feasible.

## Naming and formatting
- Follow standard Java naming conventions and formatter configuration once established.
- Use consistent package/module names based on bounded responsibilities.
- Use kebab-case for documentation filenames and uppercase underscore naming only where already established by this repository's documentation convention.
- Keep comments focused on rationale and non-obvious constraints.

## Dependencies on other documents
See `TECHNICAL_DECISIONS.md`, `API_CONVENTIONS.md`, `ERROR_HANDLING.md`, and `TESTING_STRATEGY.md`.

## Acceptance criteria
- New code follows module boundaries and conventions.
- No new dependency is introduced without rationale and approval.
- Analysis evidence remains traceable.
- Tests and docs accompany behavior changes.

## Open questions
- Which Java formatter and static-analysis tools will be pinned?
- Which frontend lint/format tools and component conventions will be adopted?
- What package/module naming scheme will be selected during Phase 1?
