# Frankenstein Bridge — Agent Instructions

## Build Command Handoff

When the user is ready to build, hand them:

```bash
cd /home/premanandal1978/android/waterlily
source build/envsetup.sh
lunch bliss_I001D-bp4a-userdebug
m services framework-minus-apex
```

If Soong lock errors occur (build was aborted), release lock first:

```bash
rm -f /home/premanandal1978/android/waterlily/out/soong/.lock
rm -f /home/premanandal1978/android/waterlily/out/soong/.out-dir.lock
```

## File Inventory (Phase 1 VM Changes)

| Path | Type | Status |
|---|---|---|
| `frameworks/base/core/java/com/android/internal/os/frankenstein/IFrankensteinBridgeService.aidl` | AIDL | Created |
| `frameworks/base/core/java/com/android/internal/os/frankenstein/FrankensteinBridgeResult.aidl` | AIDL | Created |
| `frameworks/base/core/java/com/android/internal/os/frankenstein/FrankensteinBridgeResult.java` | Java | Created |
| `frameworks/base/services/core/java/com/android/server/frankenstein/FrankensteinBridgeService.java` | Java | Created |
| `frameworks/base/services/java/com/android/server/SystemServer.java` | Edit (2 lines) | Modified |

Old (deleted) public-package files under `android/os/frankenstein/` were removed.
The package is now `com.android.internal.os.frankenstein` (internal) to avoid
metalava API lint errors.

## Build Troubleshooting

| Error | Fix |
|---|---|
| `Failed to resolve 'Bundle'` | Parcelable AIDL must be bare declaration `parcelable Foo;`, fields only in the .java |
| `ReferencesHidden` / `UnavailableSymbol` | AIDL must be in `com.android.internal.*` package, not `android.*` |
| `DuplicateSourceClass` | Remove manual .java if AIDL has field list, or make AIDL bare declaration |
| Soong lock | `rm -f out/soong/.lock out/soong/.out-dir.lock` |

## Test Workflow (Post-Build)

```bash
adb root
adb remount
adb sync
adb reboot
# After boot:
adb shell service list | grep frankenstein
adb shell dumpsys frankenstein
adb logcat -s FrankeBridge
```
