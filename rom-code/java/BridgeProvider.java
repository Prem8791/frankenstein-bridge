package com.android.server.frankenstein;

import com.android.internal.os.frankenstein.OperationDescriptor;
import com.android.internal.os.frankenstein.OperationRequest;
import com.android.internal.os.frankenstein.ProviderDescriptor;

public interface BridgeProvider {
    ProviderDescriptor descriptor();
    OperationDescriptor[] operations();
    void start(OperationRequest request, OperationManager.Completion completion);
    default void onUserStopped(int userId) {}
    default void close() {}
}
