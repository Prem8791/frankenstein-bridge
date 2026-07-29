package com.android.server.prodx;

import android.content.Context;
import android.os.Bundle;
import android.os.UserManager;
import android.util.Slog;

public class ProdXUserLifecycle {
    private static final String TAG = "ProdXUserLifecycle";
    private final Context mContext;

    public ProdXUserLifecycle(Context context) {
        mContext = context;
    }

    public void onUserStarting(int userId) {
        Slog.i(TAG, "DE state allocated for user " + userId);
    }

    public void onUserUnlocking(int userId, Bundle unlockedBundle) {
        Slog.i(TAG, "DE store unlocked for user " + userId);
    }

    public void onUserUnlocked(int userId) {
        Slog.i(TAG, "CE store available for user " + userId);
    }

    public void onUserStopping(int userId) {
        Slog.i(TAG, "Flushing state for user " + userId);
    }

    public void onUserRemoved(int userId) {
        Slog.i(TAG, "State destroyed for user " + userId);
    }
}
