package com.aichangeguard.impact.database.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Representation of a discovered Spring Data repository interface.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SpringDataRepository(
        String interfaceName,
        String qualifiedName,
        String domainEntity,
        String idType,
        List<RepositoryMethod> methods,
        String sourceFile,
        int sourceLine
) {
    public RepositoryMethod findMethod(String name) {
        if (name == null || methods == null) {
            return null;
        }
        for (RepositoryMethod m : methods) {
            if (name.equals(m.methodName())) {
                return m;
            }
        }
        return null;
    }
}

