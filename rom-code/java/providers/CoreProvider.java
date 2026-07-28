package com.android.server.frankenstein.providers;

import com.android.internal.os.frankenstein.OperationRequest;
import com.android.server.frankenstein.OperationManager;

public final class CoreProvider extends AbstractBridgeProvider {
    public CoreProvider(String providerId, String[] operations, String[] events,
            String[] schemas) {
        super(providerId, operations, events, schemas);
        setReady();
    }

    @Override
    public void start(OperationRequest request, OperationManager.Completion completion) {
        completion.complete(terminal(0, request.correlationId));
    }
}
