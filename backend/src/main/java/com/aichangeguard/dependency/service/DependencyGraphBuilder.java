package com.aichangeguard.dependency.service;

import com.aichangeguard.analysis.model.FieldMetadata;
import com.aichangeguard.analysis.model.MethodInvocationMetadata;
import com.aichangeguard.analysis.model.MethodMetadata;
import com.aichangeguard.analysis.model.ParsedFileMetadata;
import com.aichangeguard.analysis.model.TypeMetadata;
import com.aichangeguard.dependency.model.DependencyEdge;
import com.aichangeguard.dependency.model.DependencyGraph;
import com.aichangeguard.dependency.model.DependencyNode;
import com.aichangeguard.dependency.model.EdgeType;
import com.aichangeguard.dependency.model.NodeKind;
import com.aichangeguard.dependency.model.ResolutionStatus;
import com.aichangeguard.dependency.model.UnresolvedInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a method-level and type-level directed dependency graph from parsed AST metadata.
 */
@Service
public class DependencyGraphBuilder {

    private static final Logger log = LoggerFactory.getLogger(DependencyGraphBuilder.class);

    public DependencyGraph buildGraph(List<ParsedFileMetadata> parsedFiles) {
        Map<String, DependencyNode> nodes = new LinkedHashMap<>();
        Map<String, List<DependencyEdge>> outgoing = new LinkedHashMap<>();
        Map<String, List<DependencyEdge>> incoming = new LinkedHashMap<>();
        List<UnresolvedInvocation> unresolvedInvocations = new ArrayList<>();

        Map<String, List<MethodMetadata>> methodsByType = new HashMap<>();
        Map<String, List<FieldMetadata>> fieldsByType = new HashMap<>();
        Map<String, String> typeBySimpleName = new HashMap<>();
        Map<String, Set<String>> typeHierarchy = new HashMap<>(); // TypeId -> Set of SuperTypeIds

        // Step 1: Register all nodes (Types, Fields, Constructors, Methods)
        for (ParsedFileMetadata file : parsedFiles) {
            for (TypeMetadata type : file.types()) {
                registerTypeAndMembers(type, nodes, methodsByType, fieldsByType, typeBySimpleName, typeHierarchy);
            }
        }

        // Step 2: Establish class-level inheritance relationships
        for (ParsedFileMetadata file : parsedFiles) {
            for (TypeMetadata type : file.types()) {
                buildClassHierarchyEdges(type, typeBySimpleName, typeHierarchy, outgoing, incoming);
            }
        }

        // Step 3: Resolve method-level invocations
        for (Map.Entry<String, List<MethodMetadata>> entry : methodsByType.entrySet()) {
            String typeId = entry.getKey();
            List<MethodMetadata> methods = entry.getValue();
            List<FieldMetadata> fields = fieldsByType.getOrDefault(typeId, Collections.emptyList());

            for (MethodMetadata method : methods) {
                String callerMethodId = method.symbolId();

                for (MethodInvocationMetadata invocation : method.invocations()) {
                    boolean resolved = resolveInvocation(
                            callerMethodId,
                            typeId,
                            invocation,
                            methodsByType,
                            fields,
                            typeBySimpleName,
                            typeHierarchy,
                            outgoing,
                            incoming
                    );

                    if (!resolved) {
                        unresolvedInvocations.add(new UnresolvedInvocation(
                                callerMethodId,
                                invocation.methodName(),
                                invocation.scope(),
                                invocation.argumentCount(),
                                invocation.range(),
                                "Target method symbol cannot be resolved statically without classpath / symbol solver"
                        ));
                    }
                }
            }
        }

        // Step 4: Detect cycles in call relationships
        int cycleCount = detectCycleCount(outgoing);

        log.info("Built dependency graph: {} nodes, {} outgoing edge sets, {} unresolved calls, {} cycles",
                nodes.size(), outgoing.size(), unresolvedInvocations.size(), cycleCount);

        return new DependencyGraph(nodes, outgoing, incoming, unresolvedInvocations, cycleCount);
    }

    private void registerTypeAndMembers(TypeMetadata type,
                                        Map<String, DependencyNode> nodes,
                                        Map<String, List<MethodMetadata>> methodsByType,
                                        Map<String, List<FieldMetadata>> fieldsByType,
                                        Map<String, String> typeBySimpleName,
                                        Map<String, Set<String>> typeHierarchy) {
        String typeId = type.symbolId();
        nodes.put(typeId, new DependencyNode(
                typeId,
                NodeKind.TYPE,
                type.qualifiedName(),
                type.simpleName(),
                null,
                type.range(),
                Map.of("kind", type.kind().name())
        ));

        typeBySimpleName.put(type.simpleName(), typeId);
        typeBySimpleName.put(type.qualifiedName(), typeId);

        List<MethodMetadata> allMethods = new ArrayList<>();
        allMethods.addAll(type.constructors());
        allMethods.addAll(type.methods());
        methodsByType.put(typeId, allMethods);
        fieldsByType.put(typeId, type.fields());

        // Register fields
        for (FieldMetadata field : type.fields()) {
            nodes.put(field.symbolId(), new DependencyNode(
                    field.symbolId(),
                    NodeKind.FIELD,
                    field.symbolId(),
                    field.name(),
                    typeId,
                    field.range(),
                    Map.of("type", field.typeName())
            ));
        }

        // Register constructors
        for (MethodMetadata ctor : type.constructors()) {
            nodes.put(ctor.symbolId(), new DependencyNode(
                    ctor.symbolId(),
                    NodeKind.CONSTRUCTOR,
                    ctor.symbolId(),
                    ctor.name(),
                    typeId,
                    ctor.range(),
                    Map.of("isConstructor", true)
            ));
        }

        // Register methods
        for (MethodMetadata method : type.methods()) {
            nodes.put(method.symbolId(), new DependencyNode(
                    method.symbolId(),
                    NodeKind.METHOD,
                    method.symbolId(),
                    method.name(),
                    typeId,
                    method.range(),
                    Map.of("returnType", method.returnTypeName(), "isAbstract", method.isAbstract())
            ));
        }

        // Recursively register nested types
        for (TypeMetadata nested : type.nestedTypes()) {
            registerTypeAndMembers(nested, nodes, methodsByType, fieldsByType, typeBySimpleName, typeHierarchy);
        }
    }

    private void buildClassHierarchyEdges(TypeMetadata type,
                                          Map<String, String> typeBySimpleName,
                                          Map<String, Set<String>> typeHierarchy,
                                          Map<String, List<DependencyEdge>> outgoing,
                                          Map<String, List<DependencyEdge>> incoming) {
        String typeId = type.symbolId();
        Set<String> superTypes = typeHierarchy.computeIfAbsent(typeId, k -> new HashSet<>());

        for (String extended : type.extendedTypes()) {
            String targetId = typeBySimpleName.get(extended);
            if (targetId != null) {
                superTypes.add(targetId);
                addEdge(outgoing, incoming, new DependencyEdge(
                        typeId, targetId, EdgeType.EXTENDS, ResolutionStatus.RESOLVED,
                        "class-extends-relationship", type.range()
                ));
            }
        }

        for (String implemented : type.implementedInterfaces()) {
            String targetId = typeBySimpleName.get(implemented);
            if (targetId != null) {
                superTypes.add(targetId);
                addEdge(outgoing, incoming, new DependencyEdge(
                        typeId, targetId, EdgeType.IMPLEMENTS, ResolutionStatus.RESOLVED,
                        "class-implements-relationship", type.range()
                ));
            }
        }

        for (TypeMetadata nested : type.nestedTypes()) {
            buildClassHierarchyEdges(nested, typeBySimpleName, typeHierarchy, outgoing, incoming);
        }
    }

    private boolean resolveInvocation(String callerMethodId,
                                      String enclosingTypeId,
                                      MethodInvocationMetadata invocation,
                                      Map<String, List<MethodMetadata>> methodsByType,
                                      List<FieldMetadata> fields,
                                      Map<String, String> typeBySimpleName,
                                      Map<String, Set<String>> typeHierarchy,
                                      Map<String, List<DependencyEdge>> outgoing,
                                      Map<String, List<DependencyEdge>> incoming) {
        String scope = invocation.scope();
        String methodName = invocation.methodName();
        int argCount = invocation.argumentCount();

        // Case 1: Internal method call in same class (scope is null or "this")
        if (scope == null || "this".equals(scope)) {
            List<MethodMetadata> candidates = findMethodsByNameAndArgs(methodsByType.get(enclosingTypeId), methodName, argCount);
            if (!candidates.isEmpty()) {
                for (MethodMetadata target : candidates) {
                    addEdge(outgoing, incoming, new DependencyEdge(
                            callerMethodId, target.symbolId(), EdgeType.CALLS,
                            candidates.size() == 1 ? ResolutionStatus.RESOLVED : ResolutionStatus.HEURISTIC,
                            "internal-same-class-call", invocation.range()
                    ));
                }
                return true;
            }

            // Check inherited methods from supertypes
            Set<String> superTypes = typeHierarchy.getOrDefault(enclosingTypeId, Collections.emptySet());
            for (String superTypeId : superTypes) {
                List<MethodMetadata> superCandidates = findMethodsByNameAndArgs(methodsByType.get(superTypeId), methodName, argCount);
                if (!superCandidates.isEmpty()) {
                    for (MethodMetadata target : superCandidates) {
                        addEdge(outgoing, incoming, new DependencyEdge(
                                callerMethodId, target.symbolId(), EdgeType.CALLS, ResolutionStatus.RESOLVED,
                                "inherited-method-call-on-" + superTypeId, invocation.range()
                        ));
                    }
                    return true;
                }
            }
        }

        // Case 2: Call on "super"
        if ("super".equals(scope)) {
            Set<String> superTypes = typeHierarchy.getOrDefault(enclosingTypeId, Collections.emptySet());
            for (String superTypeId : superTypes) {
                List<MethodMetadata> superCandidates = findMethodsByNameAndArgs(methodsByType.get(superTypeId), methodName, argCount);
                if (!superCandidates.isEmpty()) {
                    for (MethodMetadata target : superCandidates) {
                        addEdge(outgoing, incoming, new DependencyEdge(
                                callerMethodId, target.symbolId(), EdgeType.CALLS, ResolutionStatus.RESOLVED,
                                "super-call-on-" + superTypeId, invocation.range()
                        ));
                    }
                    return true;
                }
            }
        }

        // Case 3: Constructor instantiation (new TargetType(...))
        if ("<init>".equals(methodName)) {
            String targetTypeId = typeBySimpleName.get(scope);
            if (targetTypeId != null) {
                List<MethodMetadata> targetConstructors = methodsByType.get(targetTypeId).stream()
                        .filter(m -> m.isConstructor() && m.parameters().size() == argCount)
                        .toList();

                if (!targetConstructors.isEmpty()) {
                    for (MethodMetadata ctor : targetConstructors) {
                        addEdge(outgoing, incoming, new DependencyEdge(
                                callerMethodId, ctor.symbolId(), EdgeType.CONSTRUCTS, ResolutionStatus.RESOLVED,
                                "constructor-instantiation", invocation.range()
                        ));
                    }
                    return true;
                } else {
                    // Type has default unwritten constructor
                    addEdge(outgoing, incoming, new DependencyEdge(
                            callerMethodId, targetTypeId, EdgeType.CONSTRUCTS, ResolutionStatus.RESOLVED,
                            "type-instantiation-default-ctor", invocation.range()
                    ));
                    return true;
                }
            }
        }

        // Case 4: Static call on known class (e.g. Utility.doWork())
        if (scope != null && typeBySimpleName.containsKey(scope)) {
            String targetTypeId = typeBySimpleName.get(scope);
            List<MethodMetadata> targetMethods = findMethodsByNameAndArgs(methodsByType.get(targetTypeId), methodName, argCount);
            if (!targetMethods.isEmpty()) {
                for (MethodMetadata target : targetMethods) {
                    addEdge(outgoing, incoming, new DependencyEdge(
                            callerMethodId, target.symbolId(), EdgeType.CALLS, ResolutionStatus.RESOLVED,
                            "static-or-type-scoped-call", invocation.range()
                    ));
                }
                return true;
            }
        }

        // Case 5: Call on a known field of the enclosing class
        if (scope != null) {
            FieldMetadata matchingField = fields.stream()
                    .filter(f -> f.name().equals(scope))
                    .findFirst().orElse(null);

            if (matchingField != null) {
                String fieldTypeName = matchingField.typeName();
                String targetTypeId = typeBySimpleName.get(fieldTypeName);
                if (targetTypeId != null) {
                    List<MethodMetadata> targetMethods = findMethodsByNameAndArgs(methodsByType.get(targetTypeId), methodName, argCount);
                    if (!targetMethods.isEmpty()) {
                        for (MethodMetadata target : targetMethods) {
                            addEdge(outgoing, incoming, new DependencyEdge(
                                    callerMethodId, target.symbolId(), EdgeType.CALLS, ResolutionStatus.RESOLVED,
                                    "field-invocation-on-" + scope, invocation.range()
                            ));
                        }
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private List<MethodMetadata> findMethodsByNameAndArgs(List<MethodMetadata> methods, String name, int argCount) {
        if (methods == null) {
            return Collections.emptyList();
        }
        return methods.stream()
                .filter(m -> m.name().equals(name) && m.parameters().size() == argCount)
                .toList();
    }

    private void addEdge(Map<String, List<DependencyEdge>> outgoing,
                         Map<String, List<DependencyEdge>> incoming,
                         DependencyEdge edge) {
        outgoing.computeIfAbsent(edge.sourceNodeId(), k -> new ArrayList<>()).add(edge);
        incoming.computeIfAbsent(edge.targetNodeId(), k -> new ArrayList<>()).add(edge);
    }

    private int detectCycleCount(Map<String, List<DependencyEdge>> outgoing) {
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();
        int[] cycleCounter = new int[]{0};

        for (String node : outgoing.keySet()) {
            if (!visited.contains(node)) {
                dfsCycle(node, outgoing, visited, recStack, cycleCounter);
            }
        }
        return cycleCounter[0];
    }

    private void dfsCycle(String node,
                          Map<String, List<DependencyEdge>> outgoing,
                          Set<String> visited,
                          Set<String> recStack,
                          int[] counter) {
        visited.add(node);
        recStack.add(node);

        for (DependencyEdge edge : outgoing.getOrDefault(node, Collections.emptyList())) {
            if (edge.type() != EdgeType.CALLS) {
                continue;
            }
            String neighbor = edge.targetNodeId();
            if (recStack.contains(neighbor)) {
                counter[0]++;
            } else if (!visited.contains(neighbor)) {
                dfsCycle(neighbor, outgoing, visited, recStack, counter);
            }
        }

        recStack.remove(node);
    }
}
