package com.cyvox.model;

import java.nio.file.Path;

public record VideoFile(
        Path path,
        String fileName,
        String extension,
        long sizeBytes,
        VideoMetadata metadata
) {
    public VideoFile withMetadata(VideoMetadata updatedMetadata) {
        return new VideoFile(path, fileName, extension, sizeBytes, updatedMetadata);
    }
}
