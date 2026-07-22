package com.cyvox.exception;

public final class FfprobeException extends RuntimeException {

    public FfprobeException(String message) {
        super(message);
    }

    public FfprobeException(String message, Throwable cause) {
        super(message, cause);
    }
}
