# Broker Home/Back permanent fix

**Date:** 2026-07-29

## Finding

ProdX's accessibility service was enabled and bound, but Android's SystemUI
global-action registrations for Home and Back could contain canceled pending
intents. Restarting SystemUI refreshed them and temporarily restored
`performGlobalAction()`, which made that route unsuitable as the permanent
navigation boundary.

## Implementation

- `RestrictedDeviceController` now injects complete `KEYCODE_HOME` and
  `KEYCODE_BACK` down/up sequences through `InputManagerGlobal`.
- The broker already owns the required privileged `INJECT_EVENTS` permission.
- `NavigationKeyEvents` centralizes the permanent Android key-code mapping and
  has focused regression tests.
- The independently installable broker version is 37, above the version 36
  broker baked into the current ROM. A platform-signed update can therefore be
  installed without another ROM build.

## Verification

- Focused broker unit tests and `:broker:assembleDebug` completed successfully.
- The APK verified against platform certificate SHA-256
  `447126787cb06fe094bff335087fe79988dd460d29f195f3cd095c5cb4297253`.
- `adb install -r` installed broker version 37 over the version 36 system app.
- ProdX `go home` resumed `com.home.launcher/.MainActivity`.
- ProdX `go back` resumed `com.home.launcher/.MainActivity`.
- The decisive Back test passed while accessibility was disabled, confirming
  the broker path is independent of SystemUI accessibility global actions.

Accessibility remains the owner of user-visible UI automation such as
open-and-type. This fix does not expand broker ownership beyond privileged
global navigation.
