package com.android.server.prodx;

import android.app.prodx.ProdXExecutionContext;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;

public class ProdXContextBuilder {
    private final Context mContext;
    private final PackageManager mPm;

    public ProdXContextBuilder(Context context) {
        mContext = context;
        mPm = context.getPackageManager();
    }

    public ProdXExecutionContext derive(int callingUid, String purpose) {
        int userId = UserHandle.getUserId(callingUid);
        String[] packages = mPm != null ? mPm.getPackagesForUid(callingUid) : null;
        String packageName = (packages != null && packages.length > 0) ? packages[0] : "unknown";
        return new ProdXExecutionContext(callingUid, userId, packageName, purpose);
    }
}
