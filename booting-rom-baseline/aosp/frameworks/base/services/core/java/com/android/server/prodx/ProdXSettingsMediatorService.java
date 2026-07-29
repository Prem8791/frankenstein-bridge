package com.android.server.prodx;

import android.app.prodx.IProdXSettingsMediator;
import android.app.prodx.ProdXAuditRecord;
import android.app.prodx.ProdXGrant;
import android.app.prodx.ProdXHealth;
import android.app.prodx.ProdXMode;
import android.app.prodx.ProdXRegistryEntry;
import android.app.prodx.ProdXRegistrySnapshot;
import android.content.Context;
import android.os.Binder;
import android.os.UserHandle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Authority-owned, fail-closed administration surface for Settings. */
final class ProdXSettingsMediatorService extends IProdXSettingsMediator.Stub {
    private static final String PERMISSION_ADMIN = "android.permission.PRODX_ADMIN";
    private static final int MAX_HISTORY_RECORDS = 100;

    interface AuthorityActions {
        ProdXHealth getHealth();
        ProdXMode getMode();
        boolean requestAuthentication(int uid, byte[] challenge);
        boolean hasAuthentication(int uid);
        boolean consumeAuthentication(int uid);
        void setMode(ProdXMode mode);
        void emergencyDisable();
    }

    private final Context mContext;
    private final ProdXGrantStore mGrantStore;
    private final ProdXRegistry mRegistry;
    private final AuthorityActions mActions;
    private final ArrayList<ProdXAuditRecord> mHistory = new ArrayList<>();

    ProdXSettingsMediatorService(Context context, ProdXGrantStore grantStore,
            ProdXRegistry registry, AuthorityActions actions) {
        mContext = context;
        mGrantStore = grantStore;
        mRegistry = registry;
        mActions = actions;
    }

    @Override public ProdXHealth getAuthorityHealth() {
        enforceAdmin();
        return mActions.getHealth();
    }

    @Override public ProdXMode getCurrentMode() {
        enforceAdmin();
        return mActions.getMode();
    }

    @Override public List<ProdXRegistryEntry> getProviders() {
        enforceAdmin();
        ProdXRegistrySnapshot snapshot = mRegistry.getSnapshot(
                mRegistry.getCurrentGeneration().getGenerationId());
        return snapshot == null ? Collections.emptyList() : snapshot.getEntries();
    }

    @Override public List<ProdXGrant> getGrants(int userId) {
        enforceAdmin();
        enforceUser(userId);
        return mGrantStore.getGrantsForUser(userId);
    }

    @Override public List<String> getQuarantinedProviders() {
        enforceAdmin();
        return mRegistry.getQuarantinedProviders();
    }

    @Override public synchronized List<ProdXAuditRecord> getMinimizedAuditHistory(
            int userId, long sinceTimestamp, int limit) {
        enforceAdmin();
        enforceUser(userId);
        int boundedLimit = Math.max(0, Math.min(limit, MAX_HISTORY_RECORDS));
        ArrayList<ProdXAuditRecord> result = new ArrayList<>();
        for (int i = mHistory.size() - 1; i >= 0 && result.size() < boundedLimit; i--) {
            ProdXAuditRecord record = mHistory.get(i);
            if (record.getUserId() == userId && record.getTimestamp() >= sinceTimestamp) {
                result.add(record);
            }
        }
        Collections.reverse(result);
        return result;
    }

    @Override public boolean requestSensitiveAdminAuthentication(byte[] canonicalChallenge) {
        enforceAdmin();
        if (canonicalChallenge == null || canonicalChallenge.length < 16
                || canonicalChallenge.length > 4096) return false;
        return mActions.requestAuthentication(Binder.getCallingUid(), canonicalChallenge.clone());
    }

    @Override public boolean hasSensitiveAdminAuthentication() {
        enforceAdmin();
        return mActions.hasAuthentication(Binder.getCallingUid());
    }

    @Override public boolean setModeAuthenticated(ProdXMode mode) {
        enforceAdmin();
        if (mode == null || !consumeAuthentication()) return false;
        mActions.setMode(mode);
        appendAdminRecord("MODE_CHANGED");
        return true;
    }

    @Override public boolean emergencyDisableAuthenticated() {
        enforceAdmin();
        if (!consumeAuthentication()) return false;
        mActions.emergencyDisable();
        appendAdminRecord("EMERGENCY_DISABLED");
        return true;
    }

    @Override public boolean revokeGrantAuthenticated(String grantId) {
        enforceAdmin();
        if (!validIdentifier(grantId) || !consumeAuthentication()) return false;
        boolean changed = mGrantStore.revokeGrant(grantId);
        if (changed) appendAdminRecord("GRANT_REVOKED");
        return changed;
    }

    @Override public boolean suspendGrantAuthenticated(String grantId) {
        enforceAdmin();
        if (!validIdentifier(grantId) || !consumeAuthentication()) return false;
        boolean changed = mGrantStore.suspendGrant(grantId);
        if (changed) appendAdminRecord("GRANT_SUSPENDED");
        return changed;
    }

    @Override public synchronized boolean setProviderQuarantinedAuthenticated(
            String providerId, boolean quarantined) {
        enforceAdmin();
        if (!validIdentifier(providerId) || !consumeAuthentication()) return false;
        boolean changed = mRegistry.setProviderQuarantined(providerId, quarantined);
        if (changed) appendAdminRecord(quarantined ? "PROVIDER_QUARANTINED" : "PROVIDER_RELEASED");
        return changed;
    }

    private boolean consumeAuthentication() {
        return mActions.consumeAuthentication(Binder.getCallingUid());
    }

    private synchronized void appendAdminRecord(String action) {
        long now = System.currentTimeMillis();
        mHistory.add(new ProdXAuditRecord(UUID.randomUUID().toString(), "admin", now,
                UserHandle.getUserId(Binder.getCallingUid()), action));
        if (mHistory.size() > MAX_HISTORY_RECORDS) mHistory.remove(0);
    }

    private void enforceAdmin() {
        mContext.enforceCallingPermission(PERMISSION_ADMIN,
                "ProdX Settings mediation requires PRODX_ADMIN");
    }

    private void enforceUser(int userId) {
        int callingUser = UserHandle.getUserId(Binder.getCallingUid());
        if (callingUser != userId && Binder.getCallingUid() != android.os.Process.SYSTEM_UID) {
            throw new SecurityException("cross-user ProdX administration denied");
        }
    }

    private static boolean validIdentifier(String value) {
        return value != null && !value.isEmpty() && value.length() <= 256;
    }
}
