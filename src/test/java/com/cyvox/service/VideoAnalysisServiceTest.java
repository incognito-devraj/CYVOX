package com.cyvox.service;

import com.cyvox.model.VideoFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VideoAnalysisServiceTest {

    private final VideoAnalysisService videoAnalysisService = new VideoAnalysisService(new FfprobeResolver());

    @TempDir
    Path tempDirectory;

    @Test
    @EnabledIf("bundledFfmpegAvailable")
    void shouldExtractMetadataUsingBundledFfprobe() throws IOException, InterruptedException {
        Path sampleVideo = tempDirectory.resolve("sample.mp4");
        ProcessBuilder processBuilder = new ProcessBuilder(
                Path.of("ffmpeg", "ffmpeg.exe").toAbsolutePath().toString(),
                "-y",
                "-f", "lavfi",
                "-i", "color=c=black:s=320x240:d=1",
                "-f", "lavfi",
                "-i", "sine=frequency=1000:duration=1",
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                sampleVideo.toString()
        );
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        assertEquals(0, exitCode);
        assertTrue(Files.isRegularFile(sampleVideo));

        VideoFile analyzedVideo = videoAnalysisService.analyze(new VideoFile(
                sampleVideo,
                "sample.mp4",
                "mp4",
                Files.size(sampleVideo),
                null
        ));

        assertNotNull(analyzedVideo.metadata());
        assertEquals("h264", analyzedVideo.metadata().videoCodec());
        assertEquals("aac", analyzedVideo.metadata().audioCodec());
        assertEquals("320x240", analyzedVideo.metadata().resolution());
        assertTrue(analyzedVideo.metadata().durationSeconds() > 0.9);
    }

    static boolean bundledFfmpegAvailable() {
        return Files.isRegularFile(Path.of("ffmpeg", "ffmpeg.exe"))
                && Files.isRegularFile(Path.of("ffmpeg", "ffprobe.exe"));
    }
}
