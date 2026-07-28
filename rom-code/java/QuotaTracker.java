package com.android.server.frankenstein;

import android.util.SparseIntArray;

final class QuotaTracker {
    static final int OPERATION = 1;
    static final int SUBSCRIPTION = 2;
    static final int STREAM = 3;

    private final SparseIntArray mOperations = new SparseIntArray();
    private final SparseIntArray mSubscriptions = new SparseIntArray();
    private final SparseIntArray mStreams = new SparseIntArray();

    synchronized boolean acquire(int uid, int resource) {
        SparseIntArray values = values(resource);
        int limit = limit(resource);
        int current = values.get(uid);
        if (current >= limit) return false;
        values.put(uid, current + 1);
        return true;
    }

    synchronized void release(int uid, int resource) {
        SparseIntArray values = values(resource);
        int current = values.get(uid);
        if (current <= 1) values.delete(uid);
        else values.put(uid, current - 1);
    }

    synchronized void clearUid(int uid) {
        mOperations.delete(uid);
        mSubscriptions.delete(uid);
        mStreams.delete(uid);
    }

    private SparseIntArray values(int resource) {
        switch (resource) {
            case OPERATION: return mOperations;
            case SUBSCRIPTION: return mSubscriptions;
            case STREAM: return mStreams;
            default: throw new IllegalArgumentException("unknown resource");
        }
    }

    private int limit(int resource) {
        switch (resource) {
            case OPERATION: return BridgeConstants.MAX_OPERATIONS_PER_UID;
            case SUBSCRIPTION: return BridgeConstants.MAX_SUBSCRIPTIONS_PER_UID;
            case STREAM: return BridgeConstants.MAX_STREAMS_PER_UID;
            default: throw new IllegalArgumentException("unknown resource");
        }
    }
}
