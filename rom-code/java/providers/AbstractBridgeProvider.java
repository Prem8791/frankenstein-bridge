package com.android.server.frankenstein.providers;

import android.os.SystemClock;

import com.android.internal.os.frankenstein.OperationDescriptor;
import com.android.internal.os.frankenstein.OperationRequest;
import com.android.internal.os.frankenstein.OperationResult;
import com.android.internal.os.frankenstein.OperationStatus;
import com.android.internal.os.frankenstein.ProviderDescriptor;
import com.android.server.frankenstein.BridgeProvider;
import com.android.server.frankenstein.OperationManager;

public abstract class AbstractBridgeProvider implements BridgeProvider {
    protected static final int STATE_NOT_READY = 1;
    protected static final int STATE_READY = 2;
    protected static final int STATE_UNAVAILABLE = 3;

    private final ProviderDescriptor mDescriptor;
    private final OperationDescriptor[] mOperations;

    protected AbstractBridgeProvider(String providerId, String[] operationIds,
            String[] eventIds, String[] schemaIds) {
        mDescriptor = new ProviderDescriptor();
        mDescriptor.providerId = providerId;
        mDescriptor.providerVersion = 1;
        mDescriptor.generation = 1;
        mDescriptor.state = STATE_NOT_READY;
        mDescriptor.ownerKind = 1;
        mDescriptor.unavailableReason = "dependency not ready";
        mDescriptor.operationIds = operationIds;
        mDescriptor.eventIds = eventIds;
        mDescriptor.schemaIds = schemaIds;
        mOperations = new OperationDescriptor[operationIds.length];
        for (int i = 0; i < operationIds.length; i++) {
            OperationDescriptor operation = new OperationDescriptor();
            operation.providerId = providerId;
            operation.operationId = operationIds[i];
            operation.version = 1;
            operation.requestSchemaId = providerId + ".request.v1";
            operation.resultSchemaId = providerId + ".result.v1";
            operation.asynchronous = true;
            operation.defaultTimeoutMs = 30_000;
            mOperations[i] = operation;
        }
    }

    @Override
    public final ProviderDescriptor descriptor() {
        return mDescriptor;
    }

    @Override
    public final OperationDescriptor[] operations() {
        return mOperations.clone();
    }

    public final void setReady() {
        mDescriptor.state = STATE_READY;
        mDescriptor.unavailableReason = "";
        mDescriptor.generation++;
    }

    public final void setUnavailable(String reason) {
        mDescriptor.state = STATE_UNAVAILABLE;
        mDescriptor.unavailableReason = reason;
        mDescriptor.generation++;
    }

    protected static OperationResult terminal(int code, String correlationId) {
        OperationResult result = new OperationResult();
        result.status = new OperationStatus();
        result.status.state = 3;
        result.status.code = code;
        result.status.correlationId = correlationId;
        result.status.completedElapsedMs = SystemClock.elapsedRealtime();
        result.declaredLength = -1;
        result.warnings = new String[0];
        return result;
    }
}
