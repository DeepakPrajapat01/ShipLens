package com.aichangeguard.repository.api;

import com.aichangeguard.repository.diff.model.DiffAnalysisRequest;
import com.aichangeguard.repository.model.RepositoryRegistrationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RepositoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("POST /api/v1/repositories/inspect succeeds for valid repository")
    void inspectRepositoryEndpointSuccess() throws Exception {
        Path repo = Files.createDirectory(tempDir.resolve("test-repo"));
        Files.writeString(repo.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion><artifactId>my-app</artifactId></project>");
        Path src = Files.createDirectories(repo.resolve("src/main/java"));
        Files.writeString(src.resolve("App.java"), "public class App {}");

        RepositoryRegistrationRequest request = new RepositoryRegistrationRequest(
                "repo-api-1",
                repo.toString(),
                "API Test Repo"
        );

        mockMvc.perform(post("/api/v1/repositories/inspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.repositoryId").value("repo-api-1"))
                .andExpect(jsonPath("$.projectName").value("API Test Repo"))
                .andExpect(jsonPath("$.buildSystem").value("MAVEN"))
                .andExpect(jsonPath("$.inspectionStatus").value("VALID_JAVA_MAVEN"))
                .andExpect(jsonPath("$.totalJavaFiles").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/repositories/inspect returns 400 for nonexistent path")
    void inspectRepositoryEndpointFailsForInvalidPath() throws Exception {
        RepositoryRegistrationRequest request = new RepositoryRegistrationRequest(
                "repo-invalid",
                tempDir.resolve("nonexistent-dir").toString(),
                "Invalid Repo"
        );

        mockMvc.perform(post("/api/v1/repositories/inspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("does not exist")));
    }

    @Test
    @DisplayName("POST /api/v1/repositories/diff succeeds between two commits")
    void diffRepositoryEndpointSuccess() throws Exception {
        Path gitRepo = Files.createDirectories(tempDir.resolve("api-git-repo"));
        runGit(gitRepo, "init");
        runGit(gitRepo, "config", "user.name", "ChangeGuard Tester");
        runGit(gitRepo, "config", "user.email", "test@changeguard.local");
        runGit(gitRepo, "config", "commit.gpgsign", "false");

        Files.writeString(gitRepo.resolve("FileA.java"), "public class FileA {}");
        runGit(gitRepo, "add", ".");
        runGit(gitRepo, "commit", "-m", "Commit 1");

        Files.writeString(gitRepo.resolve("FileB.java"), "public class FileB {}");
        runGit(gitRepo, "add", ".");
        runGit(gitRepo, "commit", "-m", "Commit 2");

        DiffAnalysisRequest request = new DiffAnalysisRequest(
                gitRepo.toString(),
                "HEAD~1",
                "HEAD"
        );

        mockMvc.perform(post("/api/v1/repositories/diff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.totalFilesChanged").value(1))
                .andExpect(jsonPath("$.totalJavaFilesChanged").value(1))
                .andExpect(jsonPath("$.changedFiles[0].newPath").value("FileB.java"))
                .andExpect(jsonPath("$.changedFiles[0].changeType").value("ADDED"));
    }

    private void runGit(Path workingDir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(List.of(args));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workingDir.toFile());
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
        pb.environment().put("LC_ALL", "C");

        Process p = pb.start();
        p.waitFor();
    }
}
