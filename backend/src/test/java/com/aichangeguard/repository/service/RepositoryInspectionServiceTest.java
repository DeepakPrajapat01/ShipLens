package com.aichangeguard.repository.service;

import com.aichangeguard.repository.model.BuildSystem;
import com.aichangeguard.repository.model.InspectionStatus;
import com.aichangeguard.repository.model.RepositoryInspectionReport;
import com.aichangeguard.repository.model.RepositoryRegistrationRequest;
import com.aichangeguard.repository.security.RepositoryPathValidator;
import com.aichangeguard.repository.security.RepositoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryInspectionServiceTest {

    @TempDir
    Path tempDir;

    private RepositoryInspectionService inspectionService;

    @BeforeEach
    void setUp() {
        RepositoryProperties properties = new RepositoryProperties();
        RepositoryPathValidator validator = new RepositoryPathValidator(properties);
        MavenPomExtractor pomExtractor = new MavenPomExtractor();
        inspectionService = new RepositoryInspectionService(validator, pomExtractor, properties);
    }

    @Test
    @DisplayName("Inspects a valid Maven project with standard source and test directories")
    void inspectsValidMavenProject() throws IOException {
        Path repo = Files.createDirectory(tempDir.resolve("valid-maven-app"));

        // Create pom.xml
        String pomXml = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.sample</groupId>
                    <artifactId>sample-service</artifactId>
                    <version>1.2.0</version>
                    <name>Sample Business Service</name>
                    <properties>
                        <java.version>21</java.version>
                    </properties>
                </project>
                """;
        Files.writeString(repo.resolve("pom.xml"), pomXml);

        // Create main and test Java files
        Path mainJava = Files.createDirectories(repo.resolve("src/main/java/com/sample"));
        Files.writeString(mainJava.resolve("Service.java"), "package com.sample; public class Service {}");
        Files.writeString(mainJava.resolve("Model.java"), "package com.sample; public class Model {}");

        Path testJava = Files.createDirectories(repo.resolve("src/test/java/com/sample"));
        Files.writeString(testJava.resolve("ServiceTest.java"), "package com.sample; public class ServiceTest {}");

        RepositoryRegistrationRequest request = new RepositoryRegistrationRequest(
                "repo-001",
                repo.toString(),
                "Sample Business Service"
        );

        RepositoryInspectionReport report = inspectionService.inspect(request);

        assertThat(report.repositoryId()).isEqualTo("repo-001");
        assertThat(report.projectName()).isEqualTo("Sample Business Service");
        assertThat(report.buildSystem()).isEqualTo(BuildSystem.MAVEN);
        assertThat(report.inspectionStatus()).isEqualTo(InspectionStatus.VALID_JAVA_MAVEN);
        assertThat(report.totalJavaFiles()).isEqualTo(3);
        assertThat(report.metadata().groupId()).isEqualTo("com.sample");
        assertThat(report.metadata().artifactId()).isEqualTo("sample-service");
        assertThat(report.metadata().version()).isEqualTo("1.2.0");
        assertThat(report.metadata().javaVersion()).isEqualTo("21");

        assertThat(report.sourceDirectories()).hasSize(2);
        assertThat(report.sourceDirectories())
                .extracting("relativePath")
                .containsExactlyInAnyOrder("src/main/java", "src/test/java");
    }

    @Test
    @DisplayName("Inspects an empty repository directory")
    void inspectsEmptyRepository() throws IOException {
        Path emptyRepo = Files.createDirectory(tempDir.resolve("empty-repo"));

        RepositoryRegistrationRequest request = new RepositoryRegistrationRequest(
                "repo-empty",
                emptyRepo.toString(),
                "Empty Repo"
        );

        RepositoryInspectionReport report = inspectionService.inspect(request);

        assertThat(report.buildSystem()).isEqualTo(BuildSystem.NONE);
        assertThat(report.inspectionStatus()).isEqualTo(InspectionStatus.EMPTY_REPOSITORY);
        assertThat(report.totalJavaFiles()).isEqualTo(0);
        assertThat(report.sourceDirectories()).isEmpty();
        assertThat(report.projectStructure()).isEmpty();
        assertThat(report.warnings()).isNotEmpty();
    }

    @Test
    @DisplayName("Inspects a non-Java repository (e.g. JavaScript / Markdown project)")
    void inspectsNonJavaRepository() throws IOException {
        Path jsRepo = Files.createDirectory(tempDir.resolve("node-app"));
        Files.writeString(jsRepo.resolve("package.json"), "{\"name\":\"node-app\",\"version\":\"1.0.0\"}");
        Files.writeString(jsRepo.resolve("index.js"), "console.log('hello');");
        Files.writeString(jsRepo.resolve("README.md"), "# Node App");

        RepositoryRegistrationRequest request = new RepositoryRegistrationRequest(
                "repo-js",
                jsRepo.toString(),
                "Node App"
        );

        RepositoryInspectionReport report = inspectionService.inspect(request);

        assertThat(report.buildSystem()).isEqualTo(BuildSystem.UNKNOWN);
        assertThat(report.inspectionStatus()).isEqualTo(InspectionStatus.NON_JAVA_REPOSITORY);
        assertThat(report.totalJavaFiles()).isEqualTo(0);
        assertThat(report.sourceDirectories()).isEmpty();
        assertThat(report.projectStructure()).containsExactlyInAnyOrder("README.md", "index.js", "package.json");
        assertThat(report.warnings()).contains("No Java source files or supported build descriptors were found");
    }

    @Test
    @DisplayName("Inspects repository containing Java files but missing pom.xml build descriptor")
    void inspectsJavaRepoWithoutPom() throws IOException {
        Path javaRepo = Files.createDirectory(tempDir.resolve("java-no-pom"));
        Path src = Files.createDirectories(javaRepo.resolve("src/com/foo"));
        Files.writeString(src.resolve("Main.java"), "package com.foo; public class Main {}");

        RepositoryRegistrationRequest request = new RepositoryRegistrationRequest(
                "repo-no-pom",
                javaRepo.toString(),
                "Java No POM"
        );

        RepositoryInspectionReport report = inspectionService.inspect(request);

        assertThat(report.buildSystem()).isEqualTo(BuildSystem.UNKNOWN);
        assertThat(report.inspectionStatus()).isEqualTo(InspectionStatus.JAVA_NO_BUILD_SYSTEM);
        assertThat(report.totalJavaFiles()).isEqualTo(1);
        assertThat(report.warnings()).anyMatch(w -> w.contains("no root pom.xml found"));
    }

    @Test
    @DisplayName("Inspects repository with pom.xml but 0 Java source files")
    void inspectsMavenRepoWithoutJavaSources() throws IOException {
        Path mavenEmpty = Files.createDirectory(tempDir.resolve("maven-no-sources"));
        Files.writeString(mavenEmpty.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.sample</groupId>
                    <artifactId>parent-pom</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                </project>
                """);

        RepositoryRegistrationRequest request = new RepositoryRegistrationRequest(
                "repo-pom-only",
                mavenEmpty.toString(),
                "Parent POM"
        );

        RepositoryInspectionReport report = inspectionService.inspect(request);

        assertThat(report.buildSystem()).isEqualTo(BuildSystem.MAVEN);
        assertThat(report.inspectionStatus()).isEqualTo(InspectionStatus.MAVEN_NO_JAVA_SOURCES);
        assertThat(report.totalJavaFiles()).isEqualTo(0);
        assertThat(report.warnings()).anyMatch(w -> w.contains("no .java source files were discovered"));
    }

    @Test
    @DisplayName("Inspects a multi-module repository with nested source directories")
    void inspectsNestedMultiModuleRepository() throws IOException {
        Path multiRepo = Files.createDirectory(tempDir.resolve("multi-module-app"));
        Files.writeString(multiRepo.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.sample</groupId>
                    <artifactId>multi-parent</artifactId>
                    <version>2.0.0</version>
                </project>
                """);

        // Module A
        Path moduleASrc = Files.createDirectories(multiRepo.resolve("module-core/src/main/java/com/sample/core"));
        Files.writeString(moduleASrc.resolve("CoreUtil.java"), "public class CoreUtil {}");

        // Module B
        Path moduleBSrc = Files.createDirectories(multiRepo.resolve("module-web/src/main/java/com/sample/web"));
        Files.writeString(moduleBSrc.resolve("Controller.java"), "public class Controller {}");

        Path moduleBTest = Files.createDirectories(multiRepo.resolve("module-web/src/test/java/com/sample/web"));
        Files.writeString(moduleBTest.resolve("ControllerTest.java"), "public class ControllerTest {}");

        RepositoryRegistrationRequest request = new RepositoryRegistrationRequest(
                "repo-multi",
                multiRepo.toString(),
                "Multi Module App"
        );

        RepositoryInspectionReport report = inspectionService.inspect(request);

        assertThat(report.buildSystem()).isEqualTo(BuildSystem.MAVEN);
        assertThat(report.inspectionStatus()).isEqualTo(InspectionStatus.VALID_JAVA_MAVEN);
        assertThat(report.totalJavaFiles()).isEqualTo(3);

        assertThat(report.sourceDirectories())
                .extracting("relativePath")
                .containsExactlyInAnyOrder(
                        "module-core/src/main/java",
                        "module-web/src/main/java",
                        "module-web/src/test/java"
                );
    }

    @Test
    @DisplayName("Strict Read-Only Guarantee: Repository contents remain identical before and after inspection")
    void guaranteesRepositoryIsNotModified() throws IOException {
        Path repo = Files.createDirectory(tempDir.resolve("read-only-repo"));
        Files.writeString(repo.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion><artifactId>app</artifactId></project>");
        Path src = Files.createDirectories(repo.resolve("src/main/java"));
        Files.writeString(src.resolve("App.java"), "public class App {}");

        long initialFileCount;
        try (Stream<Path> stream = Files.walk(repo)) {
            initialFileCount = stream.count();
        }

        RepositoryRegistrationRequest request = new RepositoryRegistrationRequest("ro-repo", repo.toString(), "RO App");
        inspectionService.inspect(request);

        long postInspectionFileCount;
        try (Stream<Path> stream = Files.walk(repo)) {
            postInspectionFileCount = stream.count();
        }

        assertThat(postInspectionFileCount).isEqualTo(initialFileCount);
    }
}
