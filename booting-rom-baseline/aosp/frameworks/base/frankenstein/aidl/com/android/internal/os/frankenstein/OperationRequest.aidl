package com.android.internal.os.frankenstein;

import com.android.internal.os.frankenstein.BridgePayload;

parcelable OperationRequest {
    String providerId;
    String operationId;
    int operationVersion;
    int targetUserId;
    long timeoutMs;
    String correlationId;
    BridgePayload payload;
}
