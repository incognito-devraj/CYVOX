package com.cyvox.service;

import com.cyvox.model.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VideoScannerServiceTest {

    private final VideoScannerService videoScannerService = new VideoScannerService();

    @TempDir
    Path tempDirectory;

    @Test
    void shouldScanSupportedVideoFilesRecursivelyAndAggregateStats() throws IOException {
        Path cameraDirectory = Files.createDirectories(tempDirectory.resolve("camera").resolve("day-1"));
        Path archiveDirectory = Files.createDirectories(tempDirectory.resolve("archive"));
        Files.writeString(cameraDirectory.resolve("clip-01.mp4"), "12345");
        Files.writeString(cameraDirectory.resolve("clip-02.MKV"), "123456789");
        Files.writeString(archiveDirectory.resolve("notes.txt"), "ignore");

        ScanResult result = videoScannerService.scan(tempDirectory, path -> { });

        assertEquals(2, result.statistics().videoCount());
        assertEquals(14, result.statistics().totalSizeBytes());
        assertIterableEquals(
                List.of("clip-01.mp4", "clip-02.MKV"),
                result.videos().stream().map(video -> video.fileName()).toList()
        );
    }

    @Test
    void shouldIgnoreHiddenFilesAndTemporaryDirectories() throws IOException {
        Path visibleDirectory = Files.createDirectories(tempDirectory.resolve("visible"));
        Path hiddenDirectory = Files.createDirectories(tempDirectory.resolve(".hidden"));
        Path tempDirectoryName = Files.createDirectories(tempDirectory.resolve("temp"));

        Files.writeString(visibleDirectory.resolve("movie.webm"), "abc");
        Files.writeString(visibleDirectory.resolve("~$draft.mp4"), "ignore");
        Files.writeString(hiddenDirectory.resolve("secret.mp4"), "ignore");
        Files.writeString(tempDirectoryName.resolve("temp-video.mov"), "ignore");

        List<Path> visitedPaths = new ArrayList<>();
        ScanResult result = videoScannerService.scan(tempDirectory, visitedPaths::add);

        assertEquals(1, result.statistics().videoCount());
        assertEquals("movie.webm", result.videos().getFirst().fileName());
        assertTrue(visitedPaths.stream().noneMatch(path -> path.toString().contains(".hidden\\secret.mp4")));
    }
}
