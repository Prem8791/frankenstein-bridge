package com.android.internal.os.frankenstein;

import com.android.internal.os.frankenstein.EventEnvelope;

oneway interface IBridgeEventCallback {
    void onEvents(in EventEnvelope[] events, long barrierSequence, long lostBefore);
    void onClosed(int code, String correlationId);
}
