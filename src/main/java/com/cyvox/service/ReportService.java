package com.cyvox.service;

import com.cyvox.model.BatchCompressionResult;
import com.cyvox.model.CompressionResult;
import com.cyvox.util.FileSizeFormatter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportService.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public List<Path> generate(BatchCompressionResult batchCompressionResult) {
        try {
            Files.createDirectories(Path.of("reports"));
            String reportName = "cyvox-report-" + FILE_TIMESTAMP.format(LocalDateTime.now());
            Path csvPath = Path.of("reports", reportName + ".csv");
            Path jsonPath = Path.of("reports", reportName + ".json");
            Path htmlPath = Path.of("reports", reportName + ".html");

            Files.writeString(csvPath, toCsv(batchCompressionResult), StandardCharsets.UTF_8);
            Files.writeString(jsonPath, GSON.toJson(toJsonReport(batchCompressionResult)), StandardCharsets.UTF_8);
            Files.writeString(htmlPath, toHtml(batchCompressionResult), StandardCharsets.UTF_8);
            LOGGER.info("Generated compression reports: {}, {}, {}", htmlPath, csvPath, jsonPath);

            return List.of(htmlPath, csvPath, jsonPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate compression reports", exception);
        }
    }

    private String toCsv(BatchCompressionResult batchCompressionResult) {
        StringBuilder csv = new StringBuilder("Source,Output,Original Size,Compressed Size,Saved Size,Compression Ratio,Time,Status,Message\n");
        for (CompressionResult result : batchCompressionResult.results()) {
            csv.append(csvEscape(result.sourceFileName())).append(',')
                    .append(csvEscape(result.outputFile() == null ? "" : result.outputFile().toString())).append(',')
                    .append(result.originalSizeBytes()).append(',')
                    .append(result.compressedSizeBytes()).append(',')
                    .append(result.originalSizeBytes() - result.compressedSizeBytes()).append(',')
                    .append(String.format(Locale.ROOT, "%.4f", result.savingsRatio())).append(',')
                    .append(result.elapsedTime()).append(',')
                    .append(result.status()).append(',')
                    .append(csvEscape(result.message())).append('\n');
        }
        return csv.toString();
    }

    private String toHtml(BatchCompressionResult batchCompressionResult) {
        StringBuilder html = new StringBuilder("""
                <!doctype html>
                <html lang="en">
                <head>
                    <meta charset="utf-8">
                    <title>CYVOX Compression Report</title>
                    <style>
                        body { font-family: Segoe UI, sans-serif; margin: 32px; color: #172033; }
                        table { border-collapse: collapse; width: 100%; }
                        th, td { border: 1px solid #d9e1ea; padding: 8px; text-align: left; }
                        th { background: #eef4f9; }
                    </style>
                </head>
                <body>
                <h1>CYVOX Compression Report</h1>
                """);
        html.append("<p>Completed: ").append(batchCompressionResult.completedCount())
                .append(" | Skipped: ").append(batchCompressionResult.skippedCount())
                .append(" | Failed: ").append(batchCompressionResult.failedCount())
                .append(" | Saved: ").append(FileSizeFormatter.format(batchCompressionResult.totalOriginalSizeBytes() - batchCompressionResult.totalCompressedSizeBytes()))
                .append("</p><table><thead><tr>")
                .append("<th>Source</th><th>Output</th><th>Original</th><th>Compressed</th><th>Ratio</th><th>Status</th>")
                .append("</tr></thead><tbody>");
        for (CompressionResult result : batchCompressionResult.results()) {
            html.append("<tr><td>").append(escapeHtml(result.sourceFileName()))
                    .append("</td><td>").append(escapeHtml(result.outputFile() == null ? "" : result.outputFile().toString()))
                    .append("</td><td>").append(FileSizeFormatter.format(result.originalSizeBytes()))
                    .append("</td><td>").append(FileSizeFormatter.format(result.compressedSizeBytes()))
                    .append("</td><td>").append(String.format(Locale.ROOT, "%.0f%%", result.savingsRatio() * 100))
                    .append("</td><td>").append(result.status())
                    .append("</td></tr>");
        }
        html.append("</tbody></table></body></html>");
        return html.toString();
    }

    private Map<String, Object> toJsonReport(BatchCompressionResult batchCompressionResult) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("elapsedSeconds", batchCompressionResult.elapsedTime().toSeconds());
        report.put("completedCount", batchCompressionResult.completedCount());
        report.put("skippedCount", batchCompressionResult.skippedCount());
        report.put("failedCount", batchCompressionResult.failedCount());
        report.put("totalOriginalSizeBytes", batchCompressionResult.totalOriginalSizeBytes());
        report.put("totalCompressedSizeBytes", batchCompressionResult.totalCompressedSizeBytes());
        report.put("overallSavingsRatio", batchCompressionResult.overallSavingsRatio());
        report.put("results", batchCompressionResult.results().stream()
                .map(this::toJsonResult)
                .toList());
        return report;
    }

    private Map<String, Object> toJsonResult(CompressionResult result) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("status", result.status().name());
        entry.put("sourceFileName", result.sourceFileName());
        entry.put("outputFile", result.outputFile() == null ? "" : result.outputFile().toString());
        entry.put("originalSizeBytes", result.originalSizeBytes());
        entry.put("compressedSizeBytes", result.compressedSizeBytes());
        entry.put("elapsedSeconds", result.elapsedTime().toSeconds());
        entry.put("savingsRatio", result.savingsRatio());
        entry.put("message", result.message());
        return entry;
    }

    private String csvEscape(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
