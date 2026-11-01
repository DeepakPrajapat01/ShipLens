package com.aichangeguard.repository.diff.service;

import com.aichangeguard.repository.diff.model.ChangedFileEntry;
import com.aichangeguard.repository.diff.model.DiffHunk;
import com.aichangeguard.repository.diff.model.DiffLine;
import com.aichangeguard.repository.diff.model.DiffLineType;
import com.aichangeguard.repository.diff.model.FileChangeType;
import com.aichangeguard.repository.diff.model.MethodCandidateHint;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Git diff output (unified diff and name-status) into structured models,
 * tracking line numbers, line counts, and basic changed-method candidates.
 */
@Component
public class GitDiffParser {

    private static final Pattern HUNK_HEADER_PATTERN = Pattern.compile(
            "^@@\\s+-(\\d+)(?:,(\\d+))?\\s+\\+(\\d+)(?:,(\\d+))?\\s+@@(?:\\s*(.*))?$"
    );

    // Heuristic regex to locate Java method declaration signatures in diff lines
    private static final Pattern JAVA_METHOD_SIGNATURE_PATTERN = Pattern.compile(
            "(?:public|protected|private|static|final|synchronized|abstract|default|native|\\s)+\\s+[\\w<>\\[\\],\\s]+\\s+(\\b[a-zA-Z_][a-zA-Z0-9_]*\\b)\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w,\\s]+)?\\s*\\{?"
    );

    /**
     * Parses name-status and unified diff text into a list of structured ChangedFileEntry records.
     */
    public List<ChangedFileEntry> parseDiff(String nameStatusOutput, String unifiedDiffOutput) {
        Map<String, FileStatusInfo> fileStatusMap = parseNameStatus(nameStatusOutput);
        Map<String, ParsedFileDiff> parsedDiffs = parseUnifiedDiff(unifiedDiffOutput);

        List<ChangedFileEntry> entries = new ArrayList<>();

        for (Map.Entry<String, FileStatusInfo> entry : fileStatusMap.entrySet()) {
            String pathKey = entry.getKey();
            FileStatusInfo statusInfo = entry.getValue();

            ParsedFileDiff diff = parsedDiffs.get(pathKey);
            if (diff == null && statusInfo.newPath != null) {
                diff = parsedDiffs.get(statusInfo.newPath);
            }
            if (diff == null && statusInfo.oldPath != null) {
                diff = parsedDiffs.get(statusInfo.oldPath);
            }

            boolean isJava = (statusInfo.newPath != null && statusInfo.newPath.endsWith(".java"))
                    || (statusInfo.oldPath != null && statusInfo.oldPath.endsWith(".java"));

            boolean isBinary = diff != null && diff.isBinary;
            List<DiffHunk> hunks = diff != null ? diff.hunks : Collections.emptyList();
            int linesAdded = diff != null ? diff.linesAdded : 0;
            int linesDeleted = diff != null ? diff.linesDeleted : 0;

            List<MethodCandidateHint> methodCandidates = isJava
                    ? extractMethodCandidates(hunks)
                    : Collections.emptyList();

            entries.add(new ChangedFileEntry(
                    statusInfo.changeType,
                    statusInfo.oldPath,
                    statusInfo.newPath,
                    isJava,
                    isBinary,
                    linesAdded,
                    linesDeleted,
                    hunks,
                    methodCandidates
            ));
        }

        return entries;
    }

    private record FileStatusInfo(FileChangeType changeType, String oldPath, String newPath) {}

    private Map<String, FileStatusInfo> parseNameStatus(String output) {
        Map<String, FileStatusInfo> map = new HashMap<>();
        if (output == null || output.isBlank()) {
            return map;
        }

        String[] lines = output.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            String[] tokens = line.split("\\t+");
            if (tokens.length < 2) {
                continue;
            }

            String code = tokens[0].trim();
            if (code.startsWith("R")) { // Renamed
                String oldPath = tokens[1].trim();
                String newPath = tokens.length > 2 ? tokens[2].trim() : oldPath;
                map.put(newPath, new FileStatusInfo(FileChangeType.RENAMED, oldPath, newPath));
            } else if (code.startsWith("C")) { // Copied
                String oldPath = tokens[1].trim();
                String newPath = tokens.length > 2 ? tokens[2].trim() : oldPath;
                map.put(newPath, new FileStatusInfo(FileChangeType.COPIED, oldPath, newPath));
            } else if ("A".equals(code)) { // Added
                String path = tokens[1].trim();
                map.put(path, new FileStatusInfo(FileChangeType.ADDED, null, path));
            } else if ("D".equals(code)) { // Deleted
                String path = tokens[1].trim();
                map.put(path, new FileStatusInfo(FileChangeType.DELETED, path, null));
            } else if ("M".equals(code)) { // Modified
                String path = tokens[1].trim();
                map.put(path, new FileStatusInfo(FileChangeType.MODIFIED, path, path));
            } else if ("T".equals(code)) { // Type changed
                String path = tokens[1].trim();
                map.put(path, new FileStatusInfo(FileChangeType.TYPE_CHANGED, path, path));
            } else {
                String path = tokens[1].trim();
                map.put(path, new FileStatusInfo(FileChangeType.UNKNOWN, path, path));
            }
        }
        return map;
    }

    private record ParsedFileDiff(boolean isBinary, int linesAdded, int linesDeleted, List<DiffHunk> hunks) {}

    private Map<String, ParsedFileDiff> parseUnifiedDiff(String output) {
        Map<String, ParsedFileDiff> diffMap = new HashMap<>();
        if (output == null || output.isBlank()) {
            return diffMap;
        }

        String[] lines = output.split("\\r?\\n");
        int i = 0;

        while (i < lines.length) {
            String line = lines[i];
            if (line.startsWith("diff --git ")) {
                // Header format: diff --git a/path/to/file b/path/to/file
                String targetPath = extractTargetPath(line);
                i++;

                boolean isBinary = false;
                List<DiffHunk> hunks = new ArrayList<>();
                int totalAdded = 0;
                int totalDeleted = 0;

                while (i < lines.length && !lines[i].startsWith("diff --git ")) {
                    String current = lines[i];
                    if (current.startsWith("Binary files ") && current.contains("differ")) {
                        isBinary = true;
                        i++;
                        break;
                    }

                    if (current.startsWith("@@ ")) {
                        Matcher matcher = HUNK_HEADER_PATTERN.matcher(current);
                        if (matcher.matches()) {
                            int baseStart = Integer.parseInt(matcher.group(1));
                            int baseCount = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 1;
                            int targetStart = Integer.parseInt(matcher.group(3));
                            int targetCount = matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : 1;
                            String contextHeader = matcher.group(5) != null ? matcher.group(5).trim() : "";

                            List<DiffLine> hunkLines = new ArrayList<>();
                            int currentBaseLine = baseStart;
                            int currentTargetLine = targetStart;
                            i++;

                            while (i < lines.length && !lines[i].startsWith("@@ ") && !lines[i].startsWith("diff --git ")) {
                                String hunkLine = lines[i];
                                if (hunkLine.startsWith("+")) {
                                    totalAdded++;
                                    hunkLines.add(new DiffLine(DiffLineType.ADDED, hunkLine.substring(1), null, currentTargetLine++));
                                } else if (hunkLine.startsWith("-")) {
                                    totalDeleted++;
                                    hunkLines.add(new DiffLine(DiffLineType.DELETED, hunkLine.substring(1), currentBaseLine++, null));
                                } else if (hunkLine.startsWith(" ")) {
                                    hunkLines.add(new DiffLine(DiffLineType.CONTEXT, hunkLine.substring(1), currentBaseLine++, currentTargetLine++));
                                }
                                i++;
                            }

                            hunks.add(new DiffHunk(current, contextHeader, baseStart, baseCount, targetStart, targetCount, hunkLines));
                            continue;
                        }
                    }
                    i++;
                }

                diffMap.put(targetPath, new ParsedFileDiff(isBinary, totalAdded, totalDeleted, hunks));
            } else {
                i++;
            }
        }

        return diffMap;
    }

    private String extractTargetPath(String diffGitLine) {
        // e.g. diff --git a/src/App.java b/src/App.java
        int bIndex = diffGitLine.lastIndexOf(" b/");
        if (bIndex != -1) {
            return diffGitLine.substring(bIndex + 3).trim();
        }
        return diffGitLine;
    }

    private List<MethodCandidateHint> extractMethodCandidates(List<DiffHunk> hunks) {
        List<MethodCandidateHint> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (DiffHunk hunk : hunks) {
            // 1. Check @@ context header (frequently contains the enclosing method/class signature in Git)
            if (hunk.contextHeader() != null && !hunk.contextHeader().isBlank()) {
                String ctx = hunk.contextHeader();
                Matcher m = JAVA_METHOD_SIGNATURE_PATTERN.matcher(ctx);
                if (m.find()) {
                    String methodName = m.group(1);
                    if (seen.add(methodName + ":" + hunk.targetStartLine())) {
                        candidates.add(new MethodCandidateHint(methodName, ctx, hunk.targetStartLine(), "hunk-context-header"));
                    }
                } else if (ctx.contains("(") && ctx.contains(")")) {
                    // Simpler function signature fallback
                    int parenIdx = ctx.indexOf('(');
                    String beforeParen = ctx.substring(0, parenIdx).trim();
                    int lastSpace = beforeParen.lastIndexOf(' ');
                    String candidate = lastSpace != -1 ? beforeParen.substring(lastSpace + 1) : beforeParen;
                    if (!candidate.isBlank() && !candidate.equals("class") && !candidate.equals("if") && seen.add(candidate + ":" + hunk.targetStartLine())) {
                        candidates.add(new MethodCandidateHint(candidate, ctx, hunk.targetStartLine(), "hunk-context-header"));
                    }
                }
            }

            // 2. Scan added/modified lines in hunk for method declaration lines
            for (DiffLine line : hunk.lines()) {
                if (line.type() == DiffLineType.ADDED) {
                    Matcher m = JAVA_METHOD_SIGNATURE_PATTERN.matcher(line.content());
                    if (m.find()) {
                        String methodName = m.group(1);
                        int lineNum = line.targetLineNumber() != null ? line.targetLineNumber() : hunk.targetStartLine();
                        if (seen.add(methodName + ":" + lineNum)) {
                            candidates.add(new MethodCandidateHint(methodName, line.content().trim(), lineNum, "added-line-signature"));
                        }
                    }
                }
            }
        }

        return candidates;
    }
}
