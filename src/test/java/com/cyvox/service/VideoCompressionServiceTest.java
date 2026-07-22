package com.cyvox.service;

import com.cyvox.model.CompressionPreset;
import com.cyvox.model.CompressionRequest;
import com.cyvox.model.CompressionResult;
import com.cyvox.model.CompressionStatus;
import com.cyvox.model.VideoFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VideoCompressionServiceTest {

    private final VideoAnalysisService videoAnalysisService = new VideoAnalysisService(new FfprobeResolver());
    private final VideoCompressionService videoCompressionService = new VideoCompressionService(new FfmpegResolver());

    @TempDir
    Path tempDirectory;

    @Test
    @EnabledIf("bundledFfmpegAvailable")
    void shouldCompressSingleVideoIntoOutputDirectory() throws IOException, InterruptedException {
        Path sampleVideo = createSampleVideo(tempDirectory.resolve("sample.mp4"));
        VideoFile analyzedVideo = videoAnalysisService.analyze(new VideoFile(
                sampleVideo,
                "sample.mp4",
                "mp4",
                Files.size(sampleVideo),
                null
        ));
        Path outputDirectory = Files.createDirectories(tempDirectory.resolve("compressed"));

        CompressionResult result = videoCompressionService.compress(new CompressionRequest(
                analyzedVideo,
                outputDirectory,
                CompressionPreset.BALANCED,
                "{name}_balanced",
                true,
                false,
                true
        ), (progress, statusMessage, encodedMicroseconds) -> { });

        assertEquals(CompressionStatus.COMPLETED, result.status());
        assertTrue(Files.isRegularFile(result.outputFile()));
        assertTrue(result.compressedSizeBytes() > 0);
        assertEquals("sample_balanced.mp4", result.outputFile().getFileName().toString());
    }

    @Test
    @EnabledIf("bundledFfmpegAvailable")
    void shouldSkipExistingOutputWhenRequested() throws IOException, InterruptedException {
        Path sampleVideo = createSampleVideo(tempDirectory.resolve("sample.mp4"));
        VideoFile analyzedVideo = videoAnalysisService.analyze(new VideoFile(
                sampleVideo,
                "sample.mp4",
                "mp4",
                Files.size(sampleVideo),
                null
        ));
        Path outputDirectory = Files.createDirectories(tempDirectory.resolve("compressed"));
        Path existingOutput = outputDirectory.resolve("sample_balanced.mp4");
        Files.writeString(existingOutput, "existing");

        CompressionResult result = videoCompressionService.compress(new CompressionRequest(
                analyzedVideo,
                outputDirectory,
                CompressionPreset.BALANCED,
                "{name}_balanced",
                false,
                true,
                true
        ), (progress, statusMessage, encodedMicroseconds) -> { });

        assertEquals(CompressionStatus.SKIPPED, result.status());
        assertEquals(existingOutput, result.outputFile());
    }

    static boolean bundledFfmpegAvailable() {
        return VideoAnalysisServiceTest.bundledFfmpegAvailable();
    }

    private Path createSampleVideo(Path target) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                Path.of("ffmpeg", "ffmpeg.exe").toAbsolutePath().toString(),
                "-y",
                "-f", "lavfi",
                "-i", "testsrc=size=640x360:rate=30:duration=2",
                "-f", "lavfi",
                "-i", "sine=frequency=880:duration=2",
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                target.toString()
        );
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        assertEquals(0, exitCode);
        return target;
    }
}
