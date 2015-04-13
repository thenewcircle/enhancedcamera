package com.example.android.enhancedcamera.image;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.example.android.enhancedcamera.common.PreviewCallback;

import java.util.List;

import static android.hardware.camera2.CaptureResult.*;

/**
 * Implementation of the callback required for single image capture.
 * This callback acts as a simple state machine in order to process the
 * pre-capture states for auto-focus and auto-exposure necessary before
 * an image can be saved.
 */
public class SingleImageCaptureCallback extends PreviewCallback {
    private static final String TAG =
            SingleImageCaptureCallback.class.getSimpleName();

    //Object to differentiate the capture request
    private final Object mCaptureKey = new Object();

    /** Camera state: Showing camera preview. */
    private static final int STATE_IDLE = 0;
    /** Camera state: Waiting for the focus to be locked. */
    private static final int STATE_WAITING_LOCK = 1;
    /** Camera state: Waiting for the exposure to be precapture state. */
    private static final int STATE_WAITING_PRECAPTURE = 2;
    /** Camera state: Waiting for a precapture to complete. */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;
    /** Camera state: Picture was taken. */
    private static final int STATE_PICTURE_TAKEN = 4;

    //Internal state tracker
    private int mState = STATE_IDLE;
    private ImageSaver mCaptureTarget;

    public SingleImageCaptureCallback(CameraDevice device,
                                      SurfaceTexture surface,
                                      Size targetPreviewSize,
                                      ImageSaver saver) {
        super(device, surface, targetPreviewSize);
        mCaptureTarget = saver;
    }

    //Request for a preview that supports image focus
    @Override
    protected CaptureRequest.Builder createPreviewRequestBuilder()
            throws CameraAccessException {

        CaptureRequest.Builder builder = getCameraDevice()
                .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        // Auto focus should be continuous for camera preview.
        builder.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        // Flash is automatically enabled when necessary.
        builder.set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

        return builder;
    }

    /*
     * Overrides base implementation to include the ImageReader as
     * a valid capture surface.
     */
    @Override
    protected List<Surface> getCaptureTargets() {
        List<Surface> baseTargets = super.getCaptureTargets();
        //Include the surface for image saving
        baseTargets.add(mCaptureTarget.getTargetSurface());

        return baseTargets;
    }

    /*
     * State machine that controls image capture sequence:
     * 1. Auto-focus requested (are we locked?)
     * 2. Optional: Auto-exposure pre-capture analysis (do we need flash?)
     * 3. Obtain a single still image
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_IDLE: {
                    // We have nothing to do, camera is in preview mode.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    int afState = result.get(CONTROL_AF_STATE);
                    int afMode = result.get(CONTROL_AF_MODE);
                    //Focus is locked, or auto-focus is not enabled
                    if (CONTROL_AF_MODE_OFF == afMode ||
                            CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_WAITING_NON_PRECAPTURE;
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CONTROL_AE_STATE);
                    if (aeState == null
                            || aeState != CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session,
                                        CaptureRequest request,
                                        CaptureResult partialResult) {
            //Process next state in the capture sequence
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session,
                                       CaptureRequest request,
                                       TotalCaptureResult result) {
            if (mCaptureKey == request.getTag()) {
                Log.v(TAG, "Image Capture Complete…Unlocking Focus");
                unlockFocus();
            } else {
                //Process next state in the capture sequence
                process(result);
            }
        }
    };

    /**
     * Initiate a still image capture.
     */
    public void takePicture() {
        lockFocus();
    }

    /*
     * Run an active auto-focus scan.
     * Status will be reported to the callback.
     */
    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            final CaptureRequest.Builder builder = getPreviewRequestBuilder();
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);

            mState = STATE_WAITING_LOCK;
            getActiveCaptureSession().capture(builder.build(),
                    mCaptureCallback,
                    null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /*
     * Run an auto-exposure pre-capture (which may trigger the flash).
     * Status will be reported to the callback.
     */
    private void runPrecaptureSequence() {
        try {
            // Pre-capture will trigger the flash if the AE is not converged
            final CaptureRequest.Builder builder = getPreviewRequestBuilder();
            builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

            mState = STATE_WAITING_PRECAPTURE;
            getActiveCaptureSession().capture(builder.build(),
                    mCaptureCallback,
                    null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /*
     * Run the image capture sequence after focus/exposure.
     */
    private void captureStillPicture() {
        try {
            // This is the CaptureRequest.Builder we use to take a picture.
            final CaptureRequest.Builder captureBuilder = getCameraDevice()
                    .createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mCaptureTarget.getTargetSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            /*
             * Orient captured image properly with respect to the display.
             * We are fixed in portrait, if the activity is allowed to
             * rotate, that rotation will need to be accounted for as well.
             */
            int orientation = mCaptureTarget.getSensorOrientation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, orientation);

            //Use the tag to find this request later
            captureBuilder.setTag(mCaptureKey);

            Log.v(TAG, "Triggering Capture Session");
            getActiveCaptureSession().capture(captureBuilder.build(),
                    mCaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Unlock the focus.
     * This method should be called when still image capture is finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            final CaptureRequest.Builder builder = getPreviewRequestBuilder();
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            builder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            // After this, the camera will go back to the normal preview.
            mState = STATE_IDLE;
            getActiveCaptureSession().setRepeatingRequest(builder.build(),
                    mCaptureCallback,
                    null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}
