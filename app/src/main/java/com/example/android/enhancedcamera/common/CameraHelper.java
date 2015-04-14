package com.example.android.enhancedcamera.common;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Log;
import android.util.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Helper to manage available cameras and their parameters
 */
public class CameraHelper {
    private static final String TAG = CameraHelper.class.getSimpleName();

    private CameraManager mCameraManager;

    public CameraHelper(Context context) {
        mCameraManager = (CameraManager) context
                .getSystemService(Context.CAMERA_SERVICE);
    }

    /**
     * Open the selected camera device on the main thread
     */
    public void openCamera(String cameraId,
                           CameraDevice.StateCallback stateCallback)
            throws CameraAccessException {
        mCameraManager.openCamera(cameraId, stateCallback, null);
    }

    /**
     * Select the first-detected camera device matching the
     * requested lens orientation.
     */
    public String getPreferredCameraId(int cameraType) {
        if (cameraType != CameraCharacteristics.LENS_FACING_FRONT
                && cameraType != CameraCharacteristics.LENS_FACING_BACK) {
            throw new IllegalArgumentException("Invalid camera type");
        }

        try {
            for (String cameraId : mCameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics =
                        mCameraManager.getCameraCharacteristics(cameraId);

                if (characteristics.get(CameraCharacteristics.LENS_FACING)
                        == cameraType) {
                    Log.d(TAG, "Found camera: " + cameraId);
                    return cameraId;
                }
            }

            //No matching camera found
            return null;
        } catch (CameraAccessException e) {
            Log.w(TAG, "Unable to access camera devices.", e);
            return null;
        } catch (NullPointerException e) {
            //This will happen if the device does not support Camera2
            Log.w(TAG, "No Support for Camera2 APIs.", e);
            return null;
        }
    }

    /** Camera Parameters Wrapper Methods */

    public StreamConfigurationMap getConfiguration(String cameraId)
            throws CameraAccessException {
        CameraCharacteristics characteristics =
                mCameraManager.getCameraCharacteristics(cameraId);

        return characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
    }

    public Size getTargetPreviewSize(String cameraId,
                                     int width, int height)
            throws CameraAccessException {
        StreamConfigurationMap map = getConfiguration(cameraId);

        //Pick the minimum size preview to match the view size
        return chooseOptimalSize(
                map.getOutputSizes(SurfaceTexture.class),
                width, height);
    }

    public int getSensorOrientation(String cameraId)
            throws CameraAccessException {
        CameraCharacteristics characteristics =
                mCameraManager.getCameraCharacteristics(cameraId);

        //Get the orientation of the camera sensor
        return characteristics.get(
                CameraCharacteristics.SENSOR_ORIENTATION);
    }

    public int[] getSupportedEffects(String cameraId)
            throws CameraAccessException {
        CameraCharacteristics characteristics =
                mCameraManager.getCameraCharacteristics(cameraId);

        return characteristics.get(
                CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS);
    }

    /**
     * Comparator to organize supported resolutions by overall size.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * Choose the smallest size the will satisfy the minimum
     * requested dimensions.
     */
    public static Size chooseOptimalSize(Size[] choices,
                                         int width,
                                         int height) {

        List<Size> bigEnough = new ArrayList<Size>();
        for (Size option : choices) {
            if (option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * Validate if a size is less than 1080p. Some devices
     * can't handle recording above that resolution.
     */
    public static boolean verifyVideoSize(Size option) {
        return (option.getWidth() <= 1080);
    }

}
