package com.aichangeguard.repository.diff.service;

import com.aichangeguard.common.error.ValidationException;
import com.aichangeguard.repository.diff.model.ChangedFileEntry;
import com.aichangeguard.repository.diff.model.DiffAnalysisRequest;
import com.aichangeguard.repository.diff.model.DiffAnalysisResult;
import com.aichangeguard.repository.diff.model.DiffLineType;
import com.aichangeguard.repository.diff.model.FileChangeType;
import com.aichangeguard.repository.diff.model.MethodCandidateHint;
import com.aichangeguard.repository.security.RepositoryPathValidator;
import com.aichangeguard.repository.security.RepositoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitDiffAnalyzerTest {

    @TempDir
    Path tempDir;

    private GitDiffAnalyzer diffAnalyzer;
    private Path gitRepo;

    @BeforeEach
    void setUp() throws Exception {
        RepositoryProperties properties = new RepositoryProperties();
        RepositoryPathValidator validator = new RepositoryPathValidator(properties);
        GitCommandRunner gitRunner = new GitCommandRunner();
        GitDiffParser parser = new GitDiffParser();
        diffAnalyzer = new GitDiffAnalyzer(validator, gitRunner, parser);

        gitRepo = tempDir.resolve("fixture-repo");
        Files.createDirectories(gitRepo);
        runGit(gitRepo, "init");
        runGit(gitRepo, "config", "user.name", "ChangeGuard Tester");
        runGit(gitRepo, "config", "user.email", "test@changeguard.local");
        runGit(gitRepo, "config", "commit.gpgsign", "false");
    }

    @Test
    @DisplayName("Diff detects file additions with accurate line and file counts")
    void testFileAdditions() throws Exception {
        // Base commit
        Files.writeString(gitRepo.resolve("README.md"), "# Initial Commit\n");
        runGit(gitRepo, "add", "README.md");
        runGit(gitRepo, "commit", "-m", "Initial commit");
        String baseSha = getHeadSha(gitRepo);

        // Target commit with new Java file
        Path src = Files.createDirectories(gitRepo.resolve("src/main/java/com/sample"));
        String javaContent = """
                package com.sample;

                public class OrderService {
                    public void createOrder(String id) {
                        System.out.println("Order: " + id);
                    }
                }
                """;
        Files.writeString(src.resolve("OrderService.java"), javaContent);
        runGit(gitRepo, "add", ".");
        runGit(gitRepo, "commit", "-m", "Add OrderService");
        String targetSha = getHeadSha(gitRepo);

        DiffAnalysisResult result = diffAnalyzer.analyzeDiff(
                new DiffAnalysisRequest(gitRepo.toString(), baseSha, targetSha)
        );

        assertThat(result.isClean()).isFalse();
        assertThat(result.totalFilesChanged()).isEqualTo(1);
        assertThat(result.totalJavaFilesChanged()).isEqualTo(1);
        assertThat(result.totalLinesAdded()).isGreaterThan(0);
        assertThat(result.totalLinesDeleted()).isEqualTo(0);

        ChangedFileEntry entry = result.changedFiles().get(0);
        assertThat(entry.changeType()).isEqualTo(FileChangeType.ADDED);
        assertThat(entry.newPath()).isEqualTo("src/main/java/com/sample/OrderService.java");
        assertThat(entry.isJavaFile()).isTrue();
        assertThat(entry.hunks()).isNotEmpty();
    }

    @Test
    @DisplayName("Diff detects file deletions and tracks deleted lines")
    void testFileDeletions() throws Exception {
        Path src = Files.createDirectories(gitRepo.resolve("src/main/java"));
        Files.writeString(src.resolve("LegacyService.java"), "public class LegacyService {}\n");
        runGit(gitRepo, "add", ".");
        runGit(gitRepo, "commit", "-m", "Add LegacyService");
        String baseSha = getHeadSha(gitRepo);

        // Delete file in target commit
        runGit(gitRepo, "rm", "src/main/java/LegacyService.java");
        runGit(gitRepo, "commit", "-m", "Remove LegacyService");
        String targetSha = getHeadSha(gitRepo);

        DiffAnalysisResult result = diffAnalyzer.analyzeDiff(
                new DiffAnalysisRequest(gitRepo.toString(), baseSha, targetSha)
        );

        assertThat(result.totalFilesChanged()).isEqualTo(1);
        ChangedFileEntry entry = result.changedFiles().get(0);
        assertThat(entry.changeType()).isEqualTo(FileChangeType.DELETED);
        assertThat(entry.oldPath()).isEqualTo("src/main/java/LegacyService.java");
        assertThat(entry.newPath()).isNull();
        assertThat(entry.linesDeleted()).isGreaterThan(0);
        assertThat(entry.linesAdded()).isEqualTo(0);
    }

    @Test
    @DisplayName("Diff detects modifications and identifies candidate changed methods")
    void testFileModificationsAndMethodCandidates() throws Exception {
        Path src = Files.createDirectories(gitRepo.resolve("src/main/java/com/calc"));
        String initialCode = """
                package com.calc;

                public class Calculator {
                    public int add(int a, int b) {
                        return a + b;
                    }
                }
                """;
        Files.writeString(src.resolve("Calculator.java"), initialCode);
        runGit(gitRepo, "add", ".");
        runGit(gitRepo, "commit", "-m", "Add Calculator");
        String baseSha = getHeadSha(gitRepo);

        String modifiedCode = """
                package com.calc;

                public class Calculator {
                    public int add(int a, int b) {
                        return a + b + 0;
                    }

                    public int multiply(int a, int b) {
                        return a * b;
                    }
                }
                """;
        Files.writeString(src.resolve("Calculator.java"), modifiedCode);
        runGit(gitRepo, "add", ".");
        runGit(gitRepo, "commit", "-m", "Add multiply method to Calculator");
        String targetSha = getHeadSha(gitRepo);

        DiffAnalysisResult result = diffAnalyzer.analyzeDiff(
                new DiffAnalysisRequest(gitRepo.toString(), baseSha, targetSha)
        );

        assertThat(result.totalFilesChanged()).isEqualTo(1);
        ChangedFileEntry entry = result.changedFiles().get(0);
        assertThat(entry.changeType()).isEqualTo(FileChangeType.MODIFIED);
        assertThat(entry.linesAdded()).isGreaterThan(0);

        // Verify line tracking
        assertThat(entry.hunks()).isNotEmpty();
        assertThat(entry.hunks().get(0).lines()).anyMatch(l -> l.type() == DiffLineType.ADDED && l.targetLineNumber() != null);

        // Verify heuristic method candidate identification
        assertThat(entry.candidateMethods())
                .extracting(MethodCandidateHint::candidateName)
                .contains("multiply");
    }

    @Test
    @DisplayName("Diff detects renamed files with old and new path preservation")
    void testFileRenames() throws Exception {
        Path src = Files.createDirectories(gitRepo.resolve("src/main/java"));
        Files.writeString(src.resolve("OldName.java"), "public class OldName {}\n");
        runGit(gitRepo, "add", ".");
        runGit(gitRepo, "commit", "-m", "Add OldName");
        String baseSha = getHeadSha(gitRepo);

        runGit(gitRepo, "mv", "src/main/java/OldName.java", "src/main/java/NewName.java");
        runGit(gitRepo, "commit", "-m", "Rename to NewName");
        String targetSha = getHeadSha(gitRepo);

        DiffAnalysisResult result = diffAnalyzer.analyzeDiff(
                new DiffAnalysisRequest(gitRepo.toString(), baseSha, targetSha)
        );

        assertThat(result.totalFilesChanged()).isEqualTo(1);
        ChangedFileEntry entry = result.changedFiles().get(0);
        assertThat(entry.changeType()).isEqualTo(FileChangeType.RENAMED);
        assertThat(entry.oldPath()).isEqualTo("src/main/java/OldName.java");
        assertThat(entry.newPath()).isEqualTo("src/main/java/NewName.java");
    }

    @Test
    @DisplayName("Empty diff when base and target revisions are identical")
    void testEmptyDiff() throws Exception {
        Files.writeString(gitRepo.resolve("file.txt"), "hello\n");
        runGit(gitRepo, "add", ".");
        runGit(gitRepo, "commit", "-m", "Commit 1");
        String sha = getHeadSha(gitRepo);

        DiffAnalysisResult result = diffAnalyzer.analyzeDiff(
                new DiffAnalysisRequest(gitRepo.toString(), sha, sha)
        );

        assertThat(result.isClean()).isTrue();
        assertThat(result.totalFilesChanged()).isEqualTo(0);
        assertThat(result.changedFiles()).isEmpty();
        assertThat(result.warnings()).contains("Base and target revisions are identical; zero changes detected");
    }

    @Test
    @DisplayName("Rejects missing or invalid revisions with ValidationException")
    void testMissingRevisions() throws Exception {
        Files.writeString(gitRepo.resolve("file.txt"), "hello\n");
        runGit(gitRepo, "add", ".");
        runGit(gitRepo, "commit", "-m", "Commit 1");
        String validSha = getHeadSha(gitRepo);

        assertThatThrownBy(() -> diffAnalyzer.analyzeDiff(
                new DiffAnalysisRequest(gitRepo.toString(), validSha, "invalid-revision-xyz")
        )).isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be resolved");

        assertThatThrownBy(() -> diffAnalyzer.analyzeDiff(
                new DiffAnalysisRequest(gitRepo.toString(), "nonexistent-base", validSha)
        )).isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be resolved");
    }

    @Test
    @DisplayName("Handles binary files and non-Java files gracefully")
    void testBinaryAndNonJavaFiles() throws Exception {
        Files.writeString(gitRepo.resolve("initial.txt"), "initial\n");
        runGit(gitRepo, "add", ".");
        runGit(gitRepo, "commit", "-m", "Initial");
        String baseSha = getHeadSha(gitRepo);

        // Add non-Java file
        Files.writeString(gitRepo.resolve("docs.md"), "# Documentation\n");

        // Add binary file with non-text bytes
        byte[] binaryBytes = new byte[]{0, 1, 2, 3, (byte) 0xFF, (byte) 0xFE, 0, 1};
        Files.write(gitRepo.resolve("asset.bin"), binaryBytes);

        runGit(gitRepo, "add", ".");
        runGit(gitRepo, "commit", "-m", "Add docs and binary");
        String targetSha = getHeadSha(gitRepo);

        DiffAnalysisResult result = diffAnalyzer.analyzeDiff(
                new DiffAnalysisRequest(gitRepo.toString(), baseSha, targetSha)
        );

        assertThat(result.totalFilesChanged()).isEqualTo(2);
        assertThat(result.totalJavaFilesChanged()).isEqualTo(0);

        ChangedFileEntry binEntry = result.changedFiles().stream()
                .filter(f -> f.newPath().endsWith(".bin"))
                .findFirst().orElseThrow();
        assertThat(binEntry.isBinary()).isTrue();
        assertThat(binEntry.isJavaFile()).isFalse();
        assertThat(binEntry.hunks()).isEmpty();

        ChangedFileEntry docEntry = result.changedFiles().stream()
                .filter(f -> f.newPath().endsWith(".md"))
                .findFirst().orElseThrow();
        assertThat(docEntry.isBinary()).isFalse();
        assertThat(docEntry.isJavaFile()).isFalse();
    }

    @Test
    @DisplayName("Rejects directories that are not Git repositories")
    void testNonGitRepository() throws IOException {
        Path nonGit = Files.createDirectory(tempDir.resolve("non-git-dir"));

        assertThatThrownBy(() -> diffAnalyzer.analyzeDiff(
                new DiffAnalysisRequest(nonGit.toString(), "HEAD~1", "HEAD")
        )).isInstanceOf(ValidationException.class)
                .hasMessageContaining("not a valid Git repository");
    }

    // Helper methods for Git fixture setup
    private void runGit(Path workingDir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(List.of(args));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workingDir.toFile());
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
        pb.environment().put("LC_ALL", "C");

        Process p = pb.start();
        boolean finished = p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new RuntimeException("Git command timed out: " + cmd);
        }
        if (p.exitValue() != 0) {
            String err = new String(p.getErrorStream().readAllBytes());
            throw new RuntimeException("Git command failed (" + p.exitValue() + "): " + cmd + "\n" + err);
        }
    }

    private String getHeadSha(Path workingDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "HEAD");
        pb.directory(workingDir.toFile());
        Process p = pb.start();
        String sha = new String(p.getInputStream().readAllBytes()).trim();
        p.waitFor();
        return sha;
    }
}
