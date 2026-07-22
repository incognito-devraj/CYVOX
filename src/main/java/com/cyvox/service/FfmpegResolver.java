package com.cyvox.service;

import com.cyvox.exception.FfmpegException;

import java.nio.file.Files;
import java.nio.file.Path;

public final class FfmpegResolver {

    private static final Path BUNDLED_FFMPEG = Path.of("ffmpeg", "ffmpeg.exe");

    public Path resolve() {
        if (Files.isRegularFile(BUNDLED_FFMPEG)) {
            return BUNDLED_FFMPEG.toAbsolutePath().normalize();
        }
        throw new FfmpegException("Bundled ffmpeg.exe was not found in the ffmpeg directory.");
    }
}
