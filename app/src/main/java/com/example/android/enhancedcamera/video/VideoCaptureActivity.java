package com.example.android.enhancedcamera.video;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.example.android.enhancedcamera.R;
import com.example.android.enhancedcamera.common.CameraHelper;

import java.io.IOException;

public class VideoCaptureActivity extends Activity
        implements RadioGroup.OnCheckedChangeListener {
    private static final String TAG =
            VideoCaptureActivity.class.getSimpleName();

    private TextureView mPreviewTexture;
    private RadioGroup mCameraSelector;
    private Button mRecordButton;

    /* Front/Back Camera Ids */
    private String mFrontCameraId = null;
    private String mBackCameraId = null;

    private CameraHelper mCameraHelper;
    private CameraDevice mCameraDevice;
    private VideoCaptureCallback mCameraCallback;
    private VideoSaver mVideoSaveTarget;

    //Internal tracker of recording state
    private boolean mIsRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        mCameraHelper = new CameraHelper(this);

        mPreviewTexture = (TextureView) findViewById(R.id.preview);
        mCameraSelector = (RadioGroup) findViewById(R.id.options_camera);
        mRecordButton = (Button) findViewById(R.id.button_record);

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

    //Handle user recording requests
    public void onRecordClick(View v) {
        if (mIsRecording) {
            mRecordButton.setText(R.string.button_record);
            mVideoSaveTarget.stopRecording();
            //Restart preview after recording is over
            startPreview();
            mIsRecording = false;
        } else {
            mRecordButton.setText(R.string.button_stop);
            mVideoSaveTarget.startRecording();
            mIsRecording = true;
        }
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

    //Return the user-selected camera id
    private String getSelectedCameraId() {
        switch (mCameraSelector.getCheckedRadioButtonId()) {
            case R.id.option_front:
                return mFrontCameraId;
            case R.id.option_back:
            default:
                return mBackCameraId;
        }
    }

    //Begin preview, or resume after the latest recording is finished
    private void startPreview() {
        try {
            mVideoSaveTarget.setUpMediaRecorder();
            mCameraCallback.startPreviewSession();
        } catch (CameraAccessException e) {
            Log.w(TAG, "Error starting camera preview", e);
        } catch (IOException e) {
            Log.w(TAG, "Unable to initialize video recorder", e);
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

                        mCameraCallback = new VideoCaptureCallback(mCameraDevice,
                                mPreviewTexture.getSurfaceTexture(),
                                targetPreviewSize,
                                mVideoSaveTarget);

                        startPreview();
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

            //Choose the proper video save size
            StreamConfigurationMap map =
                    mCameraHelper.getConfiguration(cameraId);
            Size[] availableSizes = map.getOutputSizes(MediaRecorder.class);
            Size videoSize = CameraHelper.chooseVideoSize(availableSizes);

            mVideoSaveTarget = new VideoSaver(this, videoSize, orientation);
        } catch (CameraAccessException e) {
            Log.w(TAG, "Unable to get camera sizes.", e);
            return;
        }

        try {
            mCameraHelper.openCamera(cameraId, mStateCallback);
        } catch (CameraAccessException e) {
            Toast.makeText(this, "Unable to access camera",
                    Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Unabled to access camera: "+cameraId, e);
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

        if (mVideoSaveTarget != null) {
            mVideoSaveTarget.close();
            mVideoSaveTarget = null;
        }
    }
}
