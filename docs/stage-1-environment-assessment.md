# Stage 1 — Environment Assessment

> **Purpose:** This document describes the current working environment for
> implementing Stage 1 (Authoritative OS Surface Inventory). It identifies the
> project folders, their roles, the roadblock, and the paths forward.
>
> Use this to brief another agent on where we are and what is needed.

## Project Folders

### Three primary folders

| Folder | Contents | Role |
|---|---|---|
| `OS Bridge/` | Architecture spec, Stage 1 checklist (`step-01-inventory.md`), ROM-freeze roadmap, permanent capability review, AGENTS.md | **Normative specification** — defines *what* to build and *why* |
| `home/` | HomeLauncher app source, `rom-integration/` (sepolicy drafts, overlays, patches, deployment manifest), GCloud SSH key, platform signing keys | **ROM integration assets** — the files that get merged into the Android tree before a build |
| `waterlily-i001d-rebuild/` | Device source snapshots (`evidence/vm-snapshots/vm-device-source-normalized-20260723/`), VINTF manifests, init configs, mapping reference, porting evidence | **Device evidence** — partial source for `device/asus/I001D` and `device/asus/sm8150-common`, but **not** the full AOSP tree |

### Three supporting folders

| Folder | Contents | Role |
|---|---|---|
| `franken_client/` | Frankenstein Bridge test app (platform-signed) | Bridge client testing |
| `franken_test2/` | Frankenstein Bridge test app v2 | Bridge client testing |
| `waterlily-i001d-rebuild (2)/` | Copy of the rebuild project | Redundant backup |

## Roadblock: Android AOSP Tree Is On An Unreachable Cloud VM

The Stage 1 implementation plan (`step-01-inventory.md`) requires working inside
the Android AOSP source tree at:

```
/home/premanandal1978/android/waterlily
```

This tree is **not on this machine**. It lives on a cloud VM:

| Detail | Value |
|---|---|
| VM host | `opencode@34.174.182.88` |
| Tree path on VM | `/home/leimapokpampremika/bliss/waterlily/` |
| SSH key | `~/.ssh/id_ed25519` (local) |
| Status | **Unreachable** — SSH and ping both time out |

There is also a GCloud VM with the tree at a different path:

| Detail | Value |
|---|---|
| Instance | `instance-20260707-045005` |
| Project | `customrom-501702` |
| Zone | `us-south1-b` |
| User | `premanandal1978` |
| Tree path | `~/android/bliss-I001D/` |
| GCloud SSH key | `home/.gcloud-ssh/home_vm_google_compute_engine` (local) |
| Access | Requires `gcloud` CLI (not installed locally) |

## The Location Problem

The Stage 1 plan specifies these paths for tool code and output:

| Role | Required path | Location |
|---|---|---|
| Tool root | `vendor/bliss/tools/frankenstein_inventory/` | Inside the AOSP tree on the VM |
| Evidence output | `device/asus/sm8150-common/frankenstein/inventory/` | Inside the AOSP tree on the VM |
| Source to scan | `frameworks/base/`, `system/core/`, `system/sepolicy/` | Inside the AOSP tree on the VM |
| Product output | `out/target/product/I001D/` | Inside the AOSP build output on the VM |

Without the tree, Stage 1 static collection cannot proceed.

## What We Have Locally That Works

| Asset | Status |
|---|---|
| ASUS I001D device | Connected via USB (`K9AIGF00U2343U3`) — `adb` works |
| Device fingerprint | `asus/WW_I001D/ASUS_I001_1:11/RKQ1.200710.002/18.0210.2201.215-0:user/release-keys` (stock ASUS Android 11) |
| Python 3 | Installed locally |
| 3.5GB 7z archive | `waterlily-i001d-rebuild-new.7z` on disk — may contain full tree but no decompressor installed |
| Device source snapshots | `waterlily-i001d-rebuild/evidence/vm-snapshots/vm-device-source-normalized-20260723/` — has `device/asus/` but not `frameworks/base/` or `system/` |

## Two Paths Forward

### Path A: Get VM access

- Start the GCloud VM or get a working IP for the opencode VM
- Then SSH in and work directly in the AOSP tree

### Path B: Work from local snapshots

- The local `vm-device-source-normalized-20260723` snapshot has only device-level source
- Missing: `frameworks/base/`, `system/core/`, `system/sepolicy/`, `vendor/bliss/`, `out/target/product/I001D/`
- Would need the full AOSP tree to be reconstructed or downloaded locally

## What Stage 1 Needs (For Reference)

The inventory tool collects:

- **Static** (from source tree): `SystemServer.java` service registrations, `LocalServices` producers/consumers, init `.rc` services, VINTF HAL instances, SELinux context files, APEX payloads, overlay APKs, protected path references
- **Runtime** (from device via adb): `service list`, `dumpsys -l`, `cmd -l`, `lshal`, `pm list features`, APEX packages, overlays, `getprop` names, `getenforce`, `ps -AZ`, device nodes, Unix sockets
- **Output**: 5 files — `static-inventory.json`, `runtime-inventory.json`, `classification.csv`, `source-revisions.txt`, `collection-report.md`
