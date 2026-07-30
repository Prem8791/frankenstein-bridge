# I001D Legacy SoundTrigger Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish the ASUS 32-bit Qualcomm SoundTrigger HAL from an isolated,
non-critical vendor process while retaining the boot-proven 64-bit Android
audio service.

**Architecture:** A new 32-bit HIDL service registers only
`android.hardware.soundtrigger@2.3::ISoundTriggerHw/default`. The 64-bit
combined audio service skips its optional SoundTrigger registration only when
an I001D vendor property is enabled. Both processes use Android's established
`hal_audio_default` SELinux family, but have separate address spaces, init
lifecycles, and ABIs.

**Tech Stack:** Android Soong, C++20 vendor binary, HIDL 2.3 passthrough HAL,
Android init, vendor properties, SELinux file contexts, Python static release
gate.

## Global Constraints

- `android.hardware.audio.service` remains 64-bit.
- The new legacy service is `compile_multilib: "32"`.
- The new service contains no `critical`, `reboot_on_failure`, or `onrestart`
  directive.
- The new service must not register audio, audio-effect, Bluetooth, or fake
  SoundTrigger interfaces.
- Existing HIDL 2.3/default VINTF ABI remains unchanged.
- No owner UDM, training audio, wake ONNX model, or bridge test app is packaged.
- Do not run a full ROM build during implementation.

---

### Task 1: Extend the static release gate

**Files:**
- Modify: `packages/apps/FrankensteinApps/rom/validate_rom_integration.py`

**Interfaces:**
- Consumes: Android tree rooted five parents above the validator.
- Produces: exit status zero only when ABI, init, product, property, packaging,
  and UI invariants all hold.

- [ ] **Step 1: Add assertions for the legacy service**

Require the service `Android.bp`, C++ entry point, init file, device property,
product package, and file-context label. Assert `compile_multilib: "32"`,
64-bit main audio configuration, the skip property, and absence of boot-fatal
init directives.

- [ ] **Step 2: Run the validator and verify RED**

Run:

```bash
python3 packages/apps/FrankensteinApps/rom/validate_rom_integration.py
```

Expected: failure because `device/asus/sm8150-common/soundtrigger/Android.bp`
does not exist.

### Task 2: Add the isolated 32-bit HIDL service

**Files:**
- Create: `device/asus/sm8150-common/soundtrigger/Android.bp`
- Create: `device/asus/sm8150-common/soundtrigger/service.cpp`
- Create: `device/asus/sm8150-common/soundtrigger/android.hardware.soundtrigger@2.3-service.i001d.rc`

**Interfaces:**
- Consumes:
  `registerPassthroughServiceImplementation("android.hardware.soundtrigger@2.3::ISoundTriggerHw")`.
- Produces:
  `android.hardware.soundtrigger@2.3::ISoundTriggerHw/default`.

- [ ] **Step 1: Define the vendor binary**

Create `android.hardware.soundtrigger@2.3-service.i001d` as a 32-bit vendor
binary installed under `vendor/bin/hw`, linking `libhidlbase`, `liblog`,
`libutils`, and `android.hardware.soundtrigger@2.3`.

- [ ] **Step 2: Implement single-interface registration**

Configure the HIDL RPC pool, register only SoundTrigger 2.3, return failure if
registration fails, and otherwise join the pool.

- [ ] **Step 3: Add non-critical init lifecycle**

Run as `audioserver` in `class hal`, declare the HIDL interface, retain the
existing audio HAL groups/capabilities needed by the proprietary HAL, and omit
all boot-fatal and audio-restart directives.

### Task 3: Give the legacy service exclusive SoundTrigger ownership

**Files:**
- Modify:
  `hardware/interfaces/audio/common/all-versions/default/service/service.cpp`
- Modify: `device/asus/sm8150-common/vendor.prop`
- Modify: `device/asus/sm8150-common/msmnile.mk`
- Modify: `device/asus/sm8150-common/sepolicy/vendor/file_contexts`

**Interfaces:**
- Consumes: boolean property
  `ro.vendor.audio.soundtrigger.separate_service`.
- Produces: deterministic single ownership of HIDL
  `ISoundTriggerHw/default`.

- [ ] **Step 1: Skip combined-service registration conditionally**

Read the property with `property_get_bool`. When true, skip only the optional
list named `Soundtrigger API`; keep every mandatory and other optional
interface unchanged.

- [ ] **Step 2: Enable the property for I001D**

Add:

```properties
ro.vendor.audio.soundtrigger.separate_service=true
```

- [ ] **Step 3: Package the new service**

Add `android.hardware.soundtrigger@2.3-service.i001d` beside the existing 2.3
implementation while retaining
`$(call soong_config_set_bool,android_hardware_audio,run_64bit,true)`.

- [ ] **Step 4: Label the executable**

Map the exact vendor binary path to `hal_audio_default_exec`.

### Task 4: Verify and mirror the implementation

**Files:**
- Modify local mirror counterparts under:
  `booting-rom-baseline/aosp/`
- Modify: `/home/home/bliss/ProdXAssistant/PROGRESS.md`

**Interfaces:**
- Consumes: canonical VM implementation.
- Produces: reproducible local source record and build handoff.

- [ ] **Step 1: Run the static gate and verify GREEN**

Run the validator in the VM and local mirror. Expected:

```text
Frankenstein ROM integration static checks passed.
```

- [ ] **Step 2: Run source-level checks**

Run C++ formatting/diff checks, Android.bp structural inspection, init safety
grep, and `git diff --check`. Do not start Soong or Ninja.

- [ ] **Step 3: Update progress**

Record the A/B boot proof, isolated-service boundary, exact files, remaining
post-build validation, and that no ROM build was run.

- [ ] **Step 4: Hand off targeted and full build commands**

Provide a targeted service/SELinux/VINTF command first, followed by the user's
GApps `blissify` command. The user performs both builds.
