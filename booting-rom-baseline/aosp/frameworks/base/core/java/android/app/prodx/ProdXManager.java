package android.app.prodx;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;
import java.util.Collections;
import java.util.List;

/**
 * Internal framework entry point for the ProdX runtime.
 *
 * @hide
 */
public class ProdXManager {
    private static final String TAG = "ProdXManager";
    private final Context mContext;
    private IProdXAuthority mService;
    private IProdXSettingsMediator mSettingsService;

    public ProdXManager(Context context) {
        mContext = context;
    }

    private IProdXAuthority getService() {
        if (mService == null) {
            android.os.IBinder b = ServiceManager.getService("prodx_authority");
            if (b != null) mService = IProdXAuthority.Stub.asInterface(b);
        }
        return mService;
    }

    private IProdXSettingsMediator getSettingsService() {
        if (mSettingsService == null) {
            android.os.IBinder b = ServiceManager.getService("prodx_settings");
            if (b != null) mSettingsService = IProdXSettingsMediator.Stub.asInterface(b);
        }
        return mSettingsService;
    }

    public ProdXHealth getSettingsHealth() {
        IProdXSettingsMediator s = getSettingsService();
        if (s == null) return new ProdXHealth(false, "settings_mediator_unavailable");
        try { return s.getAuthorityHealth(); } catch (RemoteException | SecurityException e) {
            return new ProdXHealth(false, "settings_mediator_error");
        }
    }

    public ProdXMode getSettingsMode() {
        IProdXSettingsMediator s = getSettingsService();
        if (s == null) return ProdXMode.DISABLED;
        try { return s.getCurrentMode(); } catch (RemoteException | SecurityException e) {
            return ProdXMode.DISABLED;
        }
    }

    public List<ProdXRegistryEntry> getAdminProviders() {
        IProdXSettingsMediator s = getSettingsService();
        if (s == null) return Collections.emptyList();
        try { return s.getProviders(); } catch (RemoteException | SecurityException e) {
            return Collections.emptyList();
        }
    }

    public List<ProdXGrant> getAdminGrants(int userId) {
        IProdXSettingsMediator s = getSettingsService();
        if (s == null) return Collections.emptyList();
        try { return s.getGrants(userId); } catch (RemoteException | SecurityException e) {
            return Collections.emptyList();
        }
    }

    public List<String> getQuarantinedProviders() {
        IProdXSettingsMediator s = getSettingsService();
        if (s == null) return Collections.emptyList();
        try { return s.getQuarantinedProviders(); } catch (RemoteException | SecurityException e) {
            return Collections.emptyList();
        }
    }

    public List<ProdXAuditRecord> getMinimizedAuditHistory(
            int userId, long sinceTimestamp, int limit) {
        IProdXSettingsMediator s = getSettingsService();
        if (s == null) return Collections.emptyList();
        try { return s.getMinimizedAuditHistory(userId, sinceTimestamp, limit); }
        catch (RemoteException | SecurityException e) { return Collections.emptyList(); }
    }

    public boolean requestSensitiveAdminAuthentication(byte[] canonicalChallenge) {
        IProdXSettingsMediator s = getSettingsService();
        if (s == null) return false;
        try { return s.requestSensitiveAdminAuthentication(canonicalChallenge); }
        catch (RemoteException | SecurityException e) { return false; }
    }

    public boolean hasSensitiveAdminAuthentication() {
        IProdXSettingsMediator s = getSettingsService();
        if (s == null) return false;
        try { return s.hasSensitiveAdminAuthentication(); }
        catch (RemoteException | SecurityException e) { return false; }
    }

    public boolean setModeAuthenticated(ProdXMode mode) {
        IProdXSettingsMediator s = getSettingsService();
        if (s == null || mode == null) return false;
        try { return s.setModeAuthenticated(mode); }
        catch (RemoteException | SecurityException e) { return false; }
    }

    public boolean emergencyDisableAuthenticated() {
        IProdXSettingsMediator s = getSettingsService();
        if (s == null) return false;
        try { return s.emergencyDisableAuthenticated(); }
        catch (RemoteException | SecurityException e) { return false; }
    }

    public boolean revokeGrantAuthenticated(String grantId) {
        IProdXSettingsMediator s = getSettingsService();
        if (s == null || grantId == null) return false;
        try { return s.revokeGrantAuthenticated(grantId); }
        catch (RemoteException | SecurityException e) { return false; }
    }

    public boolean suspendGrantAuthenticated(String grantId) {
        IProdXSettingsMediator s = getSettingsService();
        if (s == null || grantId == null) return false;
        try { return s.suspendGrantAuthenticated(grantId); }
        catch (RemoteException | SecurityException e) { return false; }
    }

    public boolean setProviderQuarantinedAuthenticated(
            String providerId, boolean quarantined) {
        IProdXSettingsMediator s = getSettingsService();
        if (s == null || providerId == null) return false;
        try { return s.setProviderQuarantinedAuthenticated(providerId, quarantined); }
        catch (RemoteException | SecurityException e) { return false; }
    }

    public ProdXMode getMode() {
        IProdXAuthority s = getService();
        if (s == null) return ProdXMode.DISABLED;
        try { return s.getMode(); } catch (RemoteException e) { return ProdXMode.DISABLED; }
    }

    public ProdXHealth getHealth() {
        IProdXAuthority s = getService();
        if (s == null) return new ProdXHealth(false, "service_unavailable");
        try { return s.getHealth(); } catch (RemoteException e) { return new ProdXHealth(false, "remote_error"); }
    }

    public boolean setMode(ProdXMode mode) {
        if (mode == null) return false;
        IProdXAuthority s = getService();
        if (s == null) return false;
        try {
            s.setMode(mode);
            return true;
        } catch (RemoteException | SecurityException e) {
            Slog.w(TAG, "Unable to set ProdX mode", e);
            return false;
        }
    }

    public boolean emergencyDisable() {
        IProdXAuthority s = getService();
        if (s == null) return false;
        try {
            s.emergencyDisable();
            return true;
        } catch (RemoteException | SecurityException e) {
            Slog.w(TAG, "Unable to emergency-disable ProdX", e);
            return false;
        }
    }

    public boolean isEmergencyDisabled() {
        IProdXAuthority s = getService();
        if (s == null) return true;
        try {
            return s.isEmergencyDisabled();
        } catch (RemoteException | SecurityException e) {
            return true;
        }
    }

    public ProdXRegistryGeneration getRegistryGeneration() {
        IProdXAuthority s = getService();
        if (s == null) return new ProdXRegistryGeneration(0, 0);
        try { return s.getRegistryGeneration(); } catch (RemoteException e) {
            return new ProdXRegistryGeneration(0, 0);
        }
    }

    public ProdXRegistrySnapshot getRegistrySnapshot(long generationId) {
        IProdXAuthority s = getService();
        if (s == null) return null;
        try { return s.getRegistrySnapshot(generationId); } catch (RemoteException e) { return null; }
    }

    public boolean resolveCapability(ProdXCapabilityDescriptor descriptor) {
        IProdXAuthority s = getService();
        if (s == null) return false;
        try { return s.resolveCapability(descriptor); } catch (RemoteException e) { return false; }
    }

    public ProdXExecutionContext deriveCallerContext(String purpose) {
        IProdXAuthority s = getService();
        if (s == null) return new ProdXExecutionContext(0, 0, "unknown", purpose);
        try { return s.deriveCallerContext(purpose); } catch (RemoteException e) {
            return new ProdXExecutionContext(0, 0, "unknown", purpose);
        }
    }

    public void registerRegistryObserver(IProdXRegistryObserver observer) {
        IProdXAuthority s = getService();
        if (s == null) return;
        try { s.registerRegistryObserver(observer); } catch (RemoteException e) { }
    }

    public void unregisterRegistryObserver(IProdXRegistryObserver observer) {
        IProdXAuthority s = getService();
        if (s == null) return;
        try { s.unregisterRegistryObserver(observer); } catch (RemoteException e) { }
    }

    public boolean registerConfirmationCallback(IProdXConfirmationCallback callback) {
        IProdXAuthority s = getService();
        if (s == null || callback == null) return false;
        try {
            s.registerConfirmationCallback(callback);
            return true;
        } catch (RemoteException | SecurityException e) {
            Slog.w(TAG, "Unable to register ProdX confirmation UI", e);
            return false;
        }
    }

    public void unregisterConfirmationCallback(IProdXConfirmationCallback callback) {
        IProdXAuthority s = getService();
        if (s == null || callback == null) return;
        try { s.unregisterConfirmationCallback(callback); } catch (RemoteException e) { }
    }

    public boolean submitConfirmationResult(
            byte[] canonicalChallenge, byte[] proof, boolean approved) {
        IProdXAuthority s = getService();
        if (s == null) return false;
        try {
            return s.submitConfirmationResult(canonicalChallenge, proof, approved);
        } catch (RemoteException | SecurityException e) {
            return false;
        }
    }

    public void cancelConfirmation(byte[] canonicalChallenge) {
        IProdXAuthority s = getService();
        if (s == null) return;
        try { s.cancelConfirmation(canonicalChallenge); } catch (RemoteException e) { }
    }
}
