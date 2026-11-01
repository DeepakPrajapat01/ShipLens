package com.aichangeguard.orchestration.repository;

import com.aichangeguard.orchestration.model.AnalysisRun;
import com.aichangeguard.orchestration.model.AnalysisRunStatus;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe repository for analysis runs and execution history.
 */
@Repository
public class AnalysisRunRepository {

    private final Map<String, AnalysisRun> runsById = new ConcurrentHashMap<>();

    public void save(AnalysisRun run) {
        if (run != null && run.runId() != null) {
            runsById.put(run.runId(), run);
        }
    }

    public Optional<AnalysisRun> findById(String runId) {
        if (runId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(runsById.get(runId));
    }

    public List<AnalysisRun> findAll() {
        return runsById.values().stream()
                .sorted(Comparator.comparing(AnalysisRun::createdAt).reversed())
                .toList();
    }

    public List<AnalysisRun> findByRepository(String repositoryPath) {
        if (repositoryPath == null) {
            return findAll();
        }
        return runsById.values().stream()
                .filter(run -> repositoryPath.equalsIgnoreCase(run.repositoryPath()))
                .sorted(Comparator.comparing(AnalysisRun::createdAt).reversed())
                .toList();
    }

    /**
     * Finds any active (PENDING or RUNNING) run for the exact repository and revision tuple
     * to prevent duplicate concurrent execution.
     */
    public Optional<AnalysisRun> findActiveRun(String repositoryPath, String baseRevision, String targetRevision) {
        if (repositoryPath == null || baseRevision == null || targetRevision == null) {
            return Optional.empty();
        }

        return runsById.values().stream()
                .filter(run -> (run.status() == AnalysisRunStatus.PENDING || run.status() == AnalysisRunStatus.RUNNING))
                .filter(run -> repositoryPath.equalsIgnoreCase(run.repositoryPath())
                        && baseRevision.equalsIgnoreCase(run.baseRevision())
                        && targetRevision.equalsIgnoreCase(run.targetRevision()))
                .findFirst();
    }

    public void clear() {
        runsById.clear();
    }
}

