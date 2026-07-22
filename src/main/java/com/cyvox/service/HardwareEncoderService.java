package com.cyvox.service;

import com.cyvox.model.CompressionPreset;
import com.cyvox.model.HardwareEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

public final class HardwareEncoderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HardwareEncoderService.class);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);

    private final FfmpegResolver ffmpegResolver;
    private String cachedEncoders;
    private Set<String> cachedUsableHardwareEncoders;

    public HardwareEncoderService(FfmpegResolver ffmpegResolver) {
        this.ffmpegResolver = ffmpegResolver;
    }

    public synchronized HardwareEncoder resolveBestEncoder(CompressionPreset preset) {
        try {
            if (cachedEncoders == null) {
                cachedEncoders = readEncoders();
            }
            if (cachedUsableHardwareEncoders == null) {
                cachedUsableHardwareEncoders = probeUsableHardwareEncoders(cachedEncoders);
            }
            HardwareEncoder hardwareEncoder = chooseEncoder(cachedEncoders, cachedUsableHardwareEncoders, preset);
            LOGGER.info("Selected {} compression path", hardwareEncoder.name());
            return hardwareEncoder;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.warn("Hardware encoder probing failed; using CPU fallback", exception);
            return HardwareEncoder.cpu();
        }
    }

    private String readEncoders() throws IOException, InterruptedException {
        Path probeOutput = Files.createTempFile("cyvox-ffmpeg-encoders", ".txt");
        Process process = new ProcessBuilder(
                ffmpegResolver.resolve().toString(),
                "-hide_banner",
                "-encoders"
        )
                .redirectErrorStream(true)
                .redirectOutput(probeOutput.toFile())
                .start();
        try {
            boolean finished = process.waitFor(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "";
            }
            return Files.readString(probeOutput, StandardCharsets.UTF_8).toLowerCase();
        } finally {
            Files.deleteIfExists(probeOutput);
        }
    }

    private Set<String> probeUsableHardwareEncoders(String encoders) throws IOException, InterruptedException {
        Set<String> usableEncoders = new LinkedHashSet<>();
        for (String encoder : List.of("av1_nvenc", "hevc_nvenc", "hevc_qsv", "hevc_amf")) {
            if (encoders.contains(encoder) && canEncodeOneFrame(encoder)) {
                usableEncoders.add(encoder);
            }
        }
        return usableEncoders;
    }

    private boolean canEncodeOneFrame(String encoder) throws IOException, InterruptedException {
        Path probeOutput = Files.createTempFile("cyvox-encoder-probe", ".txt");
        Process process = new ProcessBuilder(
                ffmpegResolver.resolve().toString(),
                "-hide_banner",
                "-f", "lavfi",
                "-i", "testsrc=size=128x72:rate=1:duration=0.1",
                "-frames:v", "1",
                "-c:v", encoder,
                "-f", "null",
                "NUL"
        )
                .redirectErrorStream(true)
                .redirectOutput(probeOutput.toFile())
                .start();
        try {
            boolean finished = process.waitFor(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            boolean usable = process.exitValue() == 0;
            LOGGER.info("{} hardware probe {}", encoder, usable ? "passed" : "failed");
            return usable;
        } finally {
            Files.deleteIfExists(probeOutput);
        }
    }

    private HardwareEncoder chooseEncoder(String encoders, Set<String> usableHardwareEncoders, CompressionPreset preset) {
        if (preset == CompressionPreset.ARCHIVE && usableHardwareEncoders.contains("av1_nvenc")) {
            return new HardwareEncoder("NVIDIA GPU AV1", "av1_nvenc", "-cq:v", "32", "-preset", "p5");
        }
        List<String> preferredEncoders = List.of("hevc_nvenc", "hevc_qsv", "hevc_amf");
        for (String encoder : preferredEncoders) {
            if (encoders.contains(encoder) && usableHardwareEncoders.contains(encoder)) {
                return switch (encoder) {
                    case "hevc_nvenc" -> new HardwareEncoder("NVIDIA GPU HEVC", encoder, "-cq:v", qualityFor(preset), "-preset", "p5");
                    case "hevc_qsv" -> new HardwareEncoder("Intel Quick Sync HEVC", encoder, "-global_quality", qualityFor(preset), "-preset", "medium");
                    case "hevc_amf" -> new HardwareEncoder("AMD AMF HEVC", encoder, "-qp_i", qualityFor(preset), "-quality", "balanced");
                    default -> HardwareEncoder.cpu();
                };
            }
        }
        return HardwareEncoder.cpu();
    }

    private String qualityFor(CompressionPreset preset) {
        return switch (preset) {
            case HIGH_QUALITY -> "23";
            case BALANCED -> "28";
            case MAXIMUM_COMPRESSION -> "33";
            case ARCHIVE -> "35";
        };
    }
}
