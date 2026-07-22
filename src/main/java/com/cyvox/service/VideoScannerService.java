package com.cyvox.service;

import com.cyvox.config.AppConfig;
import com.cyvox.model.ScanResult;
import com.cyvox.model.ScanStatistics;
import com.cyvox.model.VideoFile;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public final class VideoScannerService {

    public ScanResult scan(Path rootDirectory, Consumer<Path> visitedPathConsumer) throws IOException {
        Objects.requireNonNull(rootDirectory, "rootDirectory must not be null");
        if (!Files.isDirectory(rootDirectory)) {
            throw new IllegalArgumentException("Input path is not a directory: " + rootDirectory);
        }

        List<VideoFile> videos = new ArrayList<>();
        Files.walkFileTree(rootDirectory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (!directory.equals(rootDirectory) && shouldSkipDirectory(directory)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                visitedPathConsumer.accept(directory);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                visitedPathConsumer.accept(file);
                if (attributes.isRegularFile() && !isHidden(file) && isSupportedVideoFile(file)) {
                    videos.add(new VideoFile(
                            file,
                            file.getFileName().toString(),
                            extensionOf(file),
                            attributes.size()
                    ));
                }
                return FileVisitResult.CONTINUE;
            }
        });

        videos.sort(Comparator.comparing(VideoFile::fileName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(video -> video.path().toString(), String.CASE_INSENSITIVE_ORDER));

        long totalSizeBytes = videos.stream()
                .mapToLong(VideoFile::sizeBytes)
                .sum();

        return new ScanResult(videos, new ScanStatistics(videos.size(), totalSizeBytes));
    }

    private boolean shouldSkipDirectory(Path directory) throws IOException {
        String directoryName = fileNameOf(directory);
        return isHidden(directory) || AppConfig.isTemporaryDirectoryName(directoryName);
    }

    private boolean isSupportedVideoFile(Path file) {
        String extension = extensionOf(file);
        return !extension.isBlank() && AppConfig.isSupportedVideoExtension(extension);
    }

    private boolean isHidden(Path path) throws IOException {
        String fileName = fileNameOf(path);
        if (fileName.startsWith(".") || fileName.startsWith("~$")) {
            return true;
        }
        return Files.isHidden(path);
    }

    private String extensionOf(Path path) {
        String fileName = fileNameOf(path);
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String fileNameOf(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }
}
