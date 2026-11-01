package com.aichangeguard.common.error;

import java.util.Map;

/**
 * Thrown when an immutable repository, run, or resource cannot be found.
 */
public class ResourceNotFoundException extends ChangeGuardException {

    public ResourceNotFoundException(String resourceType, String identifier) {
        super(ErrorCode.RESOURCE_NOT_FOUND, "%s '%s' not found".formatted(resourceType, identifier),
                Map.of("resourceType", resourceType, "identifier", identifier));
    }

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
