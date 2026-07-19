# Frankenstein Bridge — Architecture

## 1. Architectural Overview

The bridge has three tiers:

```
┌─────────────────────────────────────────────────────────┐
│                   Assistant APK                          │
│  (com.frankenbridge.assistant / platform-signed priv-app) │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────┐  │
│  │ UI / Surface │  │ Capability   │  │ VoiceInteraction│  │
│  │ (activity)   │  │ Orchestrator │  │ Service         │  │
│  └─────────────┘  └──────┬───────┘  └────────────────┘  │
│                          │ Binder (IFrankenBridgeService) │
└──────────────────────────┼──────────────────────────────┘
                           │
┌──────────────────────────┼──────────────────────────────┐
│           system_server  │                               │
│  ┌───────────────────────▼───────────────────────────┐  │
│  │         FrankensteinBridgeService                   │  │
│  │  (SystemService in frameworks/base/services)       │  │
│  │  ┌────────────┐ ┌───────────┐ ┌────────────────┐  │  │
│  │  │ Auth &     │ │ Capability│ │ Audit Logger   │  │  │
│  │  │ Policy     │ │ Registry  │ │ (statsd)       │  │  │
│  │  └────────────┘ └───────────┘ └────────────────┘  │  │
│  │                                                    │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────────────┐   │  │
│  │  │ Task &   │ │ Package & │ │ AppOp &          │   │  │
│  │  │ Window   │ │ Perms     │ │ Permission       │   │  │
│  │  │ Provider  │ │ Provider  │ │ Provider         │   │  │
│  │  └──────────┘ └──────────┘ └──────────────────┘   │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────────────┐   │  │
│  │  │ Network  │ │ A11y/UI  │ │ Event Collector  │   │  │
│  │  │ Provider  │ │ Provider  │ │ (logging)        │   │  │
│  │  └──────────┘ └──────────┘ └──────────────────┘   │  │
│  └───────────────────────┬───────────────────────────┘  │
│                          │ Internal Binder / LocalService│
└──────────────────────────┼──────────────────────────────┘
                           │
                           ▼
              Android Framework Services
       (AMS, WMS, PMS, NetworkManagement, etc.)
```

## 2. Where The Bridge Lives In Android Source

```
frameworks/base/
├── core/java/android/os/frankenbridge/        # Public AIDL + client API
│   ├── IFrankenBridgeService.aidl              # Main service interface
│   ├── IFrankenBridgeCallback.aidl             # Client callback interface
│   ├── IFrankenBridgeEventCallback.aidl        # Event subscription callback
│   ├── FrankenBridgeCallerIdentity.aidl        # Caller authentication parcel
│   ├── FrankenBridgeCapability.aidl            # Capability descriptor
│   ├── FrankenBridgeRequest.aidl               # Generic capability request
│   ├── FrankenBridgeResult.aidl                # Generic capability result
│   └── FrankenBridgeEvent.aidl                 # Event data parcel
├── services/core/java/com/android/server/frankenbridge/
│   ├── FrankensteinBridgeService.java          # Main SystemService
│   ├── AuthPolicy.java                         # Authentication + policy engine
│   ├── CapabilityRegistry.java                 # Static + dynamic capability registry
│   ├── AuditLogger.java                        # Audit event logging
│   ├── TaskWindowProvider.java                 # Foreground/recent tasks
│   ├── PkgPermProvider.java                    # Package + permission operations
│   ├── AppOpProvider.java                      # AppOp operations
│   ├── NetworkFirewallProvider.java            # Firewall/data-leak (later phase)
│   ├── EventCollector.java                     # User action event collection
│   └── A11yProvider.java                       # Accessibility operations
├── services/java/com/android/server/
│   └── SystemServer.java                       # ADD: startService(FrankensteinBridgeService.class)
└── ...
```

## 3. Binder / AIDL Boundary

### Client → Bridge Service
- The assistant APK binds to `FrankensteinBridgeService` via `Context.bindService()`
- All calls go through `IFrankenBridgeService` AIDL interface
- Service resolves real Binder caller UID/pid on each call (no trust from arguments alone)

### Bridge → System Services
- Internal providers use `LocalServices.getService()` (no IPC overhead)
- For external system services (AMS, WMS, PMS, etc.), use `ServiceManager.getService()` or framework manager APIs with proper permissions

## 4. Caller Authentication

The service **never** trusts caller-provided package names or UIDs:

```
onTransact() →
  Binder.getCallingUid()     → real UID from kernel
  Binder.getCallingPid()     → real PID from kernel
  Binder.getCallingUserHandle() → real user
  → PackageManagerInternal.getPackage(uid) → verify expected package
  → Verify caller is the expected bridge APK package
  → Verify caller has the expected platform signature
```

### How Authentication Works

1. **Compile-time:** The bridge APK is signed with the platform key (`platform.x509.pem` / `platform.pk8`)
2. **Runtime:** The service checks `getCallingUid()` and resolves the package via `PackageManagerInternal`
3. **Secondary check:** Verify that the calling package has the platform signature (`PackageManager.checkSignatures()`)
4. **Multi-client support (future):** Trusted companion apps can be allowlisted via sysconfig

## 5. Permission Model

The bridge service uses **three layers** of permission checks:

### Layer 1: Android Framework Permissions
Each capability method checks the relevant Android permission:
```java
// Example
public void forceStopPackage(String packageName) {
    enforceCallingPermission(FORCE_STOP_PACKAGES, null);
    // ...
}
```

### Layer 2: Capability-Level Authorization
```java
// Per-capability enable/disable in config
boolean canForceStop = capabilityRegistry
    .getCapability("app.force_stop")
    .isEnabledForCaller(getCallingUid());
```

### Layer 3: Risk-Tier Consent
```java
// R3/R4 actions require explicit user confirmation
if (capability.risk == RiskLevel.R3) {
    confirmationManager.requestUserConfirmation(
        caller, capability, params, confirmationCallback);
    return; // async, callback delivers result
}
```

### Permission Source Table

| Check | Source | Scope |
|---|---|---|
| `enforceCallingPermission()` | `Context.checkCallingPermission()` | Per Android permission |
| `isEnabledForCaller()` | `capabilityRegistry` config | Per-capability switch |
| `confirmationManager` | Trusted system UI | Per-action user consent |
| `auditLogger` | statsd / logd | Post-action audit trail |

## 6. Audit Logging

### Events Logged
Every capability execution is logged:

```json
{
  "timestamp": "2026-07-19T18:30:00Z",
  "uid": 10442,
  "package": "com.frankenbridge.assistant",
  "userId": 0,
  "capability": "app.force_stop",
  "parameters": {"packageName": "com.example.app"},
  "risk": "R3",
  "result": "SUCCESS",
  "confirmation": true,
  "denialReason": null,
  "durationMs": 150,
  "sessionId": "abc-123-def"
}
```

### Audit Sinks
| Sink | Format | Retention |
|---|---|---|
| `logd` (logcat) | Text | ~256KB circular |
| `statsd` (FrameworkStatsLog) | Structured proto | 1-7 days |
| `AuditLogger` internal buffer | In-memory ring | Last 1000 events |
| DropBoxManager | Text | ~7 days |

### Redaction
Parameters and results are redacted before persistent logging:
- Package names: keep (public info)
- Intent extras: strip
- File paths: strip
- User identifiers: keep user_id without profile detail
- Credential or auth material: never logged

## 7. Denial / Error Model

### Denial Reasons
| Code | Reason | HTTP Analogy |
|---|---|---|
| `DENIED_PERMISSION` | APK lacks required Android permission | 403 Forbidden |
| `DENIED_CAPABILITY_DISABLED` | Capability is globally disabled | 403 Forbidden |
| `DENIED_RISK_TOO_HIGH` | Action requires explicit confirmation | 401 Unauthorized with challenge |
| `DENIED_BACKGROUND` | Not allowed when app is in background | 403 Forbidden |
| `DENIED_LOCKED` | Device is locked | 401 Unauthorized |
| `DENIED_RATE_LIMIT` | Called too frequently | 429 Too Many Requests |
| `DENIED_USER_RESTRICTION` | User/admin restriction applies | 403 Forbidden |
| `DENIED_INVALID_CALLER` | Caller UID/package mismatch | 403 Forbidden |

### Error Handling
```aidl
// Result parcel
parcelable FrankenBridgeResult {
    int status;        // SUCCESS, DENIED, ERROR
    int denialCode;    // DENIED_* values
    String errorMessage; // Debug message (redacted in production)
    Bundle data;       // Capability-specific result data
    long latencyMs;    // Execution time
}
```

### Timeouts
| Tier | Timeout |
|---|---|
| R0-R1 (read/reversible) | 5 seconds |
| R2 (state change) | 10 seconds |
| R3-R4 (sensitive) | 30 seconds + user confirmation timeout |
| Default | 10 seconds |

## 8. Upgrade Path: MVP to Full Bridge

### MVP (Phase 1) — No ROM Changes
| Component | What it does |
|---|---|
| Platform-signed priv-app | APK in `/system/priv-app/FrankenBridge/` |
| `privapp-permissions.xml` entry | Grants `REAL_GET_TASKS`, `MANAGE_ACTIVITY_TASKS`, `FORCE_STOP_PACKAGES`, etc. |
| sysconfig entry | `hidden-api-whitelisted` for the APK |
| No custom SystemService | APK calls framework services directly via hidden/privileged APIs |

### Phase 2 — With SystemService
| Addition | Purpose |
|---|---|
| `FrankensteinBridgeService` | Centralizes auth, policy, audit |
| `IFrankenBridgeService.aidl` | Clean API surface, hides hidden API complexity |
| Event callbacks | Foreground task change, package add/remove, notification |
| Auth & Policy engine | Multi-layer permission model |
| Audit logging | Structured audit trail |

### Phase 3 — Full Bridge
| Addition | Purpose |
|---|---|
| `NetworkFirewallProvider` | Per-UID firewall via netd |
| `EventCollector` | User action logging for learning |
| `A11yProvider` | Accessibility operations via system service |
| SELinux domain | `frankenstein_bridge.te` |
| Confirmation UI | Trusted system dialog for high-risk actions |

## 9. Risks and Guardrails

### Privacy Risks
| Risk | Guardrail |
|---|---|
| Screen content collection | Only with explicit user grant + visible indicator |
| Notification content reads | Redact OTPs, financial data, passwords |
| Usage stats aggregation | Never expose across user profiles |
| Network traffic monitoring | Only per-app aggregate, never packet capture |
| Permission grant abuse | Audit log + configurable deny list for protected permissions |

### Security Risks
| Risk | Guardrail |
|---|---|
| Binder spoofing | `Binder.getCallingUid()` always from kernel |
| Hidden API bypass | Platform signing + priv-app check |
| Ratchet escalation | Per-capacity deny list in `/system/etc/frankenbridge/config.xml` |
| Unauthorized caller | Package + signature verification on every Binder call |
| Confirmation bypass | StrongAuth required for R4 actions |

### Abuse Prevention
| Risk | Guardrail |
|---|---|
| Automation too fast | Rate limiter: max 10 actions/second, configurable |
| Loop detection | If same action repeats >5 times in 60s, slow down |
| Lock screen bypass | R2+ actions require device unlocked |
| Access from other apps | Only the bridge APK is allowed to bind (future: explicitly allowlisted) |
| Flooding audit log | Audit rate limiter + ring buffer |

### Boot Stability
| Risk | Guardrail |
|---|---|
| Bridge service crashes | `SystemService` lifecycle; watchdog monitors |
| Bridge deadlocks bridge/APK | Separate thread for Binder calls; strict timeouts |
| SELinux denial boot loop | Per-domain permissive boot option for debug builds |
| Out-of-memory | Service runs in `system_server` with bounded memory; APK gets its own process |

### SELinux
```
# /system/sepolicy/private/frankenstein_bridge.te (future)

type frankenstein_bridge, domain;
type frankenstein_bridge_exec, exec_type, file_type;

# Binder calls to system services
binder_call(frankenstein_bridge, activity_service)
binder_call(frankenstein_bridge, window_service)
binder_call(frankenstein_bridge, package_service)
binder_call(frankenstein_bridge, appops_service)
binder_call(frankenstein_bridge, network_management_service)

# Binder calls from the bridge APK
binder_call(appdomain, frankenstein_bridge)
binder_call(frankenstein_bridge, appdomain) # for callbacks

# Filesystem access for config
allow frankenstein_bridge frankenbridge_config_file:file r_file_perms;
```
