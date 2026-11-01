# Test Selection Strategy

## Purpose
Define how AI ChangeGuard selects candidate tests relevant to a proposed change while exposing coverage limitations.

## Scope
Initial target is Maven/JUnit 5 repositories. Test selection is a prioritization mechanism, not proof that selected tests cover every affected behavior.

## Inputs
- Immutable base/head revision and normalized diff.
- Changed symbols and their classification.
- Dependency graph with edge provenance and completeness metadata.
- Maven module/test-source discovery.
- Optional explicit test metadata or mapping supplied by repository conventions.

## Discovery
Identify Maven modules, test source roots, JUnit 5 test classes/methods, and build profiles where safely discoverable. Do not execute Maven during static discovery unless that action is explicitly isolated and required. Record undiscovered or unsupported test patterns.

## Association
Associate tests with production symbols using evidence in descending strength:
1. Direct references from test code to production symbols.
2. Resolved dependency paths from test methods to production symbols.
3. Explicit supported framework/test metadata.
4. Heuristic naming or package proximity, clearly labeled as heuristic.

A name match alone must not be represented as a confirmed test relationship.

## Selection algorithm
1. Map diff hunks to changed symbols.
2. Include directly associated tests where evidence exists.
3. Traverse supported dependency relationships from test roots to changed symbols within configured limits.
4. Include tests associated with impacted callers/contracts where graph evidence supports the relationship.
5. Deduplicate and group tests by Maven module.
6. Attach selection reason, evidence path, and confidence.
7. Evaluate graph/parser completeness before deciding whether a selective run is adequate.

## Fallback policy
When parsing, graph resolution, or test mapping quality is below an approved threshold, do not silently return a tiny selective set. Use a documented fallback: broaden to module-level tests, full supported test suite, or mark selection as insufficient and require CI/manual validation. The exact threshold and fallback order remain open pending evaluation data.

## Exclusions
Tests may be excluded because they are outside supported modules, cannot be discovered, require unavailable infrastructure, or exceed configured execution budgets. Every exclusion category should be observable. Do not call excluded tests unaffected.

## Output
For each candidate:
- Test identity (module/class/method where available).
- Selected or excluded state.
- Selection reason.
- Supporting symbols/graph paths.
- Evidence class and confidence.
- Discovery limitations.

Run-level output includes selection policy version, candidate count, selected count, fallback state, and completeness warnings.

## Evaluation
Use labeled changes and expected affected tests to measure recall (especially missed affected tests), precision, selected-test count, execution duration, and fallback frequency. Prioritize avoiding unjustified confidence over minimizing test count.

## Dependencies
Consumes `CODE_ANALYSIS_DESIGN.md` and `DEPENDENCY_GRAPH_DESIGN.md`; execution is specified in `../04-INTEGRATIONS/DOCKER_EXECUTION.md`.

## Acceptance criteria
- Every selected test has a reason and evidence.
- Low-confidence analysis triggers explicit fallback/insufficient status.
- Unsupported test patterns are disclosed.
- Selection behavior is reproducible for fixed inputs and policy version.

## Open questions
- Minimum graph/test-mapping quality for selective execution.
- Exact fallback policy and module boundary semantics.
- Treatment of parameterized, dynamic, inherited, and integration tests.
- How Maven profiles and external test services will be handled.
