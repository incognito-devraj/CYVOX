package com.cyvox.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class AppConfig {

    private static final String APPLICATION_NAME = "CYVOX";
    private static final List<Path> REQUIRED_DIRECTORIES = List.of(
            Path.of("runtime"),
            Path.of("ffmpeg"),
            Path.of("logs"),
            Path.of("reports"),
            Path.of("temp")
    );

    private AppConfig() {
    }

    public static String applicationName() {
        return APPLICATION_NAME;
    }

    public static List<Path> requiredDirectories() {
        return REQUIRED_DIRECTORIES;
    }

    public static void ensureWorkspaceDirectories() {
        for (Path directory : REQUIRED_DIRECTORIES) {
            try {
                Files.createDirectories(directory);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to create required directory: " + directory, exception);
            }
        }
    }
}
