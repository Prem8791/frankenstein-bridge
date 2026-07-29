package com.android.server.prodx;

import android.content.Context;
import android.util.Slog;

public class ProdXKillSwitch {
    private static final String TAG = "ProdXKillSwitch";
    private static boolean sDisabled = false;

    public static void initialize(Context context) {
        sDisabled = false;
        Slog.i(TAG, "Kill switch initialized (enabled)");
    }

    public static boolean isDisabled() { return sDisabled; }

    public static void emergencyDisable() {
        sDisabled = true;
        Slog.w(TAG, "EMERGENCY DISABLE ACTIVATED");
    }
}
