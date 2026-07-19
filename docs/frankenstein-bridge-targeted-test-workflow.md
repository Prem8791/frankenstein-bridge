# Frankenstein Bridge — Targeted Test Workflow

## Step 1: Build (Run on VM)

```bash
cd /home/premanandal1978/android/waterlily
source build/envsetup.sh
lunch bliss_I001D-bp4a-userdebug
```

Then build the affected modules:

```bash
# Build the service (services.jar — contains FrankensteinBridgeService)
m services

# Build the framework (framework.jar — contains AIDL stubs + FrankensteinBridgeResult)
m framework-minus-apex
```

Both modules build quickly (minutes, not hours). They do NOT require a full ROM
build. Soong compiles only changed files and their dependents.

If you want a single command:

```bash
m services framework-minus-apex
```

## Step 2: Deploy to Device

```bash
# Remount system partition writable
adb root
adb remount

# Push rebuilt files (paths depend on device partition layout)
adb push out/target/product/I001D/system/framework/services.jar /system/framework/
adb push out/target/product/I001D/system/framework/framework.jar /system/framework/

# Reboot
adb reboot
```

**Alternative:** If the device supports `adb sync`:

```bash
adb root
adb remount
adb sync
adb reboot
```

## Step 3: Verify Service is Registered

After reboot:

```bash
# Check if the bridge service is in the service list
adb shell service list | grep frankenstein

# Expected output:
# 123 frankenstein: [android.os.frankenstein.IFrankensteinBridgeService]

# Dumpsys
adb shell dumpsys frankenstein

# Expected output:
# Frankenstein Bridge Service Status
#   Version: 1.0.0
#   Registered: true
#   Uptime: ...
```

## Step 4: Test with Raw Binder Call (No APK needed)

```bash
# Use service call to invoke ping()
adb shell service call frankenstein 2   # method 2 = ping (0-based: 0=getBridgeVersion, 1=ping)

# Get bridge version
adb shell service call frankenstein 0   # getBridgeVersion

# Get foreground app
adb shell service call frankenstein 4   # getForegroundApp

# Get battery summary
adb shell service call frankenstein 10  # getBatterySummary
```

**Note:** Raw `service call` uses the shell UID which IS NOT in the bridge
allowlist, so you'll get DENIED responses. This is correct behavior — it proves
the auth check works. The test app (platform-signed) will succeed.

## Step 5: Build and Install Test APK

See `docs/frankenstein-dummy-assistant-test-app.md` for the test app.

Build with:

```bash
cd /home/premanandal1978/android/waterlily
source build/envsetup.sh
lunch bliss_I001D-bp4a-userdebug
m FrankensteinAssistantTest
```

Push with:

```bash
adb install -r out/target/product/I001D/system/priv-app/FrankensteinAssistantTest/FrankensteinAssistantTest.apk
```

Or if built into the ROM image, it auto-deploys after `adb reboot`.

## Step 6: Read Logs

```bash
adb logcat -s FrankeBridge
```

Look for `AUDIT:` log lines showing each capability call result.

## Expected Results

| Method | Expected Result |
|---|---|
| `ping()` | `true` |
| `getCallerIdentity()` | Return `{uid: 10xxx, package: "com.frankenstein.assistant.test"}` |
| `getForegroundApp()` | Current foreground package name |
| `getRecentTasks(10)` | List of recent tasks with package/activity/taskId |
| `getInstalledPackages(false)` | Count of installed + list of bundles |
| `getUsageStatsSummary()` | Top 20 apps by foreground time (past week) |
| `checkAppOps("com.android.settings")` | 18 AppOp modes for the package |
| `launchPackage("com.android.settings")` | Triggers launch, returns success |
| `getBatterySummary()` | Level %, status (CHARGING), health, temp |

## Rollback / Cleanup

Revert SystemServer.java changes:

```bash
cd /home/premanandal1978/android/waterlily
git checkout -- frameworks/base/services/java/com/android/server/SystemServer.java
```

Remove source files:

```bash
rm -rf frameworks/base/core/java/android/os/frankenstein/
rm -rf frameworks/base/services/core/java/com/android/server/frankenstein/
```

Then rebuild `m services framework-minus-apex` and redeploy.
