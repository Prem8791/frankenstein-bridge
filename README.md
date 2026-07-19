# Frankenstein Bridge

A ROM-baked privileged Android OS bridge for maximum-functionality AI assistant
app integration on **Bliss Android 16 / BP4A (API 36)** for **ASUS I001D
Waterlily**.

## Project Identity

| Field | Value |
|---|---|
| Target ROM | Bliss Android 16 (BP4A.251205.006) |
| Device | ASUS I001D (Waterlily, SM8150) |
| VM instance | `instance-20260710-230647` |
| GCP project | `customrom-501702` |
| Zone | `us-south1-a` |
| Android source root | `/home/premanandal1978/android/waterlily` |
| Build product | `bliss_I001D-bp4a-userdebug` |
| Assistant prototype (ref) | `D:\AndroidProjects\ProdXAssistant` |

## Repository Structure

```
OS Bridge/
├── README.md                          # This file
├── docs/
│   ├── frankenstein-bridge-capability-study.md   # Capability inventory & classification
│   ├── frankenstein-bridge-architecture.md       # Architecture proposal
│   ├── frankenstein-bridge-aidl-api.md           # AIDL/API surface design
│   └── frankenstein-bridge-implementation-plan.md # Implementation roadmap
├── findings/
│   └── vm-source-inspection-notes.md             # Raw VM source audit
├── plans/                              # Future: detailed implementation specs
└── references/                         # Future: extracted AIDL, configs, notes
```

## Design Principles

1. **Maximum functionality, minimum risk.** Expose every safe OS capability,
   never a general shell or unrestricted root.
2. **Defense in depth.** Platform signature + priv-app permissions + sysconfig +
   hidden API exemption + SELinux domain + caller authentication + audit.
3. **Deterministic control plane.** The bridge is a narrow Binder service in
   `system_server`; the AI model never touches Binder, HALs, or the kernel.
4. **Opt-in per capability.** Each capability has a risk tier, can be
   individually disabled, and requires appropriate consent.
5. **ROM-baked, not bolted on.** The service lives in `frameworks/base/services`
   and ships as part of the ROM image.

## Current Phase

**Study & Design (Phase 0).** No ROM builds, no VM modifications, no GitHub
pushes. All artifacts are planning documents in this repo.
