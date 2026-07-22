package com.cyvox.service;

import com.cyvox.exception.FfmpegException;
import com.cyvox.model.CompressionControl;
import com.cyvox.model.CompressionRequest;
import com.cyvox.model.CompressionResult;
import com.cyvox.model.CompressionStatus;
import com.cyvox.model.HardwareEncoder;
import com.cyvox.model.VideoFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoCompressionService.class);

    private final FfmpegResolver ffmpegResolver;
    private final HardwareEncoderService hardwareEncoderService;

    public VideoCompressionService(FfmpegResolver ffmpegResolver) {
        this.ffmpegResolver = ffmpegResolver;
        this.hardwareEncoderService = new HardwareEncoderService(ffmpegResolver);
    }

    public CompressionResult compress(CompressionRequest request, CompressionProgressListener progressListener) throws IOException {
        return compress(request, progressListener, new CompressionControl() {
            @Override
            public boolean isCancellationRequested() {
                return false;
            }

            @Override
            public boolean isPaused() {
                return false;
            }

            @Override
            public void waitIfPaused() {
            }
        });
    }

    public CompressionResult compress(
            CompressionRequest request,
            CompressionProgressListener progressListener,
            CompressionControl compressionControl
    ) throws IOException {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(progressListener, "progressListener must not be null");
        Objects.requireNonNull(compressionControl, "compressionControl must not be null");

        Files.createDirectories(request.outputDirectory());
        VideoFile inputFile = request.inputFile();
        Path outputFile = resolveOutputPath(request);
        LOGGER.info("Preparing compression for {} -> {}", inputFile.path(), outputFile);
        if (Files.exists(outputFile)) {
            if (request.skipExisting()) {
                LOGGER.info("Skipping existing output file {}", outputFile);
                return new CompressionResult(
                        CompressionStatus.SKIPPED,
                        inputFile.fileName(),
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

        HardwareEncoder hardwareEncoder = hardwareEncoderService.resolveBestEncoder(request.preset());
        return compressWithEncoder(request, progressListener, compressionControl, outputFile, hardwareEncoder, true);
    }

    private CompressionResult compressWithEncoder(
            CompressionRequest request,
            CompressionProgressListener progressListener,
            CompressionControl compressionControl,
            Path outputFile,
            HardwareEncoder hardwareEncoder,
            boolean allowCpuRetry
    ) throws IOException {
        VideoFile inputFile = request.inputFile();
        List<String> command = buildCommand(request, outputFile, hardwareEncoder);
        Instant start = Instant.now();
        LOGGER.info("Starting ffmpeg for {} using {}", inputFile.fileName(), hardwareEncoder.name());
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            double durationSeconds = inputFile.metadata() == null ? 0 : inputFile.metadata().durationSeconds();
            while ((line = reader.readLine()) != null) {
                if (compressionControl.isCancellationRequested()) {
                    destroyProcess(process);
                    throw new InterruptedException("Compression cancelled");
                }
                compressionControl.waitIfPaused();
                if (line.startsWith("out_time_ms=")) {
                    long outTimeMicro = parseLong(line.substring("out_time_ms=".length()));
                    if (outTimeMicro >= 0) {
                        double progress = durationSeconds <= 0 ? -1 : Math.min(1.0, outTimeMicro / (durationSeconds * 1_000_000.0));
                        progressListener.onProgress(progress, "Encoding " + inputFile.fileName(), outTimeMicro);
                    }
                } else if (line.startsWith("out_time_us=")) {
                    long outTimeMicro = parseLong(line.substring("out_time_us=".length()));
                    if (outTimeMicro >= 0) {
                        double progress = durationSeconds <= 0 ? -1 : Math.min(1.0, outTimeMicro / (durationSeconds * 1_000_000.0));
                        progressListener.onProgress(progress, "Encoding " + inputFile.fileName(), outTimeMicro);
                    }
                } else if (line.startsWith("progress=end")) {
                    progressListener.onProgress(1.0, "Compression complete", durationSeconds <= 0 ? 0 : (long) (durationSeconds * 1_000_000.0));
                }
            }
        } catch (InterruptedException exception) {
            destroyProcess(process);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading ffmpeg progress", exception);
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                if (allowCpuRetry && !hardwareEncoder.cpuFallback()) {
                    LOGGER.warn("{} failed for {}; retrying with CPU encoder", hardwareEncoder.name(), inputFile.fileName());
                    Files.deleteIfExists(outputFile);
                    return compressWithEncoder(request, progressListener, compressionControl, outputFile, HardwareEncoder.cpu(), false);
                }
                throw new FfmpegException("ffmpeg exited with code " + exitCode + " for " + inputFile.fileName());
            }
        } catch (InterruptedException exception) {
            destroyProcess(process);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for ffmpeg", exception);
        }

        long compressedSize = Files.size(outputFile);
        LOGGER.info("Completed compression for {}: {} -> {} bytes", inputFile.fileName(), inputFile.sizeBytes(), compressedSize);
        return new CompressionResult(
                CompressionStatus.COMPLETED,
                inputFile.fileName(),
                outputFile,
                inputFile.sizeBytes(),
                compressedSize,
                Duration.between(start, Instant.now()),
                "Compression completed successfully using " + hardwareEncoder.name() + "."
        );
    }

    private void destroyProcess(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
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

    private List<String> buildCommand(CompressionRequest request, Path outputFile, HardwareEncoder hardwareEncoder) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegResolver.resolve().toString());
        command.add("-y");
        command.add("-i");
        command.add(request.inputFile().path().toString());
        command.add("-c:v");
        command.add(hardwareEncoder.cpuFallback() ? request.preset().videoEncoder() : hardwareEncoder.videoEncoder());
        if (hardwareEncoder.cpuFallback()) {
            command.add("-crf");
            command.add(request.preset().crf());
            command.add("-preset");
            command.add(request.preset().presetValue());
        } else {
            command.add(hardwareEncoder.qualityOption());
            command.add(hardwareEncoder.qualityValue());
            command.add(hardwareEncoder.presetOption());
            command.add(hardwareEncoder.presetValue());
        }
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
            return -1;
        }
        String trimmedValue = value.trim();
        if (!trimmedValue.chars().allMatch(Character::isDigit)) {
            return -1;
        }
        return Long.parseLong(trimmedValue);
    }

    @FunctionalInterface
    public interface CompressionProgressListener {
        void onProgress(double progress, String statusMessage, long encodedMicroseconds);
    }
}
