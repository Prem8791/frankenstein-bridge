package com.android.server.prodx;

import android.app.prodx.IProdXRegistryObserver;
import android.app.prodx.ProdXCapabilityDescriptor;
import android.app.prodx.ProdXProviderManifest;
import android.app.prodx.ProdXRegistryEntry;
import android.app.prodx.ProdXRegistryGeneration;
import android.app.prodx.ProdXRegistrySnapshot;
import android.content.Context;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.Slog;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ProdXRegistry {
    private static final String TAG = "ProdXRegistry";
    private static final String HASH_ALGO = "SHA-256";
    private static final String REGISTRY_FILE = "prodx_registry_snapshot";

    private final ArrayMap<String, ProdXRegistryEntry> mCatalog = new ArrayMap<>();
    private final ArrayMap<String, Boolean> mProviderAlive = new ArrayMap<>();
    private final ArraySet<String> mQuarantinedProviders = new ArraySet<>();
    private final List<IBinder> mObservers = new ArrayList<>();
    private final ReadWriteLock mLock = new ReentrantReadWriteLock();

    private volatile ProdXRegistrySnapshot mCurrentSnapshot;
    private volatile long mGenerationId = 0;
    private String mPreviousSnapshotHash = "";
    private AtomicFile mPersistentFile;

    public ProdXRegistry() {
        Slog.i(TAG, "Registry initialized (in-memory)");
    }

    public ProdXRegistry(Context context) {
        File dir = new File(Environment.getDataSystemDirectory(), "prodx");
        dir.mkdirs();
        mPersistentFile = new AtomicFile(new File(dir, REGISTRY_FILE));
        loadPersistedLocked();
        Slog.i(TAG, "Registry initialized (persistent, generation=" + mGenerationId + ")");
    }

    private void loadPersistedLocked() {
        if (mPersistentFile == null) return;
        try (FileInputStream fis = mPersistentFile.openRead()) {
            byte[] data = new byte[fis.available()];
            int read = fis.read(data);
            if (read <= 0) return;
            String content = new String(data, 0, read, StandardCharsets.UTF_8);
            String[] lines = content.split("\n");
            if (lines.length < 2) return;
            mGenerationId = Long.parseLong(lines[0].trim());
            mPreviousSnapshotHash = lines[1].trim();
            Slog.i(TAG, "Restored registry: generation=" + mGenerationId);
        } catch (Exception e) {
            Slog.w(TAG, "No persisted registry state to restore", e);
        }
    }

    private void persistLocked() {
        if (mPersistentFile == null) return;
        try (FileOutputStream fos = mPersistentFile.startWrite()) {
            String content = mGenerationId + "\n" + mPreviousSnapshotHash + "\n";
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            mPersistentFile.finishWrite(fos);
        } catch (Exception e) {
            Slog.e(TAG, "Failed to persist registry", e);
        }
    }

    public void loadCatalog(List<ProdXRegistryEntry> entries) {
        mLock.writeLock().lock();
        try {
            mCatalog.clear();
            for (ProdXRegistryEntry entry : entries) {
                mCatalog.put(entry.getDescriptor().getCapabilityId(), entry);
                mProviderAlive.put(entry.getDescriptor().getProviderId(), true);
            }
            Slog.i(TAG, "Catalog loaded: " + mCatalog.size() + " entries");
        } finally {
            mLock.writeLock().unlock();
        }
    }

    public void updateProviderStatus(String providerId, boolean alive) {
        mLock.writeLock().lock();
        try {
            Boolean previous = mProviderAlive.put(providerId, alive);
            if (previous == null || previous != alive) {
                reconcileLocked();
            }
        } finally {
            mLock.writeLock().unlock();
        }
    }

    public boolean setProviderQuarantined(String providerId, boolean quarantined) {
        mLock.writeLock().lock();
        try {
            boolean changed = quarantined
                    ? mQuarantinedProviders.add(providerId)
                    : mQuarantinedProviders.remove(providerId);
            if (changed) reconcileLocked();
            return changed;
        } finally {
            mLock.writeLock().unlock();
        }
    }

    public List<String> getQuarantinedProviders() {
        mLock.readLock().lock();
        try {
            return new ArrayList<>(mQuarantinedProviders);
        } finally {
            mLock.readLock().unlock();
        }
    }

    public ProdXRegistryGeneration getCurrentGeneration() {
        ProdXRegistrySnapshot snap = mCurrentSnapshot;
        if (snap != null) return snap.getGeneration();
        return new ProdXRegistryGeneration(mGenerationId, System.currentTimeMillis());
    }

    public ProdXRegistrySnapshot getSnapshot(long generationId) {
        ProdXRegistrySnapshot snap = mCurrentSnapshot;
        if (snap != null && snap.getGeneration().getGenerationId() == generationId) {
            return snap;
        }
        return null;
    }

    public boolean resolveCapability(ProdXCapabilityDescriptor descriptor) {
        mLock.readLock().lock();
        try {
            ProdXRegistryEntry entry = mCatalog.get(descriptor.getCapabilityId());
            if (entry == null) return false;
            if (!entry.getDescriptor().getProviderId().equals(descriptor.getProviderId())) {
                return false;
            }
            if (!entry.getDescriptor().getVersion().equals(descriptor.getVersion())) {
                return false;
            }
            if (!entry.isAvailable()) return false;
            if (mQuarantinedProviders.contains(descriptor.getProviderId())) return false;
            Boolean alive = mProviderAlive.get(descriptor.getProviderId());
            return alive == null || alive;
        } finally {
            mLock.readLock().unlock();
        }
    }

    public void registerObserver(IProdXRegistryObserver observer) {
        IBinder binder = observer.asBinder();
        mLock.writeLock().lock();
        try {
            if (mObservers.contains(binder)) return;
            mObservers.add(binder);
            try {
                binder.linkToDeath(() -> {
                    mLock.writeLock().lock();
                    try {
                        mObservers.remove(binder);
                    } finally {
                        mLock.writeLock().unlock();
                    }
                }, 0);
            } catch (RemoteException e) {
                mObservers.remove(binder);
            }
        } finally {
            mLock.writeLock().unlock();
        }
    }

    public void unregisterObserver(IProdXRegistryObserver observer) {
        IBinder binder = observer.asBinder();
        mLock.writeLock().lock();
        try {
            mObservers.remove(binder);
            binder.unlinkToDeath(null, 0);
        } finally {
            mLock.writeLock().unlock();
        }
    }

    public void reconcile() {
        mLock.writeLock().lock();
        try {
            reconcileLocked();
        } finally {
            mLock.writeLock().unlock();
        }
    }

    private void reconcileLocked() {
        mGenerationId++;
        List<ProdXRegistryEntry> entries = new ArrayList<>();
        for (ProdXRegistryEntry entry : mCatalog.values()) {
            boolean alive = mProviderAlive.getOrDefault(
                    entry.getDescriptor().getProviderId(), true);
            boolean quarantined = mQuarantinedProviders.contains(
                    entry.getDescriptor().getProviderId());
            entries.add(new ProdXRegistryEntry(
                    entry.getDescriptor(),
                    entry.getProvider(),
                    entry.isAvailable() && alive && !quarantined));
        }

        String rootHash = computeEntriesHash(entries);
        ProdXRegistryGeneration generation = new ProdXRegistryGeneration(
                mGenerationId, System.currentTimeMillis());
        ProdXRegistrySnapshot snapshot = new ProdXRegistrySnapshot(
                generation, entries, mPreviousSnapshotHash, rootHash);

        mPreviousSnapshotHash = computeSnapshot(generation, rootHash);
        mCurrentSnapshot = snapshot;

        Slog.i(TAG, "Generation " + mGenerationId + " published, root=" + rootHash);
        persistLocked();
        notifyObserversLocked();
    }

    private void notifyObserversLocked() {
        List<IBinder> dead = null;
        for (IBinder binder : mObservers) {
            try {
                IProdXRegistryObserver.Stub.asInterface(binder)
                        .onRegistryChanged(mGenerationId);
            } catch (RemoteException e) {
                if (dead == null) dead = new ArrayList<>();
                dead.add(binder);
            }
        }
        if (dead != null) mObservers.removeAll(dead);
    }

    private static String computeEntriesHash(List<ProdXRegistryEntry> entries) {
        try {
            MessageDigest md = MessageDigest.getInstance(HASH_ALGO);
            for (ProdXRegistryEntry entry : entries) {
                md.update(entry.getDescriptor().getCapabilityId()
                        .getBytes(StandardCharsets.UTF_8));
                md.update(entry.getDescriptor().getProviderId()
                        .getBytes(StandardCharsets.UTF_8));
                md.update(entry.getProvider().getProviderId()
                        .getBytes(StandardCharsets.UTF_8));
                md.update((byte) (entry.isAvailable() ? 1 : 0));
            }
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            Slog.wtf(TAG, "SHA-256 not available", e);
            return "";
        }
    }

    private static String computeSnapshot(ProdXRegistryGeneration gen, String rootHash) {
        try {
            MessageDigest md = MessageDigest.getInstance(HASH_ALGO);
            ByteBuffer buf = ByteBuffer.allocate(16);
            buf.putLong(gen.getGenerationId());
            buf.putLong(gen.getTimestamp());
            md.update(buf.array());
            md.update(rootHash.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            Slog.wtf(TAG, "SHA-256 not available", e);
            return "";
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
