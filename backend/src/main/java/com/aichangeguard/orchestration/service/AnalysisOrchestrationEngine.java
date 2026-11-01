package com.aichangeguard.orchestration.service;

import com.aichangeguard.analysis.model.ParsedFileMetadata;
import com.aichangeguard.analysis.model.SourceAnalysisReport;
import com.aichangeguard.analysis.service.JavaSourceParser;
import com.aichangeguard.dependency.model.DependencyGraph;
import com.aichangeguard.dependency.model.GraphReport;
import com.aichangeguard.dependency.model.ImpactAnalysisResult;
import com.aichangeguard.dependency.service.DependencyGraphBuilder;
import com.aichangeguard.dependency.service.ImpactTraversalService;
import com.aichangeguard.impact.database.model.DatabaseImpactReport;
import com.aichangeguard.impact.database.service.DatabaseImpactAnalyzer;
import com.aichangeguard.impact.model.ApiImpactReport;
import com.aichangeguard.impact.service.ApiImpactAnalyzer;
import com.aichangeguard.integration.docker.model.DockerProperties;
import com.aichangeguard.integration.docker.model.TestExecutionRequest;
import com.aichangeguard.integration.docker.model.TestExecutionResult;
import com.aichangeguard.integration.docker.service.DockerTestExecutionEngine;
import com.aichangeguard.orchestration.model.AnalysisRequest;
import com.aichangeguard.orchestration.model.AnalysisRun;
import com.aichangeguard.orchestration.model.AnalysisRunStatus;
import com.aichangeguard.orchestration.model.AnalysisStage;
import com.aichangeguard.orchestration.repository.AnalysisRunRepository;
import com.aichangeguard.repository.diff.model.ChangedFileEntry;
import com.aichangeguard.repository.diff.model.DiffAnalysisRequest;
import com.aichangeguard.repository.diff.model.DiffAnalysisResult;
import com.aichangeguard.repository.diff.service.GitDiffAnalyzer;
import com.aichangeguard.repository.model.RepositoryInspectionReport;
import com.aichangeguard.repository.model.RepositoryRegistrationRequest;
import com.aichangeguard.repository.model.SourceDirectoryInfo;
import com.aichangeguard.repository.security.RepositoryPathValidator;
import com.aichangeguard.repository.service.RepositoryInspectionService;
import com.aichangeguard.risk.model.RiskReport;
import com.aichangeguard.risk.service.ProductionRiskAssessmentEngine;
import com.aichangeguard.testing.model.SelectedTest;
import com.aichangeguard.testing.model.TestSelectionReport;
import com.aichangeguard.testing.service.TestIntelligenceEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Main coordinator executing the end-to-end ChangeGuard analysis pipeline.
 */
@Service
public class AnalysisOrchestrationEngine {

    private static final Logger log = LoggerFactory.getLogger(AnalysisOrchestrationEngine.class);

    private final RepositoryPathValidator pathValidator;
    private final RepositoryInspectionService inspectionService;
    private final GitDiffAnalyzer diffAnalyzer;
    private final JavaSourceParser sourceParser;
    private final DependencyGraphBuilder graphBuilder;
    private final ImpactTraversalService impactTraversalService;
    private final ApiImpactAnalyzer apiImpactAnalyzer;
    private final DatabaseImpactAnalyzer dbImpactAnalyzer;
    private final TestIntelligenceEngine testIntelligenceEngine;
    private final DockerTestExecutionEngine dockerExecutionEngine;
    private final ProductionRiskAssessmentEngine riskAssessmentEngine;
    private final AnalysisRunRepository runRepository;
    private final DockerProperties dockerProperties;

    private final Executor asyncExecutor = Executors.newCachedThreadPool();

    public AnalysisOrchestrationEngine(
            RepositoryPathValidator pathValidator,
            RepositoryInspectionService inspectionService,
            GitDiffAnalyzer diffAnalyzer,
            JavaSourceParser sourceParser,
            DependencyGraphBuilder graphBuilder,
            ImpactTraversalService impactTraversalService,
            ApiImpactAnalyzer apiImpactAnalyzer,
            DatabaseImpactAnalyzer dbImpactAnalyzer,
            TestIntelligenceEngine testIntelligenceEngine,
            DockerTestExecutionEngine dockerExecutionEngine,
            ProductionRiskAssessmentEngine riskAssessmentEngine,
            AnalysisRunRepository runRepository,
            DockerProperties dockerProperties
    ) {
        this.pathValidator = pathValidator;
        this.inspectionService = inspectionService;
        this.diffAnalyzer = diffAnalyzer;
        this.sourceParser = sourceParser;
        this.graphBuilder = graphBuilder;
        this.impactTraversalService = impactTraversalService;
        this.apiImpactAnalyzer = apiImpactAnalyzer;
        this.dbImpactAnalyzer = dbImpactAnalyzer;
        this.testIntelligenceEngine = testIntelligenceEngine;
        this.dockerExecutionEngine = dockerExecutionEngine;
        this.riskAssessmentEngine = riskAssessmentEngine;
        this.runRepository = runRepository;
        this.dockerProperties = dockerProperties;
    }

    /**
     * Submits an analysis run. Checks for active duplicate runs, creates the record,
     * and triggers execution asynchronously.
     */
    public AnalysisRun submitAnalysis(AnalysisRequest request) {
        // 1. Idempotency: check if an active run already exists for identical inputs
        Optional<AnalysisRun> activeRun = runRepository.findActiveRun(
                request.repositoryPath(), request.baseRevision(), request.targetRevision()
        );
        if (activeRun.isPresent()) {
            log.info("Reusing existing active analysis run {} for repository '{}' ({} -> {})",
                    activeRun.get().runId(), request.repositoryPath(), request.baseRevision(), request.targetRevision());
            return activeRun.get();
        }

        String runId = "run-" + UUID.randomUUID();
        AnalysisRun pendingRun = AnalysisRun.pending(runId, request);
        runRepository.save(pendingRun);

        // Dispatch asynchronous pipeline execution
        CompletableFuture.runAsync(() -> executePipeline(runId, request), asyncExecutor)
                .exceptionally(ex -> {
                    log.error("Unhandled pipeline exception in run {}: {}", runId, ex.getMessage(), ex);
                    return null;
                });

        return pendingRun;
    }

    /**
     * Synchronous pipeline execution (used by async task and direct integration testing).
     */
    public AnalysisRun executePipeline(String runId, AnalysisRequest request) {
        log.info("Starting pipeline execution for run {} on '{}' ({} -> {})",
                runId, request.repositoryPath(), request.baseRevision(), request.targetRevision());

        List<String> warnings = new ArrayList<>();
        AnalysisRun currentRun = runRepository.findById(runId)
                .orElse(AnalysisRun.pending(runId, request));

        Path repoPath;
        try {
            // Stage 1: Validate repository path
            repoPath = pathValidator.validatePath(Path.of(request.repositoryPath()));
        } catch (Exception e) {
            log.error("Repository validation failed for run {}: {}", runId, e.getMessage());
            AnalysisRun failedRun = currentRun.withFailure("Repository path validation failed: " + e.getMessage());
            runRepository.save(failedRun);
            return failedRun;
        }

        RepositoryInspectionReport inspectionReport = null;
        DiffAnalysisResult diffResult = null;
        SourceAnalysisReport sourceReport = null;
        GraphReport graphReport = null;
        DependencyGraph graph = null;
        List<ImpactAnalysisResult> impactResults = new ArrayList<>();
        ApiImpactReport apiReport = null;
        DatabaseImpactReport dbReport = null;
        TestSelectionReport testSelectionReport = null;
        TestExecutionResult testExecutionResult = null;

        try {
            // Stage 2: Inspect repository
            updateStage(runId, AnalysisStage.INSPECTING_REPOSITORY, 10);
            try {
                inspectionReport = inspectionService.inspect(
                        new RepositoryRegistrationRequest(runId, request.repositoryPath())
                );
            } catch (Exception e) {
                log.warn("Repository inspection failed in run {}: {}", runId, e.getMessage());
                warnings.add("Repository inspection limitation: " + e.getMessage());
            }

            // Stage 3: Analyze Git diff
            updateStage(runId, AnalysisStage.ANALYZING_DIFF, 20);
            try {
                diffResult = diffAnalyzer.analyzeDiff(
                        new DiffAnalysisRequest(request.repositoryPath(), request.baseRevision(), request.targetRevision())
                );
            } catch (Exception e) {
                log.error("Git diff analysis failed in run {}: {}", runId, e.getMessage());
                AnalysisRun failedRun = currentRun.withFailure("Git diff analysis failed: " + e.getMessage());
                runRepository.save(failedRun);
                return failedRun;
            }

            // Stage 4: Parse relevant Java sources
            updateStage(runId, AnalysisStage.PARSING_SOURCES, 35);
            try {
                List<String> javaFilesToParse = discoverJavaFiles(repoPath, inspectionReport, diffResult);
                sourceReport = sourceParser.parseRepository(repoPath, javaFilesToParse);
            } catch (Exception e) {
                log.warn("Java source parsing encountered errors in run {}: {}", runId, e.getMessage());
                warnings.add("Source parsing limitation: " + e.getMessage());
            }

            // Stage 5: Construct Dependency Graph
            updateStage(runId, AnalysisStage.BUILDING_DEPENDENCY_GRAPH, 50);
            try {
                List<ParsedFileMetadata> parsedFiles = (sourceReport != null && sourceReport.parsedFiles() != null)
                        ? sourceReport.parsedFiles() : Collections.emptyList();
                graph = graphBuilder.buildGraph(parsedFiles);
                graphReport = graph.generateReport(0);

                // Traverse impact for candidate changed methods
                List<String> changedSymbols = extractChangedSymbols(diffResult, parsedFiles);
                for (String symbol : changedSymbols) {
                    try {
                        ImpactAnalysisResult impact = impactTraversalService.traverseImpact(graph, symbol, 10);
                        impactResults.add(impact);
                    } catch (Exception ex) {
                        log.debug("Traversal failed for symbol '{}': {}", symbol, ex.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("Dependency graph construction failed in run {}: {}", runId, e.getMessage());
                warnings.add("Dependency graph limitation: " + e.getMessage());
            }

            // Stage 6: REST API Impact Analysis
            updateStage(runId, AnalysisStage.ANALYZING_API_IMPACT, 60);
            try {
                apiReport = apiImpactAnalyzer.analyze(
                        request.repositoryPath(),
                        request.baseRevision(),
                        request.targetRevision(),
                        sourceReport,
                        sourceReport
                );
            } catch (Exception e) {
                log.warn("API impact analysis failed in run {}: {}", runId, e.getMessage());
                warnings.add("API impact analysis limitation: " + e.getMessage());
            }

            // Stage 7: Database Impact Analysis
            updateStage(runId, AnalysisStage.ANALYZING_DATABASE_IMPACT, 70);
            try {
                dbReport = dbImpactAnalyzer.analyze(
                        request.repositoryPath(),
                        request.baseRevision(),
                        request.targetRevision(),
                        sourceReport,
                        sourceReport
                );
            } catch (Exception e) {
                log.warn("Database impact analysis failed in run {}: {}", runId, e.getMessage());
                warnings.add("Database impact analysis limitation: " + e.getMessage());
            }

            // Stage 8: Test Selection
            updateStage(runId, AnalysisStage.SELECTING_TESTS, 80);
            try {
                List<String> changedSymbols = extractChangedSymbols(
                        diffResult,
                        sourceReport != null ? sourceReport.parsedFiles() : Collections.emptyList()
                );
                testSelectionReport = testIntelligenceEngine.selectTests(
                        request.repositoryPath(),
                        request.baseRevision(),
                        request.targetRevision(),
                        sourceReport,
                        graph,
                        changedSymbols,
                        diffResult
                );
            } catch (Exception e) {
                log.warn("Test selection failed in run {}: {}", runId, e.getMessage());
                warnings.add("Test selection limitation: " + e.getMessage());
            }

            // Stage 9: Test Execution (if requested and Docker permitted)
            updateStage(runId, AnalysisStage.EXECUTING_TESTS, 90);
            if (request.executeTests() && dockerProperties.isEnabled()) {
                if (testSelectionReport != null && testSelectionReport.selectedTests() != null
                        && !testSelectionReport.selectedTests().isEmpty()) {
                    List<String> testNames = testSelectionReport.selectedTests().stream()
                            .map(SelectedTest::testIdentifier)
                            .toList();

                    TestExecutionRequest execRequest = new TestExecutionRequest(
                            runId,
                            request.repositoryPath(),
                            testNames,
                            dockerProperties.getDefaultTimeoutSeconds(),
                            dockerProperties.getDefaultCpuLimit(),
                            dockerProperties.getDefaultMemoryLimit(),
                            dockerProperties.isNetworkDisabled()
                    );
                    testExecutionResult = dockerExecutionEngine.executeTests(execRequest);
                }
            }

            // Stage 10: Production Risk Assessment
            updateStage(runId, AnalysisStage.ASSESSING_RISK, 95);
            RiskReport riskReport = null;
            try {
                riskReport = riskAssessmentEngine.assessRisk(
                        diffResult,
                        impactResults,
                        graphReport,
                        apiReport,
                        dbReport,
                        testSelectionReport,
                        testExecutionResult,
                        null
                );
            } catch (Exception e) {
                log.error("Risk assessment calculation failed in run {}: {}", runId, e.getMessage());
                warnings.add("Risk assessment computation failure: " + e.getMessage());
            }

            // Stage 11: Finalize and Persist
            AnalysisRun completedRun = currentRun.withCompletedReports(
                    warnings,
                    inspectionReport,
                    diffResult,
                    sourceReport,
                    graphReport,
                    apiReport,
                    dbReport,
                    testSelectionReport,
                    testExecutionResult,
                    riskReport
            );
            runRepository.save(completedRun);
            log.info("Analysis pipeline completed successfully for run {}", runId);
            return completedRun;

        } catch (Exception e) {
            log.error("Fatal pipeline failure for run {}: {}", runId, e.getMessage(), e);
            AnalysisRun failedRun = currentRun.withFailure("Pipeline failure: " + e.getMessage());
            runRepository.save(failedRun);
            return failedRun;
        }
    }

    private void updateStage(String runId, AnalysisStage stage, int progress) {
        runRepository.findById(runId).ifPresent(run -> {
            AnalysisRun updated = run.withProgress(stage, progress);
            runRepository.save(updated);
        });
    }

    private List<String> discoverJavaFiles(Path repoPath, RepositoryInspectionReport inspection, DiffAnalysisResult diff) {
        Set<String> files = new HashSet<>();

        // 1. Files from Git Diff
        if (diff != null && diff.changedFiles() != null) {
            for (ChangedFileEntry entry : diff.changedFiles()) {
                if (entry.isJavaFile() && entry.effectivePath() != null) {
                    files.add(entry.effectivePath().replace('\\', '/'));
                }
            }
        }

        // 2. Discover all .java files under repository source directories
        if (files.isEmpty() && Files.isDirectory(repoPath)) {
            try (Stream<Path> walk = Files.walk(repoPath, 15)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".java"))
                        .filter(p -> !p.toString().contains("target") && !p.toString().contains(".git"))
                        .map(p -> repoPath.relativize(p).toString().replace('\\', '/'))
                        .forEach(files::add);
            } catch (IOException e) {
                log.debug("Directory walk failed: {}", e.getMessage());
            }
        }

        return new ArrayList<>(files);
    }

    private List<String> extractChangedSymbols(DiffAnalysisResult diff, List<ParsedFileMetadata> files) {
        Set<String> symbols = new HashSet<>();
        if (diff != null && diff.changedFiles() != null) {
            for (ChangedFileEntry file : diff.changedFiles()) {
                if (file.candidateMethods() != null) {
                    file.candidateMethods().forEach(m -> symbols.add(m.methodName()));
                }
            }
        }

        // Fallback: collect declared method IDs from parsed files
        if (symbols.isEmpty() && files != null) {
            for (ParsedFileMetadata file : files) {
                file.types().forEach(t -> t.methods().forEach(m -> symbols.add(m.symbolId())));
            }
        }
        return new ArrayList<>(symbols);
    }
}

