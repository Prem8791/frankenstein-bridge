# Frankenstein Bridge — Capability Study

> Target: Bliss Android 16 / BP4A (API 36) on ASUS I001D Waterlily (SM8150)

## Classification Key

| Layer | Requirement |
|---|---|
| **APK** | Needs `platform` signature + priv-app placement |
| **PrivPerm** | `privapp-permissions-platform.xml` entry for signature\|privileged perms |
| **SysCfg** | `framework-sysconfig.xml` entry (e.g., `hidden-api-whitelisted`) |
| **HApi** | Hidden API exemption (greylist/whitelist for `@UnsupportedAppUsage` calls) |
| **ROM-Svc** | New `SystemService` registered in `SystemServer.java` |
| **FrmMod** | `frameworks/base` modification (beyond adding a service) |
| **SELinux** | New SELinux domain or allow rules |
| **Native** | Native daemon (C/C++ binary launched from init.rc) |
| **Kernel** | Kernel module, eBPF program, or netfilter extension |

---

## 1. Foreground App Detection

### Capabilities
- Get current foreground package name and activity
- Get top task info (taskId, userId, packageName)
- Listen for foreground task changes (callback)

### Implementation Options
| Approach | Layer | Complexity | Reliability |
|---|---|---|---|
| `UsageStatsManager.queryUsageStats(INTERVAL_BEST, ...)` | APK-only | Low | Delayed (~5min) |
| `AccessibilityService.getWindows()` + `PACKAGE_NAME` | APK + user enable | Medium | Real-time, needs A11y |
| `ActivityTaskManager.getTasks(1, ...)` via `REAL_GET_TASKS` | APK (priv) | Low | Real-time |
| WindowManager `FocusChangedListener` in custom service | ROM-Svc | Medium | Real-time, authoritative |
| `BlissSystemExService` fullscreen listener (already exists) | ROM-Svc | None (reuse) | Real-time |

### Recommendation
Reuse/extend `BlissSystemExService.FullscreenTaskStackChangeListener`
(`onFullscreenTaskChanged`) via a new bridge AIDL callback to the APK.

**Classification:** APK + PrivPerm (`REAL_GET_TASKS` or `MANAGE_ACTIVITY_TASKS`)

---

## 2. Recent Tasks / Task Control

### Capabilities
- List recent tasks with metadata (package, activity, thumbnail)
- Bring task to foreground by taskId
- Remove task from recents by taskId

### Implementation Options
| Approach | Layer | Notes |
|---|---|---|
| `ActivityTaskManager.getRecentTasks()` | APK (priv) | Needs `MANAGE_ACTIVITY_TASKS` or `REAL_GET_TASKS` |
| `IAppLockManager` integration | APK (priv) | OEM-specific |
| WindowManager `TaskOrganizer` | ROM-Svc | system API, needs platform signature |

### Classification
**APK + PrivPerm** (`MANAGE_ACTIVITY_TASKS`)
+ **FrmMod** if exposing `removeTask()` beyond what public SDK allows

---

## 3. App Launch / Close / Switch

### Capabilities
- Launch any app by package name or component
- Force-stop an app
- Switch between running tasks
- Open specific activity with extras

### Existing Paths
- `startActivity()` with `NEW_TASK` flag — public SDK
- `Context.startActivity()` with `FLAG_ACTIVITY_NEW_TASK` — public
- `ActivityManager.forceStopPackage()` — requires `FORCE_STOP_PACKAGES` (signature\|privileged)
- `ActivityTaskManager.startActivityFromRecents()` — requires `MANAGE_ACTIVITY_TASKS`

### Classification
| Sub-capability | Layer |
|---|---|
| Launch app (package) | APK (public SDK) |
| Launch app (component) | APK (priv) |
| Force-stop | APK + PrivPerm (`FORCE_STOP_PACKAGES`) |
| Switch to recents task | APK + PrivPerm (`MANAGE_ACTIVITY_TASKS`) |

---

## 4. Package Manager State

### Capabilities
- List installed packages (all, including hidden)
- Get package details (version, uid, permissions, first install time, signing info)
- Check if a package is enabled/suspended
- Get package certificate info
- Query broadcast receivers / activities for a package
- Monitor package install/remove/update events

### Existing Access
| API | Permission | Notes |
|---|---|---|
| `PackageManager.getInstalledPackages()` | `QUERY_ALL_PACKAGES` | API 30+, normal permission |
| `PackageManager.getPackageInfo()` | varies by flag | `PackageInfo.signatures` needs `INSTALL_PACKAGES` or signature match |
| `PackageManager.getPackagesForUid()` | none | Returns packages for a UID |
| `IPackageManager` (hidden API) | `GET_PACKAGES*` priv perms | Full access |
| `PackageManagerInternal` (LocalService) | system_server only | Full internal access |

### Classification
**APK + PrivPerm** (`GET_PACKAGES_PRIV` for full access)
+ **SysCfg** for hidden API whitelist if using `@UnsupportedAppUsage` methods

---

## 5. Permission / AppOp Control

### Capabilities
- Grant / revoke runtime permissions for any app
- Check permission state for any app
- Set AppOp mode (allow, ignore, deny, foreground)
- Reset permissions
- Observe permission changes

### Key Permissions
| Permission | Protection | Purpose |
|---|---|---|
| `MANAGE_APP_OPS_MODES` | signature\|privileged | Set AppOp modes |
| `OBSERVE_GRANT_REVOKE_PERMISSIONS` | signature\|privileged | Permission change callbacks |
| `GRANT_RUNTIME_PERMISSIONS` | signature\|privileged | Grant permissions to apps |

### Classification
**APK + PrivPerm** (`MANAGE_APP_OPS_MODES`, `OBSERVE_GRANT_REVOKE_PERMISSIONS`, `GRANT_RUNTIME_PERMISSIONS`)
+ **SysCfg** for hidden API exemption to call `IAppOpsService` directly

---

## 6. UsageStats

### Capabilities
- Query usage stats by time range (daily, weekly, monthly, yearly)
- Get app usage time per package
- Get app standby bucket
- Get last time app was used
- Query events (foreground, configuration changes, shortcuts)

### Existing API
`IUsageStatsManager.aidl` provides:
- `queryUsageStats()`, `queryEvents()`, `queryEventsForPackage()`
- `getAppStandbyBucket()`, `setAppStandbyBucket()`
- `registerAppUsageObserver()`

Requires `PACKAGE_USAGE_STATS` (which apps can request via settings).

### Classification
**APK + PrivPerm** (`PACKAGE_USAGE_STATS` as signature\|privileged for non-settings grant)
+ **SysCfg** if directly calling `IUsageStatsManager` (hidden API)

---

## 7. Battery / Background / Network Usage Monitoring

### Capabilities
- Get battery level, charging status, health, temperature
- Get app battery usage
- Get app background restriction state
- Get network usage per app (tx/rx bytes)
- Get data saver state

### Existing APIs
- `BatteryManager` — public SDK (broadcasts)
- `PowerStatsService` — internal, needs system service
- `NetworkStatsManager` — public SDK, needs `PACKAGE_USAGE_STATS`
- `ConnectivityManager.getRestrictBackgroundStatus()` — public SDK
- `AppBatteryTracker` (internal AMS class) — needs system service access

### Classification
| Sub-capability | Layer |
|---|---|
| Battery level/charging | APK (public) |
| App battery usage | APK + PrivPerm or ROM-Svc |
| Network stats per app | APK + PrivPerm (`PACKAGE_USAGE_STATS`) |
| Background restriction state | APK (public) |

---

## 8. Notification Access

### Capabilities
- Read active notifications (text, title, app, extras)
- Dismiss notifications
- Perform notification action
- Listen for new notifications in real time
- Post notifications on behalf of user
- Get notification history
- Get notification channels and importance

### Approaches
| Approach | Layer | Notes |
|---|---|---|
| `NotificationListenerService` | APK (user-enabled) | Needs user to grant in Settings |
| `NotificationManager.getActiveNotifications()` | APK (priv) | Requires `ACCESS_NOTIFICATIONS` (signature, may be restricted) |
| `INotificationManager` AIDL | ROM-Svc | Full access via system service |

### Classification
**APK + PrivPerm** + user-granted `NotificationListenerService` for first phase
**ROM-Svc** for bypassing user grant (higher-risk)

---

## 9. Assistant / Voice Role Integration

### Capabilities
- Register as the system assistant (voice interaction service)
- Wake word detection (hotword)
- Screen context injection (assist data)
- Lock screen activation
- Voice session management

### Existing Framework
`VoiceInteractionManagerService` manages:
- Active voice interaction service component
- SoundTrigger (hotword) enrollment and detection
- Assistant activity launching
- Voice session lifecycle
- Visual query detection (Android 16 new feature)

### Requirements
- `BIND_VOICE_INTERACTION` permission
- Manifest declaration of `VoiceInteractionService`
- User selection as default assistant in Settings
- `RoleManager` role `ROLE_ASSISTANT`

### Classification
**APK** (user-enabled via Settings → default assistant)
**Nothing ROM-specific needed** — the bridge APK just declares the service

---

## 10. Settings / Device Actions

### Capabilities
- Read/write system settings (`Settings.System`, `Settings.Global`, `Settings.Secure`)
- Toggle Wi-Fi, Bluetooth, NFC, airplane mode, mobile data
- Adjust brightness, volume, screen timeout
- Set flashlight, night mode, adaptive brightness
- Trigger device admin actions

### Permission Requirements
| Action | Permission |
|---|---|
| Write system settings | `WRITE_SETTINGS` (normal) or `WRITE_SECURE_SETTINGS` (signature\|privileged) |
| Wifi toggle | `CHANGE_WIFI_STATE` (normal) |
| Bluetooth toggle | `BLUETOOTH_ADMIN` (normal) |
| NFC toggle | NFC permission (normal) |
| Mobile data toggle | `CHANGE_NETWORK_STATE` + `WRITE_SETTINGS` or MODIFY_PHONE_STATE |
| Airplane mode | `WRITE_SECURE_SETTINGS` + possibly `MODIFY_PHONE_STATE` |
| Brightness | `WRITE_SETTINGS` |
| Volume | normal (via `AudioManager`) |

### Classification
**APK + PrivPerm** for `WRITE_SECURE_SETTINGS` + hidden settings
**APK (public)** for most via public SDK

---

## 11. Accessibility-Adjacent Actions

### Capabilities
- Read screen content (node tree)
- Click / tap on specific UI elements
- Swipe, scroll, long-press
- Take screenshot
- Inject gestures (limited)
- Retrieve currently focused window info

### Implementation
| Approach | Layer | Notes |
|---|---|---|
| `AccessibilityService` | APK (user-enabled) | Full capabilities, needs user grant |
| `TEMPORARY_ENABLE_ACCESSIBILITY` | APK + PrivPerm | Can temporarily enable a11y service |
| Window injection via `InputManager` | ROM-Svc + SELinux | Maximum risk, last resort |

### Classification
**APK** (user-enabled AccessibilityService) for standard UI automation
**APK + PrivPerm** for `TEMPORARY_ENABLE_ACCESSIBILITY` to auto-start on boot
**ROM-Svc** for anything beyond standard A11y API

---

## 12. Firewall / Data-Leak Monitoring

### Capabilities
- Block/allow network access per app (UID firewall)
- Monitor per-app network traffic in real time
- Detect unexpected outbound connections
- Restrict background data per app
- Control VPN state
- Observe DNS queries

### Implementation
| Approach | Layer | Notes |
|---|---|---|
| `NetworkManagementService.setFirewallUidRule()` | ROM-Svc + SELinux | Requires `NETWORK_STACK` or system signature |
| `Netd` native API | Native | Requires new daemon or extending netd |
| eBPF (Android 16 supports eBPF program attach) | Kernel | Maximum flexibility, high complexity |
| `NetworkStatsManager` | APK (public) | Query only, no blocking |
| `ConnectivityManager.setRequireVpn()` | APK + PrivPerm | VPN requirement |

### Classification
**ROM-Svc** + **SELinux** for firewall rules
**Kernel** for eBPF-based per-packet monitoring (optional, advanced)
**APK** for query-only via public APIs

---

## 13. User Action Logging / Intent Learning Support

### Capabilities
- Log user actions (app launches, settings changes, notifications acted upon)
- Log intent chains (app A → app B via share/custom tab)
- Build time-series data of user behavior
- Detect patterns and suggest automation
- Capture structured intents and their outcomes

### Implementation
| Approach | Layer | Notes |
|---|---|---|
| `UsageStatsService` event queries | APK + PrivPerm | Retrospective, aggregated, no real-time |
| `ActivityManagerService` internal activity lifecycle hooks | ROM-Svc + FrmMod | Real-time, needs ActivityManagerInternal |
| `ContentObserver` on Settings content URIs | APK | Settings changes only |
| Custom event collector in bridge service | ROM-Svc | New service aggregating events from multiple hooks |
| `ActivityTaskManager` registerTaskStackListener | APK + PrivPerm | Task lifecycle events (add, remove, focus) |

### Classification
**APK + PrivPerm** for basic logging via `registerTaskStackListener()`, `UsageStatsManager`
**ROM-Svc** for comprehensive event collection (package lifecycle + activity lifecycle + intent resolution)
**FrmMod** needed if adding new framework hooks for intent resolution tracking

---

## Capability Mapping Summary

| # | Capability | MVP Phase | Max Phase | Primary Layer |
|---|---|---|---|---|
| 1 | Foreground app detect | Phase 1 | Phase 2 | APK + PrivPerm |
| 2 | Recent tasks / task control | Phase 2 | Phase 3 | APK + PrivPerm |
| 3 | App launch/close/switch | Phase 1 | Phase 2 | APK + PrivPerm |
| 4 | Package manager state | Phase 1 | Phase 2 | APK + PrivPerm |
| 5 | Permission/AppOp control | Phase 2 | Phase 3 | APK + PrivPerm |
| 6 | UsageStats | Phase 1 | Phase 2 | APK + PrivPerm |
| 7 | Battery/network monitoring | Phase 1 | Phase 2 | APK + PrivPerm |
| 8 | Notification access | Phase 2 | Phase 3 | APK + NLS |
| 9 | Assistant/voice role | Phase 2 | Phase 4 | APK (VIS) |
| 10 | Settings/device actions | Phase 1 | Phase 2 | APK + PrivPerm |
| 11 | Accessibility-adjacent | Phase 3 | Phase 4 | APK (A11ySvc) |
| 12 | Firewall/data-leak monitor | Phase 4 | Phase 5 | ROM-Svc + SELinux |
| 13 | User action logging | Phase 3 | Phase 4 | ROM-Svc |

## Phase Definitions

| Phase | Scope | ROM changes |
|---|---|---|
| **MVP (P1)** | Foreground app, app launch, package state, UsageStats, battery, settings R/W | No ROM changes; platform-signed priv-app only |
| **Phase 2** | Task control, permission/AppOp, notifications (via NLS), assistant role, force-stop | PrivPerm + sysconfig entries |
| **Phase 3** | Accessibility automation, user action logging, task stack listeners | ROM-Svc (bridge service in system_server) |
| **Phase 4** | Advanced notification (non-NLS path), advanced accessibility | New AIDL + SELinux domain |
| **Phase 5** | Firewall, eBPF monitoring, data leak detection | Native daemon + kernel support |

## Risk / Guardrails for Each Capability

Documented fully in the architecture doc, but summary:

| Risk Level | Capabilities | Guardrails |
|---|---|---|
| **Low (R0-R1)** | Battery state, volume, brightness, package query, UsageStats query | Read-only, reversible |
| **Medium (R2-R3)** | App launch, settings write, permission grant, notification | Confirmation + audit |
| **High (R4)** | Force-stop, firewall rules, accessibility auto-enable, boot-time operations | Explicit user consent + strong auth + audit |
| **Prohibited** | General shell, SELinux disable, bootloader unlock, key export | Never exposed |
