package com.example.faceattendance;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.room.Room;

import com.example.faceattendance.model.Employee;
import com.example.faceattendance.model.FaceDatabase;
import com.example.faceattendance.utils.FaceRecognitionHelper;
import com.example.faceattendance.utils.LivenessDetector;
import com.example.faceattendance.utils.YuvToRgbConverter;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateFaceActivity extends AppCompatActivity {
    private static final String TAG = "UpdateFaceActivity";
    public static final String EXTRA_EMPLOYEE_ID = "employeeId";
    public static final String EXTRA_EMPLOYEE_NAME = "employeeName";

    private PreviewView previewView;
    private TextView statusTextView;
    private TextView employeeInfoTextView;
    private Button captureButton;
    private Button backButton;

    private FaceDetector faceDetector;
    private ExecutorService cameraExecutor;
    private FaceRecognitionHelper faceRecognitionHelper;
    private LivenessDetector livenessDetector;
    private FaceDatabase faceDatabase;

    private boolean isCapturing = false;
    private Face currentFace = null;
    private Bitmap currentBitmap = null;
    private int currentRotationDegrees = 0;

    private String employeeId;
    private String employeeName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_face);

        // Get employee info from intent
        employeeId = getIntent().getStringExtra(EXTRA_EMPLOYEE_ID);
        employeeName = getIntent().getStringExtra(EXTRA_EMPLOYEE_NAME);

        if (employeeId == null || employeeName == null) {
            Toast.makeText(this, "Thông tin nhân viên không hợp lệ", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupListeners();
        initializeComponents();
        startCamera();
    }

    private void initViews() {
        previewView = findViewById(R.id.previewView);
        statusTextView = findViewById(R.id.statusTextView);
        employeeInfoTextView = findViewById(R.id.employeeInfoTextView);
        captureButton = findViewById(R.id.captureButton);
        backButton = findViewById(R.id.backButton);

        // Set employee info
        employeeInfoTextView.setText("Cập nhật khuôn mặt cho: " + employeeName);
    }

    private void setupListeners() {
        captureButton.setOnClickListener(v -> captureAndUpdateFace());
        backButton.setOnClickListener(v -> finish());
    }

    private void initializeComponents() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking()
                .build();
        faceDetector = FaceDetection.getClient(options);

        faceRecognitionHelper = new FaceRecognitionHelper(this);
        livenessDetector = new LivenessDetector();

        faceDatabase = Room.databaseBuilder(getApplicationContext(),
                        FaceDatabase.class, "face_attendance_db")
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build();

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, new FaceAnalyzer());

        cameraProvider.unbindAll();
        Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    private class FaceAnalyzer implements ImageAnalysis.Analyzer {
        @SuppressLint("UnsafeOptInUsageError")
        @Override
        public void analyze(@NonNull ImageProxy imageProxy) {
            if (isCapturing) {
                imageProxy.close();
                return;
            }

            Image mediaImage = imageProxy.getImage();
            if (mediaImage == null) {
                imageProxy.close();
                return;
            }

            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            Bitmap bitmap = YuvToRgbConverter.yuvToRgb(mediaImage);
            if (bitmap == null) {
                Log.e(TAG, "Bitmap conversion failed");
                imageProxy.close();
                return;
            }

            InputImage inputImage = InputImage.fromBitmap(bitmap, rotationDegrees);

            faceDetector.process(inputImage)
                    .addOnSuccessListener(faces -> {
                        if (faces.isEmpty()) {
                            updateStatus("Không phát hiện khuôn mặt. Hãy đặt mặt trong khung oval.");
                            currentFace = null;
                        } else if (faces.size() > 1) {
                            updateStatus("Phát hiện nhiều khuôn mặt. Chỉ để một khuôn mặt trong khung hình.");
                            currentFace = null;
                        } else {
                            currentFace = faces.get(0);
                            currentBitmap = bitmap;
                            currentRotationDegrees = rotationDegrees;

                            updateStatus("Đã phát hiện khuôn mặt. Hãy mỉm cười và nháy mắt.");

                            livenessDetector.processFace(currentFace);
                            if (livenessDetector.isSmileDetected()) {
                                updateStatus("Đã phát hiện nụ cười! " +
                                        (livenessDetector.isBlinkDetected() ? "Đã nháy mắt!" : "Hãy nháy mắt."));
                            }
                        }
                        imageProxy.close();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Face detection failed", e);
                        imageProxy.close();
                    });
        }
    }
// Trong UpdateFaceActivity - Thay đổi method captureAndUpdateFace()

    private void captureAndUpdateFace() {
        if (currentFace == null || currentBitmap == null) {
            Toast.makeText(this, "Không phát hiện khuôn mặt. Hãy đặt mặt đúng vị trí.", Toast.LENGTH_LONG).show();
            return;
        }

        isCapturing = true;

        Rect bounds = currentFace.getBoundingBox();
        Bitmap faceBitmap = faceRecognitionHelper.cropFace(currentBitmap, bounds, currentRotationDegrees);

        float[] faceEmbedding = faceRecognitionHelper.getFaceEmbedding(faceBitmap);
        if (faceEmbedding == null) {
            Toast.makeText(this, "Không thể trích xuất đặc trưng khuôn mặt. Hãy thử lại.", Toast.LENGTH_LONG).show();
            isCapturing = false;
            return;
        }

        // Convert face bitmap to base64
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        faceBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        String base64Image = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP);

        // KHÔNG LƯU VÀO DATABASE, CHỈ TRẢ VỀ KẾT QUẢ
        Toast.makeText(this, "Chụp ảnh thành công!", Toast.LENGTH_SHORT).show();
        updateStatus("Ảnh khuôn mặt đã được cập nhật. Nhấn Lưu để hoàn tất.");

        // Return result to parent activity
        Intent resultIntent = new Intent();
        resultIntent.putExtra("updated", true);
        resultIntent.putExtra("newFaceEmbedding", faceEmbedding);
        resultIntent.putExtra("newFaceBase64", base64Image);
        setResult(RESULT_OK, resultIntent);

        statusTextView.postDelayed(() -> {
            finish();
        }, 1500);
    }

    private void updateStatus(String message) {
        runOnUiThread(() -> statusTextView.setText(message));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        faceRecognitionHelper.close();
    }
}