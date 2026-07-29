package com.android.server.prodx;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.SigningInfo;
import android.util.Slog;

public class ProdXComponentAttestation {
    private static final String TAG = "ProdXAttestation";
    private final PackageManager mPm;

    public ProdXComponentAttestation(Context context) {
        mPm = context.getPackageManager();
    }

    public boolean verifyComponent(String packageName) {
        if (mPm == null) return false;
        try {
            mPm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            Slog.w(TAG, "Component not found: " + packageName);
            return false;
        }
    }
}
