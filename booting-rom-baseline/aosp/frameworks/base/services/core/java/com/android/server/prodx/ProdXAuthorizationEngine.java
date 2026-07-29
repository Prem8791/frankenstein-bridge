package com.android.server.prodx;

import android.app.prodx.ProdXExecutionAuthorization;
import android.app.prodx.ProdXPolicyDecision;
import android.util.Slog;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Cipher;

public class ProdXAuthorizationEngine {
    private static final String TAG = "ProdXAuthorizationEngine";
    private static final String KEY_ALIAS = "prodx_auth_key";
    private static final String KEYSTORE_TYPE = "AndroidKeyStore";
    private static final String SIGNATURE_ALGO = "SHA256withRSA";
    private static final long TOKEN_TTL_MS = 30_000;
    private static final long MAX_CLOCK_SKEW_MS = 5_000;

    private PrivateKey mPrivateKey;
    private PublicKey mPublicKey;
    private final Set<String> mUsedTokens = new HashSet<>();
    private final SecureRandom mRandom = new SecureRandom();
    private final ProdXGrantStore mGrantStore;
    private final ProdXPolicyEngine mPolicyEngine;
    private final ProdXRegistry mRegistry;

    public ProdXAuthorizationEngine(ProdXGrantStore grantStore,
            ProdXPolicyEngine policyEngine, ProdXRegistry registry) {
        mGrantStore = grantStore;
        mPolicyEngine = policyEngine;
        mRegistry = registry;
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
            ks.load(null);
            if (ks.containsAlias(KEY_ALIAS)) {
                Key key = ks.getKey(KEY_ALIAS, null);
                if (key instanceof PrivateKey) {
                    mPrivateKey = (PrivateKey) key;
                    mPublicKey = ks.getCertificate(KEY_ALIAS).getPublicKey();
                }
            }
            if (mPrivateKey == null) {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", KEYSTORE_TYPE);
                kpg.initialize(2048);
                java.security.KeyPair kp = kpg.generateKeyPair();
                mPrivateKey = kp.getPrivate();
                mPublicKey = kp.getPublic();
            }
            Slog.i(TAG, "Authorization engine initialized with key");
        } catch (Exception e) {
            Slog.e(TAG, "Failed to initialize AndroidKeyStore", e);
        }
    }

    public ProdXExecutionAuthorization mintAuthorization(ProdXPolicyDecision decision, byte[] proof) {
        if (mPrivateKey == null) {
            Slog.e(TAG, "Cannot mint: no private key");
            return null;
        }
        if (!decision.isAllowed()) {
            return null;
        }
        try {
            String authId = UUID.randomUUID().toString();
            byte[] nonce = new byte[16];
            mRandom.nextBytes(nonce);
            long now = System.currentTimeMillis();
            long expiresAt = now + TOKEN_TTL_MS;

            byte[] claims = buildClaims(decision, proof, now, expiresAt, nonce);
            Signature sig = Signature.getInstance(SIGNATURE_ALGO);
            sig.initSign(mPrivateKey);
            sig.update(claims);
            byte[] signature = sig.sign();

            ByteBuffer tokenBuf = ByteBuffer.allocate(4 + claims.length + 4 + signature.length);
            tokenBuf.putInt(claims.length);
            tokenBuf.put(claims);
            tokenBuf.putInt(signature.length);
            tokenBuf.put(signature);
            byte[] token = tokenBuf.array();

            mUsedTokens.add(bytesToHex(hash(token)));
            Slog.i(TAG, "Authorization minted: " + authId);
            return new ProdXExecutionAuthorization(authId, token, expiresAt);
        } catch (Exception e) {
            Slog.e(TAG, "Failed to mint authorization", e);
            return null;
        }
    }

    public boolean verifyAuthorization(byte[] token) {
        if (mPublicKey == null) {
            Slog.e(TAG, "Cannot verify: no public key");
            return false;
        }
        try {
            byte[] tokenHash = hash(token);
            if (mUsedTokens.contains(bytesToHex(tokenHash))) {
                Slog.w(TAG, "Replay detected");
                return false;
            }

            ByteBuffer buf = ByteBuffer.wrap(token);
            int claimsLen = buf.getInt();
            byte[] claims = new byte[claimsLen];
            buf.get(claims);
            int sigLen = buf.getInt();
            byte[] signature = new byte[sigLen];
            buf.get(signature);

            Signature sig = Signature.getInstance(SIGNATURE_ALGO);
            sig.initVerify(mPublicKey);
            sig.update(claims);
            if (!sig.verify(signature)) {
                Slog.w(TAG, "Invalid signature");
                return false;
            }

            ByteBuffer claimsBuf = ByteBuffer.wrap(claims);
            long issuedAt = claimsBuf.getLong();
            long expiresAt = claimsBuf.getLong();
            long now = System.currentTimeMillis();

            if (now < issuedAt - MAX_CLOCK_SKEW_MS) {
                Slog.w(TAG, "Token from future");
                return false;
            }
            if (now > expiresAt + MAX_CLOCK_SKEW_MS) {
                Slog.w(TAG, "Token expired");
                return false;
            }

            int registryEpoch = claimsBuf.getInt();
            int policyEpoch = claimsBuf.getInt();
            int grantEpoch = claimsBuf.getInt();

            if (registryEpoch != (int) mRegistry.getCurrentGeneration().getGenerationId()) {
                Slog.w(TAG, "Registry epoch mismatch");
                return false;
            }
            if (policyEpoch != mPolicyEngine.getCurrentPolicyEpoch()) {
                Slog.w(TAG, "Policy epoch mismatch");
                return false;
            }
            if (grantEpoch != mGrantStore.getCurrentGrantEpoch()) {
                Slog.w(TAG, "Grant epoch mismatch");
                return false;
            }

            mUsedTokens.add(bytesToHex(tokenHash));
            return true;
        } catch (Exception e) {
            Slog.e(TAG, "Failed to verify authorization", e);
            return false;
        }
    }

    private byte[] buildClaims(ProdXPolicyDecision decision, byte[] proof,
            long now, long expiresAt, byte[] nonce) {
        byte[] decisionHash = hash(decision.isAllowed()
                ? decision.getReason().getBytes(StandardCharsets.UTF_8)
                : new byte[]{0});
        byte[] proofHash = proof != null ? hash(proof) : new byte[32];

        int registryEpoch = (int) mRegistry.getCurrentGeneration().getGenerationId();
        int policyEpoch = mPolicyEngine.getCurrentPolicyEpoch();
        int grantEpoch = mGrantStore.getCurrentGrantEpoch();

        ByteBuffer buf = ByteBuffer.allocate(16 + 12 + 64 + 16 + nonce.length);
        buf.putLong(now);
        buf.putLong(expiresAt);
        buf.putInt(registryEpoch);
        buf.putInt(policyEpoch);
        buf.putInt(grantEpoch);
        buf.put(decisionHash);
        buf.put(proofHash);
        buf.put(nonce);
        return buf.array();
    }

    private static byte[] hash(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            Slog.wtf(TAG, "SHA-256 not available", e);
            return new byte[32];
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }
}
