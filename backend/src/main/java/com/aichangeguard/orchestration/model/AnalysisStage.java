package com.aichangeguard.orchestration.model;

/**
 * Granular execution stages of the end-to-end ChangeGuard analysis pipeline.
 */
public enum AnalysisStage {
    INITIALIZING(0),
    INSPECTING_REPOSITORY(10),
    ANALYZING_DIFF(20),
    PARSING_SOURCES(35),
    BUILDING_DEPENDENCY_GRAPH(50),
    ANALYZING_API_IMPACT(60),
    ANALYZING_DATABASE_IMPACT(70),
    SELECTING_TESTS(80),
    EXECUTING_TESTS(90),
    ASSESSING_RISK(95),
    COMPLETED(100),
    FAILED(100);

    private final int defaultProgressPercent;

    AnalysisStage(int defaultProgressPercent) {
        this.defaultProgressPercent = defaultProgressPercent;
    }

    public int getDefaultProgressPercent() {
        return defaultProgressPercent;
    }
}

