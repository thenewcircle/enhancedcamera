package com.example.android.enhancedcamera.video;

import android.content.Context;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

/**
 * Save destination for still video captures. Videos are stored in the
 * Pictures directory of the device's external storage.
 */
public class VideoSaver {
    private static final String TAG = VideoSaver.class.getSimpleName();

    private Context mContext;
    private File mPicturesDirectory;
    private File mCurrentRecordingFile;
    private int mSensorOrientation;
    private MediaRecorder mMediaRecorder;
    private Size mVideoSize;

    public VideoSaver(Context context, Size videoSize,
                      int sensorOrientation) {
        mContext = context.getApplicationContext();
        mSensorOrientation = sensorOrientation;
        mVideoSize = videoSize;

        //Save all photos in the default public pictures directory
        mPicturesDirectory = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);

        mMediaRecorder = new MediaRecorder();
    }

    public Surface getRecorderSurface() {
        return mMediaRecorder.getSurface();
    }

    public void close() {
        mMediaRecorder.release();
    }

    private File getVideoFile() {
        if (mCurrentRecordingFile == null) {
            String filename = "NewCircle_" + System.currentTimeMillis()
                    + "_Video.mp4";
            mCurrentRecordingFile = new File(mPicturesDirectory, filename);
        }

        return mCurrentRecordingFile;
    }

    public void setUpMediaRecorder() throws IOException {
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(getVideoFile().getAbsolutePath());

        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(),
                mVideoSize.getHeight());

        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        mMediaRecorder.setOrientationHint(mSensorOrientation);
        mMediaRecorder.prepare();
    }

    public void startRecording() {
        Log.d(TAG, "Video Recording Start!");
        mMediaRecorder.start();
    }

    public void stopRecording() {
        Log.d(TAG, "Video Recording Stop!");
        mMediaRecorder.stop();
        mMediaRecorder.reset();

        //Let the framework know about the file
        MediaScannerConnection.scanFile(mContext,
                new String[]{getVideoFile().getAbsolutePath()},
                new String[]{"video/mp4"},
                new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                        Log.i(TAG, "Scanned " + path + ":");
                        Log.i(TAG, "-> uri=" + uri);
                        Toast.makeText(mContext, "Video Record Complete",
                                Toast.LENGTH_SHORT).show();
                    }
                });

        //Clear out the media file reference, we're done with it.
        mCurrentRecordingFile = null;
    }
}
