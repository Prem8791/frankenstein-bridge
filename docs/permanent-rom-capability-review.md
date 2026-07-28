# Permanent ROM Capability Review

**Date:** 2026-07-25  
**Revision:** 2 — irreversible capability and future-extensibility review  
**Target:** Bliss Android 16 / ASUS I001D Waterlily  
**Decision constraint:** After this ROM release, `system_server`, framework code,
SELinux policy, init configuration, and the system image cannot be rebuilt.
Future development is limited to platform-signed privileged apps, ordinary apps,
AI models, broker/security layers, and plugins.

## Purpose

This review answers one question:

> What must be implemented in the ROM now because it cannot realistically be
> added later by a platform-signed privileged application?

This is not a general API-completeness review. It deliberately avoids spending
the final ROM-development window on wrappers around Android APIs that remain
available to future applications.

The permanent bridge should preserve future possibility by exposing:

1. Framework-only state and callbacks.
2. Protected diagnostic artifacts.
3. SELinux-restricted native and vendor functionality.
4. Stable extension and diagnostic primitives.

Application-facing policy, authorization, confirmations, AI orchestration, UI,
memory, and workflows belong in replaceable software outside the ROM.

## Existing implementation

The current `IFrankensteinBridgeService` exposes:

- `getBridgeVersion`
- `ping`
- `getCallerIdentity`
- `getCapabilityMatrix`
- `getForegroundApp`
- `getRecentTasks`
- `getInstalledPackages`
- `getUsageStatsSummary`
- `checkAppOps`
- `launchPackage`
- `getBatterySummary`

These methods demonstrate privileged Binder access, but almost all of them could
also be implemented later by a platform-signed application. The current fixed
interface does not preserve enough framework-only functionality for a permanent,
non-rebuildable ROM.

## Buckets

### Bucket A — Mandatory ROM implementation

Capabilities requiring one or more of:

- `system_server` changes
- `LocalServices` or private framework objects
- Framework callbacks not exported through Binder
- New Binder or native services
- SELinux policy or `service_contexts` changes
- Protected filesystem, kernel, HAL, or vendor access
- Native framework integration

These must be implemented before the final ROM release.

### Bucket B — Defer to a platform-signed application

Capabilities already available through public, system, hidden, or existing
Binder APIs with platform signing or signature permissions. These should be
implemented later in the replaceable broker.

### Bucket C — Ordinary application territory

Capabilities that ordinary Android applications can implement using public APIs,
user-granted access, application services, or model/plugin code.

### Bucket D — Intentionally omit

Low-value, unstable, obsolete, or dangerously generic internals that increase
maintenance cost without enabling meaningful assistant or diagnostic behavior.

## Primary decision criterion

Every proposed permanent operation must pass the following irreversible
capability test:

1. **Later app feasibility:** Can a future platform-signed app implement the
   operation through an existing public, system, hidden, Binder, HAL, file, or
   command interface?
2. **Exact blocker:** If not, is the blocker `LocalServices`, private framework
   state, missing callbacks, Binder SELinux rules, file/property contexts, init
   configuration, HAL visibility, native permissions, or an absent service?
3. **Workaround quality:** Can polling, accessibility, `dumpsys`, log parsing,
   device-owner provisioning, an exported Binder API, or a normal app reproduce
   the capability with acceptable fidelity?
4. **Permanent loss:** Would omission prevent future control, observation,
   diagnosis, or extension rather than merely make it less convenient?
5. **Realistic use:** Is there a credible assistant, automation, build-comparison,
   crash-analysis, or ROM-debugging workflow that consumes it?

An operation belongs in Bucket A only when the answer to question 1 is no, the
workarounds in question 3 are materially incomplete, and questions 4 and 5 are
yes. Platform signing is not treated as equivalent to running in
`system_server`: it does not grant access to `LocalServices`, create missing
callbacks, change SELinux policy, add init services, or cross every vendor/HAL
boundary.

## Subsystem decision matrix

An A classification means that at least one irreplaceable capability in the
subsystem must be implemented in the ROM. Other operations in that subsystem
may still belong in Bucket B or C.

| Subsystem | Bucket | ROM-only capability required now | Later implementation path | Omission impact | Priority |
|---|---:|---|---|---|---:|
| ActivityManager | A | Process/UID lifecycle stream, system-wide crash and ANR observer, memory-pressure events, private activity/service state snapshot | Platform app can perform most queries and force-stop operations | Loss of reliable lifecycle and crash provenance | Critical |
| ActivityTaskManager | B | None essential | Hidden Binder APIs and task-stack callbacks from platform app | Minor | Important later |
| PackageManager | A/B | Full-fidelity install, verification, rollback, dexopt, package-freeze, and internal-state event provenance | `IPackageManager`, PackageInstaller, ordinary package callbacks, and mutations remain deferred | Loss of exact package lifecycle and failure provenance | Important |
| WindowManager | A | Structured all-window/focus snapshot, private transition/focus callbacks, input-target correlation | Platform app and accessibility can cover common UI operations | Framework-level window failures remain opaque | Important |
| DisplayManager | B | None unless a device-specific display function is unexported | Hidden display APIs and exported Bliss services | Minor | Important later |
| PowerManager | A/B | Wake-lock/suspend-blocker attribution snapshots and transition events unavailable through a stable app API | Normal wake/sleep/reboot/saver controls remain deferred | Power and suspend failures become harder to attribute | Important |
| UsageStats | B | None | Existing usage-stat Binder service | Minor | Important later |
| AppOps | B | None | Existing hidden AppOps Binder API | Minor | Important later |
| NotificationManager | B | None | Privileged notification APIs or notification listener | Minor | Critical later |
| AlarmManager | B | None | Existing alarm Binder API | Minor | Important later |
| JobScheduler | B | None essential; global state can be obtained through the permanent dump broker | Existing job APIs and service dumps | Minor | Nice to have |
| UserManager | B | None | Existing hidden user service | Minor | Important later |
| DevicePolicyManager | A | Internal DPM gateway only if future device/profile-owner status cannot be guaranteed | Public/hidden DPM APIs when broker is device owner | Full administration may become impossible | Important |
| Connectivity | B | None | Existing connectivity Binder APIs | Minor | Critical later |
| Wi-Fi | B | None except restricted vendor/HAL functions | Existing Wi-Fi services | Minor | Important later |
| Bluetooth | B | None except restricted vendor/HAL functions | Existing Bluetooth services | Minor | Important later |
| Telephony | B | None for normal telephony; restricted modem diagnostics are a vendor concern | Existing telephony Binder services | Minor | Important later |
| VPN | B | None | Existing VPN manager and `VpnService` | Minor | Important later |
| NetworkPolicy | B | None | Existing policy and network-stat services | Minor | Important later |
| Sensors | C | None | Public sensor APIs; platform app for sensor-privacy controls | None | Nice to have |
| Camera | C | None for normal capture; HAL diagnostics handled separately | Public camera APIs | None | Nice to have |
| Media | C | None | Public media and media-session APIs | None | Important later |
| Audio | B | None | Hidden audio service and signature APIs | Minor | Important later |
| Speech | C | None | Local ASR/TTS/model components | None | Nice to have |
| Location | B | None normally | Public and hidden location APIs | Minor | Important later |
| Storage | B | None for managed volumes, quotas, and statistics | Existing storage manager services | Minor | Important later |
| Filesystem | A | Separate SELinux-domain daemon for protected files, `/proc`, selected `/sys`, pstore, tombstones and descriptor streaming | Apps remain confined even when platform-signed | Deep ROM diagnostics permanently unavailable | Critical |
| OverlayManager | B | None | Existing overlay Binder API | Minor | Nice to have |
| Settings | B | None | System/Global/Secure APIs with platform permissions | Minor | Critical later |
| DeviceConfig | B | None | Hidden DeviceConfig APIs and existing service | Minor | Important later |
| SystemProperties | A | Typed gateway for properties blocked by property-service SELinux, including metadata and observation | Platform apps can read many but cannot set all required properties | Low-level diagnosis and control remain incomplete | Critical |
| Clipboard | B | None | Existing clipboard service | Minor | Important later |
| Accessibility | B | None | Platform-signed accessibility service can be added later | Minor | Critical later |
| Input | A | Global input observation/filter hook, dispatch diagnostics, focused-window correlation, framework global hotkeys | Injection and device control can be done later by platform app | Workflow learning and input debugging limited | Important |
| Accounts | B | None | Existing AccountManager APIs | Minor | Nice to have |
| CredentialManager | B | None | Existing Credential Manager APIs | Minor | Nice to have |
| Biometrics | B | None | Existing biometric services and `BiometricPrompt` | Minor | Nice to have |
| Keystore | B/D | B for metadata and attestation status; D for secret or private-key extraction | Existing Keystore2 and Android Keystore APIs | None if secret export is omitted | Nice to have |
| Recovery | B | None normally | Hidden RecoverySystem APIs with platform privilege | Minor | Important later |
| OTA | B | None | Existing UpdateEngine and system-update services | Minor | Important later |
| Boot | A | BootControl HAL adapter, boot reason, verified-boot state, slot state, pstore and previous-boot artifacts | Reboot can be added later; restricted boot evidence and HAL access cannot | Boot and slot failures become difficult or impossible to diagnose | Critical |
| SELinux | A | Enforcing state, context resolution, policy digest, AVC stream and previous-boot denial correlation | Platform signing does not escape SELinux confinement | Policy failures remain permanently opaque | Critical |
| Binder ServiceManager | A | Service inventory, descriptors, death/health observation and generic `dump()` broker; raw transaction relay remains an omitted operation | Apps can access only services permitted by their SELinux domain | Newly relevant services cannot be diagnosed comprehensively | Critical |
| Statsd | B | None essential | Existing StatsManager and stats Binder APIs | Minor | Important later |
| Logcat | B | None for normal log buffers; kernel/audit logs belong in the protected daemon | Platform app with log permissions | Minor | Critical later |
| DropBox | B | None | Existing DropBox service with platform permission | Minor | Important later |
| Perfetto | B | None | Existing tracing service | Minor | Important later |
| Tombstones | A | Protected tombstone/native-crash index, metadata, streaming and crash callback | Platform app cannot reliably read all tombstones through SELinux | Native ROM crash diagnosis permanently crippled | Critical |
| Bugreports | B | None | Existing dumpstate/bugreport service | Minor | Critical later |
| Vendor HALs | A | Inventory, health, structured dumps, and typed adapters for SELinux-restricted HALs | Platform app domains frequently cannot find or call vendor HALs | Hardware diagnosis/control gaps become permanent | Critical |
| Vendor extensions | A/B | A for LocalServices, private sockets, sysfs and unexported callbacks; B for exported Binder services | Exported services can be used later | Device-specific limitation | Important |
| Vendor services | A/B | Same rule as vendor extensions | Exported AIDL services can be used later | Device-specific limitation | Important |

## Irreversible Capability Checklist

This checklist applies the primary decision criterion to every reviewed
subsystem. “Later” means after the framework, system image, SELinux policy, and
init configuration have become permanently immutable.

| Subsystem | Platform app later? | Exact blocker when not possible | Workaround | Permanent reduction if omitted? | Realistic future use |
|---|---|---|---|---|---|
| ActivityManager | Partially | Private AMS records, `LocalServices`, crash/ANR insertion points, and callbacks not exported through `IActivityManager` | Public/hidden process queries, polling, `dumpsys activity` | Yes: event provenance and coherent private state are lost | Explain process death, ANRs, background kills, and memory pressure |
| ActivityTaskManager | Yes, substantially | A few private transition details remain internal, but standard task control and listeners are exported | Hidden `IActivityTaskManager`, task-stack listener, accessibility | No material loss if window/transition diagnostics are provided separately | Launch, switch, reorganize, and observe tasks |
| PackageManager | Partially | `PackageManagerInternal`, installer/verifier/dexopt/freeze state, and some failure callbacks are not exported coherently | `IPackageManager`, PackageInstaller, broadcasts, dumps | Yes: exact install/update/optimization provenance is lost | Diagnose failed installs, bad updates, verifier decisions, and package state drift |
| WindowManager | Partially | Private `WindowState`, transition controller, input-target state, and non-exported focus callbacks | `IWindowManager`, accessibility, `dumpsys window` | Yes: exact focus, transition, and input-routing diagnosis is reduced | Explain invisible windows, focus theft, stuck transitions, and input failures |
| DisplayManager | Yes, normally | Vendor display/HWC details may be restricted, but standard display control is exported | `IDisplayManager`, Bliss display services, SurfaceControl APIs | Usually no; vendor gaps are handled under HALs | Resolution, refresh rate, brightness, external and virtual displays |
| PowerManager | Partially | Internal wake-lock ownership, suspend blockers, power-state transitions, and some attribution live in private services/native layers | `IPowerManager`, batterystats, powerstats, `dumpsys power` | Yes for precise suspend/battery diagnosis | Find wake-lock leaks, failure to suspend, unexpected wakeups, and power regressions |
| UsageStats | Yes | No material framework-only blocker | `IUsageStatsManager`, usage observers | No | Behavioral summaries and app-use diagnostics |
| AppOps | Yes | No material blocker for platform-signed callers | `IAppOpsService` and watchers | No | Inspect and modify effective app access |
| NotificationManager | Yes | No material blocker; listener/assistant roles and hidden service are available | Notification listener/assistant and `INotificationManager` | No | Read, dismiss, reply to, and act on notifications |
| AlarmManager | Yes | No material blocker | `IAlarmManager`, alarm APIs and dumps | No | Timers, alarms, and wakeup diagnosis |
| JobScheduler | Yes for normal jobs; global events only partially | Internal `JobStatus` transitions are private | `IJobScheduler`, dumps, statsd, polling | Usually no; diagnostic precision is reduced but not foundational | Diagnose delayed jobs and schedule assistant work |
| UserManager | Yes | No material blocker with platform signature | `IUserManager` | No | Multi-user and profile operations |
| DevicePolicyManager | Only if future broker is device/profile owner | DPM authorizes many calls by active-admin/owner identity, not platform signature alone; `DevicePolicyManagerInternal` is in-process | Provision broker as owner; use public/hidden DPM APIs | Yes if owner provisioning cannot be guaranteed | Kiosk, restrictions, managed profiles, and enterprise administration |
| Connectivity | Yes | No material blocker for framework-level networking | `IConnectivityManager`, callbacks, network stack services | No | Network selection, diagnosis, and recovery |
| Wi-Fi | Yes for framework functions | Vendor HAL and restricted diagnostics may be unavailable | `IWifiManager` and related exported services | No for normal Wi-Fi; vendor-specific loss handled under HALs | Connection recovery, hotspot, scanning, and diagnostics |
| Bluetooth | Yes for framework functions | Controller/vendor debug interfaces may be restricted | Existing Bluetooth manager/profile APIs | No for normal operation | Pairing, profiles, LE, and audio control |
| Telephony | Yes for framework functions | Modem/vendor diagnostics and private radio interfaces may be restricted | Internal telephony Binder services | No for normal telephony; modem-debug loss handled under vendor adapters | Calls, SMS, subscriptions, IMS, and radio diagnosis |
| VPN | Yes | No material blocker | `IVpnManager`, platform VPN profiles, `VpnService` | No | Always-on VPN and routing control |
| NetworkPolicy | Yes | No material blocker | Network policy, stats, and netd-facing framework services | No | Data saver, UID policies, quotas, and usage analysis |
| Sensors | Yes | No framework modification required for meaningful sensor use | Public sensor APIs | No | Context awareness and hardware tests |
| Camera | Yes for meaningful capture/control | Low-level provider diagnostics may be SELinux-restricted | Camera2, camera service; HAL adapter for restricted diagnostics | No for assistant camera use | Capture, vision, torch, and camera tests |
| Media | Yes | No material blocker | Media sessions, router, projection, codecs, and public media APIs | No | Playback control, casting, and media understanding |
| Audio | Yes | No material blocker with platform permissions | `IAudioService`, AudioManager, audio policy APIs | No | Routing, volume, focus, recording, and diagnosis |
| Speech | Yes | No ROM dependency | App-provided local or remote ASR/TTS | No | Voice interface |
| Location | Yes | No material blocker for platform callers | `ILocationManager` and public APIs | No | Navigation, geofencing, and context |
| Storage | Yes for managed storage | Raw protected paths remain blocked by SELinux | StorageManager, vold Binder APIs, SAF; protected daemon for raw artifacts | No if Filesystem Bucket A work is implemented | Volume, quota, and capacity management |
| Filesystem | No for protected system-wide access | App SELinux domains cannot read arbitrary `/data`, `/proc`, `/sys`, pstore, tombstones, or protected sockets | Public/scoped storage, SAF, bugreports; none provide full live access | Yes, severely | Inspect crashes, boot failures, kernel state, app/system files, and build artifacts |
| OverlayManager | Yes | No material blocker | `IOverlayManager` and fabricated overlays | No | Theme, resource, and compatibility adjustment |
| Settings | Yes | No material blocker with platform signature | Settings providers and observers | No | Device configuration and automation |
| DeviceConfig | Yes | No material blocker | DeviceConfig APIs/service | No | Feature flags and tunables |
| SystemProperties | Partially | Property-service SELinux and property contexts restrict writes by app domain | Hidden `SystemProperties` for permitted properties; shell/root unavailable by assumption | Yes for restricted properties | Hardware toggles, debug state, build comparison, and reboot-required configuration |
| Clipboard | Yes | No material blocker | `IClipboard`, public clipboard APIs | No | Cross-app workflows |
| Accessibility | Yes | No framework patch required | Platform-signed accessibility service, temporary enable APIs | No | UI semantics and automation |
| Input | Partially | Global input filtering/observation and dispatch internals live in InputManager/WMS policy; injection alone is exported | `IInputManager` for injection, accessibility gestures, input-device APIs | Yes for passive global observation and dispatch diagnosis | Learn physical workflows, global shortcuts, and diagnose stuck input |
| Accounts | Yes within Android account semantics | Authenticator-owned secrets and consent are intentionally not bypassed | AccountManager and authenticators | No meaningful loss | Account discovery and account-scoped actions |
| CredentialManager | Yes | No material blocker; credential UI/provider flow is already exported | Credential Manager APIs | No | Passkeys and credential-mediated sign-in |
| Biometrics | Yes for authentication/status | Templates and raw biometric data are intentionally inaccessible | `BiometricPrompt`, biometric service metadata | No meaningful loss | Authenticate sensitive broker actions |
| Keystore | Yes for operations and metadata | Non-exportability of private keys is intentional hardware/security behavior | Keystore2 and Android Keystore | No meaningful loss | Signing, encryption, attestation, and key health |
| Recovery | Yes for standard operations | Some artifacts are protected, but covered by Filesystem/Boot providers | RecoverySystem and recovery/update services | No if protected artifacts are implemented separately | Recovery reboot, wipe, and update workflows |
| OTA | Yes | No material blocker | UpdateEngine and system-update Binder APIs | No | Install and monitor updates |
| Boot | No for complete control/diagnosis | BootControl HAL visibility, pstore, bootloader state, and previous-boot artifacts cross SELinux/native boundaries | Reboot APIs and OTA services cover only normal operations | Yes, severely | Slot recovery, boot-loop diagnosis, verified-boot checks |
| SELinux | No for complete diagnostics | SELinux policy, audit buffers, kernel interfaces, contexts, and protected logs are unavailable to app domains | Logcat may contain a subset; bugreports are delayed snapshots | Yes, severely | Explain denials, validate policy, and compare builds |
| Binder ServiceManager | Partially | SELinux controls service discovery/calls; private services may not be findable from an app domain | Public managers, hidden ServiceManager, `dumpsys`; incomplete for restricted services | Yes for unknown/restricted service inspection | Inventory services, detect deaths, and diagnose framework state |
| Statsd | Yes | Custom framework atoms cannot be added later, but the generic event bus can provide bridge-specific telemetry | StatsManager and existing atoms | Usually no | Longitudinal performance and reliability analysis |
| Logcat | Yes for Android buffers | Kernel/audit buffers are separate and protected | READ_LOGS/LogcatManager; protected daemon for kernel/audit | No if protected daemon exists | Search runtime failures and correlate events |
| DropBox | Yes | No material blocker with platform permission | DropBoxManager service | No | Retrieve crash, ANR, watchdog, and system reports |
| Perfetto | Yes | No material blocker for trace capture | Existing tracing services | No | Performance and scheduling diagnosis |
| Tombstones | No for complete system-wide access | `/data/tombstones`, native crash pipeline, and prior-boot artifacts are SELinux-protected | Bugreport excerpts and app-owned tombstones are incomplete | Yes, severely | Diagnose native services, HALs, and system processes |
| Bugreports | Yes | No material blocker | Dumpstate/BugreportManager | No | Full support bundles and offline analysis |
| Vendor HALs | Often no | Vendor Binder/HwBinder visibility, SELinux rules, device nodes, sockets, and HAL-specific permissions | Exported framework managers or vendor Binder APIs when present | Yes for each unexported hardware capability | Diagnose and control charging, display, touch, modem, camera, Wi-Fi, and power |
| Vendor extensions | Depends on export | `LocalServices`, private sockets, sysfs, or missing AIDL prevent later access | Exported Bliss/Lineage/vendor Binder service where available | Yes for unexported features | Device-specific automation and debugging |
| Vendor services | Depends on export | Service-manager SELinux, signature checks, or private protocol may block platform app | Direct AIDL when exported; dump broker for diagnostics | Yes for restricted services | Vendor subsystem health, state, and control |

### Checklist conclusion

The checklist narrows mandatory ROM work to missing framework observation,
protected diagnostics, restricted native/vendor access, and the protocol needed
to expose them durably. It does not justify moving ordinary framework-manager
operations into `system_server`.

## Mandatory ROM capabilities

### 1. Permanent extensible bridge contract

The existing fixed AIDL must be supplemented or replaced with a stable root
interface:

```text
getBridgeInfo()
listProviders()
describeProvider(providerId)
getProvider(providerId, minimumVersion)
listOperations(providerId)
listEvents(providerId)
getSchema(schemaId, version)
getCapabilityGraph(filter)
registerExternalProvider(descriptor, binder)
unregisterExternalProvider(providerId)
observeCapabilityCatalog(callback)
subscribeSystemEvents(subscription, callback)
createDiagnosticSnapshot(request)
dumpService(serviceName, args)
openProtectedArtifact(artifactId)
```

The permanent protocol must support:

- Provider and operation versions
- Runtime feature discovery
- Explicit Android user/profile scope
- Pagination and continuation tokens
- Asynchronous operation handles
- Cancellation and progress
- Event sequence numbers and overflow indication
- `ParcelFileDescriptor`, pipes, and shared-memory transport
- Stable symbolic identifiers rather than framework numeric constants
- Structured errors, warnings, and partial results
- Federated registration of future platform-signed app/plugin providers
- Provider liveness, dependency, and availability reporting
- Schema lookup independent of compiled client DTOs
- Catalog-change events when plugins appear, disappear, or change health

**Omission risk: Extreme.** Future software would remain tied to the current
eleven methods.

### 2. Framework internal event bus

Expose events that cannot be reconstructed reliably by a later app:

- Process start, death, importance, and state
- UID state and capability changes
- System-wide Java crashes, native crashes, and ANRs
- Foreground task/activity transitions
- Window focus and display transitions
- Full-context package lifecycle events
- User/profile switching and unlocking
- Memory-pressure events
- Boot phases and service readiness
- Watchdog and pre-watchdog events
- Framework service failures
- Input dispatch failures and latency

Use typed event envelopes containing:

- Event ID and event schema version
- Correlation ID and parent correlation ID
- Monotonic global sequence and provider-local sequence
- Wall-clock, elapsed-realtime, and uptime timestamps
- Originating provider and Android subsystem
- Originating Android user/profile
- Originating UID and PID when known
- Originating package and component when known
- Related operation/session ID
- Device-state snapshot ID
- Payload schema ID and version
- Coalesced-event count
- Lost-event/overflow count
- Delivery attempt and replay marker

The bus must support initial-state delivery, filtering, replay from a bounded
sequence or timestamp, backpressure, batching, callback death handling, and
explicit gap notifications. Events must use stable bridge DTOs rather than
framework-internal objects.

**Omission risk: Extreme.** Polling cannot recover lost provenance.

### 3. Coherent framework diagnostic snapshots

Create one operation that collects a timestamped, correlated state snapshot from
framework internals:

- AMS processes and UIDs
- ATM tasks and activities
- WMS windows, focus, transitions, and displays
- Package state
- Jobs and alarms
- Power, thermal, and idle state
- Users and DPM state
- Network state
- Service health
- Build identity and configuration

Snapshots should have stable schemas and a correlation ID so future tooling can
compare builds and analyze incidents without parsing multiple race-prone dumps.

**Omission risk: High.**

### 4. Binder service inventory and dump broker

Implement:

```text
listBinderServices()
getBinderServiceDescriptor(name)
observeBinderServiceHealth(name)
dumpBinderService(name, args) -> ParcelFileDescriptor
dumpSystemServer() -> ParcelFileDescriptor
```

This is the main diagnostic escape hatch for services not anticipated before
release. It should stream existing service dumps without exposing raw Binder
transactions.

**Omission risk: High.**

### 5. Protected diagnostic daemon

Create a separate native or system process with a dedicated SELinux domain for:

- Protected filesystem access
- `/proc` and selected `/sys` inspection
- pstore and previous-boot logs
- Kernel and audit evidence
- Tombstones and ANR traces
- Recovery and update artifacts
- Native service and HAL dumps
- Large diagnostic streams
- Optional bounded diagnostic-process execution using structured executable and
  argv fields, strict timeouts, cancellation, and output limits

The Java `system_server` bridge should not perform blocking file or process work.
It should communicate with this daemon through versioned AIDL and forward file
descriptors to clients.

**Omission risk: Extreme.** An APK cannot add the required init and SELinux
configuration later.

### 6. SELinux diagnostics

Implement:

- Enforcing/permissive state
- Process, file, property, and service context lookup
- Policy version and digest
- AVC denial stream
- Filters by timestamp, PID, UID, source domain, target type, and class
- Package/process correlation
- `service_contexts`, `file_contexts`, and `property_contexts` resolution
- Previous-boot denial retrieval
- `selinux_check_access` queries where supported

Policy mutation and general permissive controls are not needed.

**Omission risk: Extreme.**

### 7. Tombstone and native-crash access

Implement:

- Tombstone index
- Crash metadata and reason
- Process, UID, package, and user mapping
- Build fingerprint and ABI
- Signal, fault address, and abort message
- Backtrace streaming
- Previous-boot crash artifacts
- Native crash callbacks
- Build ID and symbol metadata when available

**Omission risk: Extreme** for permanent ROM diagnostics.

### 8. Boot and boot-control integration

Implement:

- Current and active boot slot
- Slot success and bootable state
- Boot attempt state when supported
- Boot reason
- Verified Boot state
- Rollback state/index where available
- Previous-boot failure artifacts
- Boot phase stream
- BootControl HAL adapter
- Recovery/update status correlation

Reboot and normal OTA submission can remain in the future broker.

**Omission risk: High.**

### 9. System-property gateway

Implement:

```text
getProperty()
listProperties()
observeProperty()
setProperty()
getPropertyMetadata()
```

Metadata should include property context, type, mutability, persistence, origin,
and whether a restart or reboot is required.

**Omission risk: High.**

### 10. Restricted vendor HAL and service adapters

Inventory the exact Waterlily HALs, vendor Binder services, sockets, sysfs nodes,
and Bliss/Lineage extensions before release. Add typed adapters for anything not
callable from the future platform-app SELinux domain.

Review at minimum:

- Boot control
- Health and charging
- Power and performance
- Thermal
- Lights and vibration
- Camera provider
- Wi-Fi and Bluetooth HALs
- Touchscreen, glove, and gesture controls
- Display refresh rate and resolution
- Modem and IMS diagnostics
- USB gadget
- Sensors

Every adapter should expose availability, interface version/hash, liveness,
structured state, dump output, and relevant callbacks.

**Omission risk: High and device-specific.**

### 11. Global input and focus hooks

Implement:

- Global input observation
- Input-device change events
- Dispatch latency and failure state
- Focused-window/task correlation
- Framework-level configurable hotkey/gesture callback
- Input pipeline snapshot

Input injection can be deferred.

**Omission risk: Medium to High.**

### 12. Device-policy internal gateway

Implement this only if the future broker cannot be guaranteed to become device
or profile owner during provisioning. Expose stable operations backed by
`DevicePolicyManagerInternal` rather than raw internal records.

**Omission risk: High if owner provisioning is uncertain; otherwise Medium.**

### 13. Structured window/focus diagnostics

Implement:

- Focused window and input target
- Visible-window summary
- Window owner UID/package
- Display/task/activity correlation
- Transition and focus callbacks
- Keyguard and system-window state
- Window/input dispatch diagnostic state

Do not expose private `WindowState` or `ActivityRecord` objects.

**Omission risk: Medium.**

### 14. Package lifecycle provenance

Capture internal package events that ordinary package broadcasts and installer
callbacks do not preserve:

- Install-session creation, staging, verification, commit, and failure phases
- Verifier and integrity decisions with stable reason codes
- Package freeze/unfreeze and scan/reconcile phases
- APK/split origin and resulting package-state generation
- Dexopt/artd request, reason, result, compiler filter, and failure
- Rollback availability, enablement, commit, and failure
- Package replacement correlation across old and new versions
- Boot-time package scan and reconciliation failures

Expose stable package/build identities and artifact references, not
`PackageStateInternal` or installer implementation objects.

**Omission risk: Medium to High.**

### 15. Power attribution provenance

Capture:

- Wake-lock acquire, release, timeout, and owner attribution
- Suspend-blocker state and transition events
- Wake/sleep reason and originating UID/package when known
- Doze/device-idle transitions
- Thermal throttling transitions
- Power-mode changes
- Correlation with foreground task, process state, and battery snapshots

Normal power control remains deferred. This provider exists to retain evidence
that polling and later textual dumps cannot reconstruct.

**Omission risk: Medium to High.**

## Capability metadata requirements

Capability metadata is part of the permanent ROM contract, not optional
documentation. A future broker, plugin, diagnostic tool, or AI must be able to
understand the frozen bridge without possessing this repository.

### Provider metadata

Every ROM and externally registered provider must expose:

| Field | Permanent requirement | Purpose |
|---|---:|---|
| `providerId` | Yes | Stable, globally unique symbolic identity |
| `providerVersion` | Yes | Semantic version of the provider contract |
| `descriptorVersion` | Yes | Version of the metadata envelope itself |
| `displayName` | Yes | Human-readable identification |
| `description` | Yes | Self-contained functional description |
| `category` | Yes | Activity, diagnostics, storage, vendor, power, and so on |
| `implementationOrigin` | Yes | ROM, native daemon, vendor, or external plugin |
| `implementationStatus` | Yes | Available, degraded, unavailable, experimental, or removed |
| `supportedAndroidVersions` | Yes | Android API/build range for which semantics are defined |
| `supportedRomVersions` | Yes | ROM build/fingerprint constraints |
| `interfaceHash` | Yes | Detect descriptor and implementation mismatches |
| `dependencies` | Yes | Binder services, providers, HALs, features, or artifacts required |
| `featureFlags` | Yes | Runtime gates that change availability or semantics |
| `health` | Yes | Liveness, last check, degraded reason, and restart count |
| `operations` | Yes | Discoverable operation IDs and versions |
| `events` | Yes | Discoverable event IDs and schema versions |
| `deprecationState` | Yes | Active, deprecated, superseded, or removed |
| `replacementProvider` | When applicable | Migration target |

### Operation metadata

Every operation must expose:

- Stable `operationId` and `operationVersion`
- Provider ID and provider version
- Short name, full description, examples, and search aliases
- Capability category and related capability IDs
- Required privilege class or execution domain
- Read-only, mutating, externally visible, or diagnostic effect
- Idempotency and reversibility
- Expected latency class and timeout hint
- Synchronous or asynchronous execution
- Streaming and pagination support
- Cancellation and progress support
- Input schema ID/version
- Output schema ID/version
- Error schema and stable error codes
- Associated event schemas
- Preconditions and availability conditions
- Dependency providers/services/features
- Maximum request, result, and stream sizes
- Deprecation state and replacement operation
- Implementation status and feature flags

Canonical serialized field names should include `providerId`, `providerVersion`,
`operationId`, `operationVersion`, `supportedAndroidVersions`,
`supportedRomVersions`, `capabilityCategory`, `requiredPrivilege`, `effect`,
`expectedLatency`, `executionMode`, `streamingSupport`,
`cancellationSupport`, `timeoutHintMs`, `inputSchema`, `outputSchema`,
`eventSchemas`, `deprecationState`, `replacementCapability`,
`implementationStatus`, and `featureFlags`.

`requiredPrivilege` is metadata rather than enforcement in this document. It is
still permanent because a future broker needs to distinguish operations
executing in an app, `system_server`, the protected daemon, or a vendor/HAL
domain.

### Schema metadata

Input, output, event, and error schemas must be retrievable by stable
`schemaId`/version. The schema language must represent:

- Objects, arrays, maps, unions, and nullable values
- Required and optional fields
- String length/pattern and numeric ranges
- Enums with symbolic names and descriptions
- Units, clocks, time bases, and coordinate spaces
- Android user, UID, PID, package, component, task, display, and subscription IDs
- File descriptors, shared memory, and stream handles
- Pagination and continuation
- Forward-compatible unknown fields
- Example requests/results
- Field introduction and deprecation versions

The ROM should ship a compact schema AST as versioned parcelables. A future
broker may translate it to JSON Schema, protobuf descriptors, OpenAPI, an
LLM-specific tool schema, or another external representation. The permanent
bridge must not depend on a particular AI or web schema format.

## Dynamic capability discovery

Future software should discover the entire catalog without hardcoded provider
lists. The root service should provide:

```text
listProviders(filter, page)
describeProvider(providerId, acceptedDescriptorVersions)
listOperations(providerId, filter, page)
describeOperation(providerId, operationId, acceptedVersions)
listEvents(providerId, filter, page)
describeEvent(providerId, eventId, acceptedVersions)
listCallbackContracts(providerId)
getSchema(schemaId, acceptedVersions)
getCapabilityGraph(filter)
resolveCapability(query)
observeCapabilityCatalog(callback)
```

Discovery must include:

- Providers, operations, events, and callback contracts
- Argument, result, event, and error schemas
- Provider and operation dependencies
- Replaces/replaced-by and deprecation relationships
- Produces-event and consumes-artifact relationships
- Query/action and action/verification pairings
- Runtime availability and precise unavailable reason
- Current health and degraded modes
- Required Android feature, HAL, user state, or daemon

### Federated external providers

The permanent root protocol must allow future platform-signed broker modules and
plugins to register their own provider Binder:

```text
registerExternalProvider(ProviderDescriptor, IExternalCapabilityProvider)
renewExternalProvider(providerId, generation)
updateExternalProvider(descriptor)
unregisterExternalProvider(providerId)
```

The root catalog should merge ROM, native-daemon, vendor, broker, and plugin
providers while preserving `implementationOrigin`. Binder death must
automatically remove or mark an external provider unavailable and emit a catalog
event.

This federation is the primary answer to future app-level extensibility. It does
not load plugin code into `system_server`; it only publishes metadata and routes
calls to an external process.

## Future AI self-discovery

A future AI with no documentation should be able to:

1. Read bridge/protocol identity and limits.
2. Enumerate providers by category or natural-language query.
3. Inspect operations and their argument/result schemas.
4. Discover events and subscription contracts.
5. Understand availability, dependencies, units, side effects, and limitations.
6. Find verification operations and related capabilities.
7. Execute through a future broker and interpret structured results.

Machine-readable field names alone are insufficient. Provider and operation
descriptors also need:

- Concise purpose and detailed semantic description
- Examples and counterexamples
- Search aliases and domain vocabulary
- Units and time bases
- Side-effect description
- Preconditions and postconditions
- Result freshness and completeness guarantees
- Pagination/stream termination semantics
- Common error explanations and suggested recovery operation IDs
- Explicit limitations and unsupported cases

The bridge should expose a `resolveCapability(query)` discovery helper that
returns ranked descriptor IDs based on names, aliases, categories, and
relationships. It need not embed an AI model; deterministic indexed lookup is
sufficient. AI-specific ranking can remain in the broker.

With these additions, an AI can learn the bridge from its catalog. Without
descriptions, schemas, examples, relationships, and error semantics, discovery
would only reveal opaque method names and still require external documentation.

## Vendor adaptation strategy

Vendor support should follow one permanent adapter protocol rather than produce
unrelated vendor-specific AIDL surfaces.

Every vendor adapter must expose:

- Stable bridge provider ID
- Vendor interface descriptor
- AIDL/HIDL interface version and hash when available
- Vendor/build fingerprint constraints
- Runtime availability and unavailable reason
- Health, liveness, restart count, and last error
- Supported operations and events
- Input/output/event schemas
- Structured diagnostic snapshot
- Raw vendor dump as a streamed artifact
- Dependency HALs, services, nodes, sockets, and properties
- Feature flags and known limitations

The adapter translates vendor parcelables, numeric constants, sysfs text, or
private socket protocols into stable bridge DTOs. Future clients must never need
to import vendor Java classes or know vendor transaction codes.

The catalog should support multiple implementations of the same logical
capability. For example, `device.charging.control` might be implemented by a
Lineage health HAL on one device and a vendor sysfs adapter on another. A
`logicalCapabilityId` and `implementationPriority` allow the future broker to
select the available implementation without hardcoded device logic.

At release time, generate and archive a vendor capability manifest containing:

- Every discovered vendor Binder/HwBinder service and HAL
- Whether it is callable from the future platform-app domain
- Whether structured control, structured diagnostics, or dump-only access exists
- The adapter chosen for each inaccessible but useful feature

## Event architecture

The event bus is a permanent data plane rather than a convenience callback. Its
envelope should be:

```text
BridgeEvent {
    eventId
    eventSchemaVersion
    correlationId
    parentCorrelationId
    globalSequence
    providerSequence
    wallClockTimestampMs
    elapsedRealtimeNanos
    uptimeNanos
    providerId
    subsystem
    userId
    uid
    pid
    packageName
    componentName
    operationId
    sessionId
    deviceStateSnapshotId
    payloadSchemaId
    payload
    coalescedCount
    lostBeforeCount
    replayed
}
```

Additional permanent requirements:

- Filter by provider, event, user, UID, package, and severity
- Initial snapshot plus subsequent delta mode
- Bounded replay by sequence or timestamp
- Explicit gap events after overflow or restart
- Batch delivery
- Backpressure and subscriber quotas
- Callback death handling
- Provider-generation identifier after restarts
- Ordering guarantees documented per event type
- Durable-artifact references for payloads too large for Binder
- Correlation with snapshots, dumps, tombstones, traces, and operations

Framework events must be normalized at capture time. Future clients should not
parse log strings to recover state that was available as structured data inside
the framework.

## Long-term API stability

The bridge must freeze semantic DTOs, not Android implementation classes.

### APIs that require stable DTOs before release

| Proposed area | Do not expose | Freeze instead |
|---|---|---|
| Process snapshot | `ProcessRecord`, `ApplicationInfo` internals | `BridgeProcessInfo`, `BridgeUidState` |
| Task/activity | `ActivityRecord`, `Task`, raw `RunningTaskInfo` | `BridgeTaskInfo`, `BridgeActivityInfo` |
| Window/focus | `WindowState`, transition objects | `BridgeWindowInfo`, `BridgeFocusState`, `BridgeTransitionInfo` |
| Package lifecycle | `PackageStateInternal`, installer/verifier internals | `BridgePackageState`, `BridgePackageEvent` |
| Power | Internal wake-lock and suspend objects | `BridgeWakeLockInfo`, `BridgeSuspendBlockerInfo`, `BridgePowerEvent` |
| DPM | Private owner/admin records | `BridgePolicyState`, `BridgeRestriction` |
| Binder inspection | Raw `IBinder`, transaction code, `Parcel` | `BridgeServiceInfo`, `BridgeServiceHealth`, dump stream |
| SELinux | Raw audit lines as the primary contract | `BridgeAvcDenial`, `BridgeSecurityContext`, plus optional raw artifact |
| Tombstones | Filesystem path as the only identity | `BridgeCrashArtifact`, opaque artifact ID, descriptor stream |
| Boot/HAL | Vendor BootControl parcelables | `BridgeBootSlotInfo`, `BridgeVerifiedBootState` |
| System properties | Bare key/value only | `BridgePropertyInfo` with type/context/mutability |
| Vendor adapters | Vendor parcelables and numeric enums | Logical capability DTOs plus vendor diagnostic metadata |

### DTO evolution rules

- Never reorder or reinterpret existing fields.
- Add fields as optional with explicit introduction version.
- Use symbolic enums with an `UNKNOWN` value and preserve unknown numeric values.
- Identify every schema independently of its provider implementation.
- Avoid Java class names in protocol identity.
- Do not use filesystem paths, Binder handles, PIDs, or internal object IDs as
  durable identity.
- Use opaque handles with scope, generation, and expiry for transient objects.
- Separate timestamps by clock domain.
- Include units explicitly.
- Keep raw diagnostic text as an artifact fallback, not the structured contract.

The current `Bundle`-based `getCapabilityMatrix()` and capability results should
be retained only as compatibility methods. They are not suitable permanent
interfaces because their keys and nested value types are not self-describing or
versioned.

## Generic diagnostic and transport infrastructure

The permanent bridge should provide foundational primitives reused by every
provider:

### Generic snapshot provider

Creates coherent multi-provider state snapshots with a correlation ID,
timestamp, requested sections, schema versions, completeness status, and links
to large artifacts.

### Generic artifact provider

Lists and opens typed artifacts such as tombstones, pstore, ANRs, bugreports,
traces, service dumps, recovery logs, and vendor diagnostics by opaque ID.
Supports metadata, ranges, checksums, retention state, and
`ParcelFileDescriptor` streaming.

### Generic dump provider

Streams Binder, native-service, HAL, and bridge-provider dumps with structured
request metadata, timeout, truncation indication, exit/result status, and
artifact persistence.

### Generic event provider

Implements discovery, subscription, replay, batching, gaps, filtering, and
correlation consistently across ROM and external providers.

### Generic service inspection

Inventories framework/native/vendor services, descriptors, versions, hashes,
liveness, owning process/domain, dependencies, and dump support.

### Generic stream interface

Provides:

- Stream ID and generation
- File descriptor or shared-memory transport
- MIME/logical content type
- Declared and transferred length
- Compression
- Checksum
- Progress
- Cancellation
- Timeout
- Truncation/error state

### Generic structured result

Every operation returns or completes with:

```text
BridgeResult {
    status
    stableErrorCode
    message
    providerId
    providerVersion
    operationId
    operationVersion
    correlationId
    startedElapsedNanos
    completedElapsedNanos
    outputSchemaId
    output
    artifactRefs
    warnings
    partial
    retryHint
}
```

These primitives prevent each subsystem from inventing incompatible transport,
error, pagination, and callback conventions.

## Unknown future needs

The design must be honest about what can and cannot be future-proofed.

### New app-accessible subsystem discovered later

A platform-signed plugin implements it and registers an external provider. The
root catalog exposes its metadata, schemas, events, and health without a ROM
change.

### New exported Binder service discovered later

The plugin can call it directly. The permanent service inspector and dump broker
can diagnose it, and the plugin federates a typed provider into the catalog.

### New subsystem requiring protected files, properties, or commands

The protected daemon can serve it only if the release-time SELinux policy and
daemon primitive genuinely cover the required object classes and access. The
daemon therefore needs carefully chosen generic artifact, property, sysfs/proc,
dump, and bounded diagnostic-execution primitives now.

### New subsystem exposed only through `LocalServices` or private Java objects

No safe future app-only mechanism can add a new typed adapter. The permanent ROM
must either anticipate the required state through snapshots/events or accept
that the capability is lost.

Arbitrary reflection into `LocalServices` or dynamically loading plugin DEX into
`system_server` would appear to solve this, but would turn private Java
implementation details into a remote ABI and allow future plugin defects to
destabilize the permanent system process. This review continues to reject that
approach.

### New SELinux-restricted Binder/HAL control operation

Dump and health inspection remain possible if the generic service/HAL inspector
can reach it. Arbitrary control cannot be guaranteed without a prebuilt typed
adapter. A raw transaction relay is not considered a stable capability because
it cannot safely model Binder objects, file descriptors, parcelable class
loaders, identity semantics, or transaction compatibility.

### Maximum-flexibility conclusion

The strongest maintainable hedge against unknown needs is the combination of:

1. Federated external provider registration
2. Self-describing schemas and capability graph
3. Generic service inspection and dump streaming
4. Generic artifact and protected-file transport
5. Generic event capture and replay
6. Coherent snapshots
7. Property and bounded diagnostic-command primitives
8. Broad release-time vendor/HAL inventory and typed adapters

This preserves discovery, diagnosis, observation, and app-level extension for
unanticipated subsystems. It cannot guarantee arbitrary future mutation of a
completely unknown, unexported framework internal without accepting an unsafe
raw reflection/transaction ABI.

## Capabilities intentionally deferred to the future platform broker

### Application, tasks, and packages

- Package/activity/deep-link launch
- Recent-task listing and control
- Force-stop
- Standard running-process queries
- Package inventory and component information
- Intent resolution
- Install/uninstall sessions
- Enable, disable, suspend, and clear data
- Rollbacks, shortcuts, and launcher APIs

### Permissions and user-visible state

- Runtime permission query/mutation
- AppOps query/mutation/watchers
- Roles and ordinary restrictions
- Notifications
- Media sessions
- Audio routing and volume
- Clipboard
- Accessibility service
- Input injection
- Settings and DeviceConfig
- Overlays and wallpaper
- Display modes and brightness
- Power controls
- Usage statistics
- Alarms and jobs

### Connectivity and hardware APIs already exported

- Connectivity
- Wi-Fi
- Bluetooth
- Telephony/subscriptions/telecom
- SMS/MMS
- VPN and network policy
- Tethering
- NFC
- Location
- Sensors
- Camera
- USB

### Standard diagnostics and lifecycle operations

- Logcat
- DropBox
- Statsd
- Perfetto
- Bugreports
- Public hardware properties
- Storage statistics
- UpdateEngine/OTA control
- RecoverySystem operations

These should be broker modules or plugins rather than permanent framework APIs.

## Ordinary application territory

The following are outside the ROM:

- Assistant and conversation UI
- Voice/personality layer
- Model inference and model downloads
- Conversation history and long-term memory
- RAG, embeddings, and vector databases
- Prompt management
- Workflow planning and execution
- Plugin marketplaces
- Cloud and cross-device synchronization
- OCR and vision models
- Speech recognition and TTS engines
- Calendar, contact, email, browser, and smart-home integrations
- Preference learning
- Analytics and telemetry

## Capabilities intentionally omitted

### Raw Binder transaction proxy

Do not expose a method equivalent to:

```text
transact(serviceName, transactionCode, rawParcel)
```

It is unversioned, unstable, difficult to test, and cannot correctly generalize
Binder handles, file descriptors, parcelable class loaders, nested identity, or
oneway semantics. Although the frozen ROM makes transaction numbers less likely
to move, turning raw parcels into a permanent extension ABI would still couple
future clients to private implementation details. Use typed providers, external
provider federation, service inspection, and generic dump streaming.

### Arbitrary `LocalServices` reflection

Do not expose Java class names, method names, reflection arguments, or private
object instances. Build stable adapters for the limited set of irreplaceable
framework operations.

### Raw internal framework objects

Do not parcel `ProcessRecord`, `ActivityRecord`, `Task`, `WindowState`,
`PackageStateInternal`, `JobStatus`, or private DPM records. Translate them into
versioned bridge DTOs.

### Duplicate public or hidden API wrappers

Do not freeze wrappers around APIs already usable by the future platform broker
unless the bridge adds framework-only data, a coherent snapshot, a missing
callback, or meaningful cross-version normalization.

### Credential and key extraction

Do not expose Keystore private keys, biometric templates, Gatekeeper secrets,
credential material, or raw authentication tokens. Metadata and operations are
sufficient.

### SELinux policy mutation

Do not add arbitrary policy injection, general runtime permissive controls, or
domain disabling. Inspection and access checks are the permanent requirement.

### Shell execution inside `system_server`

Never execute shell strings in `system_server`. If diagnostic program execution
is retained, place it in the protected daemon and use structured argv, bounded
resources, timeouts, cancellation, and descriptor streaming.

### Raw HAL transaction forwarding

Do not expose HIDL/AIDL transaction codes. Use typed device adapters with
version/hash negotiation.

### Low-value internal plumbing

Skip:

- Raw SurfaceFlinger transactions
- Codec/internal media transactions
- Internal cache counters with no diagnostic interpretation
- Deprecated services with supported replacements
- App prediction, AdServices, SDK sandbox, and text-selection internals without a
  concrete assistant or diagnostic use
- Generic object graphs from framework services

## Omission-risk summary

| ROM-critical capability | Risk | Permanent consequence |
|---|---:|---|
| Extensible provider protocol | Extreme | Bridge remains permanently limited to a small fixed interface |
| Machine-readable metadata and schema registry | Extreme | Future brokers and AIs still require hardcoded private knowledge |
| External provider federation | Extreme | New app/plugin capabilities cannot join one discoverable OS catalog |
| Framework internal event bus | Extreme | Lost process, crash, task, input, and boot provenance cannot be recovered |
| Protected diagnostic daemon and SELinux domain | Extreme | No later access to protected files, kernel evidence, or restricted native nodes |
| SELinux diagnostics | Extreme | Policy failures remain incomplete and difficult to attribute |
| Tombstone/native-crash access | Extreme | Native ROM crashes remain largely opaque |
| Binder inventory and dump broker | High | Unanticipated services cannot be inspected generically |
| Coherent framework snapshots | High | Debugging depends on unstable text dumps and race-prone polling |
| BootControl and previous-boot evidence | High | Slot and boot failures cannot be diagnosed or managed completely |
| System-property gateway | High | Important vendor/runtime state remains inaccessible or immutable |
| Restricted vendor HAL adapters | High | Device-specific hardware gaps become permanent |
| Device-policy internal gateway | High/Medium | Complete administration may depend permanently on owner provisioning |
| Global input/focus hooks | Medium/High | Input debugging and physical workflow learning remain incomplete |
| Structured window diagnostics | Medium | Accessibility cannot fully explain framework focus/window failures |
| Package install/verification/dexopt provenance | Medium/High | Future install failures and package-state transitions lack exact framework context |
| Wake-lock/suspend attribution events | Medium/High | Power regressions depend on polling and unstable textual dumps |
| Custom statsd atoms | Low/Medium | Bridge-specific telemetry is weaker, but standard diagnostics remain usable |

## Final implementation recommendation

Do not use the remaining ROM-development window to implement Wi-Fi toggles,
package installation, notification access, media controls, settings writes, or
hundreds of wrappers around APIs available to platform apps.

Implement the capabilities that later software cannot manufacture:

1. Extensible provider and operation protocol
2. Machine-readable provider, operation, event, and schema metadata
3. Federated registration for future platform-signed providers and plugins
4. Framework-only lifecycle and diagnostic event bus
5. Coherent internal state snapshots
6. Binder inventory and generic dump streaming
7. Generic artifact, stream, and structured-result infrastructure
8. Protected native diagnostic daemon with dedicated SELinux policy
9. SELinux audit and context inspection
10. Tombstone, pstore, and previous-boot artifact access
11. BootControl and verified-boot integration
12. Restricted system-property gateway
13. Restricted vendor HAL and vendor-service adapters
14. Global input/focus diagnostics
15. Package install/verification/dexopt provenance
16. Wake-lock/suspend attribution snapshots and events
17. DPM internal access if owner provisioning is not guaranteed

These preserve future capability. Everything replaceable should remain outside
the permanent framework image.

## Architectural Changes Introduced In This Revision

### Newly added permanent capabilities

- Machine-readable metadata for every provider, operation, event, error, and
  schema
- A permanent schema registry independent of any AI-specific format
- External provider federation for future platform-signed brokers and plugins
- Capability-catalog change events and provider liveness tracking
- Capability graph, dependency, replacement, verification, and relationship
  discovery
- Generic artifact, stream, result, service-inspection, event, dump, and
  snapshot primitives
- Package install, verification, rollback, freeze, and dexopt provenance events
- Power wake-lock and suspend-blocker attribution snapshots/events
- Event replay, backpressure, gap reporting, and cross-artifact correlation

### Bucket changes

- **PackageManager moved from B to A/B.** Normal package operations remain
  deferrable, but full-fidelity installer, verifier, dexopt, freeze, and internal
  failure provenance cannot be recreated from public broadcasts or polling.
- **PowerManager moved from B to A/B.** Normal device power operations remain
  deferrable, but precise wake-lock/suspend-blocker attribution and transition
  provenance require framework/native capture.
- **Binder ServiceManager changed from A/D to A at subsystem level.** Service
  inventory, health, descriptors, and dumps are mandatory. Raw Binder
  transaction forwarding remains Bucket D as a specific omitted operation.
- All other bucket decisions were re-evaluated through the irreversible
  checklist and retained.

### New protocol requirements

- `listEvents`, `getSchema`, `getCapabilityGraph`, catalog observation, and
  deterministic capability resolution
- Provider/operation/event versions and implementation hashes
- Android and ROM compatibility declarations
- Input, output, event, and error schemas
- Stable metadata for latency, execution mode, streaming, cancellation,
  timeouts, effects, implementation state, deprecation, replacements,
  dependencies, and feature flags
- External provider registration with Binder-death cleanup and generation
  tracking

### New extensibility requirements

- A future AI or broker must be able to explore the bridge without this
  repository or hardcoded provider lists.
- ROM, native-daemon, vendor, broker, and plugin capabilities must appear in one
  federated catalog while preserving implementation origin.
- Vendor implementations must map to logical capability IDs and stable bridge
  DTOs instead of leaking vendor classes and transaction codes.
- The bridge must provide enough generic diagnostics and transport primitives to
  investigate an unanticipated exported subsystem and enough plugin federation
  to add its high-level operations later.

### Newly identified irreversible capabilities

- Full package lifecycle provenance around verification, dexopt, rollback, and
  internal freeze/failure states
- Wake-lock/suspend-blocker attribution and transition provenance
- Catalog/schema availability for software that has never seen the bridge
- External provider federation as the permanent path for post-ROM plugins
- Structured event correlation across snapshots, dumps, tombstones, traces, and
  operations

### Changed recommendations

- The previous provider protocol is expanded from simple provider enumeration to
  a self-describing, federated capability graph.
- The event bus is upgraded from callback delivery to a versioned, replayable,
  correlated event data plane.
- Vendor support is changed from a collection of typed device wrappers to a
  common adapter contract with logical capability IDs, version/hash discovery,
  health, schemas, and structured diagnostics.
- Generic primitives are promoted to first-class permanent APIs so specialized
  providers share transport, error, artifact, dump, event, and snapshot
  semantics.
- Raw Binder relaying, arbitrary `LocalServices` reflection, and dynamic plugin
  loading into `system_server` were reconsidered for unknown future needs and
  remain intentionally rejected. Their apparent flexibility does not outweigh
  their unstable ABI and permanent system-process risk.
