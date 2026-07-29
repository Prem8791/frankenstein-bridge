package com.android.server.prodx;

import android.util.Slog;

public class ProdXTamperEvidentEpoch {
    private static final String TAG = "ProdXEpoch";
    private static long sCurrentEpoch = 0;

    public static void initialize() {
        sCurrentEpoch = System.currentTimeMillis();
        Slog.i(TAG, "Epoch initialized: " + sCurrentEpoch);
    }

    public static long getCurrentEpoch() { return sCurrentEpoch; }

    public static void rollover() {
        sCurrentEpoch = System.currentTimeMillis();
        Slog.i(TAG, "Epoch rolled over: " + sCurrentEpoch);
    }

    public static boolean verifyEpoch(long epoch) {
        return epoch <= sCurrentEpoch;
    }
}
