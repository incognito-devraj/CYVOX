package com.cyvox.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.List;
import java.util.Set;

public final class AppConfig {

    private static final String APPLICATION_NAME = "CYVOX";
    private static final Set<String> SUPPORTED_VIDEO_EXTENSIONS = Set.of(
            "mp4", "mkv", "mov", "avi", "wmv", "webm", "m4v",
            "flv", "mpeg", "mpg", "3gp", "ts", "ogv"
    );
    private static final Set<String> TEMPORARY_DIRECTORY_NAMES = Set.of(
            "temp", "tmp", "__macosx"
    );
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

    public static boolean isSupportedVideoExtension(String extension) {
        return SUPPORTED_VIDEO_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }

    public static boolean isTemporaryDirectoryName(String directoryName) {
        return TEMPORARY_DIRECTORY_NAMES.contains(directoryName.toLowerCase(Locale.ROOT));
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
