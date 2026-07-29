package android.app.prodx;

import android.app.prodx.ProdXHealth;
import android.app.prodx.ProdXAuditRecord;
import android.app.prodx.ProdXGrant;
import android.app.prodx.ProdXMode;
import android.app.prodx.ProdXRegistryEntry;

interface IProdXSettingsMediator {
    ProdXHealth getAuthorityHealth();
    ProdXMode getCurrentMode();
    List<ProdXRegistryEntry> getProviders();
    List<ProdXGrant> getGrants(int userId);
    List<String> getQuarantinedProviders();
    List<ProdXAuditRecord> getMinimizedAuditHistory(int userId, long sinceTimestamp, int limit);
    boolean requestSensitiveAdminAuthentication(in byte[] canonicalChallenge);
    boolean hasSensitiveAdminAuthentication();
    boolean setModeAuthenticated(in ProdXMode mode);
    boolean emergencyDisableAuthenticated();
    boolean revokeGrantAuthenticated(in String grantId);
    boolean suspendGrantAuthenticated(in String grantId);
    boolean setProviderQuarantinedAuthenticated(in String providerId, boolean quarantined);
}
