package com.frankenbridge.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * User-consent surface for permissions owned by the replaceable broker.
 *
 * <p>The activity is intentionally separate from the Binder service. ProdX may launch it with
 * {@link #ACTION_SETUP}; Android itself remains the authority that grants each permission.</p>
 */
public final class BrokerSetupActivity extends Activity {
    public static final String ACTION_SETUP = "com.frankenbridge.assistant.SETUP";
    private static final int REQUEST_RUNTIME_PERMISSIONS = 100;

    private TextView mStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Broker Permissions");
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RUNTIME_PERMISSIONS) {
            refreshStatus();
        }
    }

    private ScrollView buildContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(24), dp(24), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("Frankenstein Broker setup");
        title.setTextSize(24);
        title.setTextColor(Color.BLACK);
        content.addView(title);

        TextView explanation = new TextView(this);
        explanation.setText(
                "The broker performs protected Android actions for ProdX. "
                        + "Grant these permissions once; normal app updates keep them.");
        explanation.setTextSize(16);
        explanation.setPadding(0, dp(12), 0, dp(16));
        content.addView(explanation);

        mStatus = new TextView(this);
        mStatus.setTextSize(16);
        mStatus.setPadding(0, 0, 0, dp(16));
        content.addView(mStatus);

        content.addView(button("Grant Camera and Bluetooth", view -> requestRuntimePermissions()));
        content.addView(button("Allow brightness control", view -> openWriteSettings()));
        content.addView(button("Open ProdX accessibility settings",
                view -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));

        scroll.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
        scroll.post(scroll::requestApplyInsets);
        return scroll;
    }

    private Button button(String label, android.view.View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        button.setLayoutParams(params);
        return button;
    }

    private void requestRuntimePermissions() {
        List<String> missing = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.CAMERA);
        }
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (missing.isEmpty()) {
            refreshStatus();
            return;
        }
        requestPermissions(missing.toArray(new String[0]), REQUEST_RUNTIME_PERMISSIONS);
    }

    private void openWriteSettings() {
        startActivity(new Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private void refreshStatus() {
        if (mStatus == null) {
            return;
        }
        boolean camera = checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        boolean bluetooth = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
        boolean brightness = Settings.System.canWrite(this);
        mStatus.setText(
                "Camera: " + status(camera)
                        + "\nBluetooth: " + status(bluetooth)
                        + "\nBrightness control: " + status(brightness)
                        + "\n\nBack and automatic typing are granted separately through "
                        + "the ProdX accessibility service.");
    }

    private static String status(boolean granted) {
        return granted ? "Ready" : "Permission needed";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
