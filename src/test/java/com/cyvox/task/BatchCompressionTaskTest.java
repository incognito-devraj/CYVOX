package com.cyvox.task;

import com.cyvox.model.BatchCompressionResult;
import com.cyvox.model.CompressionPreset;
import com.cyvox.model.VideoFile;
import com.cyvox.service.FfmpegResolver;
import com.cyvox.service.FfprobeResolver;
import com.cyvox.service.VideoAnalysisService;
import com.cyvox.service.VideoCompressionService;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BatchCompressionTaskTest {

    private static final AtomicBoolean JAVAFX_STARTED = new AtomicBoolean(false);

    private final VideoAnalysisService videoAnalysisService = new VideoAnalysisService(new FfprobeResolver());
    private final VideoCompressionService videoCompressionService = new VideoCompressionService(new FfmpegResolver());

    @TempDir
    Path tempDirectory;

    @BeforeAll
    static void initializeJavaFxToolkit() throws InterruptedException {
        if (JAVAFX_STARTED.compareAndSet(false, true)) {
            CountDownLatch startupLatch = new CountDownLatch(1);
            Platform.startup(startupLatch::countDown);
            if (!startupLatch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("JavaFX toolkit failed to initialize for batch task tests.");
            }
        }
    }

    @Test
    @EnabledIf("bundledFfmpegAvailable")
    void shouldCompressQueueSequentially() throws Exception {
        VideoFile first = analyze(createSampleVideo(tempDirectory.resolve("first.mp4"), "440"));
        VideoFile second = analyze(createSampleVideo(tempDirectory.resolve("second.mp4"), "880"));
        Path outputDirectory = Files.createDirectories(tempDirectory.resolve("batch"));

        BatchCompressionTask task = new BatchCompressionTask(
                videoCompressionService,
                List.of(first, second),
                outputDirectory,
                CompressionPreset.BALANCED,
                "{name}_batch",
                true,
                false,
                true
        );

        BatchCompressionResult result = task.call();

        assertEquals(2, result.results().size());
        assertEquals(2, result.completedCount());
        assertTrue(Files.isRegularFile(outputDirectory.resolve("first_batch.mp4")));
        assertTrue(Files.isRegularFile(outputDirectory.resolve("second_batch.mp4")));
    }

    static boolean bundledFfmpegAvailable() {
        return Files.isRegularFile(Path.of("ffmpeg", "ffmpeg.exe"))
                && Files.isRegularFile(Path.of("ffmpeg", "ffprobe.exe"));
    }

    private VideoFile analyze(Path videoPath) throws IOException {
        return videoAnalysisService.analyze(new VideoFile(
                videoPath,
                videoPath.getFileName().toString(),
                "mp4",
                Files.size(videoPath),
                null
        ));
    }

    private Path createSampleVideo(Path target, String frequency) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                Path.of("ffmpeg", "ffmpeg.exe").toAbsolutePath().toString(),
                "-y",
                "-f", "lavfi",
                "-i", "testsrc=size=640x360:rate=30:duration=1",
                "-f", "lavfi",
                "-i", "sine=frequency=" + frequency + ":duration=1",
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
