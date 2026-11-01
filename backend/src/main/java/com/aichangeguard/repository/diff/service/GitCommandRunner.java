package com.aichangeguard.repository.diff.service;

import com.aichangeguard.common.error.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Executes read-only Git commands using ProcessBuilder with discrete arguments
 * to prevent command injection and ensure repository isolation.
 */
@Component
public class GitCommandRunner {

    private static final Logger log = LoggerFactory.getLogger(GitCommandRunner.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /**
     * Checks if the directory is a valid Git work tree.
     */
    public boolean isGitRepository(Path repoPath) {
        CommandResult result = runGit(repoPath, List.of("rev-parse", "--is-inside-work-tree"));
        return result.exitCode() == 0 && "true".equalsIgnoreCase(result.stdout().trim());
    }

    /**
     * Resolves an arbitrary revision (branch, tag, short SHA, HEAD~n) to a full 40-character commit SHA.
     */
    public String resolveCommitSha(Path repoPath, String revision) {
        if (revision == null || revision.isBlank()) {
            throw new ValidationException("Git revision cannot be null or empty");
        }

        CommandResult result = runGit(repoPath, List.of("rev-parse", "--verify", revision + "^{commit}"));
        if (result.exitCode() != 0) {
            log.warn("Failed to resolve Git revision '{}' in {}: {}", revision, repoPath, result.stderr());
            throw new ValidationException("Git revision '%s' cannot be resolved".formatted(revision),
                    Map.of("revision", revision, "gitError", result.stderr().trim()));
        }

        String sha = result.stdout().trim();
        if (sha.length() != 40) {
            throw new ValidationException("Resolved Git revision is not a valid 40-char SHA: " + sha);
        }
        return sha;
    }

    /**
     * Determines whether the given commit SHA represents a merge commit.
     */
    public boolean isMergeCommit(Path repoPath, String commitSha) {
        CommandResult result = runGit(repoPath, List.of("rev-list", "--parents", "-n", "1", commitSha));
        if (result.exitCode() == 0) {
            String[] tokens = result.stdout().trim().split("\\s+");
            return tokens.length > 2; // commit SHA + 2 or more parent SHAs
        }
        return false;
    }

    /**
     * Returns name-status lines with rename detection (-M).
     */
    public String getNameStatusDiff(Path repoPath, String baseSha, String targetSha) {
        CommandResult result = runGit(repoPath, List.of("diff", "--name-status", "-M", baseSha, targetSha));
        if (result.exitCode() != 0) {
            throw new ValidationException("Failed to extract Git name-status diff: " + result.stderr().trim());
        }
        return result.stdout();
    }

    /**
     * Returns unified diff text with rename detection and 3 lines of context.
     */
    public String getUnifiedDiff(Path repoPath, String baseSha, String targetSha) {
        CommandResult result = runGit(repoPath, List.of("diff", "-M", "--no-color", "-U3", baseSha, targetSha));
        if (result.exitCode() != 0) {
            throw new ValidationException("Failed to extract Git unified diff: " + result.stderr().trim());
        }
        return result.stdout();
    }

    public record CommandResult(int exitCode, String stdout, String stderr) {}

    private CommandResult runGit(Path workingDir, List<String> gitArgs) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(workingDir.toAbsolutePath().toString());
        command.addAll(gitArgs);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
        pb.environment().put("LC_ALL", "C");

        try {
            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread outThread = new Thread(() -> readStream(process.getInputStream(), stdout));
            Thread errThread = new Thread(() -> readStream(process.getErrorStream(), stderr));

            outThread.start();
            errThread.start();

            boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ValidationException("Git command timed out after %d seconds".formatted(DEFAULT_TIMEOUT_SECONDS));
            }

            outThread.join(2000);
            errThread.join(2000);

            return new CommandResult(process.exitValue(), stdout.toString(), stderr.toString());
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute Git command {}: {}", command, e.getMessage());
            throw new ValidationException("Git command execution failure: " + e.getMessage());
        }
    }

    private void readStream(java.io.InputStream is, StringBuilder sb) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (Exception ignored) {
        }
    }
}
