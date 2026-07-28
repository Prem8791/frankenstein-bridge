package com.android.internal.os.frankenstein;

import com.android.internal.os.frankenstein.IBridgeOperation;
import com.android.internal.os.frankenstein.OperationRequest;

interface IExternalCapabilityProvider {
    IBridgeOperation startOperation(in OperationRequest request);
    long getGeneration();
    void ping();
}
