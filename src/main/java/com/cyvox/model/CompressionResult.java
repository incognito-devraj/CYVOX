package com.cyvox.model;

import java.nio.file.Path;
import java.time.Duration;

public record CompressionResult(
        CompressionStatus status,
        Path outputFile,
        long originalSizeBytes,
        long compressedSizeBytes,
        Duration elapsedTime,
        String message
) {
    public double savingsRatio() {
        if (originalSizeBytes <= 0) {
            return 0;
        }
        return (double) (originalSizeBytes - compressedSizeBytes) / originalSizeBytes;
    }
}
