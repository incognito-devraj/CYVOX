package com.cyvox.model;

import java.util.Arrays;

public enum CompressionPreset {
    HIGH_QUALITY("High Quality", "libx265", "23", "192k", "medium"),
    BALANCED("Balanced", "libx265", "28", "128k", "medium"),
    MAXIMUM_COMPRESSION("Maximum Compression", "libx265", "33", "96k", "slow"),
    ARCHIVE("Archive", "libsvtav1", "35", "128k", "8");

    private final String displayName;
    private final String videoEncoder;
    private final String crf;
    private final String audioBitrate;
    private final String presetValue;

    CompressionPreset(String displayName, String videoEncoder, String crf, String audioBitrate, String presetValue) {
        this.displayName = displayName;
        this.videoEncoder = videoEncoder;
        this.crf = crf;
        this.audioBitrate = audioBitrate;
        this.presetValue = presetValue;
    }

    public String displayName() {
        return displayName;
    }

    public String videoEncoder() {
        return videoEncoder;
    }

    public String crf() {
        return crf;
    }

    public String audioBitrate() {
        return audioBitrate;
    }

    public String presetValue() {
        return presetValue;
    }

    public static CompressionPreset fromDisplayName(String displayName) {
        return Arrays.stream(values())
                .filter(preset -> preset.displayName.equals(displayName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown preset: " + displayName));
    }
}
