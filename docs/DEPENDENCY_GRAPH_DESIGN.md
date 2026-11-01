# Dependency Graph Design

## Purpose
Define the method-level dependency graph used to estimate change impact and support test selection.

## Scope
Graph is built from supported static Java evidence in an analyzed repository snapshot. It is not a complete runtime call graph.

## Graph model
### Nodes
- Type/class/interface/enum/record.
- Method or constructor.
- Optional field/property node where required for supported dependency relationships.
- Test method/test class.

Each node includes stable snapshot-scoped identity, kind, qualified name, source location, and extraction status.

### Directed edges
Initial edge types may include:
- `CALLS`: method invocation from caller to callee.
- `REFERENCES_TYPE`: source symbol references a type.
- `READS_FIELD` / `WRITES_FIELD`: field access where reliably extracted.
- `IMPLEMENTS` / `EXTENDS`: type hierarchy relationship.
- `ANNOTATED_WITH`: annotation relationship when useful for framework mapping.
- `TESTS`: test-to-production symbol association.

Every edge stores provenance, resolution status, and analyzer version. Only edge types needed by a defined use case should be implemented.

## Graph construction
1. Ingest normalized symbols and references from code analysis.
2. Resolve targets using supported symbol metadata.
3. Emit resolved edges only when the target is identified.
4. Preserve unresolved references separately; do not create fabricated target nodes.
5. Add supported framework-specific relationships in dedicated adapters.
6. Validate graph invariants and record extraction coverage.

## Impact traversal
Starting from changed symbols, traverse reverse `CALLS` edges to identify potential callers; traverse selected forward/type relationships only when a use case requires them. Traversal must be bounded by configurable depth and node/edge limits. Record the exact path and edge provenance for each impact result.

A path indicates a potential static dependency, not proof that the changed behavior is exercised at runtime.

## Cycles and overloads
- Use visited-node tracking to prevent infinite traversal.
- Preserve overload signatures in node identity.
- Avoid collapsing interface declarations and concrete implementations unless a resolved relationship supports it.
- Dynamic dispatch may create multiple possible targets or unresolved relationships.

## Confidence and coverage
Graph completeness depends on source parsing, classpath resolution, generated code, reflection, framework wiring, and supported constructs. Report counts/rates of resolved, unresolved, and unsupported relationships where meaningful. Do not label a node unaffected solely because no path was found.

## Storage and versioning
Graph data is scoped to a repository snapshot and analysis-engine version. Whether to persist full graph snapshots or recompute them is open; see `../01-ARCHITECTURE/DATABASE_DESIGN.md`.

## Dependencies
Consumes `CODE_ANALYSIS_DESIGN.md`; feeds `TEST_SELECTION_STRATEGY.md` and `RISK_ASSESSMENT_MODEL.md`.

## Acceptance criteria
- Edge provenance is retained.
- Traversal is deterministic and bounded.
- Cycles and overloads are handled.
- Unresolved edges remain visible.
- Impact results include explainable paths.

## Open questions
- Exact initial edge-type subset.
- Whether field read/write edges are sufficiently reliable for Phase 3.
- Graph persistence versus recomputation.
- Traversal depth and size defaults after benchmark.
- How framework injection relationships will be represented.
