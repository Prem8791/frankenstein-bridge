package com.android.internal.os.frankenstein;

parcelable OperationDescriptor {
    String providerId;
    String operationId;
    int version;
    String requestSchemaId;
    String resultSchemaId;
    boolean asynchronous = true;
    boolean producesStream;
    int defaultTimeoutMs;
}
