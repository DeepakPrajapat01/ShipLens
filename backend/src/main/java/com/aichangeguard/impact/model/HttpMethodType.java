package com.aichangeguard.impact.model;

/**
 * HTTP method types recognized during Spring REST endpoint extraction.
 */
public enum HttpMethodType {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    HEAD,
    OPTIONS,
    TRACE,
    /**
     * Indicates that the HTTP method could not be determined from the annotation.
     */
    UNKNOWN
}
