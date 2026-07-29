package com.android.internal.os.frankenstein;

import com.android.internal.os.frankenstein.BridgePayload;

parcelable EventEnvelope {
    String providerId;
    String eventId;
    int eventVersion;
    long bootGeneration;
    long providerGeneration;
    long globalSequence;
    long providerSequence;
    long wallTimeMs;
    long elapsedTimeMs;
    long uptimeMs;
    String correlationId;
    String parentCorrelationId;
    int targetUserId = -10000;
    int uid = -1;
    int pid = -1;
    BridgePayload payload;
    int coalescedCount;
    long lostBefore;
}
