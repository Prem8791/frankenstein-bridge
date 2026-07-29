package com.frankenbridge.reachability;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONObject;

import java.util.Map;

/** Test-only one-shot entry point. Emits reachability state, never protected values. */
public final class ProbeActivity extends Activity {
    public static final String TAG = "FrankensteinProbe";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String requestId = getIntent().getStringExtra("request_id");
        String kind = getIntent().getStringExtra("kind");
        String target = getIntent().getStringExtra("target");
        String result = "NOT_APPLICABLE";
        try {
            Map<String, String> measured;
            if ("binder".equals(kind)) {
                measured = ReachabilityProbe.probeBinderNames(new String[] {target});
                result = measured.get(target);
            } else if ("path".equals(kind) || "node".equals(kind)
                    || "socket".equals(kind)) {
                measured = ReachabilityProbe.probePaths(new String[] {target});
                result = measured.get(target);
            }
        } catch (RuntimeException failure) {
            result = "PARTIAL";
        }
        try {
            JSONObject output = new JSONObject();
            output.put("request_id", requestId == null ? "" : requestId);
            output.put("result", result);
            Log.i(TAG, output.toString());
        } catch (Exception impossible) {
            Log.e(TAG, "result encoding failed");
        }
        finish();
    }
}
