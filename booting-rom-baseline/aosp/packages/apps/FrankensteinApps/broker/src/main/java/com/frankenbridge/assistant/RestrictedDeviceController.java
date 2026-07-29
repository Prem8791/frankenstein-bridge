package com.frankenbridge.assistant;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.InputEvent;
import android.view.KeyEvent;

import com.frankenbridge.broker.api.BrokerActionResult;

import java.lang.reflect.Method;

final class RestrictedDeviceController {
    private final Context mContext;

    RestrictedDeviceController(Context context) {
        mContext = context.getApplicationContext();
    }

    BrokerActionResult setWifiEnabled(boolean enabled) {
        try {
            WifiManager manager = mContext.getSystemService(WifiManager.class);
            if (manager == null) {
                return result(BrokerActionResult.STATUS_UNAVAILABLE, "Wi-Fi is unavailable.");
            }
            if (!manager.setWifiEnabled(enabled)) {
                return result(BrokerActionResult.STATUS_INTERNAL_ERROR,
                        "Android rejected the Wi-Fi state change.");
            }
            return result(BrokerActionResult.STATUS_OK,
                    enabled ? "Wi-Fi turned on." : "Wi-Fi turned off.");
        } catch (SecurityException e) {
            return result(BrokerActionResult.STATUS_NOT_AUTHORIZED,
                    "The broker is not authorized to change Wi-Fi.");
        } catch (RuntimeException e) {
            return result(BrokerActionResult.STATUS_INTERNAL_ERROR, "Wi-Fi control failed.");
        }
    }

    BrokerActionResult setBluetoothEnabled(boolean enabled) {
        try {
            BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null) {
                return result(BrokerActionResult.STATUS_UNAVAILABLE, "Bluetooth is unavailable.");
            }
            boolean accepted = enabled ? adapter.enable() : adapter.disable();
            if (!accepted) {
                return result(BrokerActionResult.STATUS_INTERNAL_ERROR,
                        "Android rejected the Bluetooth state change.");
            }
            return result(BrokerActionResult.STATUS_OK,
                    enabled ? "Bluetooth turned on." : "Bluetooth turned off.");
        } catch (SecurityException e) {
            return result(BrokerActionResult.STATUS_NOT_AUTHORIZED,
                    "The broker is not authorized to change Bluetooth.");
        } catch (RuntimeException e) {
            return result(BrokerActionResult.STATUS_INTERNAL_ERROR, "Bluetooth control failed.");
        }
    }

    BrokerActionResult setBrightness(int percent) {
        try {
            int value = Math.round(percent * 255f / 100f);
            boolean written = Settings.System.putInt(
                    mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS,
                    value);
            return written
                    ? result(BrokerActionResult.STATUS_OK,
                            "Brightness set to " + percent + " percent.")
                    : result(BrokerActionResult.STATUS_INTERNAL_ERROR,
                            "Android rejected the brightness change.");
        } catch (SecurityException e) {
            return result(BrokerActionResult.STATUS_NOT_AUTHORIZED,
                    "The broker is not authorized to change brightness.");
        } catch (RuntimeException e) {
            return result(BrokerActionResult.STATUS_INTERNAL_ERROR, "Brightness control failed.");
        }
    }

    BrokerActionResult goHome() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            return result(BrokerActionResult.STATUS_OK, "Going home.");
        } catch (RuntimeException e) {
            return result(BrokerActionResult.STATUS_INTERNAL_ERROR,
                    "Could not open the home screen.");
        }
    }

    BrokerActionResult goBack() {
        try {
            long now = SystemClock.uptimeMillis();
            KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0);
            KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0);
            if (!injectInputEvent(down) || !injectInputEvent(up)) {
                return result(BrokerActionResult.STATUS_INTERNAL_ERROR,
                        "Android rejected the Back key event.");
            }
            return result(BrokerActionResult.STATUS_OK, "Going back.");
        } catch (ReflectiveOperationException | RuntimeException e) {
            return result(BrokerActionResult.STATUS_INTERNAL_ERROR,
                    "Could not perform global Back navigation.");
        }
    }

    private static boolean injectInputEvent(InputEvent event)
            throws ReflectiveOperationException {
        Class<?> managerClass = Class.forName("android.hardware.input.InputManagerGlobal");
        Method getInstance = managerClass.getDeclaredMethod("getInstance");
        Object manager = getInstance.invoke(null);
        Method inject = managerClass.getDeclaredMethod(
                "injectInputEvent", InputEvent.class, int.class);
        return (Boolean) inject.invoke(
                manager, event, 2 /* INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH */);
    }

    private static BrokerActionResult result(int status, String message) {
        return FlashlightController.result(status, message);
    }
}
