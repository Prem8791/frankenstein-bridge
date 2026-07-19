#!/usr/bin/env bash
set -euo pipefail
cd /home/premanandal1978/android/waterlily
file=frameworks/base/services/core/java/com/android/server/frankenstein/FrankensteinBridgeService.java

perl -0pi -e 's/import android\.content\.Intent;\n/import android.content.Intent;\nimport android.content.IntentFilter;\n/' "$file"

perl -0pi -e 's/final List<ApplicationInfo> apps =\n\s*mPackageManagerInternal\.getInstalledApplications\(flags,\n\s*UserHandle\.getUserId\(uid\), getContext\(\)\.getOpPackageName\(\)\);/final List<ApplicationInfo> apps =\n                        mPackageManagerInternal.getInstalledApplications(flags,\n                                UserHandle.getUserId(uid), Process.SYSTEM_UID);/s' "$file"

perl -0pi -e 's/\n\s*p\.putLong\("firstInstallTime", app\.firstInstallTime\);//' "$file"

perl -0pi -e 's/\n\s*AppOpsManager\.OP_INTERNET,\n\s*AppOpsManager\.OP_WIFI_CHANGE,\n\s*AppOpsManager\.OP_BLUETOOTH_CHANGE,//' "$file"
perl -0pi -e 's/\n\s*"INTERNET", "WIFI_CHANGE", "BLUETOOTH_CHANGE",//' "$file"

perl -0pi -e 's/final int level = bm\.getIntProperty\(\n\s*BatteryManager\.BATTERY_PROPERTY_CAPACITY\);\n\s*final int status = bm\.getIntProperty\(\n\s*BatteryManager\.BATTERY_PROPERTY_STATUS\);\n\s*final int health = bm\.getIntProperty\(\n\s*BatteryManager\.BATTERY_PROPERTY_HEALTH\);\n\s*final int plugged = bm\.getIntProperty\(\n\s*BatteryManager\.BATTERY_PROPERTY_PLUGGED\);\n\s*final int temperature = bm\.getIntProperty\(\n\s*BatteryManager\.BATTERY_PROPERTY_TEMPERATURE\);\n\s*final int voltage = bm\.getIntProperty\(\n\s*BatteryManager\.BATTERY_PROPERTY_VOLTAGE\);\n\s*final int chargeCounter = bm\.getIntProperty\(\n\s*BatteryManager\.BATTERY_PROPERTY_CHARGE_COUNTER\);\n\s*final boolean present = bm\.getIntProperty\(\n\s*BatteryManager\.BATTERY_PROPERTY_PRESENT\) == 1;/final int level = bm.getIntProperty(\n                        BatteryManager.BATTERY_PROPERTY_CAPACITY);\n                final int chargeCounter = bm.getIntProperty(\n                        BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);\n                final Intent battery = getContext().registerReceiver(null,\n                        new IntentFilter(Intent.ACTION_BATTERY_CHANGED));\n                final int status = battery != null\n                        ? battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1) : -1;\n                final int health = battery != null\n                        ? battery.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) : -1;\n                final int plugged = battery != null\n                        ? battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) : -1;\n                final int temperature = battery != null\n                        ? battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) : -1;\n                final int voltage = battery != null\n                        ? battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) : -1;\n                final boolean present = battery != null\n                        && battery.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false);/s' "$file"

echo AIDL_IMPORTS
nl -ba frameworks/base/core/java/com/android/internal/os/frankenstein/IFrankensteinBridgeService.aidl | sed -n '16,52p'
echo JAVA_IMPORTS
nl -ba "$file" | sed -n '19,45p'
echo PACKAGE_SECTION
nl -ba "$file" | sed -n '333,360p'
echo APPOPS_SECTION
nl -ba "$file" | sed -n '435,465p'
echo BATTERY_SECTION
nl -ba "$file" | sed -n '545,575p'