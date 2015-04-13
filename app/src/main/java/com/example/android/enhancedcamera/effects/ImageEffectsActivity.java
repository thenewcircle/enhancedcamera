package com.example.android.enhancedcamera.effects;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.example.android.enhancedcamera.R;
import com.example.android.enhancedcamera.common.CameraHelper;
import com.example.android.enhancedcamera.common.PreviewCallback;

import java.util.Arrays;

public class ImageEffectsActivity extends Activity
        implements RadioGroup.OnCheckedChangeListener {
    private static final String TAG =
            ImageEffectsActivity.class.getSimpleName();

    private RadioGroup mEffectSelector;
    private TextureView mPreviewTexture;

    /* Selected Camera Id */
    private String mBackCameraId = null;

    private CameraHelper mCameraHelper;
    private CameraDevice mCameraDevice;
    private PreviewCallback mCameraCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_effect);

        mCameraHelper = new CameraHelper(this);

        mEffectSelector = (RadioGroup) findViewById(R.id.options_effects);
        mPreviewTexture = (TextureView) findViewById(R.id.preview);

        if (!discoverCamera()) {
            finish();
            return;
        }

        mEffectSelector.setOnCheckedChangeListener(this);

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
    private boolean discoverCamera() {
        mBackCameraId = mCameraHelper.getPreferredCameraId(
                CameraCharacteristics.LENS_FACING_BACK);

        if (mBackCameraId == null) {
            //No camera accessible
            return false;
        }

        try {
            int[] effects = mCameraHelper.getSupportedEffects(mBackCameraId);
            Arrays.sort(effects);
            for (int effect : effects) {
                RadioButton item = (RadioButton) getLayoutInflater().inflate(
                        R.layout.item_effect, mEffectSelector, false);
                setEffect(item, effect);
                mEffectSelector.addView(item);
            }
        } catch (CameraAccessException e) {
            Log.w(TAG, "Unable to access camera effects.", e);
            return false;
        }

        return true;
    }

    private void setEffect(RadioButton button, int effect) {
        button.setId(effect);
        switch (effect) {
            case CaptureRequest.CONTROL_EFFECT_MODE_MONO:
                button.setText(R.string.effect_mono);
                break;
            case CaptureRequest.CONTROL_EFFECT_MODE_NEGATIVE:
                button.setText(R.string.effect_negative);
                break;
            case CaptureRequest.CONTROL_EFFECT_MODE_SOLARIZE:
                button.setText(R.string.effect_solarize);
                break;
            case CaptureRequest.CONTROL_EFFECT_MODE_SEPIA:
                button.setText(R.string.effect_sepia);
                break;
            case CaptureRequest.CONTROL_EFFECT_MODE_POSTERIZE:
                button.setText(R.string.effect_posterize);
                break;
            case CaptureRequest.CONTROL_EFFECT_MODE_WHITEBOARD:
                button.setText(R.string.effect_whiteboard);
                break;
            case CaptureRequest.CONTROL_EFFECT_MODE_BLACKBOARD:
                button.setText(R.string.effect_blackboard);
                break;
            case CaptureRequest.CONTROL_EFFECT_MODE_AQUA:
                button.setText(R.string.effect_aqua);
                break;
            case CaptureRequest.CONTROL_EFFECT_MODE_OFF:
            default:
                button.setText(R.string.effect_off);
                button.setChecked(true);
                break;
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        try {
            mCameraCallback.restartPreview(checkedId);
        } catch (CameraAccessException e) {
            Log.w(TAG, "Unable to set effect value.", e);
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

                        mCameraCallback = new PreviewCallback(mCameraDevice,
                                mPreviewTexture.getSurfaceTexture(),
                                targetPreviewSize);

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
        try {
            mCameraHelper.openCamera(mBackCameraId, mStateCallback);
        } catch (CameraAccessException e) {
            Log.w(TAG, "Unable to access camera: "+mBackCameraId, e);
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
    }
}
