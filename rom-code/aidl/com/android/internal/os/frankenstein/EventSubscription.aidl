package com.android.internal.os.frankenstein;

parcelable EventSubscription {
    String[] providerIds;
    String[] eventIds;
    int targetUserId;
    long replayAfterSequence;
    boolean includeInitialSnapshot;
    int maxBatchCount = 64;
    int maxBatchBytes = 262144;
}
