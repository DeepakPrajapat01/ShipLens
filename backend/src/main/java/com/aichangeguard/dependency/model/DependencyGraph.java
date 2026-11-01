package com.aichangeguard.dependency.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory representation of the directed method- and type-level dependency graph.
 */
public class DependencyGraph {

    private final Map<String, DependencyNode> nodes;
    private final Map<String, List<DependencyEdge>> outgoingEdges;
    private final Map<String, List<DependencyEdge>> incomingEdges;
    private final List<UnresolvedInvocation> unresolvedInvocations;
    private final int cycleCount;

    public DependencyGraph(Map<String, DependencyNode> nodes,
                           Map<String, List<DependencyEdge>> outgoingEdges,
                           Map<String, List<DependencyEdge>> incomingEdges,
                           List<UnresolvedInvocation> unresolvedInvocations,
                           int cycleCount) {
        this.nodes = Collections.unmodifiableMap(nodes);
        this.outgoingEdges = Collections.unmodifiableMap(outgoingEdges);
        this.incomingEdges = Collections.unmodifiableMap(incomingEdges);
        this.unresolvedInvocations = Collections.unmodifiableList(unresolvedInvocations);
        this.cycleCount = cycleCount;
    }

    public Map<String, DependencyNode> getNodes() {
        return nodes;
    }

    public Optional<DependencyNode> getNode(String id) {
        return Optional.ofNullable(nodes.get(id));
    }

    public List<DependencyEdge> getOutgoingEdges(String sourceId) {
        return outgoingEdges.getOrDefault(sourceId, Collections.emptyList());
    }

    public List<DependencyEdge> getIncomingEdges(String targetId) {
        return incomingEdges.getOrDefault(targetId, Collections.emptyList());
    }

    public List<UnresolvedInvocation> getUnresolvedInvocations() {
        return unresolvedInvocations;
    }

    public int getCycleCount() {
        return cycleCount;
    }

    public GraphReport generateReport() {
        int totalNodes = nodes.size();
        int typeNodes = (int) nodes.values().stream().filter(n -> n.kind() == NodeKind.TYPE).count();
        int methodNodes = (int) nodes.values().stream()
                .filter(n -> n.kind() == NodeKind.METHOD || n.kind() == NodeKind.CONSTRUCTOR).count();

        int totalEdges = outgoingEdges.values().stream().mapToInt(List::size).sum();
        int resolvedCalls = (int) outgoingEdges.values().stream()
                .flatMap(List::stream)
                .filter(e -> e.type() == EdgeType.CALLS && e.resolutionStatus() == ResolutionStatus.RESOLVED)
                .count();

        List<String> limitations = List.of(
                "Static AST call resolution resolves same-class calls, inheritance hierarchy calls, and explicit constructor instantiations.",
                "Calls via interfaces, dynamic dispatch, or runtime dependency injection are not guaranteed to identify all runtime implementations.",
                "External library invocations and reflection calls remain explicitly categorized as unresolved."
        );

        return new GraphReport(
                totalNodes,
                typeNodes,
                methodNodes,
                totalEdges,
                resolvedCalls,
                unresolvedInvocations.size(),
                cycleCount,
                Instant.now(),
                limitations
        );
    }
}
