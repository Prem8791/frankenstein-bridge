# Frankenstein Bridge — Dummy Assistant Test App

## Overview

A lightweight platform-signed priv-app that binds to the Frankenstein Bridge
service, calls every capability method, and displays PASS/FAIL with returned
data. Ships as part of the ROM image for automated testing.

## Package Identity

| Field | Value |
|---|---|
| Package name | `com.frankenstein.assistant.test` |
| App name | `Frankenstein Bridge Test` |
| Signature | Platform (`LOCAL_CERTIFICATE := platform`) |
| Placement | `packages/apps/FrankensteinAssistantTest/` |
| Build target | `FrankensteinAssistantTest` |

## AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.frankenstein.assistant.test"
    coreApp="true">

    <uses-permission android:name="android.permission.REAL_GET_TASKS" />
    <uses-permission android:name="android.permission.MANAGE_ACTIVITY_TASKS" />
    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />

    <application
        android:label="Frankenstein Bridge Test"
        android:directBootAware="true">

        <activity
            android:name=".TestActivity"
            android:exported="true"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

## Test Flow (Kotlin Sketch)

```kotlin
// TestActivity.kt
class TestActivity : AppCompatActivity() {
    private var bridgeService: IFrankensteinBridgeService? = null
    private val testResults = mutableListOf<TestResult>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            bridgeService = IFrankensteinBridgeService.Stub.asInterface(binder)
            runAllTests()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            bridgeService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindService(
            Intent().setComponent(ComponentName(
                "com.frankenstein.assistant.test",
                "android.os.frankenstein.IFrankensteinBridgeService"
            )).also { it.setPackage("android") },
            connection, Context.BIND_AUTO_CREATE
        )
    }

    private fun runAllTests() {
        val s = bridgeService ?: return
        test("ping") { s.ping().toString() }
        test("getBridgeVersion") { s.bridgeVersion }
        test("getCallerIdentity") { s.callerIdentity.data?.getString("package") }
        test("getCapabilityMatrix") {
            val caps = s.capabilityMatrix.data
            caps?.getStringArray("capabilities")?.joinToString()
        }
        test("getForegroundApp") {
            s.foregroundApp.data?.getString("packageName")
        }
        test("getUsageStatsSummary") {
            "count=" + s.usageStatsSummary.data?.getInt("count")
        }
        test("checkAppOps") {
            val ops = s.checkAppOps("com.android.settings").data
            ops?.getParcelableArrayList<Bundle>("ops")?.size?.toString()
        }
        test("launchPackage") {
            s.launchPackage("com.android.settings").status.toString()
        }
        test("getBatterySummary") {
            "level=" + s.batterySummary.data?.getInt("level")
        }
        displayResults()
    }

    private fun test(name: String, block: () -> String?) {
        try {
            val result = block()
            testResults.add(TestResult(name, "PASS", result))
        } catch (e: Exception) {
            testResults.add(TestResult(name, "FAIL", e.message))
        }
    }

    private fun displayResults() {
        // Update RecyclerView or ScrollView with testResults
    }
}

data class TestResult(
    val name: String,
    val status: String,  // PASS / FAIL / DENIED
    val detail: String?
)
```

## Android.bp

```bp
android_app {
    name: "FrankensteinAssistantTest",
    srcs: ["**/*.java", "**/*.kt"],
    manifest: "AndroidManifest.xml",
    certificate: "platform",
    privileged: true,
    sdk_version: "current",
    optimize: false,
    static_libs: [
        "androidx.appcompat_appcompat",
        "framework-minus-apex",
    ],
}
```

## Placement in Source Tree

```
packages/apps/FrankensteinAssistantTest/
├── AndroidManifest.xml
├── Android.bp
└── src/com/frankenstein/assistant/test/
    ├── TestActivity.kt
    └── TestResult.kt
```

## Inclusion in ROM Build

Add to `device/asus/I001D/device.mk`:

```makefile
PRODUCT_PACKAGES += FrankensteinAssistantTest
```

This ensures the test APK is built and installed as a platform-signed priv-app
on every ROM build.

## Manual Install (Without ROM Rebuild)

If the ROM already contains the bridge service, install the test APK manually:

```bash
# Build just the test app
cd /home/premanandal1978/android/waterlily
source build/envsetup.sh
lunch bliss_I001D-bp4a-userdebug
m FrankensteinAssistantTest

# Push to device
adb root
adb remount
adb push out/target/product/I001D/system/priv-app/FrankensteinAssistantTest/ \
    /system/priv-app/FrankensteinAssistantTest/
adb reboot
```
