package com.aichangeguard.repository.service;

import com.aichangeguard.repository.model.BuildSystem;
import com.aichangeguard.repository.model.InspectionStatus;
import com.aichangeguard.repository.model.ProjectMetadata;
import com.aichangeguard.repository.model.RepositoryInspectionReport;
import com.aichangeguard.repository.model.RepositoryRegistrationRequest;
import com.aichangeguard.repository.model.SourceDirectoryInfo;
import com.aichangeguard.repository.security.RepositoryPathValidator;
import com.aichangeguard.repository.security.RepositoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service that inspects a local repository strictly in read-only mode.
 * Identifies build systems, Java source directories, file counts, and metadata.
 */
@Service
public class RepositoryInspectionService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryInspectionService.class);

    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            "target", "build", "out", ".git", ".idea", ".vscode",
            "node_modules", "dist", ".mvn", "bin", ".gradle"
    );

    private final RepositoryPathValidator pathValidator;
    private final MavenPomExtractor pomExtractor;
    private final RepositoryProperties properties;

    public RepositoryInspectionService(RepositoryPathValidator pathValidator,
                                       MavenPomExtractor pomExtractor,
                                       RepositoryProperties properties) {
        this.pathValidator = pathValidator;
        this.pomExtractor = pomExtractor;
        this.properties = properties;
    }

    /**
     * Ingests and inspects a local repository without modifying its contents.
     *
     * @param request Registration request containing repository ID and target path
     * @return Structured inspection report
     */
    public RepositoryInspectionReport inspect(RepositoryRegistrationRequest request) {
        Path repoPath = pathValidator.validate(request.repositoryPath());
        log.info("Inspecting repository '{}' at '{}'", request.repositoryId(), repoPath);

        List<String> warnings = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        List<String> projectStructure = scanTopLevelStructure(repoPath);

        // 1. Check for empty repository
        if (projectStructure.isEmpty()) {
            return new RepositoryInspectionReport(
                    request.repositoryId(),
                    repoPath.toString(),
                    request.displayName() != null ? request.displayName() : repoPath.getFileName().toString(),
                    BuildSystem.NONE,
                    InspectionStatus.EMPTY_REPOSITORY,
                    ProjectMetadata.empty(),
                    Collections.emptyList(),
                    0,
                    projectStructure,
                    Instant.now(),
                    List.of("Repository contains no files or directories"),
                    List.of("Empty repository cannot be analyzed")
            );
        }

        // 2. Build system detection (Maven pom.xml)
        Path rootPom = repoPath.resolve("pom.xml");
        boolean hasPom = Files.exists(rootPom) && Files.isRegularFile(rootPom);
        BuildSystem buildSystem = hasPom ? BuildSystem.MAVEN : BuildSystem.UNKNOWN;

        ProjectMetadata metadata = hasPom
                ? pomExtractor.extractMetadata(rootPom)
                : ProjectMetadata.empty();

        String projectName = request.displayName() != null ? request.displayName() :
                (metadata.name() != null ? metadata.name() : repoPath.getFileName().toString());

        // 3. Source directory and Java file discovery
        SourceDiscoveryResult discovery = discoverSources(repoPath);

        // 4. Determine inspection status
        InspectionStatus status;
        if (hasPom && discovery.totalJavaFiles() > 0) {
            status = InspectionStatus.VALID_JAVA_MAVEN;
        } else if (hasPom && discovery.totalJavaFiles() == 0) {
            status = InspectionStatus.MAVEN_NO_JAVA_SOURCES;
            warnings.add("pom.xml was detected, but no .java source files were discovered");
        } else if (!hasPom && discovery.totalJavaFiles() > 0) {
            status = InspectionStatus.JAVA_NO_BUILD_SYSTEM;
            warnings.add("Java source files discovered, but no root pom.xml found. Maven build system required for analysis.");
        } else {
            status = InspectionStatus.NON_JAVA_REPOSITORY;
            warnings.add("No Java source files or supported build descriptors were found");
        }

        if (buildSystem != BuildSystem.MAVEN) {
            limitations.add("Only Maven-based Java repositories are supported in the initial product scope");
        }

        if (status == InspectionStatus.VALID_JAVA_MAVEN) {
            boolean hasMain = discovery.sourceDirectories().stream().anyMatch(s -> "main".equals(s.scope()));
            boolean hasTest = discovery.sourceDirectories().stream().anyMatch(s -> "test".equals(s.scope()));
            if (!hasMain) {
                warnings.add("Standard 'src/main/java' source root not found");
            }
            if (!hasTest) {
                warnings.add("Standard 'src/test/java' test root not found; test impact analysis will be limited");
            }
        }

        return new RepositoryInspectionReport(
                request.repositoryId(),
                repoPath.toString(),
                projectName,
                buildSystem,
                status,
                metadata,
                discovery.sourceDirectories(),
                discovery.totalJavaFiles(),
                projectStructure,
                Instant.now(),
                limitations,
                warnings
        );
    }

    private List<String> scanTopLevelStructure(Path repoPath) {
        try (Stream<Path> stream = Files.list(repoPath)) {
            return stream.map(p -> {
                String name = p.getFileName().toString();
                return Files.isDirectory(p) ? name + "/" : name;
            }).sorted().collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("Failed to read top-level directory: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private record SourceDiscoveryResult(List<SourceDirectoryInfo> sourceDirectories, int totalJavaFiles) {}

    private SourceDiscoveryResult discoverSources(Path repoPath) {
        Map<Path, List<String>> dirToJavaFiles = new LinkedHashMap<>();
        int[] totalFiles = new int[]{0};

        try {
            Files.walkFileTree(repoPath, EnumSet.noneOf(FileVisitOption.class), properties.getMaxDirectoryDepth(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName().toString();
                    if (!dir.equals(repoPath) && IGNORED_DIRECTORIES.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (totalFiles[0] >= properties.getMaxFileInspectionCount()) {
                        log.warn("Max file inspection limit reached: {}", properties.getMaxFileInspectionCount());
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().endsWith(".java")) {
                        totalFiles[0]++;
                        Path parent = file.getParent();
                        dirToJavaFiles.computeIfAbsent(parent, k -> new ArrayList<>())
                                .add(repoPath.relativize(file).toString().replace('\\', '/'));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Error walking repository directory tree: {}", e.getMessage());
        }

        // Aggregate into logical source roots (e.g. src/main/java, src/test/java)
        Map<String, List<String>> sourceRootToFiles = new LinkedHashMap<>();
        for (Map.Entry<Path, List<String>> entry : dirToJavaFiles.entrySet()) {
            String relDir = repoPath.relativize(entry.getKey()).toString().replace('\\', '/');

            String logicalRoot = findLogicalSourceRoot(relDir);
            sourceRootToFiles.computeIfAbsent(logicalRoot, k -> new ArrayList<>())
                    .addAll(entry.getValue());
        }

        List<SourceDirectoryInfo> sourceDirectories = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : sourceRootToFiles.entrySet()) {
            String root = entry.getKey();
            List<String> files = entry.getValue();
            String scope = root.contains("test") ? "test" : (root.contains("main") ? "main" : "custom");
            List<String> samples = files.stream().limit(5).toList();
            sourceDirectories.add(new SourceDirectoryInfo(root, scope, files.size(), samples));
        }

        return new SourceDiscoveryResult(sourceDirectories, totalFiles[0]);
    }

    private String findLogicalSourceRoot(String relDir) {
        int mainIndex = relDir.indexOf("src/main/java");
        if (mainIndex != -1) {
            return relDir.substring(0, mainIndex + "src/main/java".length());
        }
        int testIndex = relDir.indexOf("src/test/java");
        if (testIndex != -1) {
            return relDir.substring(0, testIndex + "src/test/java".length());
        }
        int srcIndex = relDir.indexOf("src");
        if (srcIndex != -1) {
            return relDir.substring(0, srcIndex + "src".length());
        }
        return relDir.isEmpty() ? "." : relDir;
    }
}
