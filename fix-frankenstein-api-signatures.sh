#!/usr/bin/env bash
set -euo pipefail
cd /home/premanandal1978/android/waterlily
file=frameworks/base/services/core/java/com/android/server/frankenstein/FrankensteinBridgeService.java

perl -0pi -e 's/mPackageManagerInternal\.getPackagesForUid\(callingUid\)/mPackageManager.getPackagesForUid(callingUid)/g' "$file"
perl -0pi -e 's/mPackageManagerInternal\.getPackagesForUid\(uid\)/mPackageManager.getPackagesForUid(uid)/g' "$file"
perl -0pi -e 's/final ActivityManager am = getContext\(\)\.getSystemService\(\n\s*ActivityManager\.class\);\n\s*final List<ActivityManager\.RunningTaskInfo> tasks =\n\s*am\.getTasks\(1\);/final ActivityTaskManager atm = getContext().getSystemService(\n                        ActivityTaskManager.class);\n                final List<ActivityManager.RunningTaskInfo> tasks =\n                        atm.getTasks(1);/s' "$file"
perl -0pi -e 's/atm\.getRecentTasks\(cappedResults,\n\s*ActivityManager\.RECENT_WITH_EXCLUDED\)/atm.getRecentTasks(cappedResults,\n                                ActivityManager.RECENT_WITH_EXCLUDED,\n                                UserHandle.getUserId(uid))/s' "$file"

echo CALLER_SECTION
nl -ba "$file" | sed -n '95,150p'
echo FOREGROUND_SECTION
nl -ba "$file" | sed -n '252,302p'