package com.aichangeguard.common.error;

import java.util.Map;

/**
 * Thrown when domain or payload validation fails.
 */
public class ValidationException extends ChangeGuardException {

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }

    public ValidationException(String message, Map<String, Object> details) {
        super(ErrorCode.VALIDATION_ERROR, message, details);
    }
}
