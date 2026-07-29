package com.android.server.frankenstein;

import android.os.Binder;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SystemClock;

import com.android.internal.os.frankenstein.IBridgeOperation;
import com.android.internal.os.frankenstein.OperationRequest;
import com.android.internal.os.frankenstein.OperationResult;
import com.android.internal.os.frankenstein.OperationStatus;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OperationManager {
    public interface Completion {
        void complete(OperationResult result);
    }

    private final ProviderRegistry mProviders;
    private final QuotaTracker mQuotas;
    private final Handler mHandler;
    private final Map<String, Operation> mOperations = new ConcurrentHashMap<>();

    OperationManager(ProviderRegistry providers, QuotaTracker quotas, Handler handler) {
        mProviders = providers;
        mQuotas = quotas;
        mHandler = handler;
    }

    IBridgeOperation start(CallerContext caller, OperationRequest request) {
        validate(request);
        if (!mQuotas.acquire(caller.uid, QuotaTracker.OPERATION)) {
            throw new IllegalStateException("operation quota exceeded");
        }
        BridgeProvider provider = mProviders.getRom(request.providerId);
        if (provider == null) {
            mQuotas.release(caller.uid, QuotaTracker.OPERATION);
            throw new IllegalArgumentException("unknown ROM provider");
        }
        String correlation = request.correlationId == null || request.correlationId.isEmpty()
                ? UUID.randomUUID().toString() : request.correlationId;
        request.correlationId = correlation;
        long timeout = Math.max(1, Math.min(request.timeoutMs <= 0 ? 30_000 : request.timeoutMs,
                120_000));
        Operation operation = new Operation(caller.uid, caller.userId, correlation);
        mOperations.put(correlation, operation);
        mHandler.postDelayed(operation::timeout, timeout);
        try {
            provider.start(request, operation::complete);
        } catch (RuntimeException failure) {
            operation.complete(error(BridgeConstants.INTERNAL, correlation));
        }
        return operation;
    }

    void cancelUser(int userId) {
        for (Operation operation : mOperations.values()) {
            if (operation.mOwnerUserId == userId) operation.cancel();
        }
    }

    private static void validate(OperationRequest request) {
        if (request == null || request.providerId == null || request.operationId == null
                || request.targetUserId < 0) {
            throw new IllegalArgumentException("invalid operation request");
        }
        SchemaRegistry.validatePayload(request.payload);
    }

    private static OperationResult error(int code, String correlation) {
        OperationResult result = new OperationResult();
        result.status = new OperationStatus();
        result.status.state = 3;
        result.status.code = code;
        result.status.correlationId = correlation;
        result.status.completedElapsedMs = SystemClock.elapsedRealtime();
        result.declaredLength = -1;
        result.warnings = new String[0];
        return result;
    }

    private final class Operation extends IBridgeOperation.Stub {
        private final int mOwnerUid;
        private final int mOwnerUserId;
        private final AtomicBoolean mTerminal = new AtomicBoolean();
        private final OperationStatus mStatus = new OperationStatus();
        private OperationResult mResult;

        Operation(int uid, int userId, String correlation) {
            mOwnerUid = uid;
            mOwnerUserId = userId;
            mStatus.state = 1;
            mStatus.code = BridgeConstants.OK;
            mStatus.correlationId = correlation;
            mStatus.startedElapsedMs = SystemClock.elapsedRealtime();
        }

        @Override
        public int getInterfaceVersion() {
            return IBridgeOperation.VERSION;
        }

        @Override
        public String getInterfaceHash() {
            return IBridgeOperation.HASH;
        }

        @Override
        public synchronized OperationStatus getStatus() {
            enforceOwner();
            return mStatus;
        }

        @Override
        public void cancel() {
            enforceOwnerOrSystem();
            complete(error(BridgeConstants.CANCELLED, mStatus.correlationId));
        }

        @Override
        public synchronized OperationResult takeResult() {
            enforceOwner();
            if (!mTerminal.get()) return null;
            OperationResult result = mResult;
            mResult = null;
            return result;
        }

        void timeout() {
            complete(error(BridgeConstants.TIMEOUT, mStatus.correlationId));
        }

        synchronized void complete(OperationResult result) {
            if (!mTerminal.compareAndSet(false, true)) {
                close(result == null ? null : result.stream);
                return;
            }
            if (result == null || result.status == null) {
                result = error(BridgeConstants.INTERNAL, mStatus.correlationId);
            }
            mResult = result;
            mStatus.state = result.status.state;
            mStatus.code = result.status.code;
            mStatus.completedElapsedMs = SystemClock.elapsedRealtime();
            mOperations.remove(mStatus.correlationId);
            mQuotas.release(mOwnerUid, QuotaTracker.OPERATION);
        }

        private void enforceOwner() {
            if (Binder.getCallingUid() != mOwnerUid) throw new SecurityException("unowned handle");
        }

        private void enforceOwnerOrSystem() {
            int uid = Binder.getCallingUid();
            if (uid != mOwnerUid && uid != android.os.Process.SYSTEM_UID) {
                throw new SecurityException("unowned handle");
            }
        }
    }

    private static void close(ParcelFileDescriptor descriptor) {
        if (descriptor == null) return;
        try {
            descriptor.close();
        } catch (IOException ignored) {
        }
    }
}
