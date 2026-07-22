package com.cyvox.model;

import java.time.Duration;
import java.util.List;

public record BatchCompressionResult(
        List<CompressionResult> results,
        Duration elapsedTime
) {
    public long completedCount() {
        return results.stream().filter(result -> result.status() == CompressionStatus.COMPLETED).count();
    }

    public long skippedCount() {
        return results.stream().filter(result -> result.status() == CompressionStatus.SKIPPED).count();
    }

    public long failedCount() {
        return results.stream().filter(result -> result.status() == CompressionStatus.FAILED).count();
    }

    public long totalOriginalSizeBytes() {
        return results.stream().mapToLong(CompressionResult::originalSizeBytes).sum();
    }

    public long totalCompressedSizeBytes() {
        return results.stream().mapToLong(CompressionResult::compressedSizeBytes).sum();
    }

    public double overallSavingsRatio() {
        long original = totalOriginalSizeBytes();
        if (original <= 0) {
            return 0;
        }
        return (double) (original - totalCompressedSizeBytes()) / original;
    }
}
