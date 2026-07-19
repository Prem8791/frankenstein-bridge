# Frankenstein Bridge — Implementation Plan

## Phases Overview

```
MVP (Phase 1) ──► Phase 2 ──► Phase 3 ──► Phase 4 ──► Phase 5
   no ROM       ROM-Svc +    Full AIDL +  Advanced    Firewall +
   changes      AIDL API     SElinux +    notifs +    native +
                              audit         a11y        kernel
```

## Phase 1: MVP (No ROM Changes, Priv-App Only)

### Goal
Deliver a functional assistant bridge with 80% of capabilities using only
platform-signed priv-app permissions. No changes to `frameworks/base`.

### Files to Create (Local Reference Only — Not on VM)

**APK Structure:**
```
app/
├── AndroidManifest.xml          # platform signature, priv-app in system/priv-app/
├── FrankenBridgeService.kt      # Main bound service (for client APK)
├── clients/
│   └── FrankensteinBridgeClient.kt  # Client library for the assistant app
├── providers/
│   ├── ForegroundAppProvider.kt
│   ├── PackageManagerProvider.kt
│   ├── UsageStatsProvider.kt
│   ├── SettingsProvider.kt
│   ├── PowerProvider.kt
│   └── AppLaunchProvider.kt
├── utils/
│   ├── PermissionUtils.kt
│   └── AuditLogger.kt
└── config/
    └── capabilities_config.xml   # Enable/disable per capability

```

**Config Files (for ROM build):**
```
etc/permissions/privapp-permissions-frankenbridge.xml
etc/sysconfig/frankenbridge-hiddenapi.xml
```

### Permissions Manifest

```xml
<!-- privapp-permissions-frankenbridge.xml -->
<permissions>
    <privapp-permissions package="com.frankenbridge">
        <permission name="android.permission.REAL_GET_TASKS"/>
        <permission name="android.permission.MANAGE_ACTIVITY_TASKS"/>
        <permission name="android.permission.FORCE_STOP_PACKAGES"/>
        <permission name="android.permission.GET_PACKAGES_PRIV"/>
        <permission name="android.permission.GRANT_RUNTIME_PERMISSIONS"/>
        <permission name="android.permission.MANAGE_APP_OPS_MODES"/>
        <permission name="android.permission.OBSERVE_GRANT_REVOKE_PERMISSIONS"/>
        <permission name="android.permission.PACKAGE_USAGE_STATS"/>
        <permission name="android.permission.WRITE_SECURE_SETTINGS"/>
        <permission name="android.permission.CHANGE_APP_IDLE_STATE"/>
        <permission name="android.permission.REORDER_TASKS"/>
        <permission name="android.permission.REMOVE_TASKS"/>
    </privapp-permissions>
</permissions>
```

```xml
<!-- frankenbridge-hiddenapi.xml (sysconfig) -->
<config>
    <!-- Allow hidden API access for the bridge APK -->
    <hidden-api-whitelisted package="com.frankenbridge" />
</config>
```

### MVP Capabilities Checklist

| # | Capability | Implementation |
|---|---|---|
| 1 | Get foreground app | `ActivityTaskManager.getTasks(1).get(0).topActivity` |
| 2 | List installed packages | `PackageManager.getInstalledPackages(GET_META_DATA)` |
| 3 | Get package info | `PackageManager.getPackageInfo()` |
| 4 | Launch app | `context.startActivity(packageManager.getLaunchIntent())` |
| 5 | Get battery state | `BatteryManager` → `getIntProperty()`, `getLongProperty()` |
| 6 | Query usage stats | `UsageStatsManager.queryUsageStats()` |
| 7 | R/W system settings | `Settings.System/Global/Secure` getters/setters |
| 8 | Volume / brightness | `AudioManager`, `Settings.System` |
| 9 | Flashlight toggle | `CameraManager.setTorchMode()` |
| 10 | Recent tasks | `ActivityTaskManager.getRecentTasks()` |
| 11 | Permission state | `PackageManager.checkPermission()` |
| 12 | App standby bucket | `UsageStatsManager.getAppStandbyBucket()` |

### Build Process
1. Build the APK with `LOCAL_CERTIFICATE := platform`
2. Place APK in `device/asus/I001D/app/FrankenBridge/` (for device-specific inclusion)
3. Add to `device/asus/I001D/device.mk`: `PRODUCT_PACKAGES += FrankenBridge`
4. Copy permission XMLs to `device/asus/I001D/configs/privapp-permissions/` and `sysconfig/`
5. Rebuild ROM with `blissify -g I001D`

### Not Started
- No custom `SystemService` in `frameworks/base/services/`
- No `SystemServer.java` modification
- No SELinux domain changes
- No framework AIDL additions

---

## Phase 2: Custom System Service

### Goal
Add `FrankensteinBridgeService` as a `SystemService` in `frameworks/base/services/`
for centralized auth, policy, audit, and a clean AIDL contract.

### Changes Required

| File | Change Type | Purpose |
|---|---|---|
| `frameworks/base/services/core/java/com/android/server/frankenbridge/FrankensteinBridgeService.java` | **New** | SystemService implementation |
| `frameworks/base/services/core/java/com/android/server/frankenbridge/AuthPolicy.java` | **New** | Caller authentication + policy |
| `frameworks/base/services/core/java/com/android/server/frankenbridge/CapabilityRegistry.java` | **New** | Capability enum + enable/disable |
| `frameworks/base/services/core/java/com/android/server/frankenbridge/AuditLogger.java` | **New** | Structured audit logging |
| `frameworks/base/core/java/android/os/frankenbridge/IFrankenBridgeService.aidl` | **New** | Service AIDL |
| `frameworks/base/core/java/android/os/frankenbridge/IFrankenBridgeCallback.aidl` | **New** | Callback AIDL |
| `frameworks/base/core/java/android/os/frankenbridge/IFrankenBridgeEventCallback.aidl` | **New** | Event callback AIDL |
| `frameworks/base/core/java/android/os/frankenbridge/FrankenBridgeCapability.aidl` | **New** | Capability parcel |
| `frameworks/base/core/java/android/os/frankenbridge/FrankenBridgeRequest.aidl` | **New** | Request parcel |
| `frameworks/base/core/java/android/os/frankenbridge/FrankenBridgeResult.aidl` | **New** | Result parcel |
| `frameworks/base/core/java/android/os/frankenbridge/FrankenBridgeEvent.aidl` | **New** | Event parcel |
| `frameworks/base/services/java/com/android/server/SystemServer.java` | **Edit** | Add `startService(FrankensteinBridgeService.class)` |
| `frameworks/base/services/core/java/com/android/server/SystemConfig.java` | **Edit** | Register new service as system service |

### SystemServer Registration

```java
// In SystemServer.java startOtherServices(), after BlissSystemExService:
t.traceBegin("StartFrankensteinBridgeService");
mSystemServiceManager.startService(FrankensteinBridgeService.class);
t.traceEnd();
```

### Service Lifecycle

```java
public class FrankensteinBridgeService extends SystemService {
    @Override
    public void onStart() {
        // Publish Binder service
        publishBinderService(Context.FRANKENBRIDGE_SERVICE, mImpl);
        // Register with ServiceManager
        ServiceManager.addService("frankenbridge", mImpl);
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_SYSTEM_SERVICES_READY) {
            // Acquire LocalService references
            mPackageManagerInternal = LocalServices.getService(PackageManagerInternal.class);
            mActivityManagerInternal = LocalServices.getService(ActivityManagerInternal.class);
        }
        if (phase == PHASE_BOOT_COMPLETED) {
            // Start event listeners, background work
        }
    }

    @Override
    public void onUserSwitching(TargetUser from, TargetUser to) {
        // Re-evaluate per-user capability settings
    }
}
```

### AIDL Implementation Sketch

```java
private final IBinder mImpl = new IFrankenBridgeService.Stub() {

    @Override
    public FrankenBridgeResult getForegroundApp() {
        final int callingUid = Binder.getCallingUid();
        final int callingPid = Binder.getCallingPid();

        // 1. Authenticate caller
        authPolicy.enforceCaller(callingUid, callingPid);

        // 2. Check capability enabled
        capabilityRegistry.enforceEnabled("foreground_app");

        // 3. Check Android permission
        enforceCallingPermission(REAL_GET_TASKS, null);

        // 4. Execute
        final ActivityManagerInternal ami = LocalServices
            .getService(ActivityManagerInternal.class);
        final TaskSnapshot task = ami.getTopTask();

        // 5. Audit
        auditLogger.log("foreground_app", callingUid,
            SUCCESS, 0, null);

        // 6. Return
        return FrankenBridgeResult.success(task.toBundle());
    }
};
```

---

## Phase 3: Full AIDL Surface + SELinux + Audit

### New Items
| Item | Details |
|---|---|
| `frankenstein_bridge.te` | SELinux domain in `system/sepolicy/private/` and `public/` |
| `device/asus/sm8150-common/sepolicy/private/frankenstein_bridge.te` | Device-specific SELinux rules |
| `AuditLogger.java` → statsd integration | Structured async logging |
| `FrankenBridgeConfirmationActivity` | Trusted system UI for R3-R4 confirmation |
| `EventCollector.java` | Foreground switch, package lifecycle, intent logging |

### SELinux Policy

```
# system/sepolicy/private/frankenstein_bridge.te
type frankenstein_bridge, domain;
type frankenstein_bridge_exec, exec_type, file_type;

# Init transition
init_daemon_domain(frankenstein_bridge)

# Binder calls to system services
binder_call(frankenstein_bridge, appdomain)
binder_call(frankenstein_bridge, activity_service)
binder_call(frankenstein_bridge, window_service)
binder_call(frankenstein_bridge, package_service)

# File access
allow frankenstein_bridge frankenbridge_data_file:dir create_dir_perms;
allow frankenstein_bridge frankenbridge_data_file:file create_file_perms;

# netd interaction (future firewall)
allow frankenstein_bridge netd:unix_stream_socket connectto;

# appdomain can call bridge
binder_call(appdomain, frankenstein_bridge)
binder_call(frankenstein_bridge, appdomain)
```

---

## Phase 4: Advanced Capabilities

### Notification Bridge (Beyond NLS)
- Use `INotificationManager` via system service (bypasses user NLS grant)
- Requires new permission: `FRANKENBRIDGE_NOTIFICATION_ACCESS`
- Risk: R3 (full notification content access)

### Accessibility Bridge
- `TEMPORARY_ENABLE_ACCESSIBILITY` to auto-start the bridge's A11y service
- `AccessibilityService` for UI tree reading, gesture injection
- All actions gated through bridge service for policy/audit

### User Action Logging
- `EventCollector` subscribes to:
  - `ActivityManagerInternal` for activity lifecycle
  - `PackageManagerInternal` for package lifecycle
  - `NotificationManagerInternal` for notification interactions
- Events stored in a structured SQLite database on `/data/system/frankenbridge/events/`
- Privacy: events are user-local, never uploaded, and user can clear history

---

## Phase 5: Firewall / Data-Leak / Kernel

### Per-UID Firewall
- `NetworkManagementService.setFirewallUidRule()` via system service
- Safe allowlist/blocklist for apps based on user rules
- Monitor with `NetworkManagementService.getUidStats()`

### eBPF Monitoring (Optional)
- Attach eBPF programs to cgroup/socket filters via `BpfProgram` API (available in Android 16)
- Aggregate per-app connection counts, destination IPs, protocol types
- Block via eBPF return codes or netfilter integration
- Requires kernel `CONFIG_BPF` + Android specific BPF loader

### Data Leak Detection
- Combine firewall + eBPF + `NetworkStatsManager` to detect:
  - App sending data to unexpected destinations
  - Unexpected background data usage spikes
  - DNS queries to known tracking domains
- Alerting via bridge callback to assistant app

---

## Implementation Order Summary

| Phase | Dependencies | Build Impact | Verification |
|---|---|---|---|
| P1 | None | Add APK + configs to device tree | Rebuild ROM, flash, test each capability |
| P2 | P1 | service/ + AIDL + SystemServer edit | `m frankenbridge` should build; verify Binder calls |
| P3 | P2 | sepolicy additions | `m selinux_policy`; verify no denials |
| P4 | P3 | notification/flags | Integration test pass |
| P5 | P4 | kernel BPF config | Requires separate kernel build |

## Rollback

Each phase preserves the ability to remove the bridge and return to a clean ROM:
- **P1:** Remove APK from device.mk, remove XML configs, rebuild
- **P2:** Remove SystemServer line + service directory, rebuild
- **P3:** Remove SELinux rules, rebuild `selinux_policy`
- **P4-P5:** Remove code, rebuild

## Definition of Done

For each capability:

- [ ] Classify risk tier and document
- [ ] Add to `CapabilityRegistry`
- [ ] Implement provider
- [ ] Add Binder permission checks
- [ ] Write audit logging
- [ ] Confirm timeout handling
- [ ] Test: valid params, invalid params, denial, timeout
- [ ] Test: caller authentication bypass
- [ ] Document in capability list
