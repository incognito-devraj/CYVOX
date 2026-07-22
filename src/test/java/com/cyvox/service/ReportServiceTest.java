package com.cyvox.service;

import com.cyvox.model.BatchCompressionResult;
import com.cyvox.model.CompressionResult;
import com.cyvox.model.CompressionStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReportServiceTest {

    private final ReportService reportService = new ReportService();

    @Test
    void shouldGenerateHtmlCsvAndJsonReports() {
        BatchCompressionResult batchCompressionResult = new BatchCompressionResult(List.of(
                new CompressionResult(
                        CompressionStatus.COMPLETED,
                        "source.mp4",
                        Path.of("compressed", "source_cyvox.mp4"),
                        1_000,
                        600,
                        Duration.ofSeconds(5),
                        "Compression completed"
                ),
                new CompressionResult(
                        CompressionStatus.FAILED,
                        "broken.mp4",
                        null,
                        500,
                        0,
                        Duration.ZERO,
                        "Failed to compress"
                )
        ), Duration.ofSeconds(6));

        List<Path> reports = reportService.generate(batchCompressionResult);

        assertEquals(3, reports.size());
        reports.forEach(path -> assertTrue(Files.isRegularFile(path), path::toString));
        assertTrue(reports.stream().anyMatch(path -> path.toString().endsWith(".html")));
        assertTrue(reports.stream().anyMatch(path -> path.toString().endsWith(".csv")));
        assertTrue(reports.stream().anyMatch(path -> path.toString().endsWith(".json")));
    }
}
