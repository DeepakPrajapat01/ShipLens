# Testing Strategy

## Purpose
Define how AI ChangeGuard itself and target-repository tests are validated. These are separate test domains and must not be conflated.

## Scope
Covers unit, integration, contract, fixture, security, and end-to-end testing for the platform. Target repository tests are selected and executed according to `../03-ANALYSIS-ENGINE/TEST_SELECTION_STRATEGY.md`.

## Platform test layers
1. **Unit tests:** pure domain rules, diff normalization, graph traversal, selection policy, risk-factor calculations.
2. **Component tests:** Spring services, persistence adapters, parser adapters, GitHub payload handling.
3. **Integration tests:** PostgreSQL/Flyway, Git operations, Docker execution adapter, GitHub API adapter with controlled test doubles.
4. **Contract tests:** API request/response schemas and webhook payload handling.
5. **End-to-end tests:** a controlled fixture repository from revision input through report generation.
6. **Security tests:** tenant isolation, webhook signature validation, path traversal, secret redaction, container escape/resource abuse scenarios within the test environment.
7. **Evaluation tests:** labeled repositories/changes for graph quality, test-selection relevance, and risk-model behavior.

## Analysis fixture corpus
Maintain small, versioned fixtures containing:
- Direct method changes and direct callers.
- Multi-hop call chains and cycles.
- Overloads, interfaces, inheritance, lambdas, method references, and unresolved symbols.
- Spring MVC mappings and DTO/validation changes.
- Database migration additions, removals, and unsupported SQL.
- Maven multi-module layouts and JUnit 5 tests.
- Generated source, reflection, dynamic dispatch, and other known limitations.

Every fixture should define expected extraction/impact behavior and known ambiguity.

## Determinism and reproducibility
- Pin parser and analysis versions.
- Use fixed repository revisions and fixture inputs.
- Avoid time-dependent assertions unless controlled.
- Record algorithm/model version with expected outputs.
- Separate flaky infrastructure tests from deterministic logic tests.

## Test quality measures
Measure precision, recall, false-positive rate, false-negative rate, selected-test count, selected-test relevance, and analysis completeness on a labeled corpus. Define denominators and labeling rules before publishing numbers. Do not claim measured improvements without recorded experiments.

## Target repository execution
The target project's tests are untrusted. They must run only through the isolated execution adapter. Platform tests must verify container constraints, timeouts, cancellation, output bounds, and cleanup. See `../04-INTEGRATIONS/DOCKER_EXECUTION.md`.

## Failure semantics
- Assertion failure: test ran and failed.
- Build failure: build/test command failed before or during test discovery/execution.
- Timeout: execution exceeded configured limit.
- Infrastructure failure: worker/container/runtime failed.
- Cancelled: execution intentionally stopped.
- Not run/skipped: no execution result exists.

Never treat these states as passed.

## Acceptance criteria
- Critical domain behavior has unit tests.
- Analysis fixtures cover supported and unsupported constructs.
- End-to-end tests verify incomplete stages remain visible.
- Isolation and tenant-security controls have dedicated tests.
- Test results are reproducible and tied to versions.

## Dependencies
See `../03-ANALYSIS-ENGINE/`, `../04-INTEGRATIONS/DOCKER_EXECUTION.md`, and `../01-ARCHITECTURE/SECURITY_ARCHITECTURE.md`.

## Open questions
- What minimum coverage thresholds are useful without encouraging meaningless coverage?
- Who maintains and labels the evaluation corpus?
- Which container runtime and CI environment will run security tests?
- How will flaky fixture tests be quarantined without hiding regressions?
