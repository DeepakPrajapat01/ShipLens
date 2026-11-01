package com.aichangeguard.common.error;

import org.springframework.http.HttpStatus;

/**
 * Standardized application error codes categorized by failure domain
 * as specified in docs/ERROR_HANDLING.md.
 */
public enum ErrorCode {

    // VALIDATION category
    VALIDATION_ERROR("VALIDATION_ERROR", HttpStatus.BAD_REQUEST),
    INVALID_REQUEST("INVALID_REQUEST", HttpStatus.BAD_REQUEST),
    UNSUPPORTED_REPOSITORY_PROFILE("UNSUPPORTED_REPOSITORY_PROFILE", HttpStatus.UNPROCESSABLE_ENTITY),

    // AUTHORIZATION category
    UNAUTHORIZED("UNAUTHORIZED", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", HttpStatus.FORBIDDEN),

    // RESOURCE NOT FOUND
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", HttpStatus.METHOD_NOT_ALLOWED),

    // PIPELINE & DOMAIN categories
    INGESTION_ERROR("INGESTION_ERROR", HttpStatus.UNPROCESSABLE_ENTITY),
    PARSING_ERROR("PARSING_ERROR", HttpStatus.UNPROCESSABLE_ENTITY),
    RESOLUTION_ERROR("RESOLUTION_ERROR", HttpStatus.UNPROCESSABLE_ENTITY),
    ANALYSIS_ERROR("ANALYSIS_ERROR", HttpStatus.INTERNAL_SERVER_ERROR),
    TEST_SELECTION_ERROR("TEST_SELECTION_ERROR", HttpStatus.INTERNAL_SERVER_ERROR),
    EXECUTION_ERROR("EXECUTION_ERROR", HttpStatus.INTERNAL_SERVER_ERROR),
    INTEGRATION_ERROR("INTEGRATION_ERROR", HttpStatus.BAD_GATEWAY),
    PERSISTENCE_ERROR("PERSISTENCE_ERROR", HttpStatus.INTERNAL_SERVER_ERROR),

    // INTERNAL category
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final HttpStatus httpStatus;

    ErrorCode(String code, HttpStatus httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
