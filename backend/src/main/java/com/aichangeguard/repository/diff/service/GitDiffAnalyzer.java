package com.aichangeguard.repository.diff.service;

import com.aichangeguard.common.error.ValidationException;
import com.aichangeguard.repository.diff.model.ChangedFileEntry;
import com.aichangeguard.repository.diff.model.DiffAnalysisRequest;
import com.aichangeguard.repository.diff.model.DiffAnalysisResult;
import com.aichangeguard.repository.security.RepositoryPathValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Main Git diff analyzer service.
 * Performs revision resolution, name-status inspection, unified diff extraction,
 * and structured line/method candidate tracking without modifying the source repository.
 */
@Service
public class GitDiffAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(GitDiffAnalyzer.class);

    private final RepositoryPathValidator pathValidator;
    private final GitCommandRunner gitCommandRunner;
    private final GitDiffParser diffParser;

    public GitDiffAnalyzer(RepositoryPathValidator pathValidator,
                           GitCommandRunner gitCommandRunner,
                           GitDiffParser diffParser) {
        this.pathValidator = pathValidator;
        this.gitCommandRunner = gitCommandRunner;
        this.diffParser = diffParser;
    }

    /**
     * Analyzes changes between two Git revisions in a local repository.
     *
     * @param request Request specifying the repository path, base revision, and target revision
     * @return Structured diff analysis result
     */
    public DiffAnalysisResult analyzeDiff(DiffAnalysisRequest request) {
        Path repoPath = pathValidator.validate(request.repositoryPath());

        // 1. Validate that the directory is a Git repository
        if (!gitCommandRunner.isGitRepository(repoPath)) {
            throw new ValidationException("Target directory is not a valid Git repository: " + repoPath);
        }

        // 2. Resolve revisions to immutable 40-character commit SHAs
        String baseSha = gitCommandRunner.resolveCommitSha(repoPath, request.baseRevision());
        String targetSha = gitCommandRunner.resolveCommitSha(repoPath, request.targetRevision());

        log.info("Analyzing Git diff in '{}' between base [{}] and target [{}]",
                repoPath, baseSha.substring(0, 8), targetSha.substring(0, 8));

        List<String> warnings = new ArrayList<>();
        List<String> limitations = new ArrayList<>();

        // 3. Handle identical revisions (empty diff)
        if (baseSha.equals(targetSha)) {
            return new DiffAnalysisResult(
                    repoPath.toString(),
                    request.baseRevision(),
                    request.targetRevision(),
                    baseSha,
                    targetSha,
                    Collections.emptyList(),
                    0, 0, 0, 0,
                    true,
                    false,
                    Instant.now(),
                    List.of("Base and target revisions are identical; zero changes detected"),
                    limitations
            );
        }

        // 4. Merge commit detection
        boolean isMerge = gitCommandRunner.isMergeCommit(repoPath, targetSha);
        if (isMerge) {
            warnings.add("Target revision is a merge commit; changes reflect diff against the first parent revision.");
        }

        // 5. Extract Git diff data
        String nameStatus = gitCommandRunner.getNameStatusDiff(repoPath, baseSha, targetSha);
        String unifiedDiff = gitCommandRunner.getUnifiedDiff(repoPath, baseSha, targetSha);

        // 6. Parse structured changed files, hunks, lines, and method candidates
        List<ChangedFileEntry> changedFiles = diffParser.parseDiff(nameStatus, unifiedDiff);

        int totalJava = (int) changedFiles.stream().filter(ChangedFileEntry::isJavaFile).count();
        int totalNonJava = changedFiles.size() - totalJava;
        int totalBinary = (int) changedFiles.stream().filter(ChangedFileEntry::isBinary).count();

        int totalAdded = changedFiles.stream().mapToInt(ChangedFileEntry::linesAdded).sum();
        int totalDeleted = changedFiles.stream().mapToInt(ChangedFileEntry::linesDeleted).sum();

        if (totalNonJava > 0) {
            warnings.add("%d non-Java files were modified in this diff and will not be parsed by AST analysis".formatted(totalNonJava));
        }

        if (totalBinary > 0) {
            warnings.add("%d binary files were modified and excluded from line-level diff tracking".formatted(totalBinary));
        }

        limitations.add("Method candidate identification from diff context is heuristic; full AST call-graph analysis required for exact boundary resolution.");

        return new DiffAnalysisResult(
                repoPath.toString(),
                request.baseRevision(),
                request.targetRevision(),
                baseSha,
                targetSha,
                changedFiles,
                changedFiles.size(),
                totalJava,
                totalAdded,
                totalDeleted,
                changedFiles.isEmpty(),
                isMerge,
                Instant.now(),
                warnings,
                limitations
        );
    }
}
