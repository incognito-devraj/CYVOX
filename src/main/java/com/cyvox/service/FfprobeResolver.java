package com.cyvox.service;

import com.cyvox.exception.FfprobeException;

import java.nio.file.Files;
import java.nio.file.Path;

public final class FfprobeResolver {

    private static final Path BUNDLED_FFPROBE = Path.of("ffmpeg", "ffprobe.exe");

    public Path resolve() {
        if (Files.isRegularFile(BUNDLED_FFPROBE)) {
            return BUNDLED_FFPROBE.toAbsolutePath().normalize();
        }
        throw new FfprobeException("Bundled ffprobe.exe was not found in the ffmpeg directory.");
    }
}
