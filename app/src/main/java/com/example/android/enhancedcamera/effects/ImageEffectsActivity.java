package com.example.android.enhancedcamera.effects;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.example.android.enhancedcamera.R;
import com.example.android.enhancedcamera.common.CameraHelper;
import com.example.android.enhancedcamera.common.PreviewCallback;

import java.util.Arrays;

public class ImageEffectsActivity extends Activity
        implements AdapterView.OnItemSelectedListener {
    private static final String TAG =
            ImageEffectsActivity.class.getSimpleName();

    private TextureView mPreviewTexture;

    private int[] mSupportedEffects;
    private String[] mSupportedEffectNames;

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

        Spinner effectSelector = (Spinner) findViewById(R.id.selector_effects);
        mPreviewTexture = (TextureView) findViewById(R.id.preview);

        if (!discoverCamera()) {
            finish();
            return;
        }

        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(
                this, android.R.layout.simple_spinner_item, mSupportedEffectNames);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        effectSelector.setAdapter(adapter);

        effectSelector.setOnItemSelectedListener(this);

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
            mSupportedEffects = mCameraHelper.getSupportedEffects(mBackCameraId);
            Arrays.sort(mSupportedEffects);
            mSupportedEffectNames = new String[mSupportedEffects.length];
            for (int i=0; i < mSupportedEffects.length; i++) {
                mSupportedEffectNames[i] = getEffectName(mSupportedEffects[i]);
            }
        } catch (CameraAccessException e) {
            Log.w(TAG, "Unable to access camera effects.", e);
            return false;
        }

        return true;
    }

    private String getEffectName(int effect) {
        String[] names = getResources().getStringArray(R.array.effects);
        //Effect id is the index into this array
        return names[effect];
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view,
                               int position, long id) {
        int effect = mSupportedEffects[position];
        if (mCameraCallback == null) return;

        try {
            mCameraCallback.restartPreview(effect);
        } catch (CameraAccessException e) {
            Log.w(TAG, "Unable to set effect value.", e);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) { }

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
