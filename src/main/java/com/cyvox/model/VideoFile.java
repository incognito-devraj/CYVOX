package com.cyvox.model;

import java.nio.file.Path;

public record VideoFile(
        Path path,
        String fileName,
        String extension,
        long sizeBytes
) {
}
