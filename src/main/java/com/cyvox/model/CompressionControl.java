package com.cyvox.model;

public interface CompressionControl {

    boolean isCancellationRequested();

    boolean isPaused();

    void waitIfPaused() throws InterruptedException;
}
