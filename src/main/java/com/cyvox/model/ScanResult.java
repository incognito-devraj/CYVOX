package com.cyvox.model;

import java.util.List;

public record ScanResult(
        List<VideoFile> videos,
        ScanStatistics statistics
) {
}
