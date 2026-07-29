package com.android.server.prodx;

import android.util.Slog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

public class ProdXConfirmationChallenge {
    private static final String TAG = "ProdXChallenge";
    private static final long CHALLENGE_TTL_MS = 30_000;

    private final String mChallengeId;
    private final String mPurpose;
    private final long mCreatedAt;
    private final byte[] mBindingHash;
    private boolean mConsumed = false;

    public ProdXConfirmationChallenge(String purpose, byte[] bindingData) {
        mChallengeId = UUID.randomUUID().toString();
        mPurpose = purpose != null ? purpose : "unspecified";
        mCreatedAt = System.currentTimeMillis();
        mBindingHash = hashBinding(bindingData);
        Slog.i(TAG, "Challenge created: " + mChallengeId + " purpose=" + mPurpose);
    }

    public String getChallengeId() { return mChallengeId; }
    public String getPurpose() { return mPurpose; }
    public long getCreatedAt() { return mCreatedAt; }

    public boolean isExpired() {
        return mConsumed || System.currentTimeMillis() - mCreatedAt > CHALLENGE_TTL_MS;
    }

    public boolean verifyProof(byte[] proof) {
        if (isExpired()) return false;
        if (proof == null || proof.length == 0) return false;
        byte[] proofHash = hashBinding(proof);
        if (!MessageDigest.isEqual(mBindingHash, proofHash)) return false;
        mConsumed = true;
        return true;
    }

    private static byte[] hashBinding(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            if (data != null) md.update(data);
            return md.digest();
        } catch (Exception e) {
            Slog.wtf(TAG, "SHA-256 not available", e);
            return new byte[32];
        }
    }
}
