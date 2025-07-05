package com.example.faceattendance;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
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

import com.example.faceattendance.controller.FaceDetectionController;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FaceDetectionActivity extends AppCompatActivity implements FaceDetectionController.FaceDetectionListener {
    private static final String TAG = "FaceDetectionActivity";

    // UI Components
    private PreviewView previewView;
    private TextView statusTextView;
    private Button backButton;

    // Camera related
    private FaceDetector faceDetector;
    private ExecutorService cameraExecutor;

    // Controller
    private FaceDetectionController controller;

    // Handler for delayed actions
    private Handler handler = new Handler();
    private Runnable returnToMainRunnable;

    // Dialog
    private AlertDialog resultDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_detection);

        initializeViews();
        initializeController();
        initializeFaceDetector();
        initializeCameraExecutor();
        setupBackButtonHandling();
        startCamera();
    }

    private void initializeViews() {
        previewView = findViewById(R.id.previewView);
        statusTextView = findViewById(R.id.statusTextView);
        backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> {
            cleanup();
            finish();
        });
    }

    private void initializeController() {
        controller = new FaceDetectionController(this, this);
    }

    private void initializeFaceDetector() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking()
                .build();
        faceDetector = FaceDetection.getClient(options);
    }

    private void initializeCameraExecutor() {
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void setupBackButtonHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                cleanup();
                finish();
            }
        });
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
            if (controller.isProcessingFrame() ||
                    controller.getCurrentState() == FaceDetectionController.DetectionState.COMPLETED) {
                imageProxy.close();
                return;
            }

            controller.setProcessingFrame(true);

            InputImage inputImage = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees());

            faceDetector.process(inputImage)
                    .addOnSuccessListener(faces -> {
                        handleFaceDetectionResult(faces, imageProxy);
                        controller.setProcessingFrame(false);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Face detection failed", e);
                        controller.setProcessingFrame(false);
                        imageProxy.close();
                    });
        }
    }

    private void handleFaceDetectionResult(List<Face> faces, ImageProxy imageProxy) {
        if (faces.isEmpty()) {
            controller.handleNoFaceDetected();
            imageProxy.close();
        } else if (faces.size() > 1) {
            controller.handleMultipleFaces();
            imageProxy.close();
        } else {
            controller.processFace(faces.get(0), imageProxy);
            imageProxy.close();
        }
    }

    // FaceDetectionController.FaceDetectionListener implementations
    @Override
    public void onStatusUpdate(String message) {
        runOnUiThread(() -> statusTextView.setText(message));
    }

    @Override
    public void onDetectionCompleted() {
        // Không cần auto return về main nữa vì sẽ có dialog
    }

    @Override
    public void onDetectionReset() {
        statusTextView.postDelayed(() -> {
            onStatusUpdate("Position your face within the oval");
        }, 3000);
    }

    @Override
    public void onAttendanceSuccess(String employeeName, String employeeId, String time) {
        runOnUiThread(() -> showSuccessDialog(employeeName, employeeId, time));
    }

    @Override
    public void onAttendanceFailure(String reason) {
        runOnUiThread(() -> showFailureDialog(reason));
    }

    private void showSuccessDialog(String employeeName, String employeeId, String time) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_attendance_result, null);

        TextView iconTextView = dialogView.findViewById(R.id.iconTextView);
        TextView titleTextView = dialogView.findViewById(R.id.titleTextView);
        TextView messageTextView = dialogView.findViewById(R.id.messageTextView);
        Button actionButton = dialogView.findViewById(R.id.actionButton);

        // Setup success dialog
        iconTextView.setText("✓");
        iconTextView.setTextColor(Color.GREEN);
        titleTextView.setText("Chấm công thành công!");
        titleTextView.setTextColor(Color.GREEN);
        messageTextView.setText("Nhân viên: " + employeeName + "\nID: " + employeeId + "\nThời gian: " + time);
        actionButton.setText("Trở về");
        actionButton.setBackgroundColor(Color.GREEN);

        actionButton.setOnClickListener(v -> {
            if (resultDialog != null) {
                resultDialog.dismiss();
            }
            finish();
        });

        builder.setView(dialogView);
        resultDialog = builder.create();

        // Make dialog background transparent for custom styling
        if (resultDialog.getWindow() != null) {
            resultDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        resultDialog.setCancelable(false);
        resultDialog.show();
    }

    private void showFailureDialog(String reason) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_attendance_result, null);

        TextView iconTextView = dialogView.findViewById(R.id.iconTextView);
        TextView titleTextView = dialogView.findViewById(R.id.titleTextView);
        TextView messageTextView = dialogView.findViewById(R.id.messageTextView);
        Button actionButton = dialogView.findViewById(R.id.actionButton);

        // Setup failure dialog
        iconTextView.setText("✗");
        iconTextView.setTextColor(Color.RED);
        titleTextView.setText("Chấm công thất bại!");
        titleTextView.setTextColor(Color.RED);
        messageTextView.setText("Lỗi: " + reason);
        actionButton.setText("Thử lại");
        actionButton.setBackgroundColor(Color.RED);

        actionButton.setOnClickListener(v -> {
            if (resultDialog != null) {
                resultDialog.dismiss();
            }
            controller.reset();
        });

        builder.setView(dialogView);
        resultDialog = builder.create();

        // Make dialog background transparent for custom styling
        if (resultDialog.getWindow() != null) {
            resultDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        resultDialog.setCancelable(false);
        resultDialog.show();
    }

    private void cleanup() {
        if (handler != null && returnToMainRunnable != null) {
            handler.removeCallbacks(returnToMainRunnable);
        }

        if (resultDialog != null && resultDialog.isShowing()) {
            resultDialog.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanup();

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }

        if (controller != null) {
            controller.onDestroy();
        }
    }
}