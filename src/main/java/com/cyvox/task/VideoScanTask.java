package com.cyvox.task;

import com.cyvox.model.ScanResult;
import com.cyvox.service.VideoScannerService;
import javafx.concurrent.Task;

import java.io.IOException;
import java.nio.file.Path;

public final class VideoScanTask extends Task<ScanResult> {

    private final VideoScannerService videoScannerService;
    private final Path inputDirectory;

    public VideoScanTask(VideoScannerService videoScannerService, Path inputDirectory) {
        this.videoScannerService = videoScannerService;
        this.inputDirectory = inputDirectory;
    }

    @Override
    protected ScanResult call() throws IOException {
        updateTitle("Recursive scan");
        updateMessage("Scanning folders...");
        updateProgress(-1, 1);

        ScanResult result = videoScannerService.scan(inputDirectory, visitedPath -> {
            if (isCancelled()) {
                return;
            }
            updateMessage("Scanning " + visitedPath.getFileName());
        });

        updateMessage("Scan complete");
        updateProgress(1, 1);
        return result;
    }
}
