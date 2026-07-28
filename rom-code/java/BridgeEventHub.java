package com.android.server.frankenstein;

import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;

import com.android.internal.os.frankenstein.EventEnvelope;
import com.android.internal.os.frankenstein.EventSubscription;
import com.android.internal.os.frankenstein.IBridgeEventCallback;
import com.android.internal.os.frankenstein.IBridgeSubscription;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

final class BridgeEventHub {
    private static final int MAX_JOURNAL_EVENTS = 4096;
    private static final int MAX_JOURNAL_BYTES = 8 * 1024 * 1024;
    private static final int MAX_PENDING_EVENTS = 256;
    private static final int MAX_PENDING_BYTES = 512 * 1024;

    private final Object mLock = new Object();
    private final Handler mSequencer;
    private final Executor mDelivery;
    private final QuotaTracker mQuotas;
    private final ArrayDeque<EventEnvelope> mJournal = new ArrayDeque<>();
    private final Map<IBinder, Subscription> mSubscriptions = new HashMap<>();
    private final Map<String, Long> mProviderSequences = new HashMap<>();
    private final AtomicLong mSequence = new AtomicLong();
    private final long mBootGeneration;
    private int mJournalBytes;

    BridgeEventHub(Handler sequencer, Executor delivery, QuotaTracker quotas,
            long bootGeneration) {
        mSequencer = sequencer;
        mDelivery = delivery;
        mQuotas = quotas;
        mBootGeneration = bootGeneration;
    }

    void enqueue(EventEnvelope event) {
        if (event == null || event.providerId == null || event.eventId == null) return;
        mSequencer.post(() -> accept(event));
    }

    IBridgeSubscription subscribe(CallerContext caller, EventSubscription request,
            IBridgeEventCallback callback) {
        if (request == null || callback == null || request.targetUserId < 0) {
            throw new IllegalArgumentException("invalid subscription");
        }
        if (!mQuotas.acquire(caller.uid, QuotaTracker.SUBSCRIPTION)) {
            throw new IllegalStateException("subscription quota exceeded");
        }
        Subscription subscription = new Subscription(caller.uid, caller.userId, request, callback);
        try {
            callback.asBinder().linkToDeath(subscription::closeFromDeath, 0);
        } catch (RemoteException e) {
            mQuotas.release(caller.uid, QuotaTracker.SUBSCRIPTION);
            throw new IllegalStateException("callback already dead", e);
        }
        synchronized (mLock) {
            mSubscriptions.put(callback.asBinder(), subscription);
            for (EventEnvelope event : mJournal) {
                if (event.globalSequence > request.replayAfterSequence
                        && subscription.matches(event)) {
                    subscription.offerLocked(event);
                }
            }
        }
        subscription.schedule();
        return subscription;
    }

    long currentSequence() {
        return mSequence.get();
    }

    void stopUser(int userId) {
        ArrayList<Subscription> close = new ArrayList<>();
        synchronized (mLock) {
            for (Subscription subscription : mSubscriptions.values()) {
                if (subscription.mOwnerUserId == userId
                        || subscription.mRequest.targetUserId == userId) {
                    close.add(subscription);
                }
            }
        }
        for (Subscription subscription : close) subscription.cancel();
    }

    private void accept(EventEnvelope event) {
        event.bootGeneration = mBootGeneration;
        event.globalSequence = mSequence.incrementAndGet();
        event.providerSequence = mProviderSequences.merge(event.providerId, 1L, Long::sum);
        if (event.elapsedTimeMs == 0) event.elapsedTimeMs = SystemClock.elapsedRealtime();
        if (event.uptimeMs == 0) event.uptimeMs = SystemClock.uptimeMillis();
        if (event.wallTimeMs == 0) event.wallTimeMs = System.currentTimeMillis();
        int bytes = estimatedBytes(event);
        ArrayList<Subscription> deliver = new ArrayList<>();
        synchronized (mLock) {
            mJournal.addLast(event);
            mJournalBytes += bytes;
            while (mJournal.size() > MAX_JOURNAL_EVENTS || mJournalBytes > MAX_JOURNAL_BYTES) {
                mJournalBytes -= estimatedBytes(mJournal.removeFirst());
            }
            for (Subscription subscription : mSubscriptions.values()) {
                if (subscription.matches(event)) {
                    subscription.offerLocked(event);
                    deliver.add(subscription);
                }
            }
        }
        for (Subscription subscription : deliver) subscription.schedule();
    }

    private static int estimatedBytes(EventEnvelope event) {
        return 192 + (event.payload == null || event.payload.data == null
                ? 0 : event.payload.data.length);
    }

    private final class Subscription extends IBridgeSubscription.Stub {
        final int mOwnerUid;
        final int mOwnerUserId;
        final EventSubscription mRequest;
        final IBridgeEventCallback mCallback;
        final ArrayDeque<EventEnvelope> mPending = new ArrayDeque<>();
        boolean mClosed;
        boolean mScheduled;
        int mPendingBytes;
        long mLostBefore;
        long mAcknowledged;

        Subscription(int ownerUid, int ownerUserId, EventSubscription request,
                IBridgeEventCallback callback) {
            mOwnerUid = ownerUid;
            mOwnerUserId = ownerUserId;
            mRequest = request;
            mCallback = callback;
        }

        @Override
        public int getInterfaceVersion() {
            return IBridgeSubscription.VERSION;
        }

        @Override
        public String getInterfaceHash() {
            return IBridgeSubscription.HASH;
        }

        boolean matches(EventEnvelope event) {
            if (event.targetUserId >= 0 && event.targetUserId != mRequest.targetUserId) return false;
            return contains(mRequest.providerIds, event.providerId)
                    && contains(mRequest.eventIds, event.eventId);
        }

        void offerLocked(EventEnvelope event) {
            int bytes = estimatedBytes(event);
            while (!mPending.isEmpty()
                    && (mPending.size() >= MAX_PENDING_EVENTS
                    || mPendingBytes + bytes > MAX_PENDING_BYTES)) {
                EventEnvelope lost = mPending.removeFirst();
                mPendingBytes -= estimatedBytes(lost);
                mLostBefore = Math.max(mLostBefore, lost.globalSequence);
            }
            if (bytes <= MAX_PENDING_BYTES) {
                mPending.addLast(event);
                mPendingBytes += bytes;
            } else {
                mLostBefore = Math.max(mLostBefore, event.globalSequence);
            }
        }

        void schedule() {
            synchronized (mLock) {
                if (mClosed || mScheduled || mPending.isEmpty()) return;
                mScheduled = true;
            }
            mDelivery.execute(this::deliver);
        }

        void deliver() {
            EventEnvelope[] batch;
            long lost;
            synchronized (mLock) {
                int limit = Math.max(1, Math.min(64, mRequest.maxBatchCount));
                ArrayList<EventEnvelope> events = new ArrayList<>(limit);
                int bytes = 0;
                while (!mPending.isEmpty() && events.size() < limit) {
                    EventEnvelope next = mPending.peekFirst();
                    int size = estimatedBytes(next);
                    if (!events.isEmpty() && bytes + size > 256 * 1024) break;
                    events.add(mPending.removeFirst());
                    mPendingBytes -= size;
                    bytes += size;
                }
                batch = events.toArray(new EventEnvelope[0]);
                lost = mLostBefore;
                mScheduled = false;
            }
            try {
                mCallback.onEvents(batch, currentSequence(), lost);
            } catch (RemoteException e) {
                closeFromDeath();
                return;
            }
            schedule();
        }

        @Override
        public void acknowledge(long sequence) {
            enforceOwner();
            synchronized (mLock) {
                if (sequence < mAcknowledged || sequence > currentSequence()) {
                    throw new IllegalArgumentException("invalid acknowledgement");
                }
                mAcknowledged = sequence;
            }
        }

        @Override
        public long getCurrentSequence() {
            enforceOwner();
            return currentSequence();
        }

        @Override
        public void cancel() {
            enforceOwnerOrSystem();
            close(BridgeConstants.CANCELLED);
        }

        void closeFromDeath() {
            close(BridgeConstants.UNAVAILABLE);
        }

        void close(int code) {
            synchronized (mLock) {
                if (mClosed) return;
                mClosed = true;
                mSubscriptions.remove(mCallback.asBinder());
                mPending.clear();
                mPendingBytes = 0;
            }
            mQuotas.release(mOwnerUid, QuotaTracker.SUBSCRIPTION);
            mCallback.asBinder().unlinkToDeath(null, 0);
            try {
                mCallback.onClosed(code, "");
            } catch (RemoteException ignored) {
            }
        }

        private void enforceOwner() {
            if (Binder.getCallingUid() != mOwnerUid) throw new SecurityException("unowned handle");
        }

        private void enforceOwnerOrSystem() {
            int uid = Binder.getCallingUid();
            if (uid != mOwnerUid && uid != android.os.Process.SYSTEM_UID) {
                throw new SecurityException("unowned handle");
            }
        }
    }

    private static boolean contains(String[] values, String value) {
        if (values == null || values.length == 0) return true;
        for (String candidate : values) if (value.equals(candidate)) return true;
        return false;
    }
}
