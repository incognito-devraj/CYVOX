package com.cyvox.task;

import com.cyvox.model.VideoFile;
import com.cyvox.service.VideoAnalysisService;
import javafx.concurrent.Task;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class VideoAnalysisTask extends Task<List<VideoFile>> {

    private final VideoAnalysisService videoAnalysisService;
    private final List<VideoFile> videos;

    public VideoAnalysisTask(VideoAnalysisService videoAnalysisService, List<VideoFile> videos) {
        this.videoAnalysisService = videoAnalysisService;
        this.videos = List.copyOf(videos);
    }

    @Override
    protected List<VideoFile> call() throws IOException {
        updateTitle("Video analysis");
        updateProgress(0, videos.size());

        List<VideoFile> analyzedVideos = new ArrayList<>(videos.size());
        for (int index = 0; index < videos.size(); index++) {
            if (isCancelled()) {
                break;
            }
            VideoFile video = videos.get(index);
            updateMessage("Analyzing " + video.fileName());
            analyzedVideos.add(videoAnalysisService.analyze(video));
            updateProgress(index + 1L, videos.size());
        }

        updateMessage("Analysis complete");
        return analyzedVideos;
    }
}
