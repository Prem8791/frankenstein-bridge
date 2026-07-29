package android.app.prodx;

import android.app.prodx.IProdXRegistryObserver;
import android.app.prodx.IProdXConfirmationCallback;
import android.app.prodx.ProdXCapabilityDescriptor;
import android.app.prodx.ProdXCapabilityRequest;
import android.app.prodx.ProdXExecutionAuthorization;
import android.app.prodx.ProdXExecutionContext;
import android.app.prodx.ProdXHealth;
import android.app.prodx.ProdXMode;
import android.app.prodx.ProdXPolicyDecision;
import android.app.prodx.ProdXRegistryGeneration;
import android.app.prodx.ProdXRegistrySnapshot;

interface IProdXAuthority {
    ProdXMode getMode();
    ProdXHealth getHealth();
    ProdXExecutionContext deriveCallerContext(in String purpose);
    ProdXRegistryGeneration getRegistryGeneration();
    ProdXRegistrySnapshot getRegistrySnapshot(long generationId);
    boolean resolveCapability(in ProdXCapabilityDescriptor descriptor);
    ProdXPolicyDecision evaluatePolicy(in ProdXExecutionContext context, in ProdXCapabilityRequest request);
    ProdXExecutionAuthorization mintAuthorization(in ProdXPolicyDecision decision, in byte[] proof);
    void emergencyDisable();
    boolean isEmergencyDisabled();
    void setMode(in ProdXMode mode);
    void registerRegistryObserver(in IProdXRegistryObserver observer);
    void unregisterRegistryObserver(in IProdXRegistryObserver observer);
    void registerConfirmationCallback(in IProdXConfirmationCallback callback);
    void unregisterConfirmationCallback(in IProdXConfirmationCallback callback);
    boolean requestSyntheticConfirmation(in byte[] canonicalChallenge);
    boolean submitConfirmationResult(in byte[] canonicalChallenge, in byte[] proof, boolean approved);
    void cancelConfirmation(in byte[] canonicalChallenge);
}
