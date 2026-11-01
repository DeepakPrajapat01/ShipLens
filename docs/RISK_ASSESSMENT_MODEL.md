# Risk Assessment Model

## Purpose
Define an explainable, evidence-based method for communicating potential production risk from a proposed code change.

## Scope
The model combines static analysis, impact, API/database changes, test-selection quality, execution outcomes, and analysis completeness. It is decision support only and cannot guarantee defect detection or production safety.

## Model principles
- Evidence must be traceable to a revision, source location, graph path, migration, test, or execution record.
- Deterministic facts and heuristic inferences remain distinguishable.
- Missing data and incomplete analysis must not reduce risk by default.
- A passing selected test suite is not proof of safety.
- Risk model versions and factor contributions must be reproducible.
- The UI must explain why a category was assigned and what remains unknown.

## Candidate dimensions
1. **Change surface:** number and kind of changed symbols/files, signature changes, configuration changes.
2. **Impact reachability:** direct and indirect callers, affected modules, and path evidence.
3. **API contract impact:** endpoint mapping, method, DTO, validation, and response contract changes.
4. **Database impact:** schema/migration changes, destructive operations, and application/schema mismatch evidence.
5. **Test evidence:** relevant tests discovered/selected, test mapping confidence, and fallback state.
6. **Execution outcome:** passed, failed, timed out, infrastructure error, cancelled, or not run.
7. **Analysis completeness:** parser failures, unresolved edges, unsupported constructs, resource-limit truncation, and missing modules.

## Assessment representation
The initial implementation should prefer a transparent rule-based assessment with named factors and explicit weights only after calibration. A numeric score is optional and must not be introduced before a documented scale, validation corpus, and interpretation are approved.

Possible categories are `LOW`, `MODERATE`, `HIGH`, and `INDETERMINATE`; final names and thresholds are open. `INDETERMINATE` is essential when evidence is insufficient to justify a directional result.

## Evidence handling
Each contributing factor records:
- Factor identifier and model version.
- Evidence references and evidence class.
- Contribution/rationale.
- Confidence or completeness qualifier.
- Known counter-evidence, if any.

Do not infer that no impact exists from an empty graph when graph coverage is incomplete. Do not equate test pass with risk removal. Test failures may indicate a regression, a pre-existing failure, or environment instability; context is required.

## Calibration and validation
Before numeric thresholds are published:
- Build a labeled historical/fixture dataset.
- Define labeling protocol and reviewer agreement.
- Evaluate false positives and false negatives.
- Measure calibration and stability across repository types.
- Version the model and retain prior results for reproducibility.
- Review bias caused by repository size, test conventions, and unsupported constructs.

## User-facing report
A report should show:
- Overall category and assessment status.
- Key contributing factors.
- Changed symbols and impact paths.
- API/database findings.
- Selected tests and rationale.
- Execution outcomes.
- Analysis coverage and unresolved limitations.
- Clear statement that this is not a safety guarantee.

## Dependencies
Consumes output from `CODE_ANALYSIS_DESIGN.md`, `DEPENDENCY_GRAPH_DESIGN.md`, `TEST_SELECTION_STRATEGY.md`, and Docker execution.

## Acceptance criteria
- Every risk contribution is evidence-linked.
- Incomplete analysis is visible and can yield indeterminate status.
- Model version and factors are persisted.
- No unsupported safety guarantee or fabricated performance claim appears.

## Open questions
- Whether a numeric score is necessary for the first release.
- Factor weights and thresholds, if any.
- Labeling dataset and calibration owner.
- How pre-existing test failures are distinguished.
- Whether organization-specific policy overlays are in scope.
