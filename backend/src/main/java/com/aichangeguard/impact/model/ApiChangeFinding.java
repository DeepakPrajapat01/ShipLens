package com.aichangeguard.impact.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single detected API contract change between two revisions.
 *
 * <p>Each finding includes the change type, severity classification, and evidence
 * supporting the determination. Findings always reference the endpoint(s) involved
 * and include descriptive context for risk assessment downstream.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiChangeFinding(
        /** The type of API change detected. */
        ApiChangeType changeType,

        /** Severity classification (BREAKING, POTENTIALLY_BREAKING, COMPATIBLE, UNKNOWN). */
        ApiChangeSeverity severity,

        /** Human-readable description of the change. */
        String description,

        /** Endpoint ID in the base revision, null if the endpoint is new. */
        String baseEndpointId,

        /** Endpoint ID in the target revision, null if the endpoint was removed. */
        String targetEndpointId,

        /** Controller class where the change was detected. */
        String controllerClass,

        /** The specific element that changed (parameter name, type name, etc.). */
        String changedElement,

        /** Value in the base revision, null if not applicable. */
        String baseValue,

        /** Value in the target revision, null if not applicable. */
        String targetValue,

        /** Source file path for traceability. */
        String sourceFile,

        /** Source line number for traceability. */
        int sourceLine
) {}
