package com.cyvox.service;

import com.cyvox.exception.FfprobeException;
import com.cyvox.model.VideoFile;
import com.cyvox.model.VideoMetadata;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

public final class VideoAnalysisService {

    private static final Gson GSON = new Gson();

    private final FfprobeResolver ffprobeResolver;

    public VideoAnalysisService(FfprobeResolver ffprobeResolver) {
        this.ffprobeResolver = ffprobeResolver;
    }

    public VideoFile analyze(VideoFile videoFile) throws IOException {
        Objects.requireNonNull(videoFile, "videoFile must not be null");
        Path ffprobeExecutable = ffprobeResolver.resolve();
        ProcessBuilder processBuilder = new ProcessBuilder(
                ffprobeExecutable.toString(),
                "-v", "error",
                "-print_format", "json",
                "-show_streams",
                "-show_format",
                videoFile.path().toString()
        );
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        FfprobePayload payload;
        try (Reader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
            payload = GSON.fromJson(reader, FfprobePayload.class);
        } catch (JsonSyntaxException exception) {
            throw new FfprobeException("ffprobe returned unreadable JSON for " + videoFile.fileName(), exception);
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new FfprobeException("ffprobe exited with code " + exitCode + " for " + videoFile.fileName());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for ffprobe", exception);
        }

        return videoFile.withMetadata(extractMetadata(videoFile, payload));
    }

    private VideoMetadata extractMetadata(VideoFile videoFile, FfprobePayload payload) {
        if (payload == null || payload.streams == null || payload.streams.length == 0) {
            throw new FfprobeException("ffprobe returned no streams for " + videoFile.fileName());
        }

        FfprobeStream videoStream = Arrays.stream(payload.streams)
                .filter(stream -> "video".equalsIgnoreCase(stream.codecType))
                .findFirst()
                .orElseThrow(() -> new FfprobeException("No video stream found for " + videoFile.fileName()));

        FfprobeStream audioStream = Arrays.stream(payload.streams)
                .filter(stream -> "audio".equalsIgnoreCase(stream.codecType))
                .findFirst()
                .orElse(null);

        double durationSeconds = parseDouble(firstNonBlank(videoStream.duration, payload.format == null ? null : payload.format.duration));
        long bitrate = parseLong(firstNonBlank(videoStream.bitRate, payload.format == null ? null : payload.format.bitRate));
        double frameRate = parseFrameRate(videoStream.avgFrameRate);

        return new VideoMetadata(
                durationSeconds,
                videoStream.width,
                videoStream.height,
                blankToFallback(videoStream.codecName, "Unknown"),
                bitrate,
                frameRate,
                audioStream == null ? "None" : blankToFallback(audioStream.codecName, "Unknown")
        );
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private String blankToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Double.parseDouble(value);
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Long.parseLong(value);
    }

    private double parseFrameRate(String value) {
        if (value == null || value.isBlank() || "0/0".equals(value)) {
            return 0;
        }
        String[] parts = value.split("/");
        if (parts.length != 2) {
            return parseDouble(value);
        }
        double numerator = parseDouble(parts[0]);
        double denominator = parseDouble(parts[1]);
        if (denominator == 0) {
            return 0;
        }
        return numerator / denominator;
    }

    private static final class FfprobePayload {
        private FfprobeStream[] streams;
        private FfprobeFormat format;
    }

    private static final class FfprobeStream {
        @SerializedName("codec_type")
        private String codecType;
        @SerializedName("codec_name")
        private String codecName;
        @SerializedName("avg_frame_rate")
        private String avgFrameRate;
        private String duration;
        @SerializedName("bit_rate")
        private String bitRate;
        private int width;
        private int height;
    }

    private static final class FfprobeFormat {
        private String duration;
        @SerializedName("bit_rate")
        private String bitRate;
    }
}
