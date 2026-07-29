package com.android.server.frankenstein;

import android.os.UserHandle;

import java.util.Arrays;

final class CallerContext {
    final int uid;
    final int pid;
    final int userId;
    final String[] packages;

    CallerContext(int uid, int pid, String[] packages) {
        this.uid = uid;
        this.pid = pid;
        this.userId = UserHandle.getUserId(uid);
        this.packages = packages.clone();
        Arrays.sort(this.packages);
    }

    boolean ownsPackage(String packageName) {
        return Arrays.binarySearch(packages, packageName) >= 0;
    }
}
