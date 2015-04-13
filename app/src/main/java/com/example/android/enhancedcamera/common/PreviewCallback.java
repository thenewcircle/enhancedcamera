package com.example.android.enhancedcamera.common;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.util.ArrayList;
import java.util.List;

/**
 * Base implementation of camera preview functions, common to all
 * camera application operating modes.
 */
public class PreviewCallback {
    private static final String TAG =
            PreviewCallback.class.getSimpleName();

    private Size mTargetPreviewSize;
    private CaptureRequest.Builder mPreviewRequestBuilder;

    private final CameraDevice mCameraDevice;
    private final SurfaceTexture mPreviewSurface;
    private CameraCaptureSession mActiveCaptureSession;

    public PreviewCallback(CameraDevice device,
                           SurfaceTexture surface,
                           Size targetPreviewSize) {
        mCameraDevice = device;

        mPreviewSurface = surface;
        mTargetPreviewSize = targetPreviewSize;
    }

    //Request for a basic preview
    protected CaptureRequest.Builder createPreviewRequestBuilder()
            throws CameraAccessException {
        return getCameraDevice()
                .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
    }

    //Return all target surfaces for camera frames
    protected List<Surface> getCaptureTargets() {
        List<Surface> baseTargets = new ArrayList<Surface>();
        baseTargets.add(new Surface(mPreviewSurface));

        return baseTargets;
    }

    /*
     * The same builder is used for all repeated requests, and some
     * state is shared between them. The object is lazily created
     * the first time it is needed.
     */
    protected final CaptureRequest.Builder getPreviewRequestBuilder()
            throws CameraAccessException {
        if (mPreviewRequestBuilder == null) {
            mPreviewRequestBuilder = createPreviewRequestBuilder();
        }

        return mPreviewRequestBuilder;
    }

    protected final CameraDevice getCameraDevice() {
        return mCameraDevice;
    }

    private void setActiveCaptureSession(CameraCaptureSession session) {
        mActiveCaptureSession = session;
    }

    protected final CameraCaptureSession getActiveCaptureSession() {
        return mActiveCaptureSession;
    }

    public void cancelActiveCaptureSession() {
        if (mActiveCaptureSession != null) {
            mActiveCaptureSession.close();
            mActiveCaptureSession = null;
        }
    }

    /*
     * Begin streaming preview data.
     */
    public void startPreviewSession()
            throws CameraAccessException {
        //Preview request contains state we need to reset
        // when we start a new preview session.
        mPreviewRequestBuilder = null;

        // Configure the size of default buffer match the camera preview.
        mPreviewSurface.setDefaultBufferSize(mTargetPreviewSize.getWidth(),
                mTargetPreviewSize.getHeight());

        // This is the output Surface we need to start preview.
        Surface surface = new Surface(mPreviewSurface);

        // We set up a CaptureRequest.Builder with the output Surface.
        final CaptureRequest.Builder builder = getPreviewRequestBuilder();
        builder.addTarget(surface);

        // Here, we create a CameraCaptureSession for camera preview.
        getCameraDevice().createCaptureSession(getCaptureTargets(),
                new CameraCaptureSession.StateCallback() {

                    @Override
                    public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                        // The camera is already closed
                        if (null == getCameraDevice()) {
                            return;
                        }

                        // When the session is ready, we start displaying the preview.
                        setActiveCaptureSession(cameraCaptureSession);
                        try {
                            // Finally, we start displaying the camera preview.
                            CaptureRequest previewRequest = builder.build();
                            getActiveCaptureSession().setRepeatingRequest(previewRequest,
                                    null, null);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                        Log.w(TAG, "Failed to Create Camera Preview");
                    }
                }, null
        );
    }
}
