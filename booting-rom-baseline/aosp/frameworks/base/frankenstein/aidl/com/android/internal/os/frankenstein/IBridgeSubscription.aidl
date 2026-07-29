package com.android.internal.os.frankenstein;

interface IBridgeSubscription {
    void acknowledge(long sequence);
    long getCurrentSequence();
    void cancel();
}
