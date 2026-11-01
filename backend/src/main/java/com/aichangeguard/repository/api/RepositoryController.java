package com.aichangeguard.repository.api;

import com.aichangeguard.repository.diff.model.DiffAnalysisRequest;
import com.aichangeguard.repository.diff.model.DiffAnalysisResult;
import com.aichangeguard.repository.diff.service.GitDiffAnalyzer;
import com.aichangeguard.repository.model.RepositoryInspectionReport;
import com.aichangeguard.repository.model.RepositoryRegistrationRequest;
import com.aichangeguard.repository.service.RepositoryInspectionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller providing repository registration, read-only inspection, and Git diff analysis endpoints.
 */
@RestController
@RequestMapping("/api/v1/repositories")
public class RepositoryController {

    private final RepositoryInspectionService inspectionService;
    private final GitDiffAnalyzer diffAnalyzer;

    public RepositoryController(RepositoryInspectionService inspectionService,
                                GitDiffAnalyzer diffAnalyzer) {
        this.inspectionService = inspectionService;
        this.diffAnalyzer = diffAnalyzer;
    }

    @PostMapping("/inspect")
    public ResponseEntity<RepositoryInspectionReport> inspectRepository(
            @Valid @RequestBody RepositoryRegistrationRequest request) {
        RepositoryInspectionReport report = inspectionService.inspect(request);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/diff")
    public ResponseEntity<DiffAnalysisResult> analyzeDiff(
            @Valid @RequestBody DiffAnalysisRequest request) {
        DiffAnalysisResult result = diffAnalyzer.analyzeDiff(request);
        return ResponseEntity.ok(result);
    }
}
