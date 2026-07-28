# OS Bridge — Frankenstein Bridge Service

This directory contains the Frankenstein Bridge — a permanent, policy-free
Android system service embedded in `system_server` that exposes a controlled,
audited Binder interface for AI assistants and privileged apps on Bliss ROM.

## Directory Structure

```
OS Bridge/
├── README.md                    ← This file
├── AGENTS.md                    ← Agent build/deploy instructions
├── references/                  ← Prototype AIDL & Java (superseded)
│   ├── IFrankensteinBridgeService.aidl  ← Old 11-method interface
│   ├── FrankensteinBridgeService.java   ← Old prototype impl
│   └── ...
├── rom-code/                    ← ACTUAL ROM-BAKED SOURCE (from VM)
│   ├── aidl/                    ← 21 AIDL interface files (v1 frozen)
│   ├── java/                    ← 20 Java implementation files
│   ├── native-daemon/           ← C++ daemon (6 files)
│   ├── sepolicy/                ← 3 SELinux policy files
│   └── build/                   ← Android.bp + SystemServer snippet
├── docs/                        ← Architecture & design docs
│   ├── frankenstein-bridge-architecture.md
│   ├── frankenstein-bridge-aidl-api.md
│   ├── frankenstein-bridge-implementation-plan.md
│   └── ...
├── artifacts/                   ← Build artifacts & crash logs
├── plans/                       ← Implementation plans
├── findings/                    ← Source inspection notes
├── private-signing/             ← Platform signing keys
└── fix-*.sh                     ← Build fix scripts
```

## ROM Code vs Prototype

The `references/` directory contains the old prototype (11-method
`IFrankensteinBridgeService` with `Bundle`-based results).

The `rom-code/` directory contains the **actual source baked into the ROM**
— a fully evolved interface with 16 methods, structured AIDL parcelables,
event subscriptions, async operations, provider registry, capability graph,
external plugin support, quota tracking, audit ring, and native daemon
integration. This is the version that ships in `Bliss-v19.6-I001D-UNOFFICIAL`.

See `vm-state/SYNC-SUMMARY.md` for the full diff between prototype and ROM.
