package com.android.internal.os.frankenstein;

parcelable OperationStatus {
    int state;
    int code;
    long startedElapsedMs;
    long completedElapsedMs;
    long providerGeneration;
    String correlationId;
    boolean retryable;
}
