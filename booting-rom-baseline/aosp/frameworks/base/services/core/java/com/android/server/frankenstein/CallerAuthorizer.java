package com.android.server.frankenstein;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Process;
import android.os.UserHandle;

import com.android.server.LocalServices;
import android.content.pm.PackageManagerInternal;

final class CallerAuthorizer {
    private final Context mContext;
    private volatile PackageManagerInternal mPackageManagerInternal;

    CallerAuthorizer(Context context) {
        mContext = context;
    }

    void setPackageManagerReady() {
        mPackageManagerInternal = LocalServices.getService(PackageManagerInternal.class);
    }

    CallerContext authorizeBroker(int targetUserId) {
        CallerContext caller = resolve();
        mContext.enforceCallingOrSelfPermission(
                BridgeConstants.ACCESS_PERMISSION, "Frankenstein broker access");
        if (caller.uid != Process.SYSTEM_UID) {
            if (!caller.ownsPackage(BridgeConstants.BROKER_PACKAGE)
                    || caller.packages.length != 1) {
                throw new SecurityException("caller is not the dedicated broker UID");
            }
        }
        enforceTargetUser(caller, targetUserId);
        return caller;
    }

    CallerContext authorizeRegistration() {
        CallerContext caller = resolve();
        mContext.enforceCallingOrSelfPermission(
                BridgeConstants.REGISTER_PERMISSION, "Frankenstein provider registration");
        if (caller.packages.length != 1) throw new SecurityException("registration UID shared");
        return caller;
    }

    private CallerContext resolve() {
        int uid = Binder.getCallingUid();
        if (UserHandle.isIsolated(uid) || Process.isSdkSandboxUid(uid)) {
            throw new SecurityException("isolated and SDK sandbox callers are denied");
        }
        PackageManagerInternal pmi = mPackageManagerInternal;
        if (pmi == null && uid != Process.SYSTEM_UID) {
            throw new IllegalStateException("package manager is not ready");
        }
        String[] packages = mContext.getPackageManager().getPackagesForUid(uid);
        if (packages == null || packages.length == 0) {
            throw new SecurityException("caller UID has no packages");
        }
        return new CallerContext(uid, Binder.getCallingPid(), packages);
    }

    private void enforceTargetUser(CallerContext caller, int targetUserId) {
        if (targetUserId == UserHandle.USER_NULL) return;
        if (targetUserId < 0) {
            throw new IllegalArgumentException("target user must be concrete");
        }
        if (caller.userId != targetUserId) {
            mContext.enforceCallingOrSelfPermission(
                    android.Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                    "cross-user Frankenstein access");
        }
    }
}
