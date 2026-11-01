package com.aichangeguard.impact.model;

/**
 * Classification of what kind of API change was detected.
 */
public enum ApiChangeType {
    /** An endpoint was removed entirely. */
    ENDPOINT_REMOVED,

    /** A new endpoint was added. */
    ENDPOINT_ADDED,

    /** The HTTP method of an endpoint changed. */
    HTTP_METHOD_CHANGED,

    /** The URL path mapping of an endpoint changed. */
    URL_MAPPING_CHANGED,

    /** A required parameter was added. */
    REQUIRED_PARAMETER_ADDED,

    /** A required parameter was removed. */
    REQUIRED_PARAMETER_REMOVED,

    /** An optional parameter was added. */
    OPTIONAL_PARAMETER_ADDED,

    /** An optional parameter was removed. */
    OPTIONAL_PARAMETER_REMOVED,

    /** A parameter type changed. */
    PARAMETER_TYPE_CHANGED,

    /** The request body type changed. */
    REQUEST_BODY_CHANGED,

    /** The response type changed. */
    RESPONSE_TYPE_CHANGED,

    /** A validation constraint was added, removed, or modified. */
    VALIDATION_CONSTRAINT_CHANGED,

    /** An endpoint annotation attribute changed (e.g., produces, consumes). */
    ENDPOINT_ATTRIBUTE_CHANGED
}
