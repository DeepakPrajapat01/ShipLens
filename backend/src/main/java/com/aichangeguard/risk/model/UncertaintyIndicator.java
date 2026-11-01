package com.aichangeguard.risk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Flags missing analysis, unsupported constructs, or infrastructure failures.
 *
 * <p>Crucial principle: Missing or incomplete analysis must never be treated as zero risk.
 * If an indicator has {@code causesIndeterminate == true}, the overall assessment becomes INDETERMINATE.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UncertaintyIndicator(
        /** Risk category where the uncertainty or analysis limitation arose. */
        RiskCategory category,

        /** Short human-readable reason for the uncertainty. */
        String reason,

        /** Clear explanation of how this uncertainty impacts the validity of the assessment. */
        String impactOnAssessment,

        /** Whether this uncertainty is severe enough to force the overall risk to INDETERMINATE. */
        boolean causesIndeterminate
) {}

