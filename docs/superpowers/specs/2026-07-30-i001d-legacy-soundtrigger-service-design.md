# I001D Legacy SoundTrigger Service Design

## Objective

Expose the ASUS/Qualcomm 32-bit SoundTrigger HAL to Android 16 without changing
the proven 64-bit main audio service and without making wake-word availability
a boot dependency.

## Confirmed constraints

- `android.hardware.audio.service` must remain 64-bit. Changing it to 32-bit
  caused slot B to stop at the first splash screen; the unchanged 64-bit slot A
  booted successfully.
- ASUS supplies `sound_trigger.primary.msmnile.so`, `libsmwrapper.so`, and
  `libacdbloader.so` in a working 32-bit set. No matching 64-bit
  `libsmwrapper.so` exists.
- Android expects
  `android.hardware.soundtrigger@2.3::ISoundTriggerHw/default`.
- The service must be non-critical. Its failure may disable DSP wake-word
  recognition, but must not stop Android, restart the main audio service, or
  trigger a boot loop.
- The ROM contains no owner voice model and no ONNX wake-word fallback. ProdX
  remains responsible for owner enrollment, model delivery, activation
  feedback, and user-facing state.

## Selected architecture

Use Android's standard legacy-program pattern: run the legacy code in a
separate 32-bit process and communicate through the existing HIDL boundary.

Create a vendor binary named `android.hardware.soundtrigger@2.3-service.i001d`.
It is compiled 32-bit, loads the existing
`android.hardware.soundtrigger@2.3-impl`, and registers only
`ISoundTriggerHw/default`. It does not register audio, audio-effect, Bluetooth,
or unrelated HAL interfaces.

Keep `android.hardware.audio.service` 64-bit. On I001D, a vendor property tells
the combined audio service to skip its optional SoundTrigger registration.
This prevents two processes from racing to publish the same HIDL instance while
leaving the shared audio-service source behavior unchanged on other devices.

## Startup and failure behavior

Init starts the legacy service in `class hal` under the `audioserver` identity.
It is neither `critical` nor `reboot_on_failure`, and it has no `onrestart`
relationship with `audioserver` or the main audio HAL. Init may restart the
process normally if it exits, but repeated failure affects only SoundTrigger.

The service performs one job:

1. Configure a HIDL RPC thread pool.
2. Load the SoundTrigger 2.3 passthrough implementation.
3. Register `ISoundTriggerHw/default`.
4. Join the RPC thread pool.

If loading or registration fails, it logs the exact failure and exits with a
nonzero status. It must not register a fake implementation.

## Android integration

- **Soong:** Build the new vendor service as 32-bit and package its init file.
- **Product:** Include the service and retain
  `android.hardware.soundtrigger@2.3-impl`.
- **VINTF:** Retain the existing 2.3/default declaration; no new public HAL ABI
  is introduced.
- **Init:** Add one non-critical vendor HAL service definition.
- **SELinux:** Give the process a dedicated domain and executable type. Permit
  only the standard SoundTrigger HAL capabilities, vendor audio device/config
  access required by the existing HAL, and HIDL service registration. Do not
  grant system-server, filesystem-wide, shell, or arbitrary device access.
- **Linker/ABI:** The service and SoundTrigger implementation are 32-bit and
  resolve the existing 32-bit vendor support libraries. The main audio service
  and normal audio stack remain 64-bit.
- **Framework:** Android middleware continues to consume the standard HIDL
  2.3/default instance. No framework DTO, Binder contract, or ProdX API changes
  are required.

## Ownership boundaries

The ROM owns process isolation, init, SELinux, VINTF, and the stable
SoundTrigger service boundary. The legacy service owns only Qualcomm HAL
loading and HIDL publication. ProdX owns training text, recordings, owner UDM
creation, enrollment, recognition arming, chime behavior, and settings.

## Verification

Static release checks must confirm:

- the main audio service is still forced to 64-bit;
- the new service is compiled 32-bit;
- the combined audio service skips SoundTrigger only when the I001D property is
  enabled;
- the service has no boot-critical or audio-restart directives;
- the required 32-bit ASUS libraries are packaged;
- no bridge test app, broker launcher, wake ONNX model, or owner UDM is baked.

Targeted build validation must compile the new service, SELinux policy, VINTF,
and product image without performing a full ROM build first.

After a ROM build and flash:

1. Confirm Android reaches `sys.boot_completed=1`.
2. Confirm the main audio service is 64-bit and normal playback/recording work.
3. Confirm the legacy SoundTrigger service is 32-bit and running.
4. Confirm `dumpsys soundtrigger_middleware` lists the real Qualcomm module,
   not only `AOSP fake STHAL`.
5. Stop the legacy service and verify Android remains booted with normal audio.
6. Restart it, enroll the existing owner UDM through ProdX, and verify
   low-power “Hey Aura” recognition.

## Explicit non-goals

- Recompiling or translating proprietary ASUS/Qualcomm libraries to 64-bit.
- Adding another broker API or ROM-specific wake-word protocol.
- Baking a user-specific model, training audio, ONNX fallback, or “Hey Google”.
- Changing the ABI of the main audio service or existing SoundTrigger HIDL
  interface.
