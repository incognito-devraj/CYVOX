package com.cyvox.task;

import com.cyvox.model.CompressionRequest;
import com.cyvox.model.CompressionResult;
import com.cyvox.service.VideoCompressionService;
import javafx.concurrent.Task;

import java.io.IOException;

public final class VideoCompressionTask extends Task<CompressionResult> {

    private final VideoCompressionService videoCompressionService;
    private final CompressionRequest compressionRequest;

    public VideoCompressionTask(VideoCompressionService videoCompressionService, CompressionRequest compressionRequest) {
        this.videoCompressionService = videoCompressionService;
        this.compressionRequest = compressionRequest;
    }

    @Override
    protected CompressionResult call() throws IOException {
        updateTitle("Single file compression");
        updateMessage("Preparing compression...");
        updateProgress(0, 1);

        CompressionResult result = videoCompressionService.compress(compressionRequest, (progress, statusMessage, encodedMicroseconds) -> {
            if (isCancelled()) {
                return;
            }
            updateMessage(statusMessage);
            updateProgress(progress < 0 ? -1 : progress, 1);
        });

        updateMessage(result.message());
        updateProgress(1, 1);
        return result;
    }
}
