package com.android.server.frankenstein;

import android.content.Context;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.IndentingPrintWriter;
import android.util.Slog;

import com.android.internal.util.DumpUtils;
import com.android.internal.os.frankenstein.BridgeInfo;
import com.android.internal.os.frankenstein.CapabilityEdge;
import com.android.internal.os.frankenstein.EventDescriptor;
import com.android.internal.os.frankenstein.EventEnvelope;
import com.android.internal.os.frankenstein.EventSubscription;
import com.android.internal.os.frankenstein.ExternalProviderRegistration;
import com.android.internal.os.frankenstein.IBridgeEventCallback;
import com.android.internal.os.frankenstein.IBridgeOperation;
import com.android.internal.os.frankenstein.IBridgeSubscription;
import com.android.internal.os.frankenstein.ICatalogObserver;
import com.android.internal.os.frankenstein.IExternalCapabilityProvider;
import com.android.internal.os.frankenstein.IFrankensteinBridge;
import com.android.internal.os.frankenstein.OperationDescriptor;
import com.android.internal.os.frankenstein.OperationRequest;
import com.android.internal.os.frankenstein.PageRequest;
import com.android.internal.os.frankenstein.ProviderDescriptor;
import com.android.internal.os.frankenstein.ProviderPage;
import com.android.internal.os.frankenstein.SchemaDescriptor;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.frankenstein.providers.CoreProvider;
import com.android.server.frankenstein.providers.DiagnosticProvider;
import com.android.server.frankenstein.providers.FrameworkEventProvider;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Permanent, policy-free Frankenstein control plane. */
public final class FrankensteinBridgeService extends SystemService {
    private static final String TAG = "FrankensteinBridge";

    private final long mBootGeneration = new SecureRandom().nextLong();
    private final HandlerThread mControlThread =
            new HandlerThread("FrankensteinControl", Process.THREAD_PRIORITY_BACKGROUND);
    private final HandlerThread mEventThread =
            new HandlerThread("FrankensteinEvents", Process.THREAD_PRIORITY_BACKGROUND);
    private final ExecutorService mDelivery = Executors.newFixedThreadPool(2);
    private final ExecutorService mDiagnosticIo = Executors.newFixedThreadPool(2);
    private final ProviderRegistry mProviders = new ProviderRegistry();
    private final SchemaRegistry mSchemas = new SchemaRegistry();
    private final QuotaTracker mQuotas = new QuotaTracker();
    private final AuditRing mAudit = new AuditRing();
    private CallerAuthorizer mAuthorizer;
    private OperationManager mOperations;
    private BridgeEventHub mEvents;
    private SnapshotCoordinator mSnapshots;
    private DiagnosticDaemonConnector mDiagnostic;
    private volatile boolean mPackageManagerReady;
    private volatile boolean mExternalRegistrationReady;

    public FrankensteinBridgeService(Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        mControlThread.start();
        mEventThread.start();
        mAuthorizer = new CallerAuthorizer(getContext());
        mEvents = new BridgeEventHub(new Handler(mEventThread.getLooper()), mDelivery,
                mQuotas, mBootGeneration);
        mOperations = new OperationManager(mProviders, mQuotas,
                new Handler(mControlThread.getLooper()));
        mSnapshots = new SnapshotCoordinator(mProviders, mEvents);
        mDiagnostic = new DiagnosticDaemonConnector(
                new Handler(mControlThread.getLooper()), mDiagnosticIo,
                this::onDaemonState);
        registerPermanentDescriptors();
        LocalServices.addService(FrankensteinBridgeInternal.class, mLocalService);
        publishBinderService(BridgeConstants.SERVICE_NAME, mBinder, false);
        mDiagnostic.start();
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_SYSTEM_SERVICES_READY) {
            mAuthorizer.setPackageManagerReady();
            mPackageManagerReady = true;
            setFrameworkProvidersReady();
        } else if (phase == PHASE_THIRD_PARTY_APPS_CAN_START) {
            mExternalRegistrationReady = true;
        } else if (phase == PHASE_BOOT_COMPLETED) {
            emitHealth("core.catalog", "boot.completed", 0);
        }
    }

    @Override
    public void onUserStopping(TargetUser user) {
        int userId = user.getUserIdentifier();
        mEvents.stopUser(userId);
        mOperations.cancelUser(userId);
        mProviders.removeOwnedBy(-1, userId);
    }

    private void registerPermanentDescriptors() {
        mProviders.addRom(new CoreProvider("core.catalog",
                new String[] {"reconcile_inventory"}, new String[] {"catalog.changed",
                        "health.changed", "boot.completed"}, new String[] {"core.catalog.v1"}));
        mProviders.addRom(new CoreProvider("core.events",
                new String[] {"current_sequence"}, new String[] {"gap"},
                new String[] {"core.events.v1"}));
        mProviders.addRom(new CoreProvider("core.snapshots",
                new String[] {"capture"}, new String[] {"snapshot.completed"},
                new String[] {"core.snapshots.v1"}));
        mProviders.addRom(new FrameworkEventProvider("framework.activity",
                new String[] {"process.changed", "uid.changed", "crash", "anr",
                        "memory.pressure"}, new String[] {"framework.activity.v1"}));
        mProviders.addRom(new FrameworkEventProvider("framework.package_provenance",
                new String[] {"install.phase", "verification", "scan", "rollback", "dexopt"},
                new String[] {"framework.package_provenance.v1"}));
        mProviders.addRom(new FrameworkEventProvider("framework.window_input",
                new String[] {"focus.changed", "transition", "input.device", "input.timeout"},
                new String[] {"framework.window_input.v1"}));
        mProviders.addRom(new FrameworkEventProvider("framework.power_provenance",
                new String[] {"wakelock", "suspend", "wake_sleep", "idle", "thermal"},
                new String[] {"framework.power_provenance.v1"}));
        mProviders.addRom(new FrameworkEventProvider("framework.device_policy",
                new String[0], new String[] {"framework.device_policy.v1"}));
        mProviders.addRom(new DiagnosticProvider("diag.services",
                new String[] {"list", "describe", "dump"}, new String[] {"diag.services.v1"},
                mDiagnostic, 1));
        mProviders.addRom(new DiagnosticProvider("diag.artifacts",
                new String[] {"list", "open"}, new String[] {"diag.artifacts.v1"},
                mDiagnostic, 2));
        mProviders.addRom(new DiagnosticProvider("diag.selinux",
                new String[] {"state", "context", "check", "avc"}, new String[] {"diag.selinux.v1"},
                mDiagnostic, 3));
        mProviders.addRom(new DiagnosticProvider("diag.properties",
                new String[] {"metadata", "read", "list", "observe", "write_allowlisted"},
                new String[] {"diag.properties.v1"}, mDiagnostic, 4));
        mProviders.addRom(new DiagnosticProvider("diag.boot",
                new String[] {"state", "prior_boot"}, new String[] {"diag.boot.v1"},
                mDiagnostic, 5));
    }

    private void setFrameworkProvidersReady() {
        for (String id : new String[] {"framework.activity", "framework.package_provenance",
                "framework.window_input", "framework.power_provenance"}) {
            com.android.server.frankenstein.providers.AbstractBridgeProvider provider =
                    (com.android.server.frankenstein.providers.AbstractBridgeProvider)
                            mProviders.getRom(id);
            provider.setReady();
        }
        com.android.server.frankenstein.providers.AbstractBridgeProvider dpm =
                (com.android.server.frankenstein.providers.AbstractBridgeProvider)
                        mProviders.getRom("framework.device_policy");
        dpm.setUnavailable("Stage 2 selected broker device-owner provisioning; no ROM DPM gateway");
    }

    private void onDaemonState(boolean available, long generation) {
        for (String id : new String[] {"diag.services", "diag.artifacts", "diag.selinux",
                "diag.properties", "diag.boot"}) {
            com.android.server.frankenstein.providers.AbstractBridgeProvider provider =
                    (com.android.server.frankenstein.providers.AbstractBridgeProvider)
                            mProviders.getRom(id);
            if (available) provider.setReady(); else provider.setUnavailable("diagnostic daemon absent");
        }
        emitHealth("diag.services", "health.changed",
                available ? BridgeConstants.OK : BridgeConstants.UNAVAILABLE);
    }

    private void emitHealth(String providerId, String eventId, int code) {
        EventEnvelope event = new EventEnvelope();
        event.providerId = providerId;
        event.eventId = eventId;
        event.eventVersion = 1;
        event.targetUserId = android.os.UserHandle.USER_NULL;
        event.uid = -1;
        event.pid = -1;
        event.correlationId = Integer.toString(code);
        mEvents.enqueue(event);
    }

    private final FrankensteinBridgeInternal mLocalService = new FrankensteinBridgeInternal() {
        @Override
        public void emit(String providerId, String eventId, int eventVersion, int targetUserId,
                int uid, int pid, String correlationId,
                com.android.internal.os.frankenstein.BridgePayload payload) {
            EventEnvelope event = new EventEnvelope();
            event.providerId = providerId;
            event.eventId = eventId;
            event.eventVersion = eventVersion;
            event.targetUserId = targetUserId;
            event.uid = uid;
            event.pid = pid;
            event.correlationId = correlationId;
            event.payload = payload;
            mEvents.enqueue(event);
        }

        @Override
        public void providerReady(String providerId) {
            BridgeProvider provider = mProviders.getRom(providerId);
            if (provider instanceof com.android.server.frankenstein.providers.AbstractBridgeProvider) {
                ((com.android.server.frankenstein.providers.AbstractBridgeProvider) provider)
                        .setReady();
            }
        }

        @Override
        public void providerUnavailable(String providerId, int stableReason) {
            BridgeProvider provider = mProviders.getRom(providerId);
            if (provider instanceof com.android.server.frankenstein.providers.AbstractBridgeProvider) {
                ((com.android.server.frankenstein.providers.AbstractBridgeProvider) provider)
                        .setUnavailable("reason " + stableReason);
            }
        }
    };

    private final IFrankensteinBridge.Stub mBinder = new IFrankensteinBridge.Stub() {
        @Override
        public int getInterfaceVersion() {
            return IFrankensteinBridge.VERSION;
        }

        @Override
        public String getInterfaceHash() {
            return IFrankensteinBridge.HASH;
        }

        @Override
        public BridgeInfo getBridgeInfo() {
            mAuthorizer.authorizeBroker(android.os.UserHandle.USER_NULL);
            BridgeInfo info = new BridgeInfo();
            info.protocolVersion = 1;
            info.interfaceHash = IFrankensteinBridge.HASH;
            info.bootGeneration = mBootGeneration;
            return info;
        }

        @Override
        public ProviderPage listProviders(PageRequest request) {
            mAuthorizer.authorizeBroker(android.os.UserHandle.USER_NULL);
            PageRequest page = request == null ? new PageRequest() : request;
            return mProviders.list(page.pageSize, page.pageToken, page.prefix);
        }

        @Override
        public ProviderDescriptor describeProvider(String id, int[] versions) {
            mAuthorizer.authorizeBroker(android.os.UserHandle.USER_NULL);
            return mProviders.describe(id);
        }

        @Override
        public OperationDescriptor[] listOperations(String providerId, PageRequest request) {
            mAuthorizer.authorizeBroker(android.os.UserHandle.USER_NULL);
            BridgeProvider provider = mProviders.getRom(providerId);
            return provider == null ? new OperationDescriptor[0] : provider.operations();
        }

        @Override
        public OperationDescriptor describeOperation(String providerId, String operationId,
                int[] versions) {
            for (OperationDescriptor descriptor : listOperations(providerId, null)) {
                if (descriptor.operationId.equals(operationId)) return descriptor;
            }
            return null;
        }

        @Override
        public EventDescriptor[] listEvents(String providerId, PageRequest request) {
            mAuthorizer.authorizeBroker(android.os.UserHandle.USER_NULL);
            ProviderDescriptor provider = mProviders.describe(providerId);
            if (provider == null || provider.eventIds == null) return new EventDescriptor[0];
            EventDescriptor[] events = new EventDescriptor[provider.eventIds.length];
            for (int i = 0; i < events.length; i++) {
                EventDescriptor event = new EventDescriptor();
                event.providerId = providerId;
                event.eventId = provider.eventIds[i];
                event.version = 1;
                event.schemaId = providerId + ".v1";
                event.userScoped = providerId.startsWith("framework.");
                events[i] = event;
            }
            return events;
        }

        @Override
        public EventDescriptor describeEvent(String providerId, String eventId, int[] versions) {
            for (EventDescriptor descriptor : listEvents(providerId, null)) {
                if (descriptor.eventId.equals(eventId)) return descriptor;
            }
            return null;
        }

        @Override
        public SchemaDescriptor getSchema(String schemaId, int[] versions) {
            mAuthorizer.authorizeBroker(android.os.UserHandle.USER_NULL);
            return mSchemas.get(schemaId, versions);
        }

        @Override
        public CapabilityEdge[] getCapabilityGraph(PageRequest request) {
            mAuthorizer.authorizeBroker(android.os.UserHandle.USER_NULL);
            return new CapabilityEdge[0];
        }

        @Override
        public void observeCatalog(ICatalogObserver callback) {
            mAuthorizer.authorizeBroker(android.os.UserHandle.USER_NULL);
            if (callback == null) throw new IllegalArgumentException("null observer");
        }

        @Override
        public IBridgeOperation startOperation(OperationRequest request) {
            CallerContext caller = mAuthorizer.authorizeBroker(
                    request == null ? android.os.UserHandle.USER_NULL : request.targetUserId);
            return mOperations.start(caller, request);
        }

        @Override
        public IBridgeSubscription subscribeEvents(EventSubscription subscription,
                IBridgeEventCallback callback) {
            CallerContext caller = mAuthorizer.authorizeBroker(
                    subscription == null ? android.os.UserHandle.USER_NULL
                            : subscription.targetUserId);
            return mEvents.subscribe(caller, subscription, callback);
        }

        @Override
        public ExternalProviderRegistration registerExternalProvider(
                ProviderDescriptor descriptor, IExternalCapabilityProvider provider) {
            if (!mExternalRegistrationReady) throw new IllegalStateException("not ready");
            CallerContext caller = mAuthorizer.authorizeRegistration();
            return mProviders.register(caller, caller.packages[0], descriptor, provider);
        }

        @Override
        public void updateExternalProvider(ExternalProviderRegistration registration,
                ProviderDescriptor descriptor) {
            mProviders.update(mAuthorizer.authorizeRegistration(), registration, descriptor);
        }

        @Override
        public void unregisterExternalProvider(ExternalProviderRegistration registration) {
            mProviders.unregister(mAuthorizer.authorizeRegistration(), registration);
        }

        @Override
        public IExternalCapabilityProvider getExternalProvider(String providerId,
                int[] versions) {
            mAuthorizer.authorizeBroker(android.os.UserHandle.USER_NULL);
            return mProviders.getExternal(providerId);
        }

        @Override
        protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
            if (!DumpUtils.checkDumpPermission(getContext(), TAG, writer)) return;
            IndentingPrintWriter out = new IndentingPrintWriter(writer, "  ");
            out.println("Frankenstein Bridge V1");
            out.println("bootGeneration=" + mBootGeneration);
            out.println("packageManagerReady=" + mPackageManagerReady);
            out.println("externalRegistrationReady=" + mExternalRegistrationReady);
            out.println("eventSequence=" + mEvents.currentSequence());
            out.println("audit:");
            out.increaseIndent();
            mAudit.dump(out);
            out.decreaseIndent();
        }
    };
}
