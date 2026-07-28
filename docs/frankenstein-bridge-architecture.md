# Frankenstein Bridge — Architecture

**Status:** Final ROM-freeze implementation specification

**Target:** Bliss Android 16 / BP4A (API 36), ASUS I001D Waterlily

**Companion inventory:** `permanent-rom-capability-review.md`

This document defines what must be frozen into the last rebuildable ROM. Its
purpose is not to put a broad assistant API in `system_server`. It preserves
only capabilities that a future platform-signed broker or plugin cannot add:
framework-internal observation, protected diagnostics, restricted native and
vendor access, and the stable discovery/transport abstractions needed to expose
them.

The release rule is strict:

> Every final-build OS surface must be inventoried and classified. Every
> ROM-critical item must map to a permanent provider, owner, interface,
> lifecycle, SELinux rule, and verification test. Everything else remains
> outside the ROM.

## 1. Architectural Overview

The final bridge has four ownership tiers:

```
┌──────────────────────────────────────────────────────────────┐
│ Replaceable broker and plugins                               │
│ policy, consent, UI, orchestration, AI, ordinary Android APIs│
└────────────────────────────┬─────────────────────────────────┘
                             │ stable Binder control plane
┌────────────────────────────▼─────────────────────────────────┐
│ system_server: FrankensteinBridgeService                     │
│ caller enforcement · provider catalog · schema registry      │
│ handles/quotas · event transport · snapshot coordination     │
│                                                              │
│ Framework-only providers                                     │
│ AMS/ATM · PMS/ART/rollback · WMS/Input · Power · Boot        │
└───────────────┬──────────────────────────────┬───────────────┘
                │ LocalServices/private hooks  │ private Binder
                │                              │
┌───────────────▼──────────────────┐  ┌────────▼────────────────┐
│ Android framework services      │  │ frankenstein_diag       │
│ and source capture points       │  │ init-managed native     │
│                                 │  │ daemon, separate SELinux│
└─────────────────────────────────┘  │ domain, protected reads  │
                                     │ and streamed diagnostics │
                                     └────────┬────────────────┘
                                              │ typed HAL/service
                                              │ adapters only
                                     ┌────────▼────────────────┐
                                     │ Vendor/HAL boundary      │
                                     │ existing stable HALs or  │
                                     │ VINTF-stable adapter*    │
                                     └─────────────────────────┘
```

`*` A vendor-side adapter is added only where the final device audit proves that
the platform daemon cannot safely call a useful restricted vendor interface.
Neither process exposes raw transaction forwarding.

The root service is a control plane. Large files, service dumps, pstore,
tombstones, audit evidence, and blocking native work never execute on a
`system_server` Binder thread.

## 2. Where The Bridge Lives In Android Source

```
frameworks/base/
├── core/java/com/android/internal/os/frankenstein/
│   └── ...                                     # Frozen structured AIDL control types
├── core/res/AndroidManifest.xml                 # Two signature permission declarations
├── services/core/java/com/android/server/frankenstein/
│   ├── FrankensteinBridgeService.java          # Root SystemService
│   ├── FrankensteinBridgeInternal.java         # LocalService contract
│   ├── CallerAuthorizer.java                    # Binder identity + permission checks
│   ├── ProviderRegistry.java                    # ROM/external provider generations
│   ├── SchemaRegistry.java                      # Immutable ROM schemas + bounded external schemas
│   ├── OperationManager.java                    # Async handles, quotas, cancel, timeout
│   ├── BridgeEventHub.java                      # Ordered bounded event journal
│   ├── SnapshotCoordinator.java                 # Multi-provider snapshot barrier
│   ├── DiagnosticDaemonConnector.java           # Non-blocking daemon lifecycle
│   └── providers/                               # Framework-only typed adapters
│       ├── ActivityProcessProvider.java
│       ├── PackageProvenanceProvider.java
│       ├── WindowInputProvider.java
│       ├── PowerProvenanceProvider.java
│       ├── ServiceInspectionProvider.java
│       └── DevicePolicyProvider.java            # Conditional; see Section 10
├── services/java/com/android/server/
│   └── SystemServer.java                        # Early start + readiness attachment
└── Android.bp                                   # Stable AIDL module and service deps

system/core/frankenstein_diag/
├── Android.bp
├── frankenstein_diag.rc
├── main.cpp
└── ...                                          # Platform diagnostic providers

system/sepolicy/
├── public/service.te                            # VINTF adapter type only when vendor-visible
└── private/
    ├── service.te                               # Root and private daemon service types
    ├── service_contexts
    ├── frankenstein_diag.te
    └── file_contexts

device/asus/sm8150-common/
├── sepolicy/                                    # Device/HAL client rules
├── vintf/                                       # Adapter declaration, only if required
└── frankenstein/inventory/                      # Final generated classification inputs
```

The current prototype package
`com.android.internal.os.frankenstein` is retained to avoid an unnecessary
rename, but package placement does not make it a stable ABI. Stability comes
from the frozen `aidl_interface` module and its archived API/hash.

## 3. Binder / AIDL Boundary

### Client → Bridge Service

- `FrankensteinBridgeService.onStart()` publishes the stable root Binder under
  the permanent service name `frankenstein` using
  `publishBinderService(..., allowIsolated=false)`.
- Clients resolve it through `ServiceManager`; this is not an APK component and
  is never reached with `Context.bindService()`.
- The root Binder exposes discovery, subscriptions, snapshots, artifact
  requests, external-provider registration, and asynchronous operation handles.
- Every entry point captures and verifies the kernel Binder UID/PID before any
  call to `Binder.clearCallingIdentity()`.
- The old eleven-method `Bundle` prototype is pre-release scaffolding, not the
  permanent ABI. It must be replaced before the final build. If temporarily
  retained for bring-up, publish it under `frankenstein_legacy` and remove it
  from production.

### Bridge → System Services

- Framework-only providers use explicit `LocalServices` contracts or small
  source hooks owned by the subsystem. They never use reflection over
  `LocalServices` or return internal objects.
- Existing exported Binder/manager APIs are not wrapped unless the provider
  adds otherwise unavailable state, correlation, or callbacks.
- Provider code copies stable scalar state while holding a subsystem lock, then
  releases that lock before schema encoding, Binder callbacks, logging, or I/O.
- The bridge publishes a narrow `FrankensteinBridgeInternal` LocalService for
  source hooks to enqueue normalized events; it does not expose broker policy to
  framework services.

### Bridge → native and vendor services

- `DiagnosticDaemonConnector` connects asynchronously to the init-managed
  `frankenstein_diag` service. Absence or death marks only daemon-backed
  providers unavailable; it never blocks boot or crashes `system_server`.
- The daemon returns read-only `ParcelFileDescriptor` streams or bounded typed
  results. It does not accept shell command strings or arbitrary raw paths.
- Standard AIDL/HIDL HALs are called through their published versioned
  interfaces. Proprietary interfaces are translated into stable bridge schemas.
- If an adapter crosses the system/vendor partition boundary, its AIDL is
  `stability: "vintf"`, frozen, and declared in both the device VINTF manifest
  and framework compatibility matrix. The root app-facing AIDL does not claim
  VINTF stability because it is a system-side contract.

## 4. Caller Authentication

The service never trusts a UID, PID, package, user, signer, confirmation state,
or attribution tag supplied in a request. A verified immutable `CallerContext`
is created at each Binder entry:

```text
Binder.getCallingUid()/getCallingPid()
    -> UserHandle.getUserId(uid)
    -> PackageManagerInternal.getPackagesForUid(uid)
    -> custom signature permission
    -> package signer lineage and package/UID membership
    -> SELinux service-manager find/call gate
    -> immutable CallerContext
    -> optional clearCallingIdentity()
```

### How Authentication Works

1. The ROM declares two signature permissions before release:
   `android.permission.ACCESS_FRANKENSTEIN_BRIDGE` and
   `android.permission.REGISTER_FRANKENSTEIN_PROVIDER`.
   Both use `protectionLevel="signature"`; neither is granted through a
   privapp-permissions allowlist.
2. Normal bridge operations require the access permission. External-provider
   registration entry points require the registration permission instead; that
   permission does not authorize ROM-provider execution or protected data.
3. Package resolution treats a UID as a set of packages; it never selects one
   arbitrary package. The claimed package, when a method needs attribution, must
   belong to that UID and have an accepted signing lineage.
4. ROM-provider execution and protected data access additionally require the
   calling UID to contain the permanent broker package
   `com.frankenbridge.assistant`. Future broker releases update that package
   under an accepted signing lineage. Provider registration methods use the
   registration permission and caller-derived namespace instead, so new
   platform-signed plugins do not require a ROM package allowlist and do not
   gain access to ROM-provider operations.
5. The broker has a dedicated app UID and must not declare a shared user ID.
   ROM-provider authorization requires the UID's installed package set to be
   exactly the broker package, preventing another package in a shared UID from
   inheriting bridge authority.
6. Root/shell access is not implicit. `shell` receives only explicitly
   documented `dump()` diagnostics on debuggable builds; UID 0 is not a general
   protocol bypass.
7. Isolated UIDs and SDK sandbox UIDs are rejected.
8. Every provider operation, snapshot section, and subscription scope carries a
   concrete `targetUserId`; `USER_CURRENT`, `USER_ALL`, and omitted-user
   semantics are forbidden for user-scoped work. Global discovery methods are
   explicitly user-neutral. Cross-user access requires
   `INTERACT_ACROSS_USERS_FULL` and a provider that declares cross-user
   semantics.
9. Package replacement, signer change, user stop, and user removal invalidate
   subscriptions, operation handles, and external-provider registrations owned
   by the affected UID/user.

`Binder.clearCallingIdentity()` is allowed only after `CallerContext` has been
created and authorization has succeeded. The saved context, not the now-cleared
Binder identity, is used for audit and attribution. Code must restore identity
in `finally`, must not invoke an outbound callback while identity is cleared,
and must not hold a subsystem or registry lock across the cleared-identity
region.

## 5. Permission Model

The permanent ROM enforces structural security, not replaceable product policy.
Consent screens, risk tiers, assistant policy, workflow approval, and plugin
trust decisions belong to the future broker.

### Permission Source Table

| Enforcement | ROM owner | Permanent rule |
|---|---|---|
| Service discovery/call | SELinux | Only approved app domains may find/call the root service |
| ROM-provider access | Framework permission + package owner | Require `ACCESS_FRANKENSTEIN_BRIDGE` and broker-package membership |
| Provider registration | Framework permission | Require `REGISTER_FRANKENSTEIN_PROVIDER`; does not imply ROM-provider access |
| Caller identity | `CallerAuthorizer` | Kernel UID/PID, package membership, signer lineage |
| User/profile isolation | Root + provider | Explicit user, cross-user permission, lifecycle invalidation |
| Operation safety | Owning provider | Type/range checks, immutable allowlists, preconditions |
| Resource bounds | Root | Per-UID handles, subscriptions, bytes, queue depth, and rate |
| Broker consent/policy | Replaceable broker | Not implemented in `system_server` |

Providers may perform additional Android permission or user-restriction checks,
but no operation may accept caller-provided evidence that consent occurred.
ROM-only mutations are limited to an enumerated typed allowlist. In particular:

- property reads/list/observation may be broad but redact security-sensitive
  namespaces; property writes are typed and allowlisted;
- artifact access uses named roots and opaque artifact IDs, never arbitrary
  paths;
- diagnostic execution, if any typed adapter needs it, uses a compiled command
  ID and fixed executable, never a client-selected executable or shell string;
- Binder and HAL inspection never includes raw transaction forwarding;
- provider metadata cannot grant privilege and is not an authorization source.

## 6. Audit Logging

The ROM keeps a minimal security audit distinct from the event data plane. It
records authentication failures, provider registration/removal, operation
start/completion/cancel/timeout, daemon/HAL health transitions, overflow, and
access to protected artifacts.

### Audit Sinks

The mandatory sink is a bounded in-memory ring exposed through `dumpsys
frankenstein` plus rate-limited security log entries. A custom statsd atom is
optional and may be deferred because the broker can consume the structured
event stream. DropBox is used only for a bridge or daemon crash summary, not as
a duplicate log of every operation.

### Redaction

Audit records contain stable operation/provider IDs, caller UID/user, result
code, duration, byte count, and correlation ID. They do not contain payloads,
raw paths, file contents, intent extras, notification/screen text, property
values, credentials, tokens, or cryptographic material. Package names are
included only when required to diagnose caller ownership.

## 7. Denial / Error Model

Expected failures are returned as structured results. Programming errors and
failed root authentication may throw `ServiceSpecificException` or
`SecurityException`; clients never parse exception text.

| Stable code family | Meaning |
|---|---|
| `INVALID_ARGUMENT` | Type, range, schema, token, or target validation failed |
| `PERMISSION_DENIED` | Signature/framework permission or caller ownership failed |
| `USER_RESTRICTED` | User/profile/admin state forbids the operation |
| `NOT_READY` | Required boot phase or user unlock has not completed |
| `UNAVAILABLE` | Provider, service, daemon, HAL, or feature is absent |
| `VERSION_UNSUPPORTED` | Interface, provider, operation, schema, or encoding mismatch |
| `RESOURCE_EXHAUSTED` | Queue, handle, byte, stream, or rate quota exceeded |
| `TIMEOUT` | Bounded operation deadline expired |
| `CANCELLED` | Caller or lifecycle cancellation completed |
| `PARTIAL` | Some independently identified sections failed |
| `STALE_HANDLE` | Handle generation, owner, user, or expiry no longer matches |
| `INTERNAL` | Redacted implementation failure with correlation ID |

Every result includes provider/operation versions, correlation ID, start/end
elapsed times, warnings, `partial`, and retry guidance. Partial results list
each failed section and its code; they are never reported as full success.

### Timeouts

Timeouts are operation metadata, not risk-tier policy. Only bounded metadata
reads may run synchronously, with a 100 ms service-side budget. Snapshots,
dumps, protected artifacts, HAL calls, and any work that may block are
asynchronous, cancellable operations. A timeout stops delivery, closes owned
descriptors, and marks the operation terminal; it must not leave an unbounded
worker queued.

## 8. ROM Freeze Boundary

### Mandatory before the final ROM

| Permanent element | Why it cannot be added later |
|---|---|
| Frozen root AIDL, schemas, streams, events, handles | Future APKs cannot change a service ABI in the immutable framework |
| External-provider federation | Gives future broker/plugins a discoverable extension path without loading code into `system_server` |
| Framework event capture hooks | Lost private lifecycle provenance cannot be reconstructed by polling |
| Framework diagnostic providers | `LocalServices` and private state are process-local |
| Protected diagnostic daemon | Init service, executable labels, and protected file access require the ROM/SELinux build |
| Binder/service inventory and dump streaming | App domains cannot find or call every service |
| SELinux, tombstone, pstore, and prior-boot access | Platform signing does not bypass MAC or protected files |
| Boot/slot/verified-boot adapter | Boot HAL access and prior-boot evidence are restricted |
| Typed property gateway | Property-service contexts restrict future app writes |
| Required vendor/HAL adapters | Service discovery and call permissions must exist in SELinux/VINTF now |
| Package/power/window/input provenance | Exact internal transitions disappear if not captured at source |
| DPM solution or proven owner provisioning | Device-owner authority cannot be assumed after setup |
| Final-build inventory artifact | Proves every actual service/HAL/daemon/context was classified |

### Explicitly deferred to broker or plugins

App/task control, package mutations, runtime permissions, AppOps, roles,
notifications, accessibility automation, input injection, settings,
DeviceConfig, overlays, UI, Wi-Fi/Bluetooth/NFC/telephony/VPN controls, media,
voice, public sensors/camera/location, logcat, DropBox, statsd, Perfetto,
bugreports, OTA submission, workflow policy, consent, AI, and analytics use
existing app-accessible APIs. The root does not freeze duplicate wrappers.

### Explicitly omitted

Raw Binder/HIDL/AIDL transactions, arbitrary `LocalServices` reflection,
framework-object parceling, arbitrary shell/process execution, arbitrary path
reads, unrestricted property writes, SELinux mutation, credential/key/template
extraction, packet capture, and loading plugin code into `system_server` are not
extension mechanisms.

## 9. Risks and Guardrails

### Privacy Risks
| Risk | Guardrail |
|---|---|
| Event stream becomes behavior surveillance | Capture only ROM-critical lifecycle/diagnostic fields; no screen, notification, key, touch-coordinate, or intent-extra content |
| Cross-profile disclosure | Concrete target user, per-user handles, cross-user permission, invalidation on user stop |
| Protected artifact leaks | Named artifact classes, metadata-first access, signer permission, read-only descriptors, audit |
| Diagnostics retain secrets | Redaction at source; security/credential stores are hard-denied roots |

### Security Risks
| Risk | Guardrail |
|---|---|
| Binder identity laundering | Capture/authorize before clearing identity; never accept caller identity fields |
| External provider becomes confused deputy | Root catalogs and returns its Binder; it does not invoke it with `system_server` privilege |
| Generic diagnostics become root shell | No shell strings, raw paths, raw transactions, or client-selected executables |
| Vendor ABI leaks into clients | Adapter normalizes to bridge schemas and preserves raw vendor codes only as data |
| Permanent mutation primitive is too broad | Typed, enumerated, range-checked property/HAL/DPM operations only |

### Abuse Prevention
| Risk | Guardrail |
|---|---|
| Binder/FD exhaustion | Per-UID limits for operations, subscriptions, callbacks, descriptors, and bytes |
| Slow or dead callback | `oneway` batched delivery, bounded queue, death recipient, gap event, removal |
| Unbounded metadata registration | Descriptor/schema size and count quotas; stable namespace ownership |
| Audit or event flood | Separate bounded rings, coalescing, drop counters, explicit overflow |

### Boot Stability
| Risk | Guardrail |
|---|---|
| Optional provider delays boot | Root publishes without waiting; providers report `NOT_READY`/`UNAVAILABLE` |
| Daemon crash causes boot loop | Daemon is non-critical, independently restartable; connector degrades only its providers |
| Framework lock inversion | Copy under owner lock, enqueue after unlock; never callback or I/O under framework lock |
| Binder pool starvation | Long work uses named bounded executors; no synchronous dump/file/HAL calls |
| Memory growth | Fixed event/audit rings, capped schemas, paged results, FD streaming |
| SELinux bring-up masks defects | Permissive is allowed only for the daemon domain on eng/userdebug; final user build is enforcing |

### SELinux

`FrankensteinBridgeService` executes in the existing `system_server` domain. It
must not be assigned a fictitious `frankenstein_bridge` process domain.

The final policy has distinct objects:

- `frankenstein` → `frankenstein_service`: service-manager name/type for the
  public root Binder;
- `com.android.internal.os.frankenstein.diag.IFrankensteinDiagnostic/default`
  → `frankenstein_diag_service`: private daemon service name/type findable only by
  `system_server`;
- `frankenstein_diag` and `frankenstein_diag_exec`: the actual native daemon
  domain and executable label;
- specific artifact/file/property/HAL types, with no `default_service`,
  `unlabeled`, or broad `sysfs`/`proc` allows;
- `service_contexts`, `file_contexts`, any required `genfs_contexts`, and
  `property_contexts` entries;
- `hal_client_domain(frankenstein_diag, <hal>)` only for audited standard HALs;
- a separate VINTF-stable vendor adapter domain/interface where proprietary
  vendor policy cannot safely grant the platform daemon direct access.

Never grant `appdomain` as a class. Grant service lookup to the narrow domain(s)
that can hold the signature permission, and retain Java permission checks as an
independent gate. `neverallow` compliance, `sepolicy_tests`, user-build
enforcing boot, and negative access tests are release blockers.

## 10. Exhaustive OS Inventory And Freeze Test

### Completeness mechanism

A handwritten capability list is not an exhaustive inventory of a particular
ROM. The final build must generate, hash, archive, and ship a classification
manifest from these authoritative inputs:

1. All `SystemServer` service starts and direct `ServiceManager.addService()`
   calls, plus runtime `service list`/`cmd -l`.
2. All `LocalServices.addService()` and `LocalManagerRegistry.addManager()`
   producers and consumers.
3. All compiled init `.rc` service declarations from system, system_ext,
   product, vendor, and odm.
4. All AIDL service-manager, HwBinder, and HIDL instances from the assembled
   VINTF manifests/matrices and a runtime service/HAL listing.
5. `service_contexts`, `hwservice_contexts`, `vndservice_contexts`,
   `file_contexts`, `genfs_contexts`, `property_contexts`, `seapp_contexts`,
   and the future broker domain's actual find/call/read/write access.
6. Protected diagnostic roots, proc/sysfs nodes, device nodes, sockets,
   properties, boot/recovery artifacts, and vendor control files referenced by
   source, init, or policy.
7. Framework features, sysconfig/privapp grants, Mainline/APEX services, and
   device overlays that add or remove a service.

The shipped artifact is
`/system/etc/frankenstein/inventory/os-surface-inventory.cbor`, with a
human-readable copy archived beside the build. Each record contains the source,
runtime name, owner process/domain, interface descriptor/version/hash,
availability condition, future-app reachability result, bucket, rationale,
provider mapping, and evidence hash. Boot-time discovery reports additions,
removals, and mismatches as inventory events.

Release is blocked by an unclassified record or by a Bucket A record without a
provider and test. This is the permanent closure rule that makes the following
family-level inventory exhaustive even when the exact device service list
changes late in the build.

### Classification

- **A — freeze in ROM:** a future app cannot reproduce the useful operation or
  evidence because it needs source capture, `LocalServices`, init, SELinux,
  protected storage, or restricted HAL/vendor access.
- **B — future broker:** existing public, system, hidden, or Binder APIs are
  sufficient for a platform-signed app.
- **C — future ordinary app/plugin:** public APIs and user-granted roles or
  services are sufficient.
- **D — omit:** unsafe generic primitive, secret-bearing interface, obsolete
  plumbing, or no credible assistant/diagnostic value.

### OS capability family matrix

| OS family and included surfaces | Bucket | Slice frozen now | Deferred/omitted remainder |
|---|---:|---|---|
| Boot runtime: init, boot phases, bootstat, zygote, ART/linker, RescueParty, watchdog, verified boot | A/B | Boot-phase events, previous-boot evidence, build/inventory identity | Normal reboot and app-visible build data → B; runtime injection → D |
| Binder/HwBinder/service managers and `system_server` service health | A | Inventory, descriptor/version/hash, liveness/death, bounded dump streaming | Existing accessible managers → B; raw transact → D |
| AMS: process/UID state, OOM/LMKD context, crashes, ANRs, memory pressure | A/B | Source lifecycle/crash/ANR/pressure events and stable snapshots | Queries and force-stop → B |
| ATM: activities, tasks, recents, transitions | A/B | Only transition provenance needed for cross-provider correlation | Task query/control/launch → B |
| WMS, DisplayManager, SurfaceFlinger-facing framework state, keyguard/focus | A/B | Structured window/focus/transition/input-target diagnostics | Brightness, display modes, screenshots, normal task/window control → B/C |
| InputManager/InputFlinger, devices, dispatch | A/B/D | Device changes and metadata-only dispatch latency/failure/focus correlation | Injection → B; raw keys, touch coordinates, input-content capture, generic hotkeys → D |
| PMS, PackageInstaller, domain verification, app integrity, rollback, ART/artd/dexopt, hibernation | A/B | Install/verify/scan/freeze/rollback/dexopt provenance with artifact correlation | Inventory, install/uninstall, enable/suspend/clear, shortcuts → B |
| PermissionManager, AppOps, roles, URI grants, restrictions | B | None | Platform broker APIs → B; policy bypass → D |
| Users, profiles, cross-profile, DPM, lock settings, trust | A/B | DPM gateway only if owner provisioning gate fails; diagnostic state only otherwise | User/profile and owner-authorized operations → B; credential bypass → D |
| Power, suspend, wake locks, battery, health, thermal, idle, lights, vibrator, device state | A/B | Wake-lock/suspend attribution and transition provenance; restricted HAL diagnostics | Normal power/battery/light/vibration controls → B/C |
| AlarmManager, JobScheduler, TARE, app standby and background scheduling | B | None; generic service dumps already cover unanticipated diagnostics | Existing Binder APIs, statsd, dumps and broker correlation → B |
| GPU, graphics allocation/composition, memtrack, graphics/binder/looper/CPU statistics, pinner | A/B | Restricted service/HAL inventory and dump access only | Existing graphics/memory/profiling APIs → B; raw graphics transactions → D |
| StorageManager/vold, installd, storage stats, quotas, blobs, incremental/dataloader, persistent data | A/B | Named protected artifacts and daemon-mediated diagnostics | Volume/quota/statistics and app storage operations → B |
| Filesystems, `/proc`, `/sys`, pstore, ANR traces, tombstones, recovery/update artifacts | A/D | Read-only named diagnostic namespaces, opaque IDs, bounded streams | Arbitrary paths, app data, credential stores, unrestricted writes → D |
| SELinux/audit, contexts, policy identity, access checks | A/D | Policy digest/version, context resolution, AVC stream when available, previous-boot evidence | Policy mutation, permissive controls, arbitrary relabel → D |
| logd, DropBox, statsd, Perfetto, incidentd, dumpstate, bugreport | A/B | Only protected kernel/audit/prior-boot inputs and generic restricted dump access | Existing log/stat/trace/report APIs → B |
| Connectivity, netd, DNS resolver, policy/stats/score/watchlist, Ethernet, VPN, IPsec, tethering | A/B | Restricted service inventory/dump only; typed vendor diagnostics if audit proves inaccessible | Network control/stats/VPN/tethering → B; packet interception → D |
| Wi-Fi, Wi-Fi Aware/P2P/scanner, Bluetooth, NFC, UWB, Thread/Lowpan, Nearby, companion devices | A/B/C | Only inaccessible device-specific HAL diagnostics/adapters | Framework controls and public device APIs → B/C |
| Telephony, subscriptions, Telecom, IMS, RIL, SMS/MMS, satellite, secure element | A/B/D | Non-secret restricted modem/IMS health and dump adapters only when justified by device audit | Normal calls/messages/subscriptions → B; SIM/SE secrets and raw OEM hook → D |
| Location, GNSS, geofence, time/time-zone/altitude/country/emergency | B/C | None unless final vendor audit finds an unexported diagnostic requirement | Existing location/time APIs → B/C |
| Sensors, sensor privacy, Context Hub/CHRE, ambient context, attention, camera | A/B/C | Restricted HAL health/dump and calibration adapter only if app-inaccessible | Sensor/camera/context operations → C/B |
| Audio, media sessions/router/projection, codecs, MIDI, radio, HDMI/TV, music recognition | A/B/C | Restricted HAL health/dump only if app-inaccessible | Playback, routing, capture, sessions, projection → B/C; DRM extraction → D |
| USB host/gadget, serial, consumer IR, dock and other peripheral managers | A/B/C | Restricted device-node/HAL diagnostic adapter only if proved useful and inaccessible | Existing framework/public peripheral APIs → B/C; arbitrary ioctl forwarding → D |
| Accessibility, IME, autofill, content capture/suggestions, translation, clipboard, print | B/C | None | Platform broker or user-granted service → B/C |
| Notifications, status bar, launcher, shortcuts, widgets, wallpaper, overlays/themes, UI mode, locale/fonts | B/C | None | Platform broker or ordinary app → B/C |
| Accounts, Credential Manager, biometrics, keystore/keychain, Gatekeeper, attestation/provisioning | B/D | Non-secret service/HAL health and attestation status only through generic diagnostics | Supported operations/metadata → B; key, token, template, password extraction → D |
| File/app integrity, binary transparency, remote provisioning, security state, intrusion/advanced/sensitive-content protection | B/D | Generic service health/dump only; no bypass adapter | Existing status/attestation APIs → B; security-policy or secret bypass → D |
| Backup, recovery, OTA/update_engine, dynamic system, OEM lock, BootControl | A/B/D | Boot slot/success/bootable/verified-boot/rollback state and protected prior-boot evidence | OTA/recovery submission → B; unlock/security bypass → D |
| Assistant/voice interaction, speech/TTS, hotword/SoundTrigger, Search UI, Smartspace, prediction | B/C | None | Roles, services, models, and UI → B/C |
| AppSearch, Health Connect, Safety Center, App Functions, SDK sandbox, AdServices, ODP, on-device intelligence | B/C/D | None absent a concrete inaccessible diagnostic found by the final inventory | Exported APIs → B/C; private object graphs with no concrete use → D |
| Resources/configuration, settings, DeviceConfig, feature flags, overlays, WebView update | B | None | Platform broker APIs → B |
| Bliss/Lineage/OEM framework extensions: BlissSystemEx, Freeform, AppLock, GameSpace, Theme, TaskContinuity, UniversalClipboard, ProdX authority | A/B/D | Typed adapter only for a useful `LocalServices`-only state/callback identified in the final source inventory | Exported Binder APIs → B; generic OEM reflection or authority bypass → D |
| ADB, shell, test harness, coverage, instrumentation, trade-in and engineering services | B/D | Inventory/dump only on debuggable builds | Existing debug APIs → B; production privilege relay → D |
| Virtual devices, media/task continuity, cross-device and wearable-only services | B/C | None on this phone target unless actually present and inaccessible | Existing APIs/plugins → B/C |
| Kernel interfaces, eBPF, device nodes, native sockets, subsystem ramdumps | A/D | Named read-only diagnostic adapters proven useful by the device audit | Generic eBPF loading, packet capture, arbitrary ioctl/socket forwarding → D |
| Vendor/OEM HALs, Binder services, daemons, sysfs, properties, sockets | A/B/D | Typed adapters for useful final-build surfaces unreachable from the broker domain | Exported services → B; secret/security/raw transaction surfaces → D |

For the current Waterlily evidence, the vendor audit must explicitly cover the
ASUS motor/rotating-camera, glove-mode and ZenMotion services; display color and
post-processing; Qualcomm performance/IOP, Bluetooth SAR/config, Wi-Fi learner,
sensor calibration, audio extensions, FM, charging/health/thermal, modem/IMS,
NFC, and Wi-Fi Display surfaces. Gatekeeper, Keymaster/QSEE, trusted UI, DRM,
secure-element, and cryptfs interfaces are inventory/health-only and may not
expose secret-bearing operations. The final assembled VINTF and init output,
not this illustrative list, is authoritative.

## 11. Permanent Provider Set

| Stable provider ID | Owner | Permanent responsibility | Required dependency/failure behavior |
|---|---|---|---|
| `core.catalog` | root service | Bridge info, provider/operation/event/schema discovery, capability graph, external registration | Always available after `onStart`; immutable ROM descriptors |
| `core.events` | root service | Subscription, sequence, replay, batching, gaps, catalog/health events | Current-boot bounded journal; generation changes after restart |
| `core.snapshots` | root service | Correlated multi-provider snapshot barrier and section status | Never claims global atomicity; partial on provider timeout |
| `framework.activity` | AMS/ATM adapters | Process/UID/crash/ANR/memory and required transition provenance | `NOT_READY` until source hooks attached; no raw records |
| `framework.package_provenance` | PMS/Installer/ART/Rollback adapters | Install, verify, scan, freeze, rollback, dexopt provenance | Events captured at state transition; payload normalized before enqueue |
| `framework.window_input` | WMS/Input adapters | Window/focus/input-target snapshots and metadata-only dispatch failures | No UI content, key codes, touch coordinates, or injection |
| `framework.power_provenance` | Power/Suspend/Thermal adapters | Wake-lock/suspend/wake/doze/thermal attribution and snapshots | No ordinary power-control wrappers |
| `framework.device_policy` | DPM adapter, conditional | Typed internal operations only if owner provisioning cannot be guaranteed | Must be resolved by release gate; never exposes private admin records |
| `diag.services` | root + daemon | Binder/native/HAL inventory, health, descriptor and bounded dump stream | Denylisted sensitive services; no transaction proxy |
| `diag.artifacts` | native daemon | Named tombstone, pstore, ANR, recovery, kernel/audit and build artifacts | Read-only `openat2(RESOLVE_BENEATH\|RESOLVE_NO_SYMLINKS)` or equivalent per-component checks, canonical root confinement, size/time caps |
| `diag.selinux` | native daemon | Enforcing state, policy identity, context lookup, access check, AVC evidence | Availability reports kernel/log source; no policy mutation |
| `diag.boot` | daemon/HAL adapter | Slot, bootable/success, verified boot, boot reason and prior-boot correlation | Read-only by default; any mutation separately typed and justified |
| `diag.properties` | root/daemon | Property metadata/read/list/observe; allowlisted typed writes | Security namespaces redacted; no arbitrary setter |
| `vendor.*` | native or VINTF adapter | Only device-audited inaccessible hardware/service diagnostics or typed controls | Exact interface version/hash, liveness, build constraints, no raw transact |

Provider IDs, operation IDs, event IDs, and schema IDs are lowercase
dot-separated ASCII names owned permanently by a provider namespace. IDs are
never reused after removal. A provider may be unavailable on a build, but its
descriptor and unavailable reason remain discoverable.

### Framework integration points

| Provider | Android 16 owner/capture point | Integration rule |
|---|---|---|
| `framework.activity` | `ActivityManagerService`, `ProcessList`, `UidObserverController`, `AppErrors`, `AnrHelper`, and LMKD/memory-pressure callbacks | Add one package-private sink call at the authoritative transition when no existing `ActivityManagerInternal` callback carries equivalent data |
| `framework.package_provenance` | `PackageInstallerSession`, `InstallPackageHelper`, PMS scan/reconcile/freeze state, `RollbackManagerService`, and ART service/dexopt completion callbacks | Allocate one correlation ID at session/boot-scan origin and carry it through verification, commit, rollback, and dexopt; never infer phases from broadcasts |
| `framework.window_input` | `ActivityTaskManagerService`/root window container, `WindowManagerService`, `InputManagerCallback`, and input dispatch timeout/ANR paths | Capture focus/target/transition and latency state under the WMS global lock, copy to DTO scalars, enqueue only after unlocking |
| `framework.power_provenance` | `PowerManagerService` wake-lock notification points and suspend-blocker transitions, `DeviceIdleController`, `ThermalManagerService`, and system-suspend callbacks | Record owner/work-source attribution and the before/after state at the transition; never call bridge code while the power lock is held |
| `diag.services` | `ServiceManager`/HwBinder/VINTF enumeration and each target's published dump method | Resolve descriptor and liveness without a raw transaction API; execute dump asynchronously to a pipe with timeout/truncation |
| `diag.boot` | `SystemService.onBootPhase`, bootstat/boot-reason artifacts, BootControl and verified-boot interfaces | Events earlier than bridge startup are represented as boot artifacts with explicit source/freshness, not fabricated callbacks |

Hooks are owned and reviewed with the source subsystem, not concentrated in a
generic reflection layer. Each hook accepts an immutable bridge-neutral record
or the narrow `FrankensteinBridgeInternal` sink; it does not depend on broker
classes, CBOR, Binder callbacks, or provider registry locks.

### Event semantics

Each event envelope contains provider/event/schema versions, boot/provider
generation, global and provider sequence, wall/elapsed/uptime clocks,
correlation/parent/session IDs, explicit user/UID/PID/package/component when
known, payload schema, coalesced count, and lost-before count.

- Global order is the order accepted by the single event sequencer, not the
  causal order of Android subsystems.
- Each provider documents its stronger ordering rule.
- Replay is current-boot and best-effort from the bounded journal. A reboot,
  root-service restart, or eviction produces a generation/gap indication.
- Initial-state-plus-delta subscriptions capture the initial snapshot before
  enabling delivery and include a barrier sequence so the client can remove
  duplicates without missing a transition.
- Source hooks enqueue immutable normalized data without blocking. Encoding and
  delivery occur after source locks are released.

### Snapshot semantics

“Correlated” does not mean globally atomic. A snapshot has a start/end barrier,
one correlation ID, the event sequence at both barriers, and for every section:
capture start/end, provider generation, schema version, freshness, completeness,
and error. No implementation may acquire locks from multiple framework
services. Callers can therefore distinguish a useful bounded observation from
an impossible atomic system image.

## 12. Permanent AIDL, DTO, And Schema Rules

### Interface modules

1. `frankenstein-bridge-aidl`: structured Stable AIDL, Java backend, stability
   limited to the system compilation context, frozen V1 API/hash.
2. `frankenstein-diag-aidl`: structured Stable AIDL, Java and NDK backends,
   frozen V1 API/hash; private to `system_server` and the platform daemon.
3. `frankenstein-vendor-adapter-aidl`: created only if required, Java/NDK
   backends, `stability: "vintf"`, frozen and represented in VINTF.

The app-facing API must be frozen with the Soong `*-freeze-api` target and the
release build must pass `AIDL_FROZEN_REL=true`. Frozen AIDL sources, hashes,
generated client stubs, schema catalog, and a small compatibility test APK are
archived with the ROM.

### Root control-plane shape

The V1 root supports these functions, expressed as structured AIDL rather than
the exact Java spelling shown here:

```text
getBridgeInfo()
listProviders(filter, pageToken)
describeProvider(providerId, acceptedVersions)
listOperations(providerId, filter, pageToken)
describeOperation(providerId, operationId, acceptedVersions)
listEvents(providerId, filter, pageToken)
describeEvent(providerId, eventId, acceptedVersions)
getSchema(schemaId, acceptedVersions)
getCapabilityGraph(filter, pageToken)
observeCatalog(callback)
startOperation(request) -> IBridgeOperation
subscribeEvents(subscription, callback) -> IBridgeSubscription
registerExternalProvider(descriptor, providerBinder) -> registration
updateExternalProvider(registration, descriptor)
unregisterExternalProvider(registration)
getExternalProvider(providerId, acceptedVersions) -> IExternalCapabilityProvider
```

`IBridgeOperation` exposes status, cancellation, result metadata, and stream
opening. `IBridgeSubscription` exposes acknowledgement, current sequence, and
cancellation. Methods added in a later AIDL version are appended; clients
query interface version/hash and handle `UNKNOWN_TRANSACTION`.
`startOperation()` executes ROM-owned providers only. For an external provider,
the broker obtains its typed `IExternalCapabilityProvider` Binder and calls it
directly; the root never becomes its privileged invocation proxy.

### DTO and payload evolution

- No `Bundle`, `PersistableBundle`, framework-internal parcelable, Java
  serialization, JSON string, raw `Parcel`, or vendor parcelable is a permanent
  contract.
- Control-plane DTOs are structured AIDL with explicit defaults. Existing
  fields are never reordered, removed, or reinterpreted; additions are optional
  and appended.
- Collections use lists of structured entry parcelables, not AIDL maps or
  parallel primitive arrays.
- Enums define `UNKNOWN = 0`. When Android/vendor numeric values are relevant,
  preserve an additional raw value rather than casting an unknown value to a
  known enum.
- Android numeric constants, PIDs, paths, Binder objects, and framework object
  IDs are not durable identifiers. Transient handles carry owner UID, user,
  boot/provider generation, random token, and expiry.
- All clocks and units are explicit. Wall clock is never used for ordering or
  timeout calculation.
- The schema registry is a structured flat node table with index references,
  required/optional fields, ranges, units, clock domains, examples,
  introduction/deprecation versions, and unknown-field rules. It does not use
  recursive Binder object graphs.
- Dynamically schematized operation/event data uses `BridgePayload` containing
  `schemaId`, `schemaVersion`, `encoding`, and bytes. V1 requires deterministic
  CBOR (RFC 8949); decoders reject duplicate map keys, excessive nesting,
  oversized strings/collections, and values not admitted by the declared
  schema.
- Inline payloads are limited to 64 KiB. Larger data is an artifact/stream with
  content type, declared/transferred length, checksum, compression, truncation,
  and terminal status.
- A schema change that alters meaning, type, unit, required fields, or
  cardinality creates a new schema version. Old schemas remain retrievable.

These rules make the small root ABI durable while allowing future providers to
add self-described payloads without modifying framework classes.

## 13. Lifecycle And Concurrency

### Boot and dependency ordering

1. Init starts `frankenstein_diag` as a non-critical, restartable
   `class late_start` service. The root never waits for it during boot.
   Its rc stanza declares
   `interface aidl com.android.internal.os.frankenstein.diag.IFrankensteinDiagnostic/default`,
   uses a dedicated process name/domain, contains neither `critical` nor
   `oneshot`, and grants only the Unix groups justified by its named providers.
2. `SystemServer` starts `FrankensteinBridgeService` after
   `DisplayManagerService` exists and before PackageManager initialization.
   `onStart()` creates only bounded control-plane state, publishes
   `FrankensteinBridgeInternal`, and publishes `frankenstein`.
3. Requests that require PackageManager authentication return `NOT_READY` until
   PMS is ready. Starting early allows PMS/install provenance hooks to find the
   LocalService before their state transitions begin.
4. Framework owners attach providers as their dependencies become ready.
   Provider descriptors exist from `onStart()` and report `NOT_READY` rather
   than disappearing.
5. At `PHASE_SYSTEM_SERVICES_READY`, the root attaches ordinary framework
   dependencies and enables snapshots. At
   `PHASE_THIRD_PARTY_APPS_CAN_START`, it accepts external-provider
   registrations. At `PHASE_BOOT_COMPLETED`, it emits the final boot readiness
   event and performs a non-blocking inventory reconciliation.
6. User lifecycle callbacks create per-user state only at user start, expose CE
   dependent data only after unlock, and invalidate it at stop/removal.

No optional provider failure may abort a boot phase. A failure updates health,
records a redacted audit entry, emits a health event, and schedules a bounded
retry outside the boot thread.

### Threading contract

- One root control `HandlerThread` owns catalog mutations, handle lifecycle, and
  daemon connection state.
- One event sequencer assigns sequence numbers. Capture hooks only enqueue;
  callback delivery uses a separate bounded executor.
- Each provider has a serial executor for state transitions. Snapshot fan-out
  uses at most two concurrent snapshot coordinators and a maximum of four
  provider workers.
- No outbound Binder call, file/HAL operation, encoding, or callback occurs
  while a registry or framework-owner lock is held.
- Registry readers use immutable snapshots. Registry writers link death before
  publication and unlink after removal; Binder death advances provider
  generation and terminates owned handles.
- Cancellation is idempotent. Exactly one terminal result wins by atomic state
  transition; late provider results are closed and discarded.
- Callback interfaces are `oneway`. A dead, blocked, or over-quota subscriber
  cannot block the sequencer or provider.

### Fixed resource bounds

The V1 limits are part of `BridgeInfo` and may be lower on a build, never
silently higher:

| Resource | V1 maximum |
|---|---:|
| Inline request/result/event payload | 64 KiB |
| Event batch | 64 events or 256 KiB |
| Global event journal | 4096 events or 8 MiB |
| Per-subscription pending queue | 256 events or 512 KiB |
| Subscriptions per UID | 8 |
| Concurrent operations per UID | 16 |
| Open bridge-owned streams per UID | 4 |
| External providers per UID | 16 |
| Total descriptor/schema bytes per external provider | 1 MiB |

All limits use whichever count/byte/deadline threshold is reached first.
Descriptor and stream ownership transfers are explicit; every error,
cancellation, death, and timeout path closes bridge-owned descriptors.

## 14. Provider Ownership Boundaries

| Concern | Sole owner | Boundary rule |
|---|---|---|
| Product policy, consent, UI, workflows, AI | Replaceable broker | Never moves into root providers |
| Root authentication, user isolation, quotas | Root service | Cannot be delegated to metadata or plugins |
| Catalog/schema namespace | Root service | ROM entries immutable; external entries bounded and generation-scoped |
| Framework private state/hook correctness | Owning framework subsystem | Bridge adapter receives normalized copies, not private objects |
| Event ordering/replay/gaps | Event hub | Providers supply local order and payload only |
| Protected platform artifacts | `frankenstein_diag` | Root receives descriptors/metadata, never reads protected files itself |
| Vendor protocol and numeric translation | Vendor adapter | Broker never imports vendor classes or transaction numbers |
| Retention beyond bounded ROM buffers | Replaceable broker | ROM is not a behavioral database |
| Plugin provider implementation | Plugin process | Root catalogs its Binder but does not execute plugin code |

An external registration is owned by `(uid, signer lineage, user, providerId,
registration generation, binder)`. Provider IDs must lie in a namespace granted
to that signer. V1 derives that namespace as
`external.<reversed-calling-package>.*`; the selected package must belong to the
calling UID, the registering UID must resolve to exactly one installed package,
and no external provider may claim or shadow a ROM namespace.
Death, package replacement, user stop, explicit unregister, or failed ownership
revalidation removes it atomically and emits a catalog event.

The root does not proxy an external operation under cleared `system_server`
identity. It returns or identifies the provider Binder so the authorized broker
calls that process directly and Binder preserves the broker identity. Health
pings are the only root-originated calls and carry no authority or client data.
Descriptors from external processes are untrusted input: validate schema,
encoding, size, IDs, references, and relationship cycles before publication.

## 15. Implementation Dependency Order

The permanent work must land in this order because later steps consume frozen
contracts from earlier steps:

1. Generate the final-build static inventory and assign every record A/B/C/D.
2. Resolve the two conditional decisions: DPM owner provisioning and the exact
   restricted Waterlily vendor adapters.
3. Define/freeze the root, diagnostic-daemon, and any VINTF adapter AIDL,
   including DTO/schema/encoding compatibility tests.
4. Declare framework permissions, service names/types, executable labels,
   property/artifact types, and VINTF entries.
5. Implement the root catalog, schema registry, identity, handles, quotas,
   event hub, and `dumpsys` without any optional provider.
6. Start the root at the specified SystemServer point and validate boot/user
   lifecycle behavior.
7. Implement the platform diagnostic daemon, init lifecycle, SELinux policy,
   artifact roots, streaming, and reconnect behavior.
8. Add framework capture providers at subsystem-owned source points: activity,
   package provenance, window/input, and power.
9. Add boot/property/SELinux/service-inspection providers.
10. Add only vendor adapters selected by the completed device audit.
11. Run static/runtime inventory reconciliation and close every remaining
    Bucket A mapping.
12. Freeze the release API/hash, build a user image, and run the release gates.

Do not implement ordinary broker operations while a Bucket A inventory record
remains unresolved.

## 16. Final ROM Release Gates

The architecture is implementation-ready only when all gates pass:

- No `TBD`, unclassified inventory entry, or Bucket A item without an owner,
  provider ID, schema, dependency, failure mode, and test.
- Stable AIDL compatibility/freeze checks pass; a separately built V1 client
  enumerates, subscribes, cancels, streams, and tolerates unknown optional
  fields/enums.
- Root starts before PMS without accessing PMS prematurely, reaches every boot
  phase, survives daemon absence/death, and never delays third-party app start.
- Binder identity tests cover shared UIDs, package mismatch, signer rotation,
  isolated UID, cross-user calls, identity clearing, package replacement, and
  callback re-entry.
- Concurrency tests cover cancellation/result races, callback death, queue
  overflow/gaps, provider death/re-registration, user stop, FD closure, slow
  dumps, and snapshot partial failure.
- Final user build boots enforcing with no bridge AVCs; `neverallow`,
  `sepolicy_tests`, service lookup denial, protected-root denial, and negative
  HAL/property tests pass.
- `frankenstein_diag` is non-critical, restartable, bounded, cannot execute a
  shell, cannot traverse outside named roots, and cannot open denied credential
  or app-data paths.
- Every final VINTF/init/service/context record appears in the shipped inventory;
  runtime reconciliation has no unexplained additions or omissions.
- A broker-domain feasibility test proves every B item sampled from each matrix
  row is actually reachable after the ROM is frozen.
- Device-owner provisioning is proven on a clean first boot or the reviewed
  typed DPM provider is present.
- Vendor audit records exact interface versions/hashes and proves each selected
  adapter works, degrades safely, and exposes no raw secret-bearing interface.

Passing these gates means the ROM has frozen the enabling substrate, not a
speculative assistant product. New workflows and app-accessible capabilities
can then evolve in the broker/plugins without another framework build.

## 17. Normative Android References

- [AOSP Stable AIDL](https://source.android.com/docs/core/architecture/aidl/stable-aidl)
- [AOSP AIDL API guidelines](https://source.android.com/docs/core/architecture/aidl/stable-aidl-apis)
- [Android 16 `SystemService` publication and LocalServices contract](https://android.googlesource.com/platform/frameworks/base/+/android16-release/services/core/java/com/android/server/SystemService.java)
- [AOSP SELinux implementation and context files](https://source.android.com/docs/security/features/selinux/implement)
- [Android 16 service types](https://android.googlesource.com/platform/system/sepolicy/+/android16-release/public/service.te)
