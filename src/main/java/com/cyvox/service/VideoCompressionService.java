package com.cyvox.service;

import com.cyvox.exception.FfmpegException;
import com.cyvox.model.CompressionRequest;
import com.cyvox.model.CompressionResult;
import com.cyvox.model.CompressionStatus;
import com.cyvox.model.VideoFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class VideoCompressionService {

    private final FfmpegResolver ffmpegResolver;

    public VideoCompressionService(FfmpegResolver ffmpegResolver) {
        this.ffmpegResolver = ffmpegResolver;
    }

    public CompressionResult compress(CompressionRequest request, CompressionProgressListener progressListener) throws IOException {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(progressListener, "progressListener must not be null");

        Files.createDirectories(request.outputDirectory());
        VideoFile inputFile = request.inputFile();
        Path outputFile = resolveOutputPath(request);
        if (Files.exists(outputFile)) {
            if (request.skipExisting()) {
                return new CompressionResult(
                        CompressionStatus.SKIPPED,
                        outputFile,
                        inputFile.sizeBytes(),
                        Files.size(outputFile),
                        Duration.ZERO,
                        "Skipped existing output file."
                );
            }
            if (!request.overwriteExisting()) {
                throw new FfmpegException("Output file already exists: " + outputFile.getFileName());
            }
        }

        List<String> command = buildCommand(request, outputFile);
        Instant start = Instant.now();
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            double durationSeconds = inputFile.metadata() == null ? 0 : inputFile.metadata().durationSeconds();
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("out_time_ms=")) {
                    long outTimeMicro = parseLong(line.substring("out_time_ms=".length()));
                    double progress = durationSeconds <= 0 ? -1 : Math.min(1.0, outTimeMicro / (durationSeconds * 1_000_000.0));
                    progressListener.onProgress(progress, "Encoding " + inputFile.fileName(), outTimeMicro);
                } else if (line.startsWith("out_time_us=")) {
                    long outTimeMicro = parseLong(line.substring("out_time_us=".length()));
                    double progress = durationSeconds <= 0 ? -1 : Math.min(1.0, outTimeMicro / (durationSeconds * 1_000_000.0));
                    progressListener.onProgress(progress, "Encoding " + inputFile.fileName(), outTimeMicro);
                } else if (line.startsWith("progress=end")) {
                    progressListener.onProgress(1.0, "Compression complete", durationSeconds <= 0 ? 0 : (long) (durationSeconds * 1_000_000.0));
                }
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new FfmpegException("ffmpeg exited with code " + exitCode + " for " + inputFile.fileName());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for ffmpeg", exception);
        }

        long compressedSize = Files.size(outputFile);
        return new CompressionResult(
                CompressionStatus.COMPLETED,
                outputFile,
                inputFile.sizeBytes(),
                compressedSize,
                Duration.between(start, Instant.now()),
                "Compression completed successfully."
        );
    }

    private Path resolveOutputPath(CompressionRequest request) {
        String originalFileName = request.inputFile().fileName();
        int dotIndex = originalFileName.lastIndexOf('.');
        String baseName = dotIndex < 0 ? originalFileName : originalFileName.substring(0, dotIndex);
        String pattern = request.filenamePattern().isBlank() ? "{name}_cyvox" : request.filenamePattern();
        String resolvedName = pattern.replace("{name}", baseName)
                .replace("{ext}", request.inputFile().extension());
        return request.outputDirectory().resolve(resolvedName + ".mp4");
    }

    private List<String> buildCommand(CompressionRequest request, Path outputFile) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegResolver.resolve().toString());
        command.add("-y");
        command.add("-i");
        command.add(request.inputFile().path().toString());
        command.add("-c:v");
        command.add(request.preset().videoEncoder());
        command.add("-crf");
        command.add(request.preset().crf());
        command.add("-preset");
        command.add(request.preset().presetValue());
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add(request.preset().audioBitrate());
        command.add("-movflags");
        command.add("+faststart");
        command.add("-map_metadata");
        command.add(request.keepMetadata() ? "0" : "-1");
        command.add("-progress");
        command.add("pipe:1");
        command.add("-nostats");
        command.add(outputFile.toString());
        return command;
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Long.parseLong(value.trim());
    }

    @FunctionalInterface
    public interface CompressionProgressListener {
        void onProgress(double progress, String statusMessage, long encodedMicroseconds);
    }
}
