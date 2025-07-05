package com.example.faceattendance;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
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

import com.example.faceattendance.controller.UpdateFaceController;
import com.example.faceattendance.utils.YuvToRgbConverter;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateFaceActivity extends AppCompatActivity implements UpdateFaceController.UpdateFaceCallback {
    private static final String TAG = "UpdateFaceActivity";
    public static final String EXTRA_EMPLOYEE_ID = "employeeId";
    public static final String EXTRA_EMPLOYEE_NAME = "employeeName";

    // UI Components
    private PreviewView previewView;
    private TextView statusTextView;
    private TextView employeeInfoTextView;
    private Button captureButton;
    private Button backButton;

    // Controller
    private UpdateFaceController controller;
    private ExecutorService cameraExecutor;

    // State management
    private boolean isCapturing = false;
    private Face currentFace = null;
    private Bitmap currentBitmap = null;
    private int currentRotationDegrees = 0;

    // Employee info
    private String employeeId;
    private String employeeName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_face);

        // Get employee info from intent
        extractEmployeeInfo();
        if (!validateEmployeeInfo()) {
            return;
        }

        initViews();
        setupListeners();
        initializeController();
        startCamera();
    }

    private void extractEmployeeInfo() {
        employeeId = getIntent().getStringExtra(EXTRA_EMPLOYEE_ID);
        employeeName = getIntent().getStringExtra(EXTRA_EMPLOYEE_NAME);
    }

    private boolean validateEmployeeInfo() {
        if (employeeId == null || employeeName == null) {
            showToast("Thông tin nhân viên không hợp lệ");
            finish();
            return false;
        }
        return true;
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
        captureButton.setOnClickListener(v -> handleCaptureButtonClick());
        backButton.setOnClickListener(v -> finish());
    }

    private void initializeController() {
        controller = new UpdateFaceController(this, this);
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void handleCaptureButtonClick() {
        if (currentFace == null || currentBitmap == null) {
            showToast("Không phát hiện khuôn mặt. Hãy đặt mặt đúng vị trí.");
            return;
        }

        isCapturing = true;
        controller.captureAndProcessFace(currentFace, currentBitmap, currentRotationDegrees);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
                showToast("Lỗi khởi động camera");
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

            controller.getFaceDetector().process(inputImage)
                    .addOnSuccessListener(faces -> {
                        controller.processFaces(faces, bitmap, rotationDegrees);
                        imageProxy.close();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Face detection failed", e);
                        onFaceProcessingError("Lỗi phát hiện khuôn mặt");
                        imageProxy.close();
                    });
        }
    }

    // Controller callback implementations
    @Override
    public void onStatusUpdate(String message) {
        runOnUiThread(() -> statusTextView.setText(message));
    }

    @Override
    public void onFaceDetected(Face face, Bitmap bitmap, int rotationDegrees) {
        currentFace = face;
        currentBitmap = bitmap;
        currentRotationDegrees = rotationDegrees;
    }

    @Override
    public void onNoFaceDetected() {
        currentFace = null;
        currentBitmap = null;
    }

    @Override
    public void onMultipleFacesDetected() {
        currentFace = null;
        currentBitmap = null;
    }

    @Override
    public void onFaceProcessingError(String error) {
        runOnUiThread(() -> showToast(error));
    }

    @Override
    public void onFaceCaptureSuccess(float[] faceEmbedding, String base64Image) {
        runOnUiThread(() -> {
            showToast("Chụp ảnh thành công!");

            // Return result to parent activity
            Intent resultIntent = new Intent();
            resultIntent.putExtra("updated", true);
            resultIntent.putExtra("newFaceEmbedding", faceEmbedding);
            resultIntent.putExtra("newFaceBase64", base64Image);
            setResult(RESULT_OK, resultIntent);

            // Delay finish to show success message
            statusTextView.postDelayed(this::finish, 1500);
        });
    }

    @Override
    public void onFaceCaptureError(String error) {
        runOnUiThread(() -> {
            showToast(error);
            isCapturing = false;
        });
    }

    @Override
    public void onLivenessDetected(boolean smileDetected, boolean blinkDetected) {
        // Additional UI feedback can be added here if needed
        // For now, status updates are handled in the controller
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (controller != null) {
            controller.cleanup();
        }
    }
}