package com.frankenbridge.assistant;

import android.os.PersistableBundle;

import com.frankenbridge.broker.api.BrokerActionRequest;
import com.frankenbridge.broker.api.BrokerActionResult;

import java.util.Set;

final class BrokerActionDispatcher {
    static final int SCHEMA_VERSION = 1;
    static final String ACTION_FLASHLIGHT_SET = "device.flashlight.set";
    static final String ACTION_WIFI_SET = "device.wifi.set";
    static final String ACTION_BLUETOOTH_SET = "device.bluetooth.set";
    static final String ACTION_BRIGHTNESS_SET = "device.brightness.set";
    static final String ACTION_NAVIGATION_HOME = "navigation.home";
    static final String ACTION_NAVIGATION_BACK = "navigation.back";

    private final FlashlightController mFlashlightController;
    private final RestrictedDeviceController mRestrictedDeviceController;

    BrokerActionDispatcher(
            FlashlightController flashlightController,
            RestrictedDeviceController restrictedDeviceController) {
        mFlashlightController = flashlightController;
        mRestrictedDeviceController = restrictedDeviceController;
    }

    BrokerActionResult execute(BrokerActionRequest request) {
        if (request == null) {
            return invalid("Action request is missing.");
        }
        if (request.schemaVersion != SCHEMA_VERSION) {
            return invalid("Unsupported action schema version.");
        }
        PersistableBundle arguments = request.arguments;
        switch (request.actionId) {
            case ACTION_FLASHLIGHT_SET:
                return executeBooleanAction(arguments, "Flashlight",
                        mFlashlightController::setEnabled);
            case ACTION_WIFI_SET:
                return executeBooleanAction(arguments, "Wi-Fi",
                        mRestrictedDeviceController::setWifiEnabled);
            case ACTION_BLUETOOTH_SET:
                return executeBooleanAction(arguments, "Bluetooth",
                        mRestrictedDeviceController::setBluetoothEnabled);
            case ACTION_BRIGHTNESS_SET:
                return executeBrightness(arguments);
            case ACTION_NAVIGATION_HOME:
                return requireNoArguments(arguments, mRestrictedDeviceController::goHome);
            case ACTION_NAVIGATION_BACK:
                return requireNoArguments(arguments, mRestrictedDeviceController::goBack);
            default:
                return FlashlightController.result(BrokerActionResult.STATUS_UNSUPPORTED,
                        "Unsupported broker action.");
        }
    }

    private BrokerActionResult executeBooleanAction(
            PersistableBundle arguments,
            String label,
            BooleanAction action) {
        if (arguments == null || !arguments.containsKey("enabled")) {
            return invalid(label + " action requires Boolean argument 'enabled'.");
        }
        Set<String> keys = arguments.keySet();
        if (keys.size() != 1 || !keys.contains("enabled")
                || !(arguments.get("enabled") instanceof Boolean)) {
            return invalid(label + " action accepts only Boolean argument 'enabled'.");
        }
        return action.execute(arguments.getBoolean("enabled"));
    }

    private BrokerActionResult executeBrightness(PersistableBundle arguments) {
        if (arguments == null || arguments.keySet().size() != 1
                || !arguments.containsKey("level")
                || !(arguments.get("level") instanceof Integer)) {
            return invalid("Brightness action accepts only Integer argument 'level'.");
        }
        int level = arguments.getInt("level");
        if (level < 0 || level > 100) {
            return invalid("Brightness level must be between 0 and 100.");
        }
        return mRestrictedDeviceController.setBrightness(level);
    }

    private BrokerActionResult requireNoArguments(
            PersistableBundle arguments,
            NoArgumentAction action) {
        if (arguments != null && !arguments.isEmpty()) {
            return invalid("Navigation action does not accept arguments.");
        }
        return action.execute();
    }

    private static BrokerActionResult invalid(String message) {
        return FlashlightController.result(BrokerActionResult.STATUS_INVALID_REQUEST, message);
    }

    private interface BooleanAction {
        BrokerActionResult execute(boolean enabled);
    }

    private interface NoArgumentAction {
        BrokerActionResult execute();
    }
}
