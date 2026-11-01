package com.aichangeguard.analysis.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Syntactic method invocation extracted from an AST block.
 * Note: Pure AST parsing captures the call expression syntactically; target resolution is unresolved
 * until a symbol solver or classpath analysis is performed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MethodInvocationMetadata(
        String scope,
        String methodName,
        int argumentCount,
        SourceRange range,
        boolean isResolved,
        String unresolvedReason
) {
    public static MethodInvocationMetadata syntactic(String scope, String methodName, int argumentCount, SourceRange range) {
        return new MethodInvocationMetadata(scope, methodName, argumentCount, range, false,
                "Target method symbol is unresolved without semantic symbol solver / classpath");
    }
}
