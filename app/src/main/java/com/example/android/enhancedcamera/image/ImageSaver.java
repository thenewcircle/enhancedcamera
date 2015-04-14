package com.example.android.enhancedcamera.image;

import android.content.Context;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Save destination for still image captures. Images are stored in the
 * Pictures directory of the device's external storage.
 */
public class ImageSaver implements ImageReader.OnImageAvailableListener {
    private static final String TAG = ImageSaver.class.getSimpleName();

    private ImageReader mImageReader;
    private File mPicturesDirectory;

    private Context mContext;

    private int mSensorOrientation;

    public ImageSaver(Context context, Size imageSize, int sensorOrientation) {
        mContext = context.getApplicationContext();
        mSensorOrientation = sensorOrientation;

        mImageReader = ImageReader.newInstance(
                imageSize.getWidth(),
                imageSize.getHeight(),
                ImageFormat.JPEG, /* ImageFormat */
                2 /* MaxImages */ );
        mImageReader.setOnImageAvailableListener(this, null);

        //Save all photos in the default public pictures directory
        mPicturesDirectory = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
    }

    public Surface getTargetSurface() {
        return mImageReader.getSurface();
    }

    public void close() {
        mImageReader.close();
    }

    public int getSensorOrientation() {
        return mSensorOrientation;
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        //Save the next available image
        saveImage(reader.acquireNextImage());
        Log.d(TAG, "Image Save Complete!");
    }

    private File getImageFile() {
        String filename = "NewCircle_"
                + System.currentTimeMillis() + ".jpg";
        return new File(mPicturesDirectory, filename);
    }

    private void saveImage(Image image) {
        File dest = getImageFile();

        //Write the file to the external pictures location
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(dest);
            output.write(bytes);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            image.close();
            if (null != output) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        //Tell the framework, so the image will be in the gallery
        MediaScannerConnection.scanFile(mContext,
                new String[]{dest.getAbsolutePath()},
                new String[]{"image/jpeg"},
                new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                        Log.i(TAG, "Scanned " + path + ":");
                        Log.i(TAG, "-> uri=" + uri);
                    }
                });

        Toast.makeText(mContext, "Image Capture Complete",
                Toast.LENGTH_SHORT).show();
    }
}
