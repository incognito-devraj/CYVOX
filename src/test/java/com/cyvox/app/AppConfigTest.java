package com.cyvox.app;

import com.cyvox.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class AppConfigTest {

    @Test
    void shouldCreateRequiredWorkspaceDirectories() {
        AppConfig.ensureWorkspaceDirectories();

        for (Path directory : AppConfig.requiredDirectories()) {
            assertTrue(Files.isDirectory(directory), () -> "Missing directory: " + directory);
        }
    }
}
