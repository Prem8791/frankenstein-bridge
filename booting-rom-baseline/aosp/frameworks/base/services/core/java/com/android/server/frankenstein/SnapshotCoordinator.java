package com.android.server.frankenstein;

import android.os.Handler;
import android.os.SystemClock;

import com.android.internal.os.frankenstein.BridgePayload;
import com.android.internal.os.frankenstein.OperationRequest;
import com.android.internal.os.frankenstein.OperationResult;
import com.android.internal.os.frankenstein.OperationStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

final class SnapshotCoordinator {
    private final ProviderRegistry mProviders;
    private final BridgeEventHub mEvents;
    private final ExecutorService mWorkers = Executors.newFixedThreadPool(4);
    private final Semaphore mCoordinators = new Semaphore(2);

    SnapshotCoordinator(ProviderRegistry providers, BridgeEventHub events) {
        mProviders = providers;
        mEvents = events;
    }

    void capture(String[] providerIds, int targetUserId, String correlationId,
            long timeoutMs, OperationManager.Completion completion) {
        if (!mCoordinators.tryAcquire()) {
            completion.complete(result(BridgeConstants.RESOURCE_EXHAUSTED, correlationId, null));
            return;
        }
        final long startBarrier = mEvents.currentSequence();
        final AtomicInteger remaining = new AtomicInteger(providerIds.length);
        final List<byte[]> sections = java.util.Collections.synchronizedList(new ArrayList<>());
        if (providerIds.length == 0) {
            mCoordinators.release();
            completion.complete(result(BridgeConstants.OK, correlationId,
                    encodeSnapshot(startBarrier, startBarrier, sections)));
            return;
        }
        for (String providerId : providerIds) {
            mWorkers.execute(() -> {
                BridgeProvider provider = mProviders.getRom(providerId);
                sections.add(encodeSection(providerId, provider == null
                        ? BridgeConstants.UNAVAILABLE : BridgeConstants.OK));
                if (remaining.decrementAndGet() == 0) {
                    long endBarrier = mEvents.currentSequence();
                    mCoordinators.release();
                    completion.complete(result(BridgeConstants.OK, correlationId,
                            encodeSnapshot(startBarrier, endBarrier, sections)));
                }
            });
        }
    }

    private static OperationResult result(int code, String correlationId, byte[] payloadBytes) {
        OperationResult result = new OperationResult();
        result.status = new OperationStatus();
        result.status.state = 3;
        result.status.code = code;
        result.status.correlationId = correlationId;
        result.status.completedElapsedMs = SystemClock.elapsedRealtime();
        if (payloadBytes != null) {
            result.payload = new BridgePayload();
            result.payload.schemaId = "core.snapshots.v1";
            result.payload.schemaVersion = 1;
            result.payload.encoding = 1;
            result.payload.data = payloadBytes;
        }
        result.warnings = new String[0];
        result.declaredLength = -1;
        return result;
    }

    private static byte[] encodeSnapshot(long start, long end, List<byte[]> sections) {
        // Deterministic minimal CBOR map: detailed schema encoding is isolated here.
        return new byte[] {(byte) 0xa3, 0x01, 0x1b,
                (byte) (start >>> 56), (byte) (start >>> 48), (byte) (start >>> 40),
                (byte) (start >>> 32), (byte) (start >>> 24), (byte) (start >>> 16),
                (byte) (start >>> 8), (byte) start,
                0x02, 0x1b,
                (byte) (end >>> 56), (byte) (end >>> 48), (byte) (end >>> 40),
                (byte) (end >>> 32), (byte) (end >>> 24), (byte) (end >>> 16),
                (byte) (end >>> 8), (byte) end,
                0x03, (byte) 0x80};
    }

    private static byte[] encodeSection(String providerId, int code) {
        return (providerId + ":" + code).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
