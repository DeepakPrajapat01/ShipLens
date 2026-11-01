# Code Analysis Design

## Purpose
Describe the planned deterministic pipeline that converts a repository snapshot and Git diff into structured Java code facts.

## Scope
Initial support is Java 21 source in Maven/Spring Boot repositories. JavaParser is the initial AST library. This design does not promise complete compiler semantics or runtime behavior reconstruction.

## Pipeline
1. **Snapshot validation:** confirm repository identity, immutable revision, and supported layout.
2. **Source discovery:** locate configured Maven modules and Java source roots; exclude generated/vendor directories according to explicit rules.
3. **Diff normalization:** map changed paths and hunks to the head revision and relevant base context.
4. **Parsing:** parse Java files with JavaParser using the configured language level.
5. **Symbol extraction:** collect packages, types, methods, constructors, fields, annotations, imports, signatures, and source ranges required by analysis.
6. **Resolution:** resolve references where supported using available source/build metadata; retain unresolved states.
7. **Change-to-symbol mapping:** associate changed lines/ranges with symbols and classify additions, removals, signature changes, and body changes where possible.
8. **Evidence normalization:** emit versioned facts with source path, revision, location, extraction method, and resolution quality.

## Symbol identity
Use a stable, revision-scoped identity containing repository/snapshot context, qualified type, member name, and normalized signature. Overloads must remain distinct. Do not use line number alone as symbol identity because line numbers shift.

## Evidence classes
- **Deterministic:** directly parsed syntax or Git metadata.
- **Resolved:** reference linked using supported symbol/type resolution.
- **Heuristic:** inferred association with documented rule and confidence.
- **Unresolved:** syntax/reference exists but target cannot be resolved.
- **Unsupported:** construct is outside supported analysis behavior.
- **Failed:** parser or analyzer could not produce a valid result.

Downstream graph and risk modules must preserve these distinctions.

## JavaParser considerations
JavaParser provides AST structure but does not by itself guarantee full Java compiler symbol resolution. Classpath, generated sources, annotation processing, modules, and framework conventions affect resolution. Any symbol solver integration must be separately approved and version-pinned.

## Changed-symbol classification
Potential categories:
- New/deleted/renamed file.
- Added/deleted type or member.
- Method body modification.
- Method signature or visibility change.
- Annotation or mapping change.
- Field/type change.
- Import/configuration change.
Classification should be evidence-based; ambiguous refactoring/rename detection must be labeled heuristic.

## Unsupported and difficult cases
Reflection, dynamic dispatch, runtime proxies, dependency injection, generated code, Lombok-generated members, annotation processors, Kotlin/Scala sources, native code, runtime configuration, and external services may limit analysis. The analyzer must publish coverage and limitations.

## Performance and resource controls
Bound file count, file size, parse time, memory, graph node count, and traversal depth. Record when limits truncate analysis. Do not publish performance claims before benchmarking representative repositories.

## Output contract
The module should return:
- Snapshot and analyzer version.
- Parsed file inventory and diagnostics.
- Symbol inventory and source locations.
- Changed-symbol set and classification.
- Resolution/coverage metadata.
- Unsupported constructs and resource-limit events.

## Dependencies
Feeds `DEPENDENCY_GRAPH_DESIGN.md`, API/database impact analysis, and `TEST_SELECTION_STRATEGY.md`.

## Acceptance criteria
- Same input and pinned tool versions produce reproducible normalized facts.
- Parse failures and unsupported constructs remain visible.
- Overloaded methods have distinct identities.
- Every extracted fact has revision and source provenance.

## Open questions
- JavaParser version and language-level configuration.
- Maven model/classpath discovery strategy.
- Whether to adopt JavaSymbolSolver or compiler-backed resolution.
- Treatment of generated sources and Lombok.
- Rename detection strategy and confidence semantics.
