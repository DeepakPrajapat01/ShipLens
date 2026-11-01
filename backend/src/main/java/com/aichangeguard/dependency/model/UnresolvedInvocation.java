package com.aichangeguard.dependency.model;

import com.aichangeguard.analysis.model.SourceRange;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Records an invocation that could not be deterministically resolved to an internal repository symbol.
 * Preserves the call site without fabricating imaginary target nodes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnresolvedInvocation(
        String callerMethodId,
        String invokedName,
        String invokedScope,
        int argumentCount,
        SourceRange callSite,
        String reason
) {}
