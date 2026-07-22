package com.cyvox.task;

import com.cyvox.model.BatchCompressionResult;
import com.cyvox.model.CompressionPreset;
import com.cyvox.model.CompressionRequest;
import com.cyvox.model.CompressionResult;
import com.cyvox.model.VideoFile;
import com.cyvox.service.VideoCompressionService;
import javafx.concurrent.Task;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class BatchCompressionTask extends Task<BatchCompressionResult> {

    private final VideoCompressionService videoCompressionService;
    private final List<VideoFile> videos;
    private final Path outputDirectory;
    private final CompressionPreset preset;
    private final String filenamePattern;
    private final boolean overwriteExisting;
    private final boolean skipExisting;
    private final boolean keepMetadata;

    public BatchCompressionTask(
            VideoCompressionService videoCompressionService,
            List<VideoFile> videos,
            Path outputDirectory,
            CompressionPreset preset,
            String filenamePattern,
            boolean overwriteExisting,
            boolean skipExisting,
            boolean keepMetadata
    ) {
        this.videoCompressionService = videoCompressionService;
        this.videos = List.copyOf(videos);
        this.outputDirectory = outputDirectory;
        this.preset = preset;
        this.filenamePattern = filenamePattern;
        this.overwriteExisting = overwriteExisting;
        this.skipExisting = skipExisting;
        this.keepMetadata = keepMetadata;
    }

    @Override
    protected BatchCompressionResult call() throws IOException {
        updateTitle("Batch compression");
        updateProgress(0, videos.size());
        updateMessage("Preparing batch queue...");

        List<CompressionResult> results = new ArrayList<>(videos.size());
        Instant startedAt = Instant.now();

        for (int index = 0; index < videos.size(); index++) {
            if (isCancelled()) {
                break;
            }

            VideoFile video = videos.get(index);
            CompressionRequest request = new CompressionRequest(
                    video,
                    outputDirectory,
                    preset,
                    filenamePattern,
                    overwriteExisting,
                    skipExisting,
                    keepMetadata
            );

            int completedItems = index;
            CompressionResult result = videoCompressionService.compress(request, (fileProgress, statusMessage, encodedMicroseconds) -> {
                if (isCancelled()) {
                    return;
                }
                double normalizedFileProgress = fileProgress < 0 ? 0 : fileProgress;
                double aggregateProgress = (completedItems + normalizedFileProgress) / videos.size();
                updateProgress(aggregateProgress, 1);
                updateMessage("[" + (completedItems + 1) + "/" + videos.size() + "] " + statusMessage);
            });

            results.add(result);
            updateProgress((double) (index + 1) / videos.size(), 1);
            updateMessage("[" + (index + 1) + "/" + videos.size() + "] " + result.message());
        }

        return new BatchCompressionResult(results, Duration.between(startedAt, Instant.now()));
    }
}
