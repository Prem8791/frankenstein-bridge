package com.android.server.prodx.testables;

import android.app.prodx.IProdXAuthority;
import android.app.prodx.IProdXRegistryObserver;
import android.app.prodx.IProdXConfirmationCallback;
import android.app.prodx.ProdXCapabilityDescriptor;
import android.app.prodx.ProdXCapabilityRequest;
import android.app.prodx.ProdXExecutionContext;
import android.app.prodx.ProdXExecutionAuthorization;
import android.app.prodx.ProdXHealth;
import android.app.prodx.ProdXMode;
import android.app.prodx.ProdXPolicyDecision;
import android.app.prodx.ProdXRegistryGeneration;
import android.app.prodx.ProdXRegistrySnapshot;

public class FakeProdXAuthorityService extends IProdXAuthority.Stub {
    @Override public ProdXMode getMode() { return ProdXMode.TEST_NO_OP; }
    @Override public ProdXHealth getHealth() { return new ProdXHealth(true, "fake_operational"); }
    @Override public ProdXExecutionContext deriveCallerContext(String purpose) {
        return new ProdXExecutionContext(0, 0, "fake", purpose);
    }
    @Override public ProdXRegistryGeneration getRegistryGeneration() {
        return new ProdXRegistryGeneration(0, 0);
    }
    @Override public ProdXRegistrySnapshot getRegistrySnapshot(long generationId) { return null; }
    @Override public boolean resolveCapability(ProdXCapabilityDescriptor descriptor) { return false; }
    @Override public ProdXPolicyDecision evaluatePolicy(ProdXExecutionContext context, ProdXCapabilityRequest request) {
        return new ProdXPolicyDecision(false, "fake_not_implemented");
    }
    @Override public ProdXExecutionAuthorization mintAuthorization(ProdXPolicyDecision decision, byte[] proof) {
        return null;
    }
    @Override public void emergencyDisable() {}
    @Override public boolean isEmergencyDisabled() { return false; }
    @Override public void setMode(ProdXMode mode) {}
    @Override public void registerRegistryObserver(IProdXRegistryObserver observer) {}
    @Override public void unregisterRegistryObserver(IProdXRegistryObserver observer) {}
    @Override public void registerConfirmationCallback(IProdXConfirmationCallback callback) {}
    @Override public void unregisterConfirmationCallback(IProdXConfirmationCallback callback) {}
    @Override public boolean requestSyntheticConfirmation(byte[] challenge) { return false; }
    @Override public boolean submitConfirmationResult(byte[] challenge, byte[] proof, boolean approved) { return false; }
    @Override public void cancelConfirmation(byte[] challenge) {}
}
