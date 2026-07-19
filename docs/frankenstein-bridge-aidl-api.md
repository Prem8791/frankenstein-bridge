# Frankenstein Bridge — AIDL / API Surface

## 1. Core Service Interface

File: `frameworks/base/core/java/android/os/frankenbridge/IFrankenBridgeService.aidl`

```aidl
package android.os.frankenbridge;

import android.os.frankenbridge.FrankenBridgeCallerIdentity;
import android.os.frankenbridge.FrankenBridgeCapability;
import android.os.frankenbridge.FrankenBridgeRequest;
import android.os.frankenbridge.FrankenBridgeResult;
import android.os.frankenbridge.FrankenBridgeEvent;
import android.os.frankenbridge.IFrankenBridgeCallback;
import android.os.frankenbridge.IFrankenBridgeEventCallback;

/**
 * Main interface for the Frankenstein Bridge service.
 * All methods check real Binder caller UID/PID — never trust arguments
 * for identity.
 */
interface IFrankenBridgeService {

    // ─── Lifecycle ─────────────────────────────────────────

    /** Returns the bridge API version (major.minor.patch). */
    String getVersion();

    /** Returns the list of capabilities this build supports. */
    List<FrankenBridgeCapability> getCapabilities();

    /** Check if a named capability is currently enabled. */
    boolean isCapabilityEnabled(String capabilityId);

    // ─── Foreground App ───────────────────────────────────

    /**
     * Get the currently focused app's package name and activity.
     * Requires REAL_GET_TASKS or MANAGE_ACTIVITY_TASKS.
     */
    FrankenBridgeResult getForegroundApp();

    /**
     * Register a listener for foreground app changes.
     * Returns a listener handle (needs unregister).
     */
    void registerForegroundCallback(IFrankenBridgeCallback callback);

    void unregisterForegroundCallback(IFrankenBridgeCallback callback);

    // ─── Tasks ────────────────────────────────────────────

    /**
     * List recent tasks with metadata.
     * maxResults caps the returned count (1-20).
     * Requires MANAGE_ACTIVITY_TASKS.
     */
    FrankenBridgeResult getRecentTasks(int maxResults);

    /**
     * Bring a task to the foreground by taskId.
     * Requires MANAGE_ACTIVITY_TASKS.
     */
    FrankenBridgeResult bringTaskToFront(int taskId);

    /**
     * Remove a task from recents.
     * Requires MANAGE_ACTIVITY_TASKS.
     */
    FrankenBridgeResult removeTask(int taskId);

    // ─── App Launch / Control ─────────────────────────────

    /**
     * Launch an app by package name.
     * Uses the launcher intent. Risk: R1.
     */
    FrankenBridgeResult launchApp(String packageName);

    /**
     * Launch an activity by explicit component name.
     * Risk: R1-R2 depending on extras.
     */
    FrankenBridgeResult launchComponent(
            String packageName, String activityName, in Bundle extras);

    /**
     * Open a deep link (URL/URI intent).
     * Risk: R2 (may navigate to any handler).
     */
    FrankenBridgeResult openDeepLink(String uri);

    /**
     * Force-stop a package.
     * Requires FORCE_STOP_PACKAGES. Risk: R3.
     */
    FrankenBridgeResult forceStopPackage(String packageName);

    // ─── Package Manager ──────────────────────────────────

    /**
     * List installed packages, optionally including disabled/uninstalled.
     * Requires appropriate GET_PACKAGES permission.
     */
    FrankenBridgeResult getInstalledPackages(boolean includeDisabled);

    /**
     * Get detailed package info.
     * Requires appropriate PACKAGE_INFO permission for full data.
     */
    FrankenBridgeResult getPackageInfo(String packageName);

    /**
     * Check if a package is installed and enabled.
     * Low risk (R0).
     */
    boolean isPackageInstalled(String packageName);

    /**
     * Get signing certificate digest for a package.
     * For verification purposes.
     */
    FrankenBridgeResult getPackageCertificateDigest(String packageName);

    // ─── Permissions ──────────────────────────────────────

    /**
     * Get the grant state of a runtime permission for a package.
     * Risk: R0.
     */
    FrankenBridgeResult getPermissionState(
            String packageName, String permissionName);

    /**
     * Grant a runtime permission to a package.
     * Requires GRANT_RUNTIME_PERMISSIONS. Risk: R3.
     */
    FrankenBridgeResult grantPermission(
            String packageName, String permissionName);

    /**
     * Revoke a runtime permission from a package.
     * Requires GRANT_RUNTIME_PERMISSIONS. Risk: R3.
     */
    FrankenBridgeResult revokePermission(
            String packageName, String permissionName);

    // ─── AppOps ───────────────────────────────────────────

    /**
     * Get the current AppOp mode for a package.
     * Risk: R0.
     */
    FrankenBridgeResult getAppOpMode(int appOpCode, String packageName);

    /**
     * Set the AppOp mode for a package.
     * Requires MANAGE_APP_OPS_MODES. Risk: R3.
     * Modes: MODE_ALLOWED, MODE_IGNORED, MODE_FOREGROUND.
     */
    FrankenBridgeResult setAppOpMode(
            int appOpCode, String packageName, int mode);

    // ─── Usage Stats ──────────────────────────────────────

    /**
     * Query usage stats for a time range.
     * Requires PACKAGE_USAGE_STATS. Risk: R1.
     */
    FrankenBridgeResult queryUsageStats(
            int bucketType, long beginTime, long endTime);

    /**
     * Query usage events for a time range.
     * Requires PACKAGE_USAGE_STATS. Risk: R1.
     */
    FrankenBridgeResult queryUsageEvents(
            long beginTime, long endTime);

    /**
     * Get app standby bucket.
     */ 
    int getAppStandbyBucket(String packageName);

    // ─── Battery / Power ──────────────────────────────────

    /**
     * Get battery state (level, charging, health, temperature).
     * Low risk (R0). Public SDK data.
     */
    FrankenBridgeResult getBatteryState();

    /**
     * Get battery saver state.
     * Low risk (R0).
     */
    boolean isBatterySaverEnabled();

    // ─── Settings ─────────────────────────────────────────

    /**
     * Get a System/Global/Secure setting value.
     * READ risk: R0. Privacy risk if reading secure settings.
     */
    FrankenBridgeResult getSetting(String namespace, String key);

    /**
     * Set a System/Global/Secure setting value.
     * Write to System: R1. Write to Global/Secure: R2-R3.
     * Requires WRITE_SETTINGS / WRITE_SECURE_SETTINGS.
     */
    FrankenBridgeResult setSetting(
            String namespace, String key, String value);

    // ─── Device Actions ───────────────────────────────────

    /** Set screen brightness (0-255). R1. */
    FrankenBridgeResult setBrightness(int brightness);

    /** Get current brightness. R0. */
    int getBrightness();

    /** Set flashlight on/off. R1. */
    FrankenBridgeResult setFlashlight(boolean enabled);

    /** Get flashlight state. R0. */
    boolean isFlashlightOn();

    // ─── Event Subscriptions ──────────────────────────────

    /**
     * Subscribe to system events.
     * eventTypes: bitmask of EVENT_PACKAGE_ADDED, EVENT_PACKAGE_REMOVED,
     *   EVENT_FOREGROUND_CHANGED, EVENT_SCREEN_ON, EVENT_SCREEN_OFF,
     *   EVENT_USER_SWITCH, EVENT_TASK_ADDED, EVENT_TASK_REMOVED.
     */
    void subscribeEvents(
            int eventTypes, IFrankenBridgeEventCallback callback);

    /**
     * Unsubscribe from system events.
     */
    void unsubscribeEvents(IFrankenBridgeEventCallback callback);

    // ─── Ping / Health ────────────────────────────────────

    /** Health check — returns true if service is responsive. */
    boolean ping();
}
```

## 2. Callback Interfaces

### Capability Result Callback

File: `IFrankenBridgeCallback.aidl`

```aidl
package android.os.frankenbridge;

oneway interface IFrankenBridgeCallback {
    void onForegroundAppChanged(String packageName, String activityName, int taskId);
}
```

### Event Subscription Callback

File: `IFrankenBridgeEventCallback.aidl`

```aidl
package android.os.frankenbridge;

oneway interface IFrankenBridgeEventCallback {
    void onEvent(in FrankenBridgeEvent event);
}
```

## 3. Data Classes (Parcelables)

### FrankenBridgeCapability

File: `FrankenBridgeCapability.aidl`

```aidl
package android.os.frankenbridge;

parcelable FrankenBridgeCapability {
    String id;           // e.g. "app.launch", "app.force_stop"
    String displayName;
    String description;
    int riskLevel;       // 0=R0, 1=R1, 2=R2, 3=R3, 4=R4
    boolean enabled;
    String permission;   // Required Android permission (or null)
    boolean requiresConfirmation;
    int version;
}
```

### FrankenBridgeRequest

File: `FrankenBridgeRequest.aidl`

```aidl
package android.os.frankenbridge;

parcelable FrankenBridgeRequest {
    String capabilityId;
    Bundle parameters;
    long timeoutMs;      // Per-call timeout override
    boolean requireUnlockedDevice;
    String explanation;  // Human-readable reason for the action (shown in confirmation UI)
}
```

### FrankenBridgeResult

File: `FrankenBridgeResult.aidl`

```aidl
package android.os.frankenbridge;

@Backing(type="int")
enum FrankenBridgeStatus {
    SUCCESS,
    DENIED,
    ERROR,
    PENDING_CONFIRMATION,
    TIMEOUT,
    CANCELLED,
}

@Backing(type="int")
enum FrankenBridgeDenialCode {
    NONE,
    PERMISSION,
    CAPABILITY_DISABLED,
    RISK_TOO_HIGH,
    BACKGROUND,
    LOCKED,
    RATE_LIMIT,
    USER_RESTRICTION,
    INVALID_CALLER,
}

parcelable FrankenBridgeResult {
    FrankenBridgeStatus status;
    FrankenBridgeDenialCode denialCode;
    String errorMessage;    // Debug-friendly, may be empty in production
    Bundle data;            // Capability-specific result payload
    long latencyMs;         // Time for bridge to process (not including confirmation UI)
}
```

### FrankenBridgeEvent

File: `FrankenBridgeEvent.aidl`

```aidl
package android.os.frankenbridge;

@Backing(type="int")
enum FrankenBridgeEventType {
    EVENT_FOREGROUND_CHANGED = 1,
    EVENT_PACKAGE_ADDED = 2,
    EVENT_PACKAGE_REMOVED = 4,
    EVENT_SCREEN_ON = 8,
    EVENT_SCREEN_OFF = 16,
    EVENT_USER_SWITCH = 32,
    EVENT_TASK_ADDED = 64,
    EVENT_TASK_REMOVED = 128,
}

parcelable FrankenBridgeEvent {
    long timestampMs;
    FrankenBridgeEventType eventType;
    int userId;
    String packageName;
    Bundle extras;
}
```

## 4. Client-Side Kotlin API (Sketch)

```kotlin
// FragileBridgeClient.kt — client wrapper in the APK

class FrankensteinBridgeClient(
    private val context: Context,
) {
    private var service: IFrankenBridgeService? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IFrankenBridgeService.Stub.asInterface(binder)
        }
        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    fun bind() {
        val intent = Intent()
            .setComponent(ComponentName(
                "com.frankenbridge.bridge",
                "com.frankenbridge.bridge.FrankenBridgeService"
            ))
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    // --- Typed wrappers ---

    suspend fun getForegroundApp(): ForegroundAppInfo? =
        service?.foregroundApp?.data?.let { /* parse */ }

    suspend fun launchApp(packageName: String): Result<Unit> =
        service?.let {
            val result = it.launchApp(packageName)
            if (result.status == FrankenBridgeStatus.SUCCESS)
                Result.success(Unit)
            else
                Result.failure(FrankenBridgeException(result))
        } ?: Result.failure(ServiceNotBoundException())

    // Callback-based for event subscriptions
    fun subscribeToForegroundChanges(callback: (ForegroundAppInfo) -> Unit) {
        // wraps IFrankenBridgeCallback.Stub
    }
}
```

## 5. Key Design Decisions

| Decision | Rationale |
|---|---|
| **Generic `FrankenBridgeResult` vs typed methods** | Single result type simplifies AIDL evolution. Clients parse typed data from `Bundle`. |
| **Risk-tiered consent in service** | The bridge service determines whether confirmation is needed, not the APK (APK can't be trusted to classify its own actions). |
| **Event subscription with bitmask** | Avoids registering multiple AIDL callback interfaces. Single callback with typed events. |
| **No request tokens initially** | MVP uses per-call permission checks. Future: capability tokens for multi-step plans. |
| **`Bundle` for parameter/data extensibility** | Avoids AIDL versioning headaches. Capabilities parse their own Bundle schema. |
