package com.example.android.enhancedcamera.image;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.RadioGroup;

import com.example.android.enhancedcamera.common.CameraHelper;
import com.example.android.enhancedcamera.R;

public class ImageCaptureActivity extends Activity
        implements RadioGroup.OnCheckedChangeListener {
    private static final String TAG =
            ImageCaptureActivity.class.getSimpleName();

    private TextureView mPreviewTexture;
    private RadioGroup mCameraSelector;

    /* Front/Back Camera Ids */
    private String mFrontCameraId = null;
    private String mBackCameraId = null;

    private CameraHelper mCameraHelper;
    private CameraDevice mCameraDevice;
    private SingleImageCaptureCallback mCameraCallback;
    private ImageSaver mImageSaveTarget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);

        mCameraHelper = new CameraHelper(this);

        mCameraSelector = (RadioGroup) findViewById(R.id.options_camera);
        mPreviewTexture = (TextureView) findViewById(R.id.preview);

        if (!discoverCameras()) {
            finish();
            return;
        }

        mCameraSelector.setOnCheckedChangeListener(this);

        //While we are visible, do not go to sleep
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // When the screen is turned off and turned back on,
        // SurfaceTexture is already available. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until
        // the surface is ready in the SurfaceTextureListener).
        if (mPreviewTexture.isAvailable()) {
            openCamera();
        } else {
            mPreviewTexture
                    .setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeCamera();
    }

    //Handle camera selection events
    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        //Restart the camera session with the new selection
        closeCamera();
        openCamera();
    }

    //Handle user capture requests
    public void onCaptureClick(View v) {
         mCameraCallback.takePicture();
    }

    /*
     * Texture creation is asynchronous. We can't handle preview until
     * we have a surface onto which we can render.
     */
    private TextureView.SurfaceTextureListener mSurfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface,
                                              int width, int height) {
            //Camera is now safe to open
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface,
                                                int width, int height) { }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
    };

    /** Methods to connect with the camera devices */

    /*
     * Find the cameras we need, and implicitly check for device support
     * of the proper APIs.
     */
    private boolean discoverCameras() {
        mFrontCameraId = mCameraHelper.getPreferredCameraId(
                CameraCharacteristics.LENS_FACING_FRONT);
        mBackCameraId = mCameraHelper.getPreferredCameraId(
                CameraCharacteristics.LENS_FACING_BACK);

        if (mFrontCameraId == null && mBackCameraId == null) {
            //No cameras accessible
            return false;
        }

        //Select the default camera option
        if (mFrontCameraId == null) {
            findViewById(R.id.option_front).setEnabled(false);
        } else {
            mCameraSelector.check(R.id.option_front);
        }
        if (mBackCameraId == null) {
            findViewById(R.id.option_back).setEnabled(false);
        } else {
            mCameraSelector.check(R.id.option_back);
        }

        return true;
    }

    private String getSelectedCameraId() {
        switch (mCameraSelector.getCheckedRadioButtonId()) {
            case R.id.option_front:
                return mFrontCameraId;
            case R.id.option_back:
            default:
                return mBackCameraId;
        }
    }

    /*
     * Handle state changes regarding the actual camera device
     */
    private final CameraDevice.StateCallback mStateCallback =
            new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            Log.d(TAG, "StateCallback.onOpened");
            // The camera is open, we can start a preview here.
            mCameraDevice = cameraDevice;

            try {
                //Determine the optimal target preview size
                Size targetPreviewSize = mCameraHelper.getTargetPreviewSize(
                        mCameraDevice.getId(),
                        mPreviewTexture.getWidth(),
                        mPreviewTexture.getHeight());

                mCameraCallback = new SingleImageCaptureCallback(mCameraDevice,
                        mPreviewTexture.getSurfaceTexture(),
                        targetPreviewSize,
                        mImageSaveTarget);

                mCameraCallback.startPreviewSession();
            } catch (CameraAccessException e) {
                Log.w(TAG, "Error starting camera preview", e);
            }
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            Log.d(TAG, "StateCallback.onDisconnected");
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            Log.w(TAG, "StateCallback.onError");
            cameraDevice.close();
            mCameraDevice = null;
        }

    };

    /*
     * Initialize a new camera session
     */
    private void openCamera() {
        final String cameraId = getSelectedCameraId();

        //Create the save target
        try {
            //Image orientation
            int orientation = mCameraHelper.getSensorOrientation(cameraId);

            //Choose the largest size for the image save
            StreamConfigurationMap map =
                    mCameraHelper.getConfiguration(cameraId);
            Size[] availableSizes = map.getOutputSizes(ImageFormat.JPEG);
            Size imageSize = availableSizes[0];
            mImageSaveTarget = new ImageSaver(this,
                    imageSize,
                    orientation);
        } catch (CameraAccessException e) {
            Log.w(TAG, "Unable to get camera sizes.", e);
            return;
        }

        try {
            mCameraHelper.openCamera(cameraId, mStateCallback);
        } catch (CameraAccessException e) {
            Log.w(TAG, "Unable to access camera: "+cameraId, e);
        }
    }

    /*
     * Terminate the active camera session
     */
    private void closeCamera() {
        if (mCameraCallback != null) {
            mCameraCallback.cancelActiveCaptureSession();
            mCameraCallback = null;
        }

        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        if (mImageSaveTarget != null) {
            mImageSaveTarget.close();
            mImageSaveTarget = null;
        }
    }
}
