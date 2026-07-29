package com.android.server.frankenstein;

import android.os.IBinder;
import android.os.RemoteException;

import com.android.internal.os.frankenstein.ExternalProviderRegistration;
import com.android.internal.os.frankenstein.IExternalCapabilityProvider;
import com.android.internal.os.frankenstein.ProviderDescriptor;
import com.android.internal.os.frankenstein.ProviderPage;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ProviderRegistry {
    private final Object mLock = new Object();
    private final Map<String, BridgeProvider> mRom = new HashMap<>();
    private final Map<String, ExternalRecord> mExternal = new HashMap<>();
    private final SecureRandom mRandom = new SecureRandom();
    private long mGeneration = 1;

    void addRom(BridgeProvider provider) {
        ProviderDescriptor descriptor = provider.descriptor();
        validateId(descriptor.providerId);
        synchronized (mLock) {
            if (mRom.containsKey(descriptor.providerId)
                    || mExternal.containsKey(descriptor.providerId)) {
                throw new IllegalArgumentException("duplicate provider ID");
            }
            mRom.put(descriptor.providerId, provider);
            mGeneration++;
        }
    }

    BridgeProvider getRom(String providerId) {
        synchronized (mLock) {
            return mRom.get(providerId);
        }
    }

    ProviderDescriptor describe(String providerId) {
        synchronized (mLock) {
            BridgeProvider provider = mRom.get(providerId);
            if (provider != null) return provider.descriptor();
            ExternalRecord external = mExternal.get(providerId);
            return external == null ? null : external.descriptor;
        }
    }

    ProviderPage list(int pageSize, String after, String prefix) {
        synchronized (mLock) {
            ArrayList<String> ids = new ArrayList<>(mRom.keySet());
            ids.addAll(mExternal.keySet());
            Collections.sort(ids);
            ArrayList<ProviderDescriptor> page = new ArrayList<>();
            String next = "";
            int limit = Math.max(1, Math.min(200, pageSize));
            for (String id : ids) {
                if (after != null && !after.isEmpty() && id.compareTo(after) <= 0) continue;
                if (prefix != null && !prefix.isEmpty() && !id.startsWith(prefix)) continue;
                if (page.size() == limit) {
                    next = page.get(page.size() - 1).providerId;
                    break;
                }
                page.add(describe(id));
            }
            ProviderPage result = new ProviderPage();
            result.providers = page.toArray(new ProviderDescriptor[0]);
            result.nextPageToken = next;
            result.catalogGeneration = mGeneration;
            return result;
        }
    }

    ExternalProviderRegistration register(CallerContext caller, String packageName,
            ProviderDescriptor descriptor, IExternalCapabilityProvider binder) {
        validateExternal(caller, packageName, descriptor, binder);
        synchronized (mLock) {
            int owned = 0;
            for (ExternalRecord record : mExternal.values()) {
                if (record.ownerUid == caller.uid) owned++;
            }
            if (owned >= BridgeConstants.MAX_EXTERNAL_PROVIDERS_PER_UID) {
                throw new IllegalStateException("external provider quota exceeded");
            }
            if (mRom.containsKey(descriptor.providerId)
                    || mExternal.containsKey(descriptor.providerId)) {
                throw new IllegalArgumentException("provider namespace already exists");
            }
            long generation = ++mGeneration;
            String token = randomToken();
            ExternalRecord record = new ExternalRecord(
                    caller.uid, caller.userId, generation, token, descriptor, binder);
            try {
                binder.asBinder().linkToDeath(() -> removeDead(descriptor.providerId, generation), 0);
            } catch (RemoteException e) {
                throw new IllegalStateException("provider already dead", e);
            }
            mExternal.put(descriptor.providerId, record);
            return record.registration();
        }
    }

    void update(CallerContext caller, ExternalProviderRegistration registration,
            ProviderDescriptor descriptor) {
        synchronized (mLock) {
            ExternalRecord record = requireOwned(caller, registration);
            if (!record.descriptor.providerId.equals(descriptor.providerId)) {
                throw new IllegalArgumentException("provider ID is immutable");
            }
            record.descriptor = descriptor;
            mGeneration++;
        }
    }

    void unregister(CallerContext caller, ExternalProviderRegistration registration) {
        synchronized (mLock) {
            ExternalRecord record = requireOwned(caller, registration);
            mExternal.remove(record.descriptor.providerId);
            record.binder.asBinder().unlinkToDeath(null, 0);
            mGeneration++;
        }
    }

    IExternalCapabilityProvider getExternal(String providerId) {
        synchronized (mLock) {
            ExternalRecord record = mExternal.get(providerId);
            return record == null ? null : record.binder;
        }
    }

    void removeOwnedBy(int uid, int userId) {
        synchronized (mLock) {
            List<String> remove = new ArrayList<>();
            for (Map.Entry<String, ExternalRecord> entry : mExternal.entrySet()) {
                ExternalRecord record = entry.getValue();
                if (record.ownerUid == uid || record.ownerUserId == userId) {
                    remove.add(entry.getKey());
                }
            }
            for (String id : remove) mExternal.remove(id);
            if (!remove.isEmpty()) mGeneration++;
        }
    }

    private void removeDead(String providerId, long generation) {
        synchronized (mLock) {
            ExternalRecord record = mExternal.get(providerId);
            if (record != null && record.generation == generation) {
                mExternal.remove(providerId);
                mGeneration++;
            }
        }
    }

    private ExternalRecord requireOwned(CallerContext caller,
            ExternalProviderRegistration registration) {
        ExternalRecord record = mExternal.get(registration.providerId);
        if (record == null || record.ownerUid != caller.uid
                || record.generation != registration.generation
                || !record.token.equals(registration.token)) {
            throw new SecurityException("stale or unowned registration");
        }
        return record;
    }

    private static void validateExternal(CallerContext caller, String packageName,
            ProviderDescriptor descriptor, IExternalCapabilityProvider binder) {
        if (binder == null || descriptor == null) throw new IllegalArgumentException("null provider");
        validateId(descriptor.providerId);
        String namespace = "external." + reverse(packageName) + ".";
        if (!descriptor.providerId.startsWith(namespace)) {
            throw new SecurityException("provider outside caller namespace");
        }
    }

    private static void validateId(String id) {
        if (id == null || !id.matches("[a-z][a-z0-9]*(?:\\.[a-z0-9_]+)+")
                || id.length() > 160) {
            throw new IllegalArgumentException("invalid stable ID");
        }
    }

    private static String reverse(String packageName) {
        String[] parts = packageName.split("\\.");
        StringBuilder out = new StringBuilder();
        for (int i = parts.length - 1; i >= 0; i--) {
            if (out.length() > 0) out.append('.');
            out.append(parts[i]);
        }
        return out.toString();
    }

    private String randomToken() {
        byte[] token = new byte[24];
        mRandom.nextBytes(token);
        return android.util.Base64.encodeToString(
                token, android.util.Base64.NO_WRAP | android.util.Base64.URL_SAFE);
    }

    private static final class ExternalRecord {
        final int ownerUid;
        final int ownerUserId;
        final long generation;
        final String token;
        final IExternalCapabilityProvider binder;
        ProviderDescriptor descriptor;

        ExternalRecord(int ownerUid, int ownerUserId, long generation, String token,
                ProviderDescriptor descriptor, IExternalCapabilityProvider binder) {
            this.ownerUid = ownerUid;
            this.ownerUserId = ownerUserId;
            this.generation = generation;
            this.token = token;
            this.descriptor = descriptor;
            this.binder = binder;
        }

        ExternalProviderRegistration registration() {
            ExternalProviderRegistration result = new ExternalProviderRegistration();
            result.providerId = descriptor.providerId;
            result.generation = generation;
            result.token = token;
            result.ownerUid = ownerUid;
            result.ownerUserId = ownerUserId;
            result.expiresElapsedMs = Long.MAX_VALUE;
            return result;
        }
    }
}
