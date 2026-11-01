package com.aichangeguard.common.error;

import java.util.Collections;
import java.util.Map;

/**
 * Base domain exception for AI ChangeGuard.
 * Encapsulates an ErrorCode, safe client-facing message, and optional structured details.
 */
public class ChangeGuardException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public ChangeGuardException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = Collections.emptyMap();
    }

    public ChangeGuardException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = Collections.emptyMap();
    }

    public ChangeGuardException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details != null ? details : Collections.emptyMap();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
