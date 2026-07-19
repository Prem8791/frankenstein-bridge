/*
 * Copyright (C) 2026 The Frankenstein Bridge Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.frankenstein;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.app.usage.UsageStatsManagerInternal;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.UserHandle;
import android.util.Slog;

import com.android.server.LocalServices;
import com.android.server.SystemService;

import com.android.internal.os.frankenstein.IFrankensteinBridgeService;

import java.util.ArrayList;
import java.util.List;

/**
 * Frankenstein Bridge System Service — ROM-baked privileged bridge for AI assistant.
 *
 * Runs inside system_server. Provides a Binder AIDL interface for the assistant APK.
 * Every method verifies the real caller identity via Binder.getCallingUid().
 * No shell, no root, no raw command execution.
 */
public class FrankensteinBridgeService extends SystemService {

    private static final String TAG = "FrankeBridge";
    private static final String BRIDGE_VERSION = "1.0.0";

    private static final String ALLOWED_CALLER_PACKAGE =
            "com.frankenstein.assistant.test";

    private PackageManagerInternal mPackageManagerInternal;
    private UsageStatsManagerInternal mUsageStatsManagerInternal;
    private PackageManager mPackageManager;

    public FrankensteinBridgeService(Context context) {
        super(context);
    }

    @Override
    public void onStart() {
        Slog.i(TAG, "Starting Frankenstein Bridge v" + BRIDGE_VERSION);
        publishBinderService("frankenstein", mBinderImpl);
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_SYSTEM_SERVICES_READY) {
            mPackageManagerInternal = LocalServices.getService(PackageManagerInternal.class);
            mUsageStatsManagerInternal = LocalServices.getService(
                    UsageStatsManagerInternal.class);
            mPackageManager = getContext().getPackageManager();
            Slog.i(TAG, "Bridge service ready — LocalServices acquired");
        }
        if (phase == PHASE_BOOT_COMPLETED) {
            Slog.i(TAG, "Bridge service boot completed");
        }
    }

    @Override
    public void onUserSwitching(TargetUser from, TargetUser to) {
        Slog.i(TAG, "User switched to " + to.getUserIdentifier());
    }

    // ─── Caller Authentication ─────────────────────────────────

    private int enforceCaller() {
        final int callingUid = Binder.getCallingUid();
        final int callingPid = Binder.getCallingPid();

        if (callingUid == Process.SYSTEM_UID) {
            return callingUid;
        }

        final String[] packages = mPackageManagerInternal.getPackagesForUid(callingUid);
        if (packages == null || packages.length == 0) {
            Slog.w(TAG, "Caller UID " + callingUid + " has no packages");
            throw new SecurityException("Caller has no packages");
        }

        boolean allowed = false;
        for (String pkg : packages) {
            if (ALLOWED_CALLER_PACKAGE.equals(pkg)) {
                allowed = true;
                break;
            }
        }

        if (!allowed) {
            Slog.w(TAG, "Caller UID=" + callingUid + " PID=" + callingPid
                    + " packages=" + java.util.Arrays.toString(packages)
                    + " not allowed to use bridge");
            throw new SecurityException("Caller not authorized");
        }

        try {
            final int sigMatch = mPackageManager.checkSignatures(
                    callingUid, Process.SYSTEM_UID);
            if (sigMatch != PackageManager.SIGNATURE_MATCH) {
                Slog.w(TAG, "Caller " + packages[0] + " does not have platform signature");
                throw new SecurityException("Caller lacks platform signature");
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            Slog.w(TAG, "Signature check failed", e);
            throw new SecurityException("Signature check failed");
        }

        return callingUid;
    }

    private String getCallerPackage(int uid) {
        final String[] packages = mPackageManagerInternal.getPackagesForUid(uid);
        return (packages != null && packages.length > 0) ? packages[0] : "unknown";
    }

    private void logCall(int callingUid, String method, boolean success,
            long startMs, String detail) {
        final long elapsed = System.currentTimeMillis() - startMs;
        Slog.i(TAG, "AUDIT: caller=" + getCallerPackage(callingUid)
                + " uid=" + callingUid
                + " method=" + method
                + " result=" + (success ? "OK" : "DENIED")
                + " latencyMs=" + elapsed
                + (detail != null ? " detail=" + detail : ""));
    }

    private Bundle okBundle(Bundle data) {
        data.putInt("_status", 0);
        data.putInt("_denialCode", 0);
        return data;
    }

    private Bundle deniedBundle(int denialCode, String message) {
        final Bundle b = new Bundle();
        b.putInt("_status", 1);
        b.putInt("_denialCode", denialCode);
        b.putString("_errorMessage", message);
        return b;
    }

    private Bundle errorBundle(String message) {
        final Bundle b = new Bundle();
        b.putInt("_status", 2);
        b.putString("_errorMessage", message);
        return b;
    }

    // ─── Binder Implementation ─────────────────────────────────

    private final IBinder mBinderImpl = new IFrankensteinBridgeService.Stub() {

        @Override
        public String getBridgeVersion() {
            return BRIDGE_VERSION;
        }

        @Override
        public boolean ping() {
            return true;
        }

        @Override
        public Bundle getCallerIdentity() {
            final long startMs = System.currentTimeMillis();
            try {
                final int uid = enforceCaller();
                final Bundle data = new Bundle();
                data.putInt("uid", uid);
                data.putString("package", getCallerPackage(uid));
                data.putInt("pid", Binder.getCallingPid());
                data.putLong("timestampMs", System.currentTimeMillis());
                logCall(uid, "getCallerIdentity", true, startMs, null);
                return okBundle(data);
            } catch (SecurityException e) {
                logCall(Binder.getCallingUid(), "getCallerIdentity", false, startMs,
                        e.getMessage());
                return deniedBundle(1, e.getMessage());
            } catch (Exception e) {
                Slog.e(TAG, "getCallerIdentity error", e);
                return errorBundle(e.getMessage());
            }
        }

        @Override
        public Bundle getCapabilityMatrix() {
            final long startMs = System.currentTimeMillis();
            try {
                final int uid = enforceCaller();
                final Bundle caps = new Bundle();
                caps.putString("bridge_version", BRIDGE_VERSION);
                caps.putStringArray("capabilities", new String[]{
                    "ping",
                    "getCallerIdentity",
                    "getCapabilityMatrix",
                    "getForegroundApp",
                    "getRecentTasks",
                    "getInstalledPackages",
                    "getUsageStatsSummary",
                    "checkAppOps",
                    "launchPackage",
                    "getBatterySummary"
                });
                final Bundle meta = new Bundle();
                meta.putInt("total_capabilities", 10);
                meta.putInt("api_version", 1);
                meta.putString("build_fingerprint", Build.FINGERPRINT);
                caps.putBundle("metadata", meta);
                logCall(uid, "getCapabilityMatrix", true, startMs, null);
                return okBundle(caps);
            } catch (SecurityException e) {
                logCall(Binder.getCallingUid(), "getCapabilityMatrix", false, startMs,
                        e.getMessage());
                return deniedBundle(1, e.getMessage());
            } catch (Exception e) {
                Slog.e(TAG, "getCapabilityMatrix error", e);
                return errorBundle(e.getMessage());
            }
        }

        @Override
        public Bundle getForegroundApp() {
            final long startMs = System.currentTimeMillis();
            try {
                final int uid = enforceCaller();
                final ActivityManager am = getContext().getSystemService(
                        ActivityManager.class);
                final List<ActivityManager.RunningTaskInfo> tasks =
                        am.getTasks(1);
                final Bundle data = new Bundle();
                if (tasks != null && !tasks.isEmpty()) {
                    final ActivityManager.RunningTaskInfo task = tasks.get(0);
                    data.putString("packageName",
                            task.topActivity != null
                                    ? task.topActivity.getPackageName() : null);
                    data.putString("activityName",
                            task.topActivity != null
                                    ? task.topActivity.getClassName() : null);
                    data.putInt("taskId", task.taskId);
                    data.putInt("userId", task.userId);
                } else {
                    data.putString("packageName", null);
                }
                logCall(uid, "getForegroundApp", true, startMs, null);
                return okBundle(data);
            } catch (SecurityException e) {
                logCall(Binder.getCallingUid(), "getForegroundApp", false, startMs,
                        e.getMessage());
                return deniedBundle(1, e.getMessage());
            } catch (Exception e) {
                Slog.e(TAG, "getForegroundApp error", e);
                return errorBundle(e.getMessage());
            }
        }

        @Override
        public Bundle getRecentTasks(int maxResults) {
            final long startMs = System.currentTimeMillis();
            try {
                final int uid = enforceCaller();
                final int cappedResults = Math.min(Math.max(maxResults, 1), 50);
                final ActivityTaskManager atm = getContext().getSystemService(
                        ActivityTaskManager.class);
                final List<ActivityManager.RecentTaskInfo> recentTasks =
                        atm.getRecentTasks(cappedResults,
                                ActivityManager.RECENT_WITH_EXCLUDED);
                final Bundle data = new Bundle();
                final int count = recentTasks != null ? recentTasks.size() : 0;
                data.putInt("count", count);
                final ArrayList<Bundle> taskList = new ArrayList<>(count);
                if (recentTasks != null) {
                    for (ActivityManager.RecentTaskInfo task : recentTasks) {
                        final Bundle t = new Bundle();
                        t.putInt("taskId", task.taskId);
                        t.putString("packageName",
                                task.topActivity != null
                                        ? task.topActivity.getPackageName() : null);
                        t.putString("activityName",
                                task.topActivity != null
                                        ? task.topActivity.getClassName() : null);
                        t.putInt("userId", task.userId);
                        t.putString("description",
                                task.description != null
                                        ? task.description.toString() : null);
                        taskList.add(t);
                    }
                }
                data.putParcelableArrayList("tasks", taskList);
                logCall(uid, "getRecentTasks", true, startMs, "count=" + count);
                return okBundle(data);
            } catch (SecurityException e) {
                logCall(Binder.getCallingUid(), "getRecentTasks", false, startMs,
                        e.getMessage());
                return deniedBundle(1, e.getMessage());
            } catch (Exception e) {
                Slog.e(TAG, "getRecentTasks error", e);
                return errorBundle(e.getMessage());
            }
        }

        @Override
        public Bundle getInstalledPackages(boolean includeDisabled) {
            final long startMs = System.currentTimeMillis();
            try {
                final int uid = enforceCaller();
                final int flags = includeDisabled
                        ? 0
                        : PackageManager.MATCH_ENABLED_COMPONENTS;
                final List<ApplicationInfo> apps =
                        mPackageManagerInternal.getInstalledApplications(flags,
                                UserHandle.getUserId(uid), getContext().getOpPackageName());
                final Bundle data = new Bundle();
                data.putInt("count", apps != null ? apps.size() : 0);
                final ArrayList<Bundle> pkgList = new ArrayList<>();
                if (apps != null) {
                    for (ApplicationInfo app : apps) {
                        final Bundle p = new Bundle();
                        p.putString("packageName", app.packageName);
                        p.putString("label",
                                app.loadLabel(mPackageManager).toString());
                        p.putInt("uid", app.uid);
                        p.putInt("flags", app.flags);
                        p.putLong("firstInstallTime", app.firstInstallTime);
                        p.putBoolean("enabled", app.enabled);
                        p.putBoolean("systemApp",
                                (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                        pkgList.add(p);
                    }
                }
                data.putParcelableArrayList("packages", pkgList);
                logCall(uid, "getInstalledPackages", true, startMs,
                        "count=" + (apps != null ? apps.size() : 0));
                return okBundle(data);
            } catch (SecurityException e) {
                logCall(Binder.getCallingUid(), "getInstalledPackages", false,
                        startMs, e.getMessage());
                return deniedBundle(1, e.getMessage());
            } catch (Exception e) {
                Slog.e(TAG, "getInstalledPackages error", e);
                return errorBundle(e.getMessage());
            }
        }

        @Override
        public Bundle getUsageStatsSummary() {
            final long startMs = System.currentTimeMillis();
            try {
                final int uid = enforceCaller();
                final UsageStatsManager usm = getContext().getSystemService(
                        UsageStatsManager.class);
                final int userId = UserHandle.getUserId(uid);
                final long now = System.currentTimeMillis();
                final long weekAgo = now - 7L * 24 * 60 * 60 * 1000;

                final List<UsageStats> stats =
                        usm.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY,
                                weekAgo, now);
                final Bundle data = new Bundle();
                data.putInt("count", stats != null ? stats.size() : 0);

                if (stats != null) {
                    stats.sort((a, b) -> Long.compare(
                            b.getTotalTimeInForeground(),
                            a.getTotalTimeInForeground()));
                    final int topCount = Math.min(stats.size(), 20);
                    final ArrayList<Bundle> statList = new ArrayList<>(topCount);
                    for (int i = 0; i < topCount; i++) {
                        final UsageStats s = stats.get(i);
                        final Bundle entry = new Bundle();
                        entry.putString("packageName", s.getPackageName());
                        entry.putLong("totalTimeForegroundMs",
                                s.getTotalTimeInForeground());
                        entry.putLong("lastTimeUsedMs", s.getLastTimeUsed());
                        entry.putLong("firstTimeStampMs", s.getFirstTimeStamp());
                        entry.putLong("lastTimeStampMs", s.getLastTimeStamp());
                        statList.add(entry);
                    }
                    data.putParcelableArrayList("usageStats", statList);
                }

                logCall(uid, "getUsageStatsSummary", true, startMs,
                        "count=" + (stats != null ? stats.size() : 0));
                return okBundle(data);
            } catch (SecurityException e) {
                logCall(Binder.getCallingUid(), "getUsageStatsSummary", false,
                        startMs, e.getMessage());
                return deniedBundle(1, e.getMessage());
            } catch (Exception e) {
                Slog.e(TAG, "getUsageStatsSummary error", e);
                return errorBundle(e.getMessage());
            }
        }

        @Override
        public Bundle checkAppOps(String packageName) {
            final long startMs = System.currentTimeMillis();
            try {
                final int uid = enforceCaller();
                final AppOpsManager aom = getContext().getSystemService(
                        AppOpsManager.class);
                final Bundle data = new Bundle();
                data.putString("packageName", packageName);

                final int[] opCodes = {
                    AppOpsManager.OP_COARSE_LOCATION,
                    AppOpsManager.OP_FINE_LOCATION,
                    AppOpsManager.OP_CAMERA,
                    AppOpsManager.OP_RECORD_AUDIO,
                    AppOpsManager.OP_READ_CONTACTS,
                    AppOpsManager.OP_READ_CALENDAR,
                    AppOpsManager.OP_SEND_SMS,
                    AppOpsManager.OP_READ_SMS,
                    AppOpsManager.OP_WRITE_EXTERNAL_STORAGE,
                    AppOpsManager.OP_INTERNET,
                    AppOpsManager.OP_WIFI_CHANGE,
                    AppOpsManager.OP_BLUETOOTH_CHANGE,
                    AppOpsManager.OP_POST_NOTIFICATION,
                    AppOpsManager.OP_READ_PHONE_STATE,
                    AppOpsManager.OP_CALL_PHONE,
                    AppOpsManager.OP_SYSTEM_ALERT_WINDOW,
                    AppOpsManager.OP_REQUEST_INSTALL_PACKAGES,
                    AppOpsManager.OP_ACCESS_NOTIFICATIONS,
                };

                final String[] opNames = {
                    "COARSE_LOCATION", "FINE_LOCATION", "CAMERA",
                    "RECORD_AUDIO", "READ_CONTACTS", "READ_CALENDAR",
                    "SEND_SMS", "READ_SMS", "WRITE_EXTERNAL_STORAGE",
                    "INTERNET", "WIFI_CHANGE", "BLUETOOTH_CHANGE",
                    "POST_NOTIFICATION", "READ_PHONE_STATE", "CALL_PHONE",
                    "SYSTEM_ALERT_WINDOW", "REQUEST_INSTALL_PACKAGES",
                    "ACCESS_NOTIFICATIONS",
                };

                final int pkgUid = mPackageManagerInternal.getPackageUid(
                        packageName, 0, UserHandle.getUserId(uid));
                if (pkgUid < 0) {
                    data.putString("error", "Package not found");
                    logCall(uid, "checkAppOps", true, startMs, "package_not_found");
                    return okBundle(data);
                }

                final ArrayList<Bundle> opList = new ArrayList<>(opCodes.length);
                for (int i = 0; i < opCodes.length; i++) {
                    final int mode = aom.checkOpNoThrow(
                            opCodes[i], pkgUid, packageName);
                    final Bundle entry = new Bundle();
                    entry.putString("op", opNames[i]);
                    entry.putInt("code", opCodes[i]);
                    entry.putInt("mode", mode);
                    String modeStr;
                    switch (mode) {
                        case AppOpsManager.MODE_ALLOWED: modeStr = "ALLOWED"; break;
                        case AppOpsManager.MODE_IGNORED: modeStr = "IGNORED"; break;
                        case AppOpsManager.MODE_FOREGROUND: modeStr = "FOREGROUND"; break;
                        default: modeStr = "MODE_" + mode;
                    }
                    entry.putString("modeName", modeStr);
                    opList.add(entry);
                }
                data.putParcelableArrayList("ops", opList);

                logCall(uid, "checkAppOps", true, startMs,
                        "package=" + packageName);
                return okBundle(data);
            } catch (SecurityException e) {
                logCall(Binder.getCallingUid(), "checkAppOps", false, startMs,
                        e.getMessage());
                return deniedBundle(1, e.getMessage());
            } catch (Exception e) {
                Slog.e(TAG, "checkAppOps error", e);
                return errorBundle(e.getMessage());
            }
        }

        @Override
        public Bundle launchPackage(String packageName) {
            final long startMs = System.currentTimeMillis();
            try {
                final int uid = enforceCaller();
                final PackageManager pm = getContext().getPackageManager();
                final Intent launchIntent = pm.getLaunchIntentForPackage(packageName);

                if (launchIntent == null) {
                    logCall(uid, "launchPackage", true, startMs,
                            "no_launch_intent_for=" + packageName);
                    return errorBundle("No launch intent found for " + packageName);
                }

                launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                getContext().startActivityAsUser(launchIntent,
                        UserHandle.of(UserHandle.getUserId(uid)));

                final Bundle data = new Bundle();
                data.putString("packageName", packageName);
                data.putString("launched", "true");

                logCall(uid, "launchPackage", true, startMs,
                        "launched=" + packageName);
                return okBundle(data);
            } catch (SecurityException e) {
                logCall(Binder.getCallingUid(), "launchPackage", false, startMs,
                        e.getMessage());
                return deniedBundle(1, e.getMessage());
            } catch (Exception e) {
                Slog.e(TAG, "launchPackage error", e);
                return errorBundle(e.getMessage());
            }
        }

        @Override
        public Bundle getBatterySummary() {
            final long startMs = System.currentTimeMillis();
            try {
                final int uid = enforceCaller();
                final BatteryManager bm = getContext().getSystemService(
                        BatteryManager.class);
                final Bundle data = new Bundle();

                final int level = bm.getIntProperty(
                        BatteryManager.BATTERY_PROPERTY_CAPACITY);
                final int status = bm.getIntProperty(
                        BatteryManager.BATTERY_PROPERTY_STATUS);
                final int health = bm.getIntProperty(
                        BatteryManager.BATTERY_PROPERTY_HEALTH);
                final int plugged = bm.getIntProperty(
                        BatteryManager.BATTERY_PROPERTY_PLUGGED);
                final int temperature = bm.getIntProperty(
                        BatteryManager.BATTERY_PROPERTY_TEMPERATURE);
                final int voltage = bm.getIntProperty(
                        BatteryManager.BATTERY_PROPERTY_VOLTAGE);
                final int chargeCounter = bm.getIntProperty(
                        BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                final boolean present = bm.getIntProperty(
                        BatteryManager.BATTERY_PROPERTY_PRESENT) == 1;

                data.putInt("level", level);
                data.putInt("status", status);
                data.putInt("health", health);
                data.putInt("plugged", plugged);
                data.putInt("temperature", temperature);
                data.putInt("voltage", voltage);
                data.putInt("chargeCounter", chargeCounter);
                data.putBoolean("present", present);

                String statusStr;
                switch (status) {
                    case BatteryManager.BATTERY_STATUS_CHARGING: statusStr = "CHARGING"; break;
                    case BatteryManager.BATTERY_STATUS_DISCHARGING: statusStr = "DISCHARGING"; break;
                    case BatteryManager.BATTERY_STATUS_FULL: statusStr = "FULL"; break;
                    case BatteryManager.BATTERY_STATUS_NOT_CHARGING: statusStr = "NOT_CHARGING"; break;
                    default: statusStr = "UNKNOWN_" + status;
                }
                data.putString("statusName", statusStr);

                String healthStr;
                switch (health) {
                    case BatteryManager.BATTERY_HEALTH_GOOD: healthStr = "GOOD"; break;
                    case BatteryManager.BATTERY_HEALTH_OVERHEAT: healthStr = "OVERHEAT"; break;
                    case BatteryManager.BATTERY_HEALTH_DEAD: healthStr = "DEAD"; break;
                    case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: healthStr = "OVER_VOLTAGE"; break;
                    case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: healthStr = "FAILURE"; break;
                    default: healthStr = "UNKNOWN_" + health;
                }
                data.putString("healthName", healthStr);

                logCall(uid, "getBatterySummary", true, startMs,
                        "level=" + level + " status=" + statusStr);
                return okBundle(data);
            } catch (SecurityException e) {
                logCall(Binder.getCallingUid(), "getBatterySummary", false, startMs,
                        e.getMessage());
                return deniedBundle(1, e.getMessage());
            } catch (Exception e) {
                Slog.e(TAG, "getBatterySummary error", e);
                return errorBundle(e.getMessage());
            }
        }
    };
}
