package com.aichangeguard.dependency.service;

import com.aichangeguard.dependency.model.DependencyEdge;
import com.aichangeguard.dependency.model.DependencyGraph;
import com.aichangeguard.dependency.model.EdgeType;
import com.aichangeguard.dependency.model.ImpactAnalysisResult;
import com.aichangeguard.dependency.model.ImpactPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Traverses reverse dependency edges (callers) to identify methods potentially affected by a changed symbol.
 * Enforces cycle protection and configurable depth bounds.
 */
@Service
public class ImpactTraversalService {

    private static final Logger log = LoggerFactory.getLogger(ImpactTraversalService.class);
    private static final int DEFAULT_MAX_DEPTH = 15;

    /**
     * Traverses reverse dependency edges to find all direct and indirect callers affected by the changed symbol.
     *
     * @param graph In-memory dependency graph
     * @param changedSymbolId Symbol ID of the modified or added method/type
     * @return Result containing affected caller IDs and explainable impact paths
     */
    public ImpactAnalysisResult findAffectedCallers(DependencyGraph graph, String changedSymbolId) {
        return findAffectedCallers(graph, changedSymbolId, DEFAULT_MAX_DEPTH);
    }

    public ImpactAnalysisResult findAffectedCallers(DependencyGraph graph, String changedSymbolId, int maxDepth) {
        Set<String> affectedMethods = new LinkedHashSet<>();
        List<ImpactPath> paths = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int[] cyclesHit = new int[]{0};
        int[] maxDepthReached = new int[]{0};

        log.debug("Traversing reverse call graph for changed symbol: {}", changedSymbolId);

        List<String> initialPath = new ArrayList<>();
        initialPath.add(changedSymbolId);

        traverseReverse(
                graph,
                changedSymbolId,
                initialPath,
                new ArrayList<>(),
                new HashSet<>(),
                affectedMethods,
                paths,
                0,
                maxDepth,
                maxDepthReached,
                cyclesHit
        );

        if (cyclesHit[0] > 0) {
            warnings.add("Encountered %d cycle(s) during traversal; cyclic branches were bounded safely to prevent infinite loops.".formatted(cyclesHit[0]));
        }

        if (maxDepthReached[0] >= maxDepth) {
            warnings.add("Configured traversal depth limit (%d) was reached; impact graph may be truncated.".formatted(maxDepth));
        }

        return new ImpactAnalysisResult(
                changedSymbolId,
                affectedMethods,
                paths,
                maxDepthReached[0],
                cyclesHit[0],
                warnings
        );
    }

    private void traverseReverse(DependencyGraph graph,
                                String currentSymbolId,
                                List<String> currentPath,
                                List<DependencyEdge> currentEdges,
                                Set<String> visitedInCurrentPath,
                                Set<String> allAffectedCallers,
                                List<ImpactPath> allPaths,
                                int currentDepth,
                                int maxDepth,
                                int[] maxDepthReached,
                                int[] cyclesHit) {
        if (currentDepth > maxDepthReached[0]) {
            maxDepthReached[0] = currentDepth;
        }

        if (currentDepth >= maxDepth) {
            return;
        }

        visitedInCurrentPath.add(currentSymbolId);

        List<DependencyEdge> callers = graph.getIncomingEdges(currentSymbolId);
        for (DependencyEdge edge : callers) {
            if (edge.type() != EdgeType.CALLS && edge.type() != EdgeType.CONSTRUCTS) {
                continue;
            }

            String callerId = edge.sourceNodeId();

            if (visitedInCurrentPath.contains(callerId)) {
                // Cycle detected in current branch - terminate recursion on this branch
                cyclesHit[0]++;
                continue;
            }

            allAffectedCallers.add(callerId);

            List<String> newPath = new ArrayList<>(currentPath);
            newPath.add(0, callerId); // caller -> ... -> changedSymbol

            List<DependencyEdge> newEdges = new ArrayList<>(currentEdges);
            newEdges.add(0, edge);

            allPaths.add(new ImpactPath(newPath, newEdges, currentDepth + 1));

            traverseReverse(
                    graph,
                    callerId,
                    newPath,
                    newEdges,
                    new HashSet<>(visitedInCurrentPath),
                    allAffectedCallers,
                    allPaths,
                    currentDepth + 1,
                    maxDepth,
                    maxDepthReached,
                    cyclesHit
            );
        }
    }
}
