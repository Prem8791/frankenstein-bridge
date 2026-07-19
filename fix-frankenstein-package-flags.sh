#!/usr/bin/env bash
set -euo pipefail
cd /home/premanandal1978/android/waterlily
file=frameworks/base/services/core/java/com/android/server/frankenstein/FrankensteinBridgeService.java
perl -0pi -e 's/final int flags = includeDisabled\n\s*\? 0\n\s*: PackageManager\.MATCH_ENABLED_COMPONENTS;/final int flags = includeDisabled\n                        ? PackageManager.MATCH_DISABLED_COMPONENTS\n                        : 0;/s' "$file"
nl -ba "$file" | sed -n '333,345p'