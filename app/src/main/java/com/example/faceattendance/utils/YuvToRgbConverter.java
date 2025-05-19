package com.example.faceattendance.utils;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class YuvToRgbConverter {
    public static Bitmap yuvToRgb(Image image) {
        try {
            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
            ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(
                    nv21,
                    ImageFormat.NV21,
                    image.getWidth(),
                    image.getHeight(),
                    null
            );

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
            byte[] jpegBytes = out.toByteArray();

            return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
        } catch (Exception e) {
            Log.e("YuvToRgbConverter", "Conversion failed", e);
            return null;
        }
    }
}
