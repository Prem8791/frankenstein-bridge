package com.frankenbridge.assistant;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;

import com.frankenbridge.broker.api.BrokerActionRequest;
import com.frankenbridge.broker.api.BrokerActionResult;
import com.frankenbridge.broker.api.IBridgeBroker;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

public final class BridgeBrokerService extends Service {
    private static final Set<String> ALLOWED_CALLERS = Set.of(
            "com.frankenbridge.test",
            "com.prodx.assistant",
            "com.prodx.romhealth");
    private static final String BRIDGE_SERVICE = "frankenstein";
    private static final String EXPECTED_DESCRIPTOR =
            "com.android.internal.os.frankenstein.IFrankensteinBridge";
    private BrokerActionDispatcher mDispatcher;

    private final IBridgeBroker.Stub mBinder = new IBridgeBroker.Stub() {
        @Override
        public String probeBridge() {
            enforceAllowedCaller();
            final long token = Binder.clearCallingIdentity();
            try {
                IBinder bridge = findService(BRIDGE_SERVICE);
                if (bridge == null) {
                    return "FAIL: ROM service 'frankenstein' was not found";
                }
                String descriptor = bridge.getInterfaceDescriptor();
                if (!EXPECTED_DESCRIPTOR.equals(descriptor)) {
                    return "FAIL: unexpected descriptor: " + descriptor;
                }
                if (!bridge.pingBinder() || !bridge.isBinderAlive()) {
                    return "FAIL: bridge Binder exists but is not alive";
                }
                return "PASS\nTester → Broker → ROM bridge\n"
                        + "Descriptor: " + descriptor + "\nBinder alive: true";
            } catch (ReflectiveOperationException e) {
                return "FAIL: cannot access Android ServiceManager: " + e;
            } catch (RemoteException e) {
                return "FAIL: bridge Binder call failed: " + e;
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        public BrokerActionResult executeAction(BrokerActionRequest request) {
            enforceAllowedCaller();
            final long token = Binder.clearCallingIdentity();
            try {
                return mDispatcher.execute(request);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mDispatcher = new BrokerActionDispatcher(
                new FlashlightController(this),
                new RestrictedDeviceController(this));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void enforceAllowedCaller() {
        int uid = Binder.getCallingUid();
        String[] packages = getPackageManager().getPackagesForUid(uid);
        if (packages == null || Arrays.stream(packages).noneMatch(ALLOWED_CALLERS::contains)) {
            throw new SecurityException("Caller UID " + uid + " is not authorized");
        }
    }

    private static IBinder findService(String name) throws ReflectiveOperationException {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Method getService = serviceManager.getDeclaredMethod("getService", String.class);
        return (IBinder) getService.invoke(null, name);
    }
}
