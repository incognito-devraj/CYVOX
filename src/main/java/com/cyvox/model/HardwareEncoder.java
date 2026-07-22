package com.cyvox.model;

public record HardwareEncoder(
        String name,
        String videoEncoder,
        String qualityOption,
        String qualityValue,
        String presetOption,
        String presetValue
) {
    public boolean cpuFallback() {
        return videoEncoder == null || videoEncoder.isBlank();
    }

    public static HardwareEncoder cpu() {
        return new HardwareEncoder("CPU", "", "", "", "", "");
    }
}
