package com.android.server.prodx;

import android.app.prodx.IProdXGrantAdmin;
import android.app.prodx.ProdXGrant;
import android.content.Context;
import android.os.Binder;
import android.util.Slog;
import java.util.List;

public class ProdXGrantAdminService extends IProdXGrantAdmin.Stub {
    private static final String TAG = "ProdXGrantAdmin";
    private static final String PERMISSION_ADMIN = "android.permission.PRODX_ADMIN";
    private final Context mContext;
    private final ProdXGrantStore mGrantStore;

    public ProdXGrantAdminService(Context context, ProdXGrantStore grantStore) {
        mContext = context;
        mGrantStore = grantStore;
    }

    @Override
    public List<ProdXGrant> getGrants(int userId) {
        enforceAdminPermission();
        return mGrantStore.getGrantsForUser(userId);
    }

    @Override
    public ProdXGrant getGrant(String grantId) {
        enforceAdminPermission();
        ProdXGrant grant = mGrantStore.getGrant(grantId);
        if (grant == null) return null;
        if (mGrantStore.isSuspended(grantId)) {
            return new ProdXGrant(grant.getGrantId(), grant.getUserId(),
                    grant.getPackageName(), grant.getCapabilityId(),
                    grant.getGrantedAt(), false);
        }
        return grant;
    }

    @Override
    public boolean revokeGrant(String grantId) {
        enforceAdminPermission();
        return mGrantStore.revokeGrant(grantId);
    }

    @Override
    public boolean suspendGrant(String grantId) {
        enforceAdminPermission();
        return mGrantStore.suspendGrant(grantId);
    }

    @Override
    public List<ProdXGrant> getGrantsByPackage(String packageName, int userId) {
        enforceAdminPermission();
        return mGrantStore.getGrantsForPackage(packageName, userId);
    }

    private void enforceAdminPermission() {
        mContext.enforceCallingPermission(
            PERMISSION_ADMIN,
            "ProdXGrantAdmin requires PRODX_ADMIN");
    }
}
