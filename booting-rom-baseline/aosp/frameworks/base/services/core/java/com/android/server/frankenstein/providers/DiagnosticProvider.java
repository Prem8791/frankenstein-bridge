package com.android.server.frankenstein.providers;

import com.android.internal.os.frankenstein.OperationRequest;
import com.android.internal.os.frankenstein.diag.DiagRequest;
import com.android.server.frankenstein.DiagnosticDaemonConnector;
import com.android.server.frankenstein.BridgeCbor;
import com.android.server.frankenstein.OperationManager;

public final class DiagnosticProvider extends AbstractBridgeProvider {
    private final DiagnosticDaemonConnector mConnector;
    private final int mOperation;

    public DiagnosticProvider(String providerId, String[] operationIds, String[] schemaIds,
            DiagnosticDaemonConnector connector, int operation) {
        super(providerId, operationIds, new String[] {"health.changed"}, schemaIds);
        mConnector = connector;
        mOperation = operation;
    }

    @Override
    public void start(OperationRequest request, OperationManager.Completion completion) {
        java.util.Map<String, Object> input = BridgeCbor.decodeFlatMap(
                request.payload == null ? null : request.payload.data);
        DiagRequest diagnostic = new DiagRequest();
        diagnostic.operation = mOperation;
        diagnostic.scope = BridgeCbor.string(input, "scope", descriptor().providerId);
        diagnostic.opaqueId = BridgeCbor.string(input, "opaque_id", request.operationId);
        diagnostic.offset = BridgeCbor.integer(input, "offset", 0);
        diagnostic.length = BridgeCbor.integer(input, "length", 0);
        diagnostic.timeoutMs = (int) Math.min(Integer.MAX_VALUE, request.timeoutMs);
        diagnostic.filters = new String[0];
        mConnector.execute(diagnostic, result -> {
            com.android.internal.os.frankenstein.OperationResult terminal =
                    terminal(result.code, request.correlationId);
            terminal.stream = result.stream;
            terminal.declaredLength = result.declaredLength;
            terminal.truncated = result.truncated;
            completion.complete(terminal);
        });
    }
}
