package com.cyvox.model;

import java.nio.file.Path;

public record CompressionRequest(
        VideoFile inputFile,
        Path outputDirectory,
        CompressionPreset preset,
        String filenamePattern,
        boolean overwriteExisting,
        boolean skipExisting,
        boolean keepMetadata
) {
}
