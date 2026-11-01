package com.aichangeguard.impact.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Complete report of REST API impact analysis between two revisions.
 *
 * <p>Contains the extracted endpoints from each revision, the detected changes,
 * summary statistics, and analysis limitations.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiImpactReport(
        /** Repository path that was analyzed. */
        String repositoryPath,

        /** Base revision identifier. */
        String baseRevision,

        /** Target revision identifier. */
        String targetRevision,

        /** Endpoints discovered in the base revision. */
        List<ApiEndpoint> baseEndpoints,

        /** Endpoints discovered in the target revision. */
        List<ApiEndpoint> targetEndpoints,

        /** Detected API contract changes. */
        List<ApiChangeFinding> findings,

        /** Number of breaking changes. */
        int breakingChangeCount,

        /** Number of potentially breaking changes. */
        int potentiallyBreakingChangeCount,

        /** Number of compatible (additive) changes. */
        int compatibleChangeCount,

        /** Number of changes with unknown severity. */
        int unknownChangeCount,

        /** Total number of controllers analyzed in base revision. */
        int baseControllerCount,

        /** Total number of controllers analyzed in target revision. */
        int targetControllerCount,

        /** Whether the analysis completed successfully. */
        boolean isComplete,

        /** Timestamp of the analysis. */
        Instant analyzedAt,

        /** Warnings encountered during analysis. */
        List<String> warnings,

        /**
         * Known limitations of this analysis.
         * Every report must include these to prevent false confidence.
         */
        List<String> limitations
) {}
