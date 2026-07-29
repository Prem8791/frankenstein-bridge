package com.android.internal.os.frankenstein;

parcelable BridgeInfo {
    int protocolVersion = 1;
    String interfaceHash;
    long bootGeneration;
    int maxInlineBytes = 65536;
    int maxEventBatchCount = 64;
    int maxEventBatchBytes = 262144;
    int maxSubscriptionsPerUid = 8;
    int maxOperationsPerUid = 16;
    int maxStreamsPerUid = 4;
}
