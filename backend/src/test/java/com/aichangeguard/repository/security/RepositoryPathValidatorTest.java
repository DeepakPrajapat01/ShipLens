package com.aichangeguard.repository.security;

import com.aichangeguard.common.error.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryPathValidatorTest {

    @TempDir
    Path tempDir;

    private RepositoryProperties properties;
    private RepositoryPathValidator validator;

    @BeforeEach
    void setUp() {
        properties = new RepositoryProperties();
        validator = new RepositoryPathValidator(properties);
    }

    @Test
    @DisplayName("Validates existing directory successfully when no whitelist is configured")
    void validatesExistingDirectoryWithoutWhitelist() throws IOException {
        Path repoDir = Files.createDirectory(tempDir.resolve("valid-repo"));
        Path validated = validator.validate(repoDir.toString());

        assertThat(validated).isEqualTo(repoDir.toRealPath());
    }

    @Test
    @DisplayName("Rejects null or blank path strings")
    void rejectsNullOrBlankPath() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be null or empty");

        assertThatThrownBy(() -> validator.validate("   "))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    @DisplayName("Rejects nonexistent repository path")
    void rejectsNonexistentPath() {
        Path nonexistent = tempDir.resolve("does-not-exist");
        assertThatThrownBy(() -> validator.validate(nonexistent.toString()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    @DisplayName("Rejects path that is a regular file instead of a directory")
    void rejectsRegularFile() throws IOException {
        Path file = Files.createFile(tempDir.resolve("some-file.txt"));
        assertThatThrownBy(() -> validator.validate(file.toString()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must be a directory");
    }

    @Test
    @DisplayName("Accepts path located inside configured allowed base directories")
    void acceptsPathInsideAllowedBase() throws IOException {
        Path allowedBase = Files.createDirectory(tempDir.resolve("allowed-root"));
        Path repoDir = Files.createDirectory(allowedBase.resolve("my-repo"));

        properties.setAllowedBaseDirectories(List.of(allowedBase.toString()));

        Path validated = validator.validate(repoDir.toString());
        assertThat(validated).isEqualTo(repoDir.toRealPath());
    }

    @Test
    @DisplayName("Rejects path located outside configured allowed base directories")
    void rejectsPathOutsideAllowedBase() throws IOException {
        Path allowedBase = Files.createDirectory(tempDir.resolve("allowed-root"));
        Path outsideDir = Files.createDirectory(tempDir.resolve("unauthorized-dir"));

        properties.setAllowedBaseDirectories(List.of(allowedBase.toString()));

        assertThatThrownBy(() -> validator.validate(outsideDir.toString()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("outside the permitted directories");
    }

    @Test
    @DisplayName("Prevents path traversal attempts that escape allowed base directory")
    void preventsPathTraversal() throws IOException {
        Path allowedBase = Files.createDirectory(tempDir.resolve("allowed-base"));
        Path subDir = Files.createDirectory(allowedBase.resolve("sub"));
        Path unauthorizedDir = Files.createDirectory(tempDir.resolve("secret-target"));

        properties.setAllowedBaseDirectories(List.of(allowedBase.toString()));

        // Traversal path: allowed-base/sub/../../secret-target
        String traversalPath = subDir.resolve("../..").resolve("secret-target").toString();

        assertThatThrownBy(() -> validator.validate(traversalPath))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("outside the permitted directories");
    }
}
