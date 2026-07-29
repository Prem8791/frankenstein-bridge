package com.android.server.prodx;

import android.app.prodx.ProdXGrant;
import android.util.ArrayMap;
import android.util.ArraySet;
import java.util.ArrayList;
import java.util.List;

public class ProdXGrantStore {
    private final ArrayMap<String, ProdXGrant> mGrants = new ArrayMap<>();
    private final ArraySet<String> mSuspendedGrants = new ArraySet<>();
    private int mGrantEpoch = 0;

    public ProdXGrantStore() {}

    public synchronized int getCurrentGrantEpoch() {
        return mGrantEpoch;
    }

    public synchronized List<ProdXGrant> getGrantsForUser(int userId) {
        List<ProdXGrant> result = new ArrayList<>();
        for (ProdXGrant grant : mGrants.values()) {
            if (grant.getUserId() == userId) result.add(grant);
        }
        return result;
    }

    public synchronized List<ProdXGrant> getGrantsForPackage(String packageName, int userId) {
        List<ProdXGrant> result = new ArrayList<>();
        for (ProdXGrant grant : mGrants.values()) {
            if (grant.getPackageName().equals(packageName) && grant.getUserId() == userId) {
                result.add(grant);
            }
        }
        return result;
    }

    public synchronized ProdXGrant getGrant(String grantId) {
        return mGrants.get(grantId);
    }

    public synchronized boolean revokeGrant(String grantId) {
        mSuspendedGrants.remove(grantId);
        if (mGrants.remove(grantId) != null) {
            mGrantEpoch++;
            return true;
        }
        return false;
    }

    public synchronized boolean addGrant(ProdXGrant grant) {
        if (mGrants.containsKey(grant.getGrantId())) return false;
        mGrants.put(grant.getGrantId(), grant);
        mGrantEpoch++;
        return true;
    }

    public synchronized boolean suspendGrant(String grantId) {
        if (!mGrants.containsKey(grantId)) return false;
        mSuspendedGrants.add(grantId);
        mGrantEpoch++;
        return true;
    }

    public synchronized boolean unsuspendGrant(String grantId) {
        if (mSuspendedGrants.remove(grantId)) {
            mGrantEpoch++;
            return true;
        }
        return false;
    }

    public synchronized boolean isSuspended(String grantId) {
        return mSuspendedGrants.contains(grantId);
    }

    public synchronized boolean hasActiveGrant(String packageName, String capabilityId, int userId) {
        for (ProdXGrant grant : mGrants.values()) {
            if (grant.getPackageName().equals(packageName)
                    && grant.getCapabilityId().equals(capabilityId)
                    && grant.getUserId() == userId
                    && grant.isActive()
                    && !mSuspendedGrants.contains(grant.getGrantId())) {
                return true;
            }
        }
        return false;
    }

    public synchronized void invalidateOnKillSwitch() {
        mGrantEpoch++;
        mSuspendedGrants.addAll(mGrants.keySet());
    }
}
