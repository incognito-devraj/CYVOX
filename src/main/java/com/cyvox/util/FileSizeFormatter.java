package com.cyvox.util;

import java.text.DecimalFormat;

public final class FileSizeFormatter {

    private static final long KIB = 1024L;
    private static final long MIB = KIB * 1024L;
    private static final long GIB = MIB * 1024L;
    private static final long TIB = GIB * 1024L;
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.0");

    private FileSizeFormatter() {
    }

    public static String format(long bytes) {
        if (bytes < KIB) {
            return bytes + " B";
        }
        if (bytes < MIB) {
            return DECIMAL_FORMAT.format((double) bytes / KIB) + " KB";
        }
        if (bytes < GIB) {
            return DECIMAL_FORMAT.format((double) bytes / MIB) + " MB";
        }
        if (bytes < TIB) {
            return DECIMAL_FORMAT.format((double) bytes / GIB) + " GB";
        }
        return DECIMAL_FORMAT.format((double) bytes / TIB) + " TB";
    }
}
