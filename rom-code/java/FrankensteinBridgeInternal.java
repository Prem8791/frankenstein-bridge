package com.android.server.frankenstein;

import com.android.internal.os.frankenstein.BridgePayload;

/**
 * Narrow lock-safe source-hook contract. Callers must pass normalized copies and
 * must not hold an owning framework lock while invoking this sink.
 */
public abstract class FrankensteinBridgeInternal {
    public abstract void emit(String providerId, String eventId, int eventVersion,
            int targetUserId, int uid, int pid, String correlationId, BridgePayload payload);
    public abstract void providerReady(String providerId);
    public abstract void providerUnavailable(String providerId, int stableReason);
}
