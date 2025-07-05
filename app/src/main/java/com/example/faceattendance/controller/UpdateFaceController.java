package com.example.faceattendance.controller;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import androidx.room.Room;

import com.example.faceattendance.model.FaceDatabase;
import com.example.faceattendance.utils.FaceRecognitionHelper;
import com.example.faceattendance.utils.LivenessDetector;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.ByteArrayOutputStream;
import java.util.List;

public class UpdateFaceController {
    private static final String TAG = "UpdateFaceController";

    private Context context;
    private FaceDetector faceDetector;
    private FaceRecognitionHelper faceRecognitionHelper;
    private LivenessDetector livenessDetector;
    private FaceDatabase faceDatabase;

    // Callback interface for communication with View
    public interface UpdateFaceCallback {
        void onStatusUpdate(String message);
        void onFaceDetected(Face face, Bitmap bitmap, int rotationDegrees);
        void onNoFaceDetected();
        void onMultipleFacesDetected();
        void onFaceProcessingError(String error);
        void onFaceCaptureSuccess(float[] faceEmbedding, String base64Image);
        void onFaceCaptureError(String error);
        void onLivenessDetected(boolean smileDetected, boolean blinkDetected);
    }

    private UpdateFaceCallback callback;

    public UpdateFaceController(Context context, UpdateFaceCallback callback) {
        this.context = context;
        this.callback = callback;
        initializeComponents();
    }

    private void initializeComponents() {
        // Initialize face detector
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking()
                .build();
        faceDetector = FaceDetection.getClient(options);

        // Initialize helper classes
        faceRecognitionHelper = new FaceRecognitionHelper(context);
        livenessDetector = new LivenessDetector();

        // Initialize database
        faceDatabase = Room.databaseBuilder(context.getApplicationContext(),
                        FaceDatabase.class, "face_attendance_db")
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build();
    }

    public void processFaces(List<Face> faces, Bitmap bitmap, int rotationDegrees) {
        if (faces.isEmpty()) {
            callback.onNoFaceDetected();
            callback.onStatusUpdate("Không phát hiện khuôn mặt. Hãy đặt mặt trong khung oval.");
        } else if (faces.size() > 1) {
            callback.onMultipleFacesDetected();
            callback.onStatusUpdate("Phát hiện nhiều khuôn mặt. Chỉ để một khuôn mặt trong khung hình.");
        } else {
            Face face = faces.get(0);
            callback.onFaceDetected(face, bitmap, rotationDegrees);
            callback.onStatusUpdate("Đã phát hiện khuôn mặt. Hãy mỉm cười và nháy mắt.");

            // Process liveness detection
            livenessDetector.processFace(face);
            boolean smileDetected = livenessDetector.isSmileDetected();
            boolean blinkDetected = livenessDetector.isBlinkDetected();

            callback.onLivenessDetected(smileDetected, blinkDetected);

            if (smileDetected) {
                String message = "Đã phát hiện nụ cười! " +
                        (blinkDetected ? "Đã nháy mắt!" : "Hãy nháy mắt.");
                callback.onStatusUpdate(message);
            }
        }
    }

    public void captureAndProcessFace(Face face, Bitmap bitmap, int rotationDegrees) {
        if (face == null || bitmap == null) {
            callback.onFaceCaptureError("Không phát hiện khuôn mặt. Hãy đặt mặt đúng vị trí.");
            return;
        }

        try {
            // Crop face from bitmap
            Rect bounds = face.getBoundingBox();
            Bitmap faceBitmap = faceRecognitionHelper.cropFace(bitmap, bounds, rotationDegrees);

            // Extract face embedding
            float[] faceEmbedding = faceRecognitionHelper.getFaceEmbedding(faceBitmap);
            if (faceEmbedding == null) {
                callback.onFaceCaptureError("Không thể trích xuất đặc trưng khuôn mặt. Hãy thử lại.");
                return;
            }

            // Convert face bitmap to base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            faceBitmap.compress(Bitmap.CompressFormat.WEBP, 10, baos);
            String base64Image = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP);

            // Notify success
            callback.onFaceCaptureSuccess(faceEmbedding, base64Image);
            callback.onStatusUpdate("Ảnh khuôn mặt đã được cập nhật. Nhấn Lưu để hoàn tất.");

        } catch (Exception e) {
            Log.e(TAG, "Error processing face capture", e);
            callback.onFaceCaptureError("Có lỗi xảy ra khi xử lý ảnh. Hãy thử lại.");
        }
    }

    public FaceDetector getFaceDetector() {
        return faceDetector;
    }

    public void cleanup() {
        if (faceRecognitionHelper != null) {
            faceRecognitionHelper.close();
        }
    }
}