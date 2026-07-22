package com.cyvox.model;

public record VideoMetadata(
        double durationSeconds,
        int width,
        int height,
        String videoCodec,
        long bitrate,
        double frameRate,
        String audioCodec
) {
    public String resolution() {
        if (width <= 0 || height <= 0) {
            return "Unknown";
        }
        return width + "x" + height;
    }
}
