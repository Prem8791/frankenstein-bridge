package com.android.server.prodx;

import android.app.prodx.IProdXAuthority;
import android.app.prodx.IProdXConfirmationCallback;
import android.app.prodx.IProdXRegistryObserver;
import android.app.prodx.ProdXCapabilityDescriptor;
import android.app.prodx.ProdXCapabilityRequest;
import android.app.prodx.ProdXExecutionAuthorization;
import android.app.prodx.ProdXExecutionContext;
import android.app.prodx.ProdXHealth;
import android.app.prodx.ProdXMode;
import android.app.prodx.ProdXPolicyDecision;
import android.app.prodx.ProdXProviderManifest;
import android.app.prodx.ProdXRegistryEntry;
import android.app.prodx.ProdXRegistryGeneration;
import android.app.prodx.ProdXRegistrySnapshot;
import android.content.Context;
import android.os.Bundle;
import android.os.Binder;
import android.os.RemoteException;
import android.util.Slog;
import com.android.server.SystemService;
import java.util.ArrayList;
import java.util.List;
import java.security.MessageDigest;

public class ProdXAuthorityService extends SystemService {
    private static final String TAG = "ProdXAuthority";
    private static final String PERMISSION_AUTHORITY = "android.permission.PRODX_AUTHORITY";
    private static final String PERMISSION_BROKER = "android.permission.PRODX_BROKER";
    private static final String PERMISSION_ADMIN = "android.permission.PRODX_ADMIN";

    private ProdXMode mMode = ProdXMode.DISABLED;
    private boolean mOperational = false;
    private static final int MAX_CONFIRMATION_BYTES = 64 * 1024;
    private static final long CONFIRMATION_TTL_MS = 30_000L;
    private static final long ADMIN_AUTH_TTL_MS = 60_000L;
    private final Object mConfirmationLock = new Object();
    private IProdXConfirmationCallback mConfirmationCallback;
    private int mConfirmationUid = -1;
    private byte[] mPendingConfirmation;
    private long mPendingConfirmationExpiresAt;
    private int mPendingAdminAuthUid = -1;
    private int mAuthenticatedAdminUid = -1;
    private long mAuthenticatedAdminExpiresAt;

    private final ProdXContextBuilder mContextBuilder;
    private final ProdXGrantStore mGrantStore;
    private final ProdXRegistry mRegistry;
    private final ProdXPolicyEngine mPolicyEngine;
    private final ProdXAuthorizationEngine mAuthorizationEngine;
    private final ProdXComponentAttestation mAttestation;
    private final ProdXUserLifecycle mUserLifecycle;
    private final ProdXGrantAdminService mGrantAdminService;
    private final ProdXSettingsMediatorService mSettingsMediatorService;

    private final IProdXAuthority.Stub mBinder = new IProdXAuthority.Stub() {
        @Override public ProdXMode getMode() {
            enforcePermission(PERMISSION_AUTHORITY, "get ProdX mode");
            return mMode;
        }

        @Override public ProdXHealth getHealth() {
            enforcePermission(PERMISSION_AUTHORITY, "get ProdX health");
            return getHealthInternal();
        }

        @Override public ProdXExecutionContext deriveCallerContext(String purpose) {
            enforcePermission(PERMISSION_AUTHORITY, "derive ProdX caller context");
            return mContextBuilder.derive(getCallingUid(), purpose);
        }

        @Override public ProdXRegistryGeneration getRegistryGeneration() {
            enforcePermission(PERMISSION_AUTHORITY, "get ProdX registry generation");
            return mRegistry.getCurrentGeneration();
        }

        @Override public ProdXRegistrySnapshot getRegistrySnapshot(long generationId) {
            enforcePermission(PERMISSION_AUTHORITY, "get ProdX registry snapshot");
            return mRegistry.getSnapshot(generationId);
        }

        @Override public boolean resolveCapability(ProdXCapabilityDescriptor descriptor) {
            enforcePermission(PERMISSION_AUTHORITY, "resolve ProdX capability");
            return mRegistry.resolveCapability(descriptor);
        }

        @Override public ProdXPolicyDecision evaluatePolicy(
                ProdXExecutionContext context, ProdXCapabilityRequest request) {
            enforcePermission(PERMISSION_AUTHORITY, "evaluate ProdX policy");
            return mPolicyEngine.evaluate(context, request);
        }

        @Override public ProdXExecutionAuthorization mintAuthorization(
                ProdXPolicyDecision decision, byte[] proof) {
            enforcePermission(PERMISSION_BROKER, "mint ProdX authorization");
            return mAuthorizationEngine.mintAuthorization(decision, proof);
        }

        @Override public void emergencyDisable() {
            enforcePermission(PERMISSION_ADMIN, "emergency-disable ProdX");
            mMode = ProdXMode.DISABLED;
            mOperational = false;
            ProdXKillSwitch.emergencyDisable();
            mGrantStore.invalidateOnKillSwitch();
            mPolicyEngine.setMode(ProdXMode.DISABLED);
        }

        @Override public boolean isEmergencyDisabled() {
            enforcePermission(PERMISSION_AUTHORITY, "read ProdX emergency state");
            return mMode == ProdXMode.DISABLED || ProdXKillSwitch.isDisabled();
        }

        @Override public void setMode(ProdXMode mode) {
            enforcePermission(PERMISSION_ADMIN, "set ProdX mode");
            mMode = mode;
            mPolicyEngine.setMode(mode);
        }

        @Override public void registerRegistryObserver(IProdXRegistryObserver observer) {
            enforcePermission(PERMISSION_AUTHORITY, "register registry observer");
            mRegistry.registerObserver(observer);
        }

        @Override public void unregisterRegistryObserver(IProdXRegistryObserver observer) {
            enforcePermission(PERMISSION_AUTHORITY, "unregister registry observer");
            mRegistry.unregisterObserver(observer);
        }

        @Override public void registerConfirmationCallback(IProdXConfirmationCallback callback) {
            enforcePermission("android.permission.PRODX_CONFIRMATION", "register ProdX confirmation UI");
            if (callback == null) throw new IllegalArgumentException("callback required");
            final int callingUid = Binder.getCallingUid();
            if (!isTrustedSystemUiUid(callingUid)) {
                throw new SecurityException("ProdX confirmation UI must be SystemUI");
            }
            synchronized (mConfirmationLock) {
                clearConfirmationLocked(false);
                mConfirmationCallback = callback;
                mConfirmationUid = callingUid;
                final android.os.IBinder callbackBinder = callback.asBinder();
                try {
                    callbackBinder.linkToDeath(() -> {
                        synchronized (mConfirmationLock) {
                            if (mConfirmationCallback != null
                                    && mConfirmationCallback.asBinder() == callbackBinder) {
                                clearConfirmationLocked(false);
                            }
                        }
                    }, 0);
                } catch (RemoteException e) {
                    clearConfirmationLocked(false);
                    throw new IllegalStateException("confirmation UI already dead", e);
                }
            }
        }

        @Override public void unregisterConfirmationCallback(IProdXConfirmationCallback callback) {
            enforcePermission("android.permission.PRODX_CONFIRMATION", "unregister ProdX confirmation UI");
            synchronized (mConfirmationLock) {
                if (callback != null && mConfirmationCallback != null
                        && callback.asBinder() == mConfirmationCallback.asBinder()
                        && Binder.getCallingUid() == mConfirmationUid) {
                    clearConfirmationLocked(false);
                }
            }
        }

        @Override public boolean requestSyntheticConfirmation(byte[] canonicalChallenge) {
            enforcePermission(PERMISSION_ADMIN, "request synthetic ProdX confirmation");
            if (canonicalChallenge == null || canonicalChallenge.length == 0
                    || canonicalChallenge.length > MAX_CONFIRMATION_BYTES) return false;
            synchronized (mConfirmationLock) {
                if (mConfirmationCallback == null || mPendingConfirmation != null) return false;
                return requestConfirmationLocked(canonicalChallenge, -1);
            }
        }

        @Override public boolean submitConfirmationResult(
                byte[] canonicalChallenge, byte[] proof, boolean approved) {
            enforcePermission("android.permission.PRODX_CONFIRMATION", "submit ProdX confirmation");
            synchronized (mConfirmationLock) {
                boolean valid = Binder.getCallingUid() == mConfirmationUid
                        && mPendingConfirmation != null
                        && System.currentTimeMillis() < mPendingConfirmationExpiresAt
                        && canonicalChallenge != null
                        && MessageDigest.isEqual(mPendingConfirmation, canonicalChallenge)
                        && proof != null && proof.length == 32;
                IProdXConfirmationCallback callback = mConfirmationCallback;
                mPendingConfirmation = null;
                mPendingConfirmationExpiresAt = 0;
                if (valid && approved && mPendingAdminAuthUid >= 0) {
                    mAuthenticatedAdminUid = mPendingAdminAuthUid;
                    mAuthenticatedAdminExpiresAt = System.currentTimeMillis() + ADMIN_AUTH_TTL_MS;
                }
                mPendingAdminAuthUid = -1;
                if (callback != null) {
                    try { callback.onProofVerified(valid && approved); } catch (RemoteException ignored) { }
                }
                return valid;
            }
        }

        @Override public void cancelConfirmation(byte[] canonicalChallenge) {
            enforcePermission("android.permission.PRODX_CONFIRMATION", "cancel ProdX confirmation");
            synchronized (mConfirmationLock) {
                if (Binder.getCallingUid() != mConfirmationUid) return;
                if (mPendingConfirmation != null && canonicalChallenge != null
                        && !MessageDigest.isEqual(mPendingConfirmation, canonicalChallenge)) return;
                clearConfirmationLocked(true);
            }
        }
    };

    private void clearConfirmationLocked(boolean notify) {
        IProdXConfirmationCallback callback = mConfirmationCallback;
        mPendingConfirmation = null;
        mPendingConfirmationExpiresAt = 0;
        mPendingAdminAuthUid = -1;
        if (notify && callback != null) {
            try { callback.onCancelled(); } catch (RemoteException ignored) { }
        }
        if (!notify) {
            mConfirmationCallback = null;
            mConfirmationUid = -1;
        }
    }

    private boolean isTrustedSystemUiUid(int uid) {
        String[] packages = getContext().getPackageManager().getPackagesForUid(uid);
        if (packages == null) return false;
        for (String packageName : packages) {
            if ("com.android.systemui".equals(packageName)) return true;
        }
        return false;
    }

    private void enforcePermission(String permission, String operation) {
        getContext().enforceCallingOrSelfPermission(
                permission, "Permission required to " + operation);
    }

    public ProdXAuthorityService(Context context) {
        super(context);
        mContextBuilder = new ProdXContextBuilder(context);
        mGrantStore = new ProdXGrantStore();
        mRegistry = new ProdXRegistry(context);
        mPolicyEngine = new ProdXPolicyEngine(mGrantStore, mRegistry);
        mAuthorizationEngine = new ProdXAuthorizationEngine(mGrantStore, mPolicyEngine, mRegistry);
        mAttestation = new ProdXComponentAttestation(context);
        mUserLifecycle = new ProdXUserLifecycle(context);
        mGrantAdminService = new ProdXGrantAdminService(context, mGrantStore);
        mSettingsMediatorService = new ProdXSettingsMediatorService(context, mGrantStore, mRegistry,
                new ProdXSettingsMediatorService.AuthorityActions() {
                    @Override public ProdXHealth getHealth() { return getHealthInternal(); }
                    @Override public ProdXMode getMode() { return mMode; }
                    @Override public boolean requestAuthentication(int uid, byte[] challenge) {
                        synchronized (mConfirmationLock) {
                            if (mConfirmationCallback == null || mPendingConfirmation != null) {
                                return false;
                            }
                            return requestConfirmationLocked(challenge, uid);
                        }
                    }
                    @Override public boolean hasAuthentication(int uid) {
                        return hasAdminAuthentication(uid);
                    }
                    @Override public boolean consumeAuthentication(int uid) {
                        return consumeAdminAuthentication(uid);
                    }
                    @Override public void setMode(ProdXMode mode) { mMode = mode; }
                    @Override public void emergencyDisable() {
                        mMode = ProdXMode.DISABLED;
                        mOperational = false;
                        ProdXKillSwitch.emergencyDisable();
                        mGrantStore.invalidateOnKillSwitch();
                    }
                });
    }

    @Override
    public void onStart() {
        publishBinderService("prodx_authority", mBinder);
        publishBinderService("prodx_grant_admin", mGrantAdminService);
        publishBinderService("prodx_settings", mSettingsMediatorService);
        Slog.i(TAG, "ProdX Authority published (not ready)");
    }

    private ProdXHealth getHealthInternal() {
        boolean killSwitchActive = ProdXKillSwitch.isDisabled();
        String status = killSwitchActive ? "kill_switched"
                : (!mOperational ? "not_ready" : "operational");
        return new ProdXHealth(mOperational && !killSwitchActive, status);
    }

    private boolean requestConfirmationLocked(byte[] canonicalChallenge, int adminUid) {
        mPendingConfirmation = canonicalChallenge.clone();
        mPendingConfirmationExpiresAt = System.currentTimeMillis() + CONFIRMATION_TTL_MS;
        mPendingAdminAuthUid = adminUid;
        try {
            mConfirmationCallback.onChallengeReady(mPendingConfirmation.clone());
            return true;
        } catch (RemoteException e) {
            clearConfirmationLocked(false);
            return false;
        }
    }

    private boolean hasAdminAuthentication(int uid) {
        synchronized (mConfirmationLock) {
            if (uid != mAuthenticatedAdminUid
                    || System.currentTimeMillis() >= mAuthenticatedAdminExpiresAt) {
                if (System.currentTimeMillis() >= mAuthenticatedAdminExpiresAt) {
                    mAuthenticatedAdminUid = -1;
                    mAuthenticatedAdminExpiresAt = 0;
                }
                return false;
            }
            return true;
        }
    }

    private boolean consumeAdminAuthentication(int uid) {
        synchronized (mConfirmationLock) {
            if (!hasAdminAuthentication(uid)) return false;
            mAuthenticatedAdminUid = -1;
            mAuthenticatedAdminExpiresAt = 0;
            return true;
        }
    }

    private void clearAdminAuthentication() {
        synchronized (mConfirmationLock) {
            mAuthenticatedAdminUid = -1;
            mAuthenticatedAdminExpiresAt = 0;
            mPendingAdminAuthUid = -1;
        }
    }

    @Override
    public void onBootPhase(int phase) {
        Slog.i(TAG, "Boot phase: " + phase);
        if (phase == PHASE_LOCK_SETTINGS_READY) {
            ProdXKillSwitch.initialize(getContext());
            ProdXTamperEvidentEpoch.initialize();
        } else if (phase == PHASE_BOOT_COMPLETED) {
            loadBuiltinCatalog();
            mRegistry.reconcile();
            Slog.i(TAG, "Registry catalog loaded and reconciled");
        } else if (phase == PHASE_SYSTEM_SERVICES_READY) {
            mOperational = true;
        }
    }

    private void loadBuiltinCatalog() {
        List<ProdXRegistryEntry> catalog = new ArrayList<>();
        catalog.add(new ProdXRegistryEntry(
                new android.app.prodx.ProdXCapabilityDescriptor(
                        "prodx.identity.verify", "prodx.system", "1.0"),
                new ProdXProviderManifest("prodx.system",
                        "ProdX System Provider", "1.0"),
                true));
        catalog.add(new ProdXRegistryEntry(
                new android.app.prodx.ProdXCapabilityDescriptor(
                        "prodx.audit.append", "prodx.system", "1.0"),
                new ProdXProviderManifest("prodx.system",
                        "ProdX System Provider", "1.0"),
                true));
        mRegistry.loadCatalog(catalog);
    }

    @Override
    public void onUserStarting(TargetUser user) {
        Slog.i(TAG, "User starting: " + user);
        mUserLifecycle.onUserStarting(user.getUserIdentifier());
    }

    @Override
    public void onUserUnlocking(TargetUser user) {
        Slog.i(TAG, "User unlocking: " + user);
        mUserLifecycle.onUserUnlocking(user.getUserIdentifier(), Bundle.EMPTY);
    }

    @Override
    public void onUserUnlocked(TargetUser user) {
        Slog.i(TAG, "User unlocked (CE): " + user);
        mUserLifecycle.onUserUnlocked(user.getUserIdentifier());
    }

    @Override
    public void onUserStopping(TargetUser user) {
        Slog.i(TAG, "User stopping: " + user);
        clearAdminAuthentication();
        mUserLifecycle.onUserStopping(user.getUserIdentifier());
    }

}
