package com.example.android.enhancedcamera.video;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.util.Size;
import android.view.Surface;

import com.example.android.enhancedcamera.common.PreviewCallback;

import java.io.IOException;
import java.util.List;

/**
 * Implementation of the callback needed for basic video capture.
 * Video is just an extension of basic preview where the frames are
 * delivered to the recorder simultaneously. Thus, this class is
 * fairly small.
 */
public class VideoCaptureCallback extends PreviewCallback {
    private static final String TAG =
            VideoCaptureCallback.class.getSimpleName();

    private VideoSaver mVideoSaver;

    public VideoCaptureCallback(CameraDevice device,
                                SurfaceTexture surface,
                                Size targetPreviewSize) {
        super(device, surface, targetPreviewSize);
    }

    //Request for a preview that supports video
    @Override
    protected CaptureRequest.Builder createPreviewRequestBuilder()
            throws CameraAccessException {

        CaptureRequest.Builder builder = getCameraDevice()
                .createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        // Use automatic settings for video record
        builder.set(CaptureRequest.CONTROL_MODE,
                CameraMetadata.CONTROL_MODE_AUTO);

        //Add the video recorder surface target
        builder.addTarget(mVideoSaver.getRecorderSurface());

        return builder;
    }

    public void setCaptureTarget(VideoSaver captureTarget) {
        if (mVideoSaver != null) {
            mVideoSaver.close();
        }
        mVideoSaver = captureTarget;
    }

    @Override
    protected List<Surface> getCaptureTargets() {
        List<Surface> baseTargets = super.getCaptureTargets();
        baseTargets.add(mVideoSaver.getRecorderSurface());

        return baseTargets;
    }

    public void setUpMediaRecorder() throws IOException {
        mVideoSaver.setUpMediaRecorder();
    }

    public void startRecording() {
        mVideoSaver.startRecording();
    }

    public void stopRecording() {
        mVideoSaver.stopRecording();
    }
}
