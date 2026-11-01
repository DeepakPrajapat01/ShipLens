package com.aichangeguard.impact.database.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Representation of a Spring Data repository method.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RepositoryMethod(
        String methodName,
        String returnType,
        List<String> parameterTypes,
        String queryType, // DERIVED, JPQL_QUERY, NATIVE_QUERY, UNKNOWN
        String queryString,
        boolean isNative,
        boolean isModifying,
        int sourceLine
) {}

