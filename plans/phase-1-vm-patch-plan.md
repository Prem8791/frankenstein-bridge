# Phase 1 — VM Patch Plan: Frankenstein Bridge System Service

## Overview

Add a real ROM-baked `system_server` bridge service to the Bliss Android 16
source tree on the VM. This is NOT an APK-only bridge — the bridge runs inside
`system_server` as a `SystemService` with a clean AIDL interface.

## Files to Create

### 1. AIDL Interface (Service Contract)

**`frameworks/base/core/java/android/os/frankenstein/IFrankensteinBridgeService.aidl`**

Main service AIDL. All methods check real Binder caller UID. Methods:

| Method | Returns | Notes |
|---|---|---|
| `getBridgeVersion()` | `String` | "1.0.0" |
| `ping()` | `boolean` | Health check |
| `getCallerIdentity()` | `FrankensteinBridgeResult` | Returns UID/package for verification |
| `getCapabilityMatrix()` | `FrankensteinBridgeResult` | All capabilities as JSON-like Bundle |
| `getForegroundApp()` | `FrankensteinBridgeResult` | Current pkg/activity/taskId |
| `getRecentTasks(int maxResults)` | `FrankensteinBridgeResult` | List of {pkg, activity, taskId, time} |
| `getInstalledPackages(boolean includeDisabled)` | `FrankensteinBridgeResult` | Package list with basic info |
| `getUsageStatsSummary()` | `FrankensteinBridgeResult` | Top N apps by usage time |
| `checkAppOps(String packageName)` | `FrankensteinBridgeResult` | All AppOp states for package |
| `launchPackage(String packageName)` | `FrankensteinBridgeResult` | Launch default intent |
| `getBatterySummary()` | `FrankensteinBridgeResult` | Level, charging, health, temp |

### 2. Result Parcelable AIDL + Java

**`frameworks/base/core/java/android/os/frankenstein/FrankensteinBridgeResult.aidl`**

```aidl
package android.os.frankenstein;
parcelable FrankensteinBridgeResult {
    int status;        // 0=OK, 1=DENIED, 2=ERROR
    int denialCode;    // 0=NONE, 1=INVALID_CALLER, 2=PERMISSION, 3=UNAVAILABLE
    String errorMessage;
    Bundle data;
    long latencyMs;
}
```

**`frameworks/base/core/java/android/os/frankenstein/FrankensteinBridgeResult.java`**

Manual implementation (since AIDL parcelables with Bundle need a Java handshake).

### 3. Service Implementation

**`frameworks/base/services/core/java/com/android/server/frankenstein/FrankensteinBridgeService.java`**

- Extends `SystemService`
- Publishes Binder service as `"frankenstein"` 
- All methods enforce caller verification:
  - Resolve `Binder.getCallingUid()` → `PackageManagerInternal.getPackage(uid)`
  - Check caller package is in allowlist: `com.frankenstein.assistant.test` (and self)
  - Check signature match if possible
- Capabilities implemented directly via framework `LocalServices` and hidden APIs:
  - `getForegroundApp()` → `ActivityTaskManager.getTasks(1)`, `ActivityTaskManager.getRecentTasks()`
  - `getInstalledPackages()` → `PackageManagerInternal.getInstalledApplications()`
  - `getUsageStatsSummary()` → `UsageStatsManagerInternal` via LocalServices
  - `checkAppOps()` → `AppOpsManager` via context
  - `launchPackage()` → `context.startActivity()` with `Intent` + `NEW_TASK`
  - `getBatterySummary()` → `BatteryManager` via context
- Structured `Slog` audit logging on every call
- No shell execution, no root, no raw command execution

### 4. SystemServer Registration

**`frameworks/base/services/java/com/android/server/SystemServer.java`**

Two edits:
- **Import** (line 345 area): Add `import com.android.server.frankenstein.FrankensteinBridgeService;`
- **Start** (~line 1749 area): Add after `StartBlissSystemExService`:

```java
t.traceBegin("StartFrankensteinBridgeService");
mSystemServiceManager.startService(FrankensteinBridgeService.class);
t.traceEnd();
```

## Build Impact

| Module | Affected? | Reason |
|---|---|---|
| `framework-minus-apex` | Yes (auto) | New AIDL under `core/java/` picked up by wildcard |
| `services` | Yes (auto) | New Java under `services/core/java/` picked up by wildcard |
| `bootimage` / `system.img` | Yes | Contains `services.jar` |

No `Android.bp` changes needed — both `core/java/**/*.aidl` and
`services/core/java/**/*.java` are already picked up by Soong filegroup globs.

## Verification Strategy

1. Targeted Soong build (no full ROM):
   ```bash
   m services framework-minus-apex
   ```
2. Adb remount + push rebuilt `services.jar` or `framework.jar` to device
3. Reboot
4. Connect via `adb shell dumpsys frankenstein` to verify service registered
5. Run dummy test app to call each method and display results

## Rollback

Revert the two SystemServer.java line changes and delete the new directories.
```bash
rm -rf frameworks/base/core/java/android/os/frankenstein/
rm -rf frameworks/base/services/core/java/com/android/server/frankenstein/
git checkout frameworks/base/services/java/com/android/server/SystemServer.java
```

## Confirmation

Changes are: **12 new files, 2 modified lines in SystemServer.java**.
No Android.bp changes. No SELinux changes (service runs in `system_server`
process, inherits its domain). No ROM rebuild needed — targeted `m` suffices.
