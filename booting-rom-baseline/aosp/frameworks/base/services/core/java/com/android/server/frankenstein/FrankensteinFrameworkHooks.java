package com.android.server.frankenstein;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.UserHandle;

import com.android.internal.os.frankenstein.BridgePayload;
import com.android.server.LocalServices;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Lock-safe capture facade for framework owners. Arguments are copied scalars;
 * encoding and LocalService lookup happen on a dedicated thread after return.
 */
public final class FrankensteinFrameworkHooks {
    private static final HandlerThread THREAD =
            new HandlerThread("FrankensteinCapture", Process.THREAD_PRIORITY_BACKGROUND);
    private static final Handler HANDLER;

    static {
        THREAD.start();
        HANDLER = new Handler(THREAD.getLooper());
    }

    private FrankensteinFrameworkHooks() {}

    public static void processDied(int uid, int pid, String processName, String reason) {
        emit("framework.activity", "process.changed", UserHandle.getUserId(uid), uid, pid, "",
                map("state", "died", "process", safe(processName), "reason", safe(reason)));
    }

    public static void crashOrAnr(boolean anr, int uid, int pid, String processName,
            String correlationId, int stableReason) {
        emit("framework.activity", anr ? "anr" : "crash", UserHandle.getUserId(uid), uid, pid,
                correlationId, map("process", safe(processName),
                        "reason_code", Integer.toString(stableReason)));
    }

    public static void packagePhase(int userId, int uid, long sessionId, String packageName,
            String phase, int resultCode) {
        emit("framework.package_provenance", "install.phase", userId, uid, -1,
                Long.toUnsignedString(sessionId),
                map("package", safe(packageName), "phase", phase,
                        "result_code", Integer.toString(resultCode)));
    }

    public static void focusChanged(int userId, int uid, int pid, int displayId,
            String component, boolean focused) {
        emit("framework.window_input", "focus.changed", userId, uid, pid, "",
                map("display_id", Integer.toString(displayId), "component", safe(component),
                        "focused", Boolean.toString(focused)));
    }

    public static void inputTimeout(int userId, int uid, int pid, long timeoutMs,
            int stableReason) {
        emit("framework.window_input", "input.timeout", userId, uid, pid, "",
                map("timeout_ms", Long.toString(timeoutMs),
                        "reason_code", Integer.toString(stableReason)));
    }

    public static void wakeLock(int uid, int pid, String tag, int level, boolean acquired,
            int releaseReason) {
        emit("framework.power_provenance", "wakelock", UserHandle.getUserId(uid), uid, pid, "",
                map("tag", safe(tag), "level", Integer.toString(level),
                        "acquired", Boolean.toString(acquired),
                        "release_reason", Integer.toString(releaseReason)));
    }

    public static void wakefulness(int uid, int groupId, int wakefulness, int reason) {
        emit("framework.power_provenance", "wake_sleep", UserHandle.getUserId(uid), uid, -1, "",
                map("group_id", Integer.toString(groupId),
                        "wakefulness", Integer.toString(wakefulness),
                        "reason", Integer.toString(reason)));
    }

    public static void thermal(int status) {
        emit("framework.power_provenance", "thermal", UserHandle.USER_SYSTEM, Process.SYSTEM_UID,
                Process.myPid(), "", map("status", Integer.toString(status)));
    }

    private static void emit(String provider, String event, int userId, int uid, int pid,
            String correlation, byte[] cbor) {
        HANDLER.post(() -> {
            FrankensteinBridgeInternal bridge =
                    LocalServices.getService(FrankensteinBridgeInternal.class);
            if (bridge == null) return;
            BridgePayload payload = new BridgePayload();
            payload.schemaId = provider + ".v1";
            payload.schemaVersion = 1;
            payload.encoding = 1;
            payload.data = cbor;
            bridge.emit(provider, event, 1, userId, uid, pid, correlation, payload);
        });
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.length() <= 256 ? value : value.substring(0, 256);
    }

    private static byte[] map(String... entries) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(0xa0 | (entries.length / 2));
        for (String entry : entries) text(output, entry);
        return output.toByteArray();
    }

    private static void text(ByteArrayOutputStream output, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(bytes.length, 255);
        if (length < 24) output.write(0x60 | length);
        else {
            output.write(0x78);
            output.write(length);
        }
        output.write(bytes, 0, length);
    }
}
