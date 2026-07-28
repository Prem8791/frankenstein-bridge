package com.android.server.frankenstein.providers;

import com.android.internal.os.frankenstein.OperationRequest;
import com.android.server.frankenstein.OperationManager;

public final class FrameworkEventProvider extends AbstractBridgeProvider {
    public FrameworkEventProvider(String providerId, String[] eventIds, String[] schemaIds) {
        super(providerId, new String[] {"snapshot"}, eventIds, schemaIds);
    }

    @Override
    public void start(OperationRequest request, OperationManager.Completion completion) {
        completion.complete(terminal(4, request.correlationId));
    }
}
