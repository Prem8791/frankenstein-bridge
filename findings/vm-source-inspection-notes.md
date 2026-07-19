# VM Source Inspection Notes

> Target: VM `instance-20260710-230647`, project `customrom-501702`,
> source root `/home/premanandal1978/android/waterlily`,
> product `bliss_I001D-bp4a-userdebug`.

## 1. System Server Startup Order

File: `frameworks/base/services/java/com/android/server/SystemServer.java`

Key phases and services (relevant to bridge):

| Phase | Service | Relevance |
|---|---|---|
| Bootstrap | `PlatformCompat` | Hidden API enforcement |
| Bootstrap | `Installer` | Package installation |
| Bootstrap | `PowerStatsService` | Power monitoring |
| Core | `BatteryService` | Battery state |
| Core | `UsageStatsService` | App usage tracking |
| Other | `AccountManagerService` | Account access |
| Other | `ContentService` | Content providers |
| Other | `RoleManagerService` | Android Roles (assistant, dialer, SMS) |
| Other | `AlarmManagerService` | Alarms/timers |
| Other | `WindowManagerService` | Task/window management |
| Other | `BlissSystemExService` | **Bliss extension** (fullscreen tracking, display control) |
| Other | `InputManagerService` | Input injection flows |
| Other | `NotificationManagerService` | Notification access |
| Other | `VoiceInteractionManagerService` | Assistant role, hotword |
| Other | `AccessibilityManagerService` | Accessibility service management |
| Other | `AppFunctionManagerService` | **Android 16 AppFunctions** |
| Other | `NetworkManagementService` | Firewall, network QoS |
| Other | `GameSpaceManagerService` | **Bliss game mode** |

## 2. Key AIDL Interfaces Discovered

| Interface | Path | Purpose |
|---|---|---|
| `IUsageStatsManager.aidl` | `core/java/android/app/usage/` | Usage stats query, standby buckets |
| `IAppOpsService.aidl` | `core/java/com/android/internal/app/` | AppOp check/note/start/finish |
| `IVoiceInteractionManagerService.aidl` | `core/java/com/android/internal/app/` | Assistant sessions, hotword, voice activity |
| `IAppFunctionManager.aidl` | `core/java/android/app/appfunctions/` | **Android 16 AppFunction execution** |
| `IPermissionManager.aidl` | `core/java/android/permission/` | Permission grants |
| `IAppLockManager` | (present via `AppLockManager.java`) | App lock (I001D may have ASUS impl) |

## 3. Permission Inventory (from AndroidManifest.xml)

Critical permissions for bridge capabilities:

### Task/Window Control
- `GET_TASKS` (deprecated) / `REAL_GET_TASKS` (replacement)
- `REORDER_TASKS`
- `REMOVE_TASKS`
- `MANAGE_ACTIVITY_TASKS`

### Package Management
- `INSTALL_PACKAGES` / `EMERGENCY_INSTALL_PACKAGES`
- `DELETE_PACKAGES`
- `REQUEST_INSTALL_PACKAGES` / `REQUEST_DELETE_PACKAGES`

### App Ops & Permissions
- `MANAGE_APP_OPS_MODES` (signature|privileged)
- `OBSERVE_GRANT_REVOKE_PERMISSIONS`
- `CHANGE_APP_IDLE_STATE`

### Device Control
- `CONTROL_LOCATION_UPDATES`
- `HIDE_NON_SYSTEM_OVERLAY_WINDOWS`

### AppFunctions
- `EXECUTE_APP_FUNCTIONS`

### Accessibility
- `BIND_ACCESSIBILITY_SERVICE`
- `MODIFY_ACCESSIBILITY_DATA`
- `TEMPORARY_ENABLE_ACCESSIBILITY`

### Voice Interaction
- `BIND_VOICE_INTERACTION`

## 4. Existing Bliss Custom Services

### BlissSystemExService
- Path: `services/core/java/com/android/server/BlissSystemExService.java`
- **Fullscreen task tracking** via `FullscreenTaskStackChangeListener`
- Display refresh rate / resolution control
- Package removed listener
- Screen state listener
- **Serves as a precedent for adding ROM-specific system services**

### GameSpaceManagerService
- Path: `services/core/java/com/android/server/GameSpaceManagerService.java`
- Bliss gaming mode hooks

## 5. AppFunctions (Android 16)

Path: `core/java/android/app/appfunctions/`

Complete AIDL surface exists:
- `IAppFunctionManager.aidl` — execution, access flags, agent/target management
- `IAppFunctionService.aidl` — service-side contract
- `ExecuteAppFunctionAidlRequest` / `ExecuteAppFunctionResponse`
- `ICancellationCallback`, `IExecuteAppFunctionCallback`

Service: `AppFunctionManagerService` (starts in SystemServer)

## 6. Hidden API Exemption

Hidden API lists are at `frameworks/base/boot/hiddenapi/`:
- `hiddenapi-unsupported.txt`
- `hiddenapi-max-target-{o,p,q,r-loprio}.txt`
- `hiddenapi-unsupported-packages.txt`

The bridge's client APK will need exemption via priv-app permission XML or
sysconfig `hidden-api-whitelisted` flag, since it will call `@UnsupportedAppUsage`
and `@SystemApi` methods from its system service.

## 7. SELinux Domains

Current domains (from `system/sepolicy/`):
- `platform_app.te` — platform-signed apps
- `priv_app.te` — privileged apps
- `system_app.te` — system apps
- `untrusted_app.te` — regular apps
- `isolated_app.te` — isolated processes

The bridge will need its own domain (e.g., `frankenstein_bridge.te`) with
targeted allow rules for Binder calls to system services.

## 8. Framework-Sysconfig & Privapp Permissions

- `frameworks/base/data/etc/framework-sysconfig.xml` — system-wide config
- `frameworks/base/data/etc/privapp-permissions-platform.xml` — platform priv-app permissions

Both are the correct mechanism for granting the bridge APK its required
privileges.

## 9. Service Registration Model

The preferred pattern for adding a new system service (from Bliss examples):
1. Create service class in `frameworks/base/services/core/java/com/android/server/`
2. Add AIDL interface in `frameworks/base/core/java/` (under `app/` for public or `com/android/internal/` for internal)
3. Register in `SystemServer.java` with `mSystemServiceManager.startService()`
4. Add SELinux policy in `system/sepolicy/` or device-specific sepolicy
5. Add privapp permissions XML entry
6. Add sysconfig entry if needed for hidden API exemption

## 10. Device-Specific SEPolicy

Device-specific sepolicy exists at:
`device/asus/sm8150-common/sepolicy/private/` and `vendor/`

This is where bridge-specific SELinux rules for the I001D should be added.

## 11. AppLockManager

Present in framework: `android.app.AppLockManager`
This is an OEM extension (likely for ASUS app lock). The bridge could leverage it.
