package com.aichangeguard.orchestration.api;

import com.aichangeguard.common.error.ChangeGuardException;
import com.aichangeguard.common.error.ErrorCode;
import com.aichangeguard.orchestration.model.AnalysisRequest;
import com.aichangeguard.orchestration.model.AnalysisRun;
import com.aichangeguard.orchestration.model.AnalysisRunSummary;
import com.aichangeguard.orchestration.model.UnifiedAnalysisReport;
import com.aichangeguard.orchestration.repository.AnalysisRunRepository;
import com.aichangeguard.orchestration.service.AnalysisOrchestrationEngine;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * REST API for submitting analysis requests, checking run status, querying history,
 * and retrieving unified risk assessment reports.
 */
@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private final AnalysisOrchestrationEngine orchestrationEngine;
    private final AnalysisRunRepository runRepository;

    public AnalysisController(
            AnalysisOrchestrationEngine orchestrationEngine,
            AnalysisRunRepository runRepository
    ) {
        this.orchestrationEngine = orchestrationEngine;
        this.runRepository = runRepository;
    }

    /**
     * Submits a code change for end-to-end ChangeGuard analysis.
     * Returns HTTP 202 ACCEPTED with the created or reused run representation immediately.
     */
    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<AnalysisRun> submitAnalysis(@Valid @RequestBody AnalysisRequest request) {
        AnalysisRun run = orchestrationEngine.submitAnalysis(request);
        URI location = URI.create("/api/v1/analysis/" + run.runId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).location(location).body(run);
    }

    /**
     * Retrieves the lifecycle status, stage progress, and intermediate results of an analysis run.
     */
    @GetMapping("/{runId}")
    public ResponseEntity<AnalysisRun> getRun(@PathVariable String runId) {
        return runRepository.findById(runId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ChangeGuardException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Analysis run not found: " + runId
                ));
    }

    /**
     * Retrieves the unified, explainable production risk report for a completed or evaluated run.
     */
    @GetMapping("/{runId}/report")
    public ResponseEntity<UnifiedAnalysisReport> getReport(@PathVariable String runId) {
        AnalysisRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ChangeGuardException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Analysis run not found: " + runId
                ));

        return ResponseEntity.ok(UnifiedAnalysisReport.from(run));
    }

    /**
     * Lists historical analysis runs, optionally filtered by repository path.
     */
    @GetMapping(produces = "application/json")
    public ResponseEntity<List<AnalysisRunSummary>> listRuns(
            @RequestParam(required = false) String repositoryPath
    ) {
        List<AnalysisRun> runs = (repositoryPath != null && !repositoryPath.isBlank())
                ? runRepository.findByRepository(repositoryPath)
                : runRepository.findAll();

        List<AnalysisRunSummary> summaries = runs.stream()
                .map(AnalysisRunSummary::from)
                .toList();

        return ResponseEntity.ok(summaries);
    }
}

