package com.frankenbridge.assistant;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.PersistableBundle;

import com.frankenbridge.broker.api.BrokerActionResult;

final class FlashlightController {
    private static final int SCHEMA_VERSION = 1;

    private final CameraManager mCameraManager;

    FlashlightController(Context context) {
        mCameraManager = context.getSystemService(CameraManager.class);
    }

    BrokerActionResult setEnabled(boolean enabled) {
        if (mCameraManager == null) {
            return result(BrokerActionResult.STATUS_UNAVAILABLE,
                    "Flashlight is not available on this device.");
        }
        try {
            String cameraId = findBackFlashCamera();
            if (cameraId == null) {
                return result(BrokerActionResult.STATUS_UNAVAILABLE,
                        "No back camera with a flashlight is available.");
            }
            mCameraManager.setTorchMode(cameraId, enabled);
            BrokerActionResult result = result(BrokerActionResult.STATUS_OK,
                    enabled ? "Flashlight turned on." : "Flashlight turned off.");
            result.data.putBoolean("enabled", enabled);
            return result;
        } catch (CameraAccessException e) {
            int status = e.getReason() == CameraAccessException.CAMERA_IN_USE
                    || e.getReason() == CameraAccessException.MAX_CAMERAS_IN_USE
                    ? BrokerActionResult.STATUS_BUSY
                    : BrokerActionResult.STATUS_INTERNAL_ERROR;
            return result(status, status == BrokerActionResult.STATUS_BUSY
                    ? "Flashlight is temporarily busy."
                    : "Android could not access the flashlight.");
        } catch (SecurityException e) {
            return result(BrokerActionResult.STATUS_NOT_AUTHORIZED,
                    "The broker does not have camera permission.");
        } catch (RuntimeException e) {
            return result(BrokerActionResult.STATUS_INTERNAL_ERROR,
                    "Flashlight operation failed.");
        }
    }

    private String findBackFlashCamera() throws CameraAccessException {
        String flashFallback = null;
        for (String id : mCameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics =
                    mCameraManager.getCameraCharacteristics(id);
            Boolean flash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (Boolean.TRUE.equals(flash)) {
                if (Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(facing)) {
                    return id;
                }
                if (flashFallback == null) {
                    flashFallback = id;
                }
            }
        }
        return flashFallback;
    }

    static BrokerActionResult result(int status, String message) {
        BrokerActionResult result = new BrokerActionResult();
        result.schemaVersion = SCHEMA_VERSION;
        result.status = status;
        result.message = message;
        result.data = new PersistableBundle();
        return result;
    }
}
