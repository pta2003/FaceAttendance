package com.example.faceattendance;


import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.Image;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;

import java.util.List;

public class FaceDetectionProcessor {
    private static final String TAG = "FaceDetectionProcessor";

    private final Context context;
    private final GraphicOverlay graphicOverlay;
    private final ImageView previewImg;
    private final TextView detectionTextView;
    private final FaceRecognizer faceRecognizer;

    private boolean flipX = false;
    private boolean isDetectionPaused = false;

    public FaceDetectionProcessor(Context context, GraphicOverlay graphicOverlay,
                                  ImageView previewImg, TextView detectionTextView,
                                  FaceRecognizer faceRecognizer) {
        this.context = context;
        this.graphicOverlay = graphicOverlay;
        this.previewImg = previewImg;
        this.detectionTextView = detectionTextView;
        this.faceRecognizer = faceRecognizer;
    }

    public void setFlipX(boolean flipX) {
        this.flipX = flipX;
    }

    public void pauseDetection() {
        isDetectionPaused = true;
    }

    public void resumeDetection() {
        isDetectionPaused = false;
    }

    @SuppressLint("UnsafeOptInUsageError")
    public void analyze(@NonNull ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) return;

        InputImage inputImage = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );

        FaceDetector faceDetector = FaceDetection.getClient();

        faceDetector.process(inputImage)
                .addOnSuccessListener(faces -> onSuccessListener(faces, inputImage, imageProxy.getImage()))
                .addOnFailureListener(e -> Log.e(TAG, "Face detection failure", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void onSuccessListener(List<Face> faces, InputImage inputImage, Image mediaImage) {
        Rect boundingBox = null;
        String name = null;
        float scaleX = (float) previewImg.getWidth() / (float) inputImage.getHeight();
        float scaleY = (float) previewImg.getHeight() / (float) inputImage.getWidth();

        if (faces.size() > 0) {
            detectionTextView.setText(R.string.face_detected);
            // get first face detected
            Face face = faces.get(0);

            // get bounding box of face;
            boundingBox = face.getBoundingBox();

            // convert img to bitmap & crop img
            Bitmap bitmap = BitmapUtils.mediaImgToBmp(
                    mediaImage,
                    inputImage.getRotationDegrees(),
                    boundingBox,
                    flipX);

            if (!isDetectionPaused) {
                name = faceRecognizer.recognizeImage(bitmap);
            }

            if (name != null) {
                detectionTextView.setText(name);
            }
        } else {
            detectionTextView.setText(R.string.no_face_detected);
        }

        graphicOverlay.draw(boundingBox, scaleX, scaleY, name);
    }
}