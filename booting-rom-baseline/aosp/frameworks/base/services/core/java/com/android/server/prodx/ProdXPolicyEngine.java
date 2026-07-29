package com.android.server.prodx;

import android.app.prodx.ProdXPolicyDecision;
import android.app.prodx.ProdXExecutionContext;
import android.app.prodx.ProdXCapabilityRequest;
import android.app.prodx.ProdXCapabilityDescriptor;
import android.app.prodx.ProdXMode;
import android.util.Slog;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ProdXPolicyEngine {
    private static final String TAG = "ProdXPolicyEngine";
    private static final int POLICY_EPOCH = 0;

    private final ProdXGrantStore mGrantStore;
    private final ProdXRegistry mRegistry;
    private final AtomicInteger mPolicyEpoch = new AtomicInteger(0);
    private volatile ProdXMode mMode = ProdXMode.DISABLED;

    public ProdXPolicyEngine(ProdXGrantStore grantStore, ProdXRegistry registry) {
        mGrantStore = grantStore;
        mRegistry = registry;
        Slog.i(TAG, "Policy engine initialized (mode=DISABLED)");
    }

    public void setMode(ProdXMode mode) {
        if (mode != mMode) {
            mPolicyEpoch.incrementAndGet();
            mMode = mode;
            Slog.i(TAG, "Mode changed to " + mode + " (epoch=" + mPolicyEpoch.get() + ")");
        }
    }

    public ProdXPolicyDecision evaluate(ProdXExecutionContext context, ProdXCapabilityRequest request) {
        ProdXCapabilityDescriptor descriptor = new ProdXCapabilityDescriptor(
            request.getCapabilityId(), request.getTargetProvider(), "1");
        int userId = context.getUserId();
        String packageName = context.getPackageName();

        if (ProdXKillSwitch.isDisabled()) {
            return deny("kill_switch_active");
        }

        if (mMode == ProdXMode.DISABLED) {
            return deny("mode_disabled");
        }

        if (mMode == ProdXMode.INVENTORY_ONLY) {
            return deny("mode_inventory_only");
        }

        if (!mRegistry.resolveCapability(descriptor)) {
            return deny("capability_not_resolved");
        }

        if (mMode == ProdXMode.SHADOW_POLICY) {
            boolean wouldDeny = false;
            StringBuilder shadow = new StringBuilder("shadow");
            if (!mGrantStore.hasActiveGrant(packageName, descriptor.getCapabilityId(), userId)) {
                shadow.append("|no_active_grant");
                wouldDeny = true;
            }
            if (wouldDeny) {
                Slog.w(TAG, "Shadow deny: " + shadow + " for " + descriptor.getCapabilityId());
            }
            return new ProdXPolicyDecision(true, shadow.toString());
        }

        if (!mGrantStore.hasActiveGrant(packageName, descriptor.getCapabilityId(), userId)) {
            return deny("no_active_grant");
        }

        return new ProdXPolicyDecision(true, "allowed");
    }

    public int getCurrentPolicyEpoch() {
        return mPolicyEpoch.get();
    }

    public ProdXMode getCurrentMode() {
        return mMode;
    }

    private static ProdXPolicyDecision deny(String reason) {
        return new ProdXPolicyDecision(false, reason);
    }
}
