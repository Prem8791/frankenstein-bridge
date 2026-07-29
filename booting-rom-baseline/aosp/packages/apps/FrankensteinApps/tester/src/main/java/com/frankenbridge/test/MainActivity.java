package com.frankenbridge.test;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.frankenbridge.broker.api.BrokerActionRequest;
import com.frankenbridge.broker.api.BrokerActionResult;
import com.frankenbridge.broker.api.IBridgeBroker;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final ComponentName BROKER_COMPONENT = new ComponentName(
            "com.frankenbridge.assistant",
            "com.frankenbridge.assistant.BridgeBrokerService");

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private IBridgeBroker mBroker;
    private Button mProbeButton;
    private Button mFlashlightOnButton;
    private Button mFlashlightOffButton;
    private TextView mResult;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBroker = IBridgeBroker.Stub.asInterface(service);
            setActionButtonsEnabled(true);
            mResult.setText("Broker connected. Tap the button to probe the ROM bridge.");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBroker = null;
            setActionButtonsEnabled(false);
            mResult.setText("Broker disconnected.");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("Frankenstein Bridge Test");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mProbeButton = new Button(this);
        mProbeButton.setText("Probe ROM Bridge");
        mProbeButton.setEnabled(false);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = padding;
        root.addView(mProbeButton, buttonParams);

        mFlashlightOnButton = new Button(this);
        mFlashlightOnButton.setText("Flashlight On");
        mFlashlightOnButton.setEnabled(false);
        root.addView(mFlashlightOnButton, buttonParams);

        mFlashlightOffButton = new Button(this);
        mFlashlightOffButton.setText("Flashlight Off");
        mFlashlightOffButton.setEnabled(false);
        root.addView(mFlashlightOffButton, buttonParams);

        mResult = new TextView(this);
        mResult.setText("Connecting to broker…");
        mResult.setTextSize(16);
        mResult.setTextIsSelectable(true);
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resultParams.topMargin = padding;
        root.addView(mResult, resultParams);

        mProbeButton.setOnClickListener(v -> probeBridge());
        mFlashlightOnButton.setOnClickListener(v -> setFlashlight(true));
        mFlashlightOffButton.setOnClickListener(v -> setFlashlight(false));
        setContentView(root);

        Intent intent = new Intent();
        intent.setComponent(BROKER_COMPONENT);
        if (!bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
            mResult.setText("Could not bind to the broker app.");
        }
    }

    private void probeBridge() {
        IBridgeBroker broker = mBroker;
        if (broker == null) {
            mResult.setText("Broker is not connected.");
            return;
        }
        setActionButtonsEnabled(false);
        mResult.setText("Probing…");
        mExecutor.execute(() -> {
            String result;
            try {
                result = broker.probeBridge();
            } catch (RemoteException | RuntimeException e) {
                result = "FAIL: broker call failed: " + e;
            }
            String displayed = result;
            runOnUiThread(() -> {
                mResult.setText(displayed);
                setActionButtonsEnabled(mBroker != null);
            });
        });
    }

    private void setFlashlight(boolean enabled) {
        IBridgeBroker broker = mBroker;
        if (broker == null) {
            mResult.setText("Broker is not connected.");
            return;
        }
        setActionButtonsEnabled(false);
        mResult.setText(enabled ? "Turning flashlight on…" : "Turning flashlight off…");
        mExecutor.execute(() -> {
            String displayed;
            try {
                PersistableBundle arguments = new PersistableBundle();
                arguments.putBoolean("enabled", enabled);
                BrokerActionRequest request = new BrokerActionRequest();
                request.schemaVersion = 1;
                request.actionId = "device.flashlight.set";
                request.arguments = arguments;
                BrokerActionResult result = broker.executeAction(request);
                displayed = result != null && result.status == BrokerActionResult.STATUS_OK
                        ? "PASS: " + result.message
                        : "FAIL: status=" + (result == null ? "null" : result.status)
                                + " message=" + (result == null ? "No result" : result.message);
            } catch (RemoteException | RuntimeException e) {
                displayed = "FAIL: broker call failed: " + e;
            }
            String finalDisplayed = displayed;
            runOnUiThread(() -> {
                mResult.setText(finalDisplayed);
                setActionButtonsEnabled(mBroker != null);
            });
        });
    }

    private void setActionButtonsEnabled(boolean enabled) {
        mProbeButton.setEnabled(enabled);
        mFlashlightOnButton.setEnabled(enabled);
        mFlashlightOffButton.setEnabled(enabled);
    }

    @Override
    protected void onDestroy() {
        unbindService(mConnection);
        mExecutor.shutdownNow();
        super.onDestroy();
    }
}
