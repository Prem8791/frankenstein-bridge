# Frankenstein Bridge ROM-Freeze Implementation Roadmap

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:executing-plans` to implement exactly one reviewed stage at a
> time. Do not use parallel agents: stages share a frozen protocol and every
> stage requires human review before the next instruction is issued.

**Goal:** Implement the immutable Frankenstein Bridge substrate while keeping
all replaceable broker, policy, UI, workflow, and plugin behavior outside the
ROM.

**Architecture:** A frozen Stable AIDL control plane in `system_server`
coordinates ROM providers, a bounded event/snapshot data plane, and an
init-managed protected diagnostic daemon. Framework-only state is captured at
the owning Android subsystem; protected native and vendor access crosses typed
Binder/HAL boundaries. Every stage is independently built, tested, reviewed,
and accepted before the next stage begins.

**Tech Stack:** Android 16/BP4A (API 36), Java system services, structured
Stable AIDL, C++/NDK Binder, Soong, init, SELinux, VINTF, Tradefed/JUnit,
GoogleTest, Python 3 inventory tooling, adb.

## Global Constraints

- Target tree: `/home/leimapokpampremika/bliss/waterlily`.
- Lunch target: `bliss_I001D-bp4a-userdebug`.
- Normative specification:
  `/home/home/bliss/OS Bridge/docs/frankenstein-bridge-architecture.md`.
- Permanent Binder service name: `frankenstein`.
- Permanent broker package: `com.frankenbridge.assistant`, on a dedicated UID
  with no shared user ID.
- Permanent AIDL package: `com.android.internal.os.frankenstein`.
- No `Bundle`, framework-internal object, raw `Parcel`, vendor parcelable, raw
  Binder/HIDL/AIDL transaction, arbitrary path, shell string, unrestricted
  property setter, or dynamically loaded plugin code in a frozen contract.
- Product policy, consent, UI, risk tiers, workflows, AI, and ordinary Android
  API wrappers remain in the replaceable broker/plugins.
- Long or blocking work never runs on a `system_server` Binder or boot thread.
- Every expected failure is structured; every asynchronous resource has
  bounded lifetime, cancellation, owner, generation, and close behavior.
- Every final service, LocalService, init daemon, HAL/VINTF instance, SELinux
  context, property, node, and socket is classified A/B/C/D before release.
- Only one stage may be implemented at a time. Stop after its verification and
  provide the review packet. Do not begin, scaffold, or opportunistically fix a
  later stage.
- Do not rewrite or discard unrelated work. Before editing, record `repo
  status`, project-specific `git status`, and the exact base revisions.
- Do not commit unless the human reviewer explicitly requests a commit after
  accepting the stage.

## Review Protocol For Every Stage

The implementing agent must finish with one review packet containing:

1. Scope completed and explicit non-goals.
2. Exact files created/modified/deleted.
3. Diff summary and any pre-existing dirty files left untouched.
4. Tests run, full commands, exit codes, and concise result counts.
5. Build artifacts or device evidence with SHA-256 hashes where applicable.
6. Security, ABI, lifecycle, concurrency, and SELinux implications.
7. Known failures or deviations. No hidden follow-up work.
8. A statement that no later stage was started.

The reviewer then accepts, rejects, or requests changes. A rejected stage is
fixed under the same stage instruction. The next stage instruction is written
only after acceptance.

---

## Stage Sequence

### Stage 1: Authoritative OS Surface Inventory

**Agent scope:** Implement deterministic static/runtime inventory tooling and
collect the actual final-tree/device service, LocalService, init, HAL/VINTF,
SELinux-context, feature, sysconfig/privapp, APEX, overlay, property-name, node,
and socket evidence. Producers, consumers, assembled declarations, and runtime
observations remain distinct evidence on one canonical surface identity.

**Produces:** Canonical inventory JSON, runtime evidence JSON, a classification
worksheet, collection report, tests, and source/build/device hashes.

**Review gate:** Inventory sources are complete and deterministic; runtime
collection is from the intended build/device; no values or secrets are
captured; every record has a stable evidence identity.

### Stage 2: Freeze Classification And Irreversible Decision Record

**Consumes:** Accepted Stage 1 inventory.

**Agent scope:** Build test-only, non-user-build future-broker-domain and
ordinary-app reachability probes, record
`REACHABLE`/`DENIED`/`PARTIAL`/`NOT_APPLICABLE` for every surface, assign
A/B/C/D to every record, and close the exact DPM provisioning choice, named
protected artifact roots, property write allowlist, standard HAL clients, and
Waterlily vendor adapter set. The Stage 4 permanent broker domain must reproduce
the accepted probe identity and access assumptions exactly.

**Produces:** Signed-off classification manifest and a zero-unclassified
decision report. Each A record names its permanent provider and test; each B/C
record names a demonstrated later API path; each D record names the rejection
reason.

**Review gate:** Zero `UNCLASSIFIED` or `UNTESTED` records, zero A records
without an owner, provider, dependency/failure contract, and test ID; every B/C
row has measured later reachability; no speculative vendor or mutation adapter.

### Stage 3: Frozen V1 Stable AIDL Contract

**Consumes:** Accepted classification/provider list.

**Agent scope:** Define dedicated structured Stable AIDL modules for the root
and diagnostic daemon alongside the still-building prototype; add VINTF AIDL
only if Stage 2 selected a vendor-side adapter. Do not publish the new root or
leave any `Bundle`/prototype type in its dependency graph. Record the exact
prototype removal/cutover set for Stage 9 so contract review does not require a
temporarily broken framework build.

**Produces:** Frozen V1 API/hash, DTO/schema/payload types, root/provider/
operation/subscription interfaces, generated Java/NDK libraries, and
compatibility tests.

**Review gate:** `*-checkapi`, `*-freeze-api`, `AIDL_FROZEN_REL=true`, old/new
generated-stub compatibility across the frozen/current trees, a separately
built V1 test client, enum/default/unknown-field tests, and no forbidden type.

### Stage 4: Permanent Names, Types, And Build Declarations

**Consumes:** Frozen V1 AIDL and accepted Stage 2 decisions.

**Agent scope:** Declare the two signature permissions, root/private Binder
service names and SELinux types, broker domain/package mapping, executable and
artifact/property types, selected HAL client types, module ownership, and only
the VINTF entries selected in Stage 2. Add no broad allow rule and start no
service.

**Produces:** A compiling declaration substrate and an exact name/type
inventory used by every later stage.

**Review gate:** Policy/build compilation, duplicate-name checks, API lint,
neverallow coverage, no default service/file/property type, and exact
classification-manifest traceability for every declaration.

### Stage 5: Catalog, Schema Registry, And External Federation

**Consumes:** Frozen descriptor/schema types and permanent declarations.

**Agent scope:** Implement the unpublished core model: immutable ROM
descriptors, schema lookup, paging, capability graph, caller-derived external
namespaces, registration/update/unregister, death cleanup, catalog observation,
validation, and quotas.

**Produces:** Unit-tested `core.catalog` internals with a fake external
provider; no SystemServer publication yet.

**Review gate:** Namespace shadowing, malformed/cyclic schemas, size quotas,
package replacement, signer/UID mismatch, Binder death, generation, paging,
and direct-provider-Binder tests.

### Stage 6: Operation Handles, Results, Streams, And Quotas

**Consumes:** Catalog/provider contracts.

**Agent scope:** Implement ROM-provider dispatch, terminal state races,
structured errors, timeouts, cancellation, FD/shared-stream ownership,
inline-size limits, per-provider serial state executors, and per-UID resource
accounting behind unpublished test entry points.

**Produces:** `OperationManager`, stream/result infrastructure, and a synthetic
ROM test provider.

**Review gate:** Exactly-one-terminal-result, cancellation races, timeout,
late-result discard, descriptor leak, EPIPE/client death, quota, and 64 KiB
inline-boundary tests.

### Stage 7: Event Hub

**Consumes:** Catalog, schemas, handles, and quotas.

**Agent scope:** Implement the single sequencer, provider/global generation,
current-boot bounded replay, initial-snapshot barrier, batching,
acknowledgement, coalescing, overflow/gap events, callback death, and per-user
filtering.

**Produces:** `core.events` with a synthetic event provider.

**Review gate:** Ordering contract, wrap/overflow, slow/dead callback,
subscription cancellation, user stop, restart generation, and fixed memory
bounds.

### Stage 8: Snapshot Coordinator

**Consumes:** Provider registry, operation infrastructure, and event barrier.

**Agent scope:** Implement correlated non-atomic multi-provider snapshots with
per-section timing, generation, schema, freshness, completeness, timeout, and
partial error reporting.

**Produces:** `core.snapshots` with synthetic fast/slow/failing providers.

**Review gate:** No cross-provider locks, bounded fan-out, partial failure,
cancellation, barrier sequence, and truthful non-atomic semantics.

### Stage 9: Root Publication, Caller Security, And Lifecycle

**Consumes:** Accepted core catalog/operation/event/snapshot internals.

**Agent scope:** Remove the eleven-method `Bundle` prototype and its result
DTO, cut the service over to the frozen V1 module, and implement the minimal
root `SystemService`, early SystemServer start, Binder and LocalService
publication, readiness/boot/user lifecycle, dedicated-UID/package/signer
checks, registration-only caller class, multi-user checks, audit, and
`dumpsys`. Attach only synthetic/always-present core providers, but publish
every accepted ROM descriptor from startup as `NOT_READY` until its owning
stage attaches the implementation. Enable snapshots, external registration,
and boot-complete reconciliation only at their specified boot phases.

**Produces:** A bootable, secured root control plane.

**Review gate:** Build/boot, exact start order, service publication, package/
shared-UID/isolated/cross-user negative tests, identity-clear tests, app-domain
service lookup denial, lifecycle invalidation, and no boot regression.

### Stage 10: Protected Diagnostic Daemon Skeleton

**Consumes:** Frozen diagnostic AIDL, declarations, root connector, and Stage 2
daemon access list.

**Agent scope:** Add the NDK Binder daemon, init service, executable/service
labels, least-privilege allow rules, non-critical restart behavior, asynchronous
root connector, health/generation reporting, and zero privileged operations.

**Produces:** Boot-safe `frankenstein_diag` connectivity.

**Review gate:** Native tests, daemon absent/death/restart behavior, root
non-blocking boot, app-domain lookup denial, `neverallow`/policy tests, and
enforcing boot.

### Stage 11: Activity/Process/Crash/ANR Provider

**Consumes:** Event hub and snapshot coordinator.

**Agent scope:** Add narrowly reviewed capture points in AMS/ProcessList/
UidObserverController/AppErrors/AnrHelper and memory-pressure paths, copying
stable data under owner locks and enqueueing after unlock.

**Produces:** `framework.activity` events/snapshots.

**Review gate:** Process/UID lifecycle ordering, crash/ANR correlation,
high-churn load, no lock inversion, no raw records, redaction, and system_server
memory bounds.

### Stage 12: Package/Installer/ART/Rollback Provenance

**Consumes:** Event/artifact-correlation contracts.

**Agent scope:** Capture authoritative session, verification, scan/reconcile,
freeze, commit/failure, rollback, and dexopt transitions with one propagated
correlation identity. Artifact opening remains unavailable until Stage 16.

**Produces:** `framework.package_provenance`.

**Review gate:** Success/failure/update/rollback/boot-scan flows, stable reason
codes, old/new version correlation, no broadcast inference, and no APK content
leak.

### Stage 13: Window/Focus/Input Diagnostics

**Consumes:** Event and snapshot infrastructure.

**Agent scope:** Capture structured window/focus/transition/input-target state,
input-device changes, and metadata-only dispatch latency/failure from WMS/Input
owner points.

**Produces:** `framework.window_input`.

**Review gate:** Global-lock discipline, focus/transition races, multi-display/
user behavior, input ANR correlation, and proof that keys, text, touch
coordinates, screenshots, and injection are absent.

### Stage 14: Power/Suspend/Thermal Provenance

**Consumes:** Event and snapshot infrastructure.

**Agent scope:** Capture wake-lock acquire/change/release/timeout, work-source
attribution, suspend blockers, wake/sleep, idle/doze, thermal, and power-mode
transitions at authoritative owner points.

**Produces:** `framework.power_provenance`.

**Review gate:** Nested locks, timeout/release races, WorkSource attribution,
suspend cycles, thermal events, high-frequency coalescing, no power-lock
callback, and no ordinary control wrapper.

### Stage 15: Binder/HAL Inventory And Dump Streaming

**Consumes:** Diagnostic daemon and operation/stream infrastructure.

**Agent scope:** Implement `diag.services`: service/HAL descriptors,
versions/hashes, liveness/death, owning process/domain metadata where available,
denylisted targets, and bounded asynchronous dump-to-pipe.

**Produces:** Typed service inventory and dump streams without raw transactions.

**Review gate:** Unknown/dead/hung/sensitive service tests, truncation, timeout,
FD closure, descriptor mismatch, and no arbitrary transaction path.

### Stage 16: Protected Artifact Provider

**Consumes:** Stage 2 named roots and daemon streaming.

**Agent scope:** Implement `diag.artifacts` for the selected tombstone, pstore,
ANR, recovery/update, kernel/audit, and build-evidence namespaces using opaque
IDs and confined read-only opens.

**Produces:** Metadata-first listing/opening with checksum, range, truncation,
retention, and correlation fields.

**Review gate:** `openat2`/equivalent traversal and symlink attacks,
app/credential path denial, rotation/deletion race, size/time limits,
previous-boot freshness, and FD leak tests.

### Stage 17: SELinux Diagnostic Provider

**Consumes:** Diagnostic daemon and artifact provider.

**Agent scope:** Implement `diag.selinux`: enforcing state, policy
version/digest, process/file/property/service context lookup, access checks,
bounded AVC stream, filters, and previous-boot evidence when actually
available.

**Produces:** Structured SELinux diagnostics plus optional raw evidence
artifact.

**Review gate:** Context correctness, redaction, overflow/gaps, unavailable
source semantics, access-check accuracy, no mutation/permissive/relabel API,
and negative policy tests.

### Stage 18: System Property Provider

**Consumes:** Stage 2 read/redaction/write decisions.

**Agent scope:** Implement `diag.properties`: metadata, read/list/observe,
typed validation, immutable allowlisted writes, property-context enforcement,
and restart/reboot requirement metadata.

**Produces:** No general property setter.

**Review gate:** Security namespace redaction, disallowed writes, type/range,
read-only and persistent property behavior, observation ordering, and
property-service SELinux denials.

### Stage 19: Boot And BootControl Provider

**Consumes:** Selected standard/vendor HAL path and protected artifacts.

**Agent scope:** Implement `diag.boot`: boot-phase events, boot reason,
slot/active/success/bootable state, verified-boot/rollback metadata, prior-boot
correlation, and read-only BootControl adapter by default.

**Produces:** Stable boot schemas with explicit unsupported/unavailable fields.

**Review gate:** Interface version/hash, single/dual-slot variants, missing HAL,
daemon/HAL death, prior-boot provenance, and no reboot/OTA/unlock bypass.

### Stage 20: Selected Waterlily Vendor Adapters

**Consumes:** The exact accepted adapter rows from Stage 2.

**Agent scope:** Implement one selected logical adapter per review cycle,
starting with read-only health/dump and adding only Stage 2-approved typed
control. Use existing stable HAL clients where possible; use a separately
frozen VINTF adapter only when required.

**Produces:** One `vendor.<logical-capability>` provider per accepted cycle.

**Review gate:** Exact interface version/hash/build constraints, service death,
unsupported firmware, normalization, SELinux least privilege, no vendor
parcelable/raw transaction/secret leak. Repeat Stage 20 as separate reviewed
cycles until the selected set is complete.

### Stage 21: Device-Owner Resolution

**Consumes:** Stage 2 DPM decision.

**Agent scope:** If clean-first-boot owner provisioning was selected, implement
and test that provisioning path for `com.frankenbridge.assistant`. If and only
if Stage 2 selected a finite DPM internal provider, implement those exact typed
operations and no generic admin gateway.

**Produces:** One proven administration path or a recorded decision that no DPM
capability is required.

**Review gate:** Clean first boot, upgrade/reinstall, signer rotation, user/
profile ownership, factory reset, unauthorized caller, and no policy bypass.

### Stage 22: Final Inventory Reconciliation And Broker Feasibility

**Consumes:** All accepted providers and final candidate image.

**Agent scope:** Regenerate static/runtime inventory, require zero unexplained
delta and zero unclassified/A-without-provider records, and rerun the exact
Stage 2 future-broker/ordinary-app reachability suite for every B/C record.

**Produces:** Shipped canonical CBOR inventory, archived human-readable
manifest, reconciliation report, and B/C feasibility evidence.

**Review gate:** Exact final-image hashes, complete reconciliation, provider
mapping, no missing runtime service/HAL/context/config/APEX/overlay surface, and
no B/C classification based only on assumption.

### Stage 23: Freeze And Final User-Build Qualification

**Consumes:** Accepted reconciliation and all implementation stages.

**Agent scope:** Re-run the AIDL freeze/check targets and prove the Stage 3
hashes are unchanged, build the production-equivalent user image, run ABI/
security/concurrency/boot/daemon/provider/device tests, archive artifacts, and
make no feature changes.

**Produces:** Release-candidate ROM and immutable handoff evidence.

**Review gate:** Every architecture Section 16 gate passes with fresh output.
Any failure returns to its owning stage; no waiver converts an unresolved
Bucket A item into deferred work.

## Instruction Issuance Rule

Only Stage 1 has a detailed implementation instruction at plan creation time:
`2026-07-26-frankenstein-bridge-step-01-inventory.md`.

After Stage 1 review, write the Stage 2 instruction using the accepted paths,
record schema, and evidence hashes. Continue this pattern. This is deliberate:
later detailed instructions must consume reviewed facts and must not invent
vendor adapters, DPM behavior, source hook locations, or ABI types in advance.
