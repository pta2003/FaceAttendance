package com.example.faceattendance;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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

import com.example.faceattendance.controller.AddEmployeeController;
import com.example.faceattendance.utils.PinInputDialog;
import com.example.faceattendance.utils.YuvToRgbConverter;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddEmployeeActivity extends AppCompatActivity implements AddEmployeeController.AddEmployeeView {
    private static final String TAG = "AddEmployeeActivity";

    // UI Components
    private PreviewView previewView;
    private TextView statusTextView;
    private EditText employeeNameEditText;
    private Button captureButton;
    private Button backButton;

    // Camera components
    private FaceDetector faceDetector;
    private ExecutorService cameraExecutor;

    // Controller
    private AddEmployeeController controller;

    // Current capture data
    private boolean isCapturing = false;
    private Face currentFace = null;
    private Bitmap currentBitmap = null;
    private int currentRotationDegrees = 0;

    // Result Dialog
    private AlertDialog resultDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_employee);

        initializeViews();
        initializeController();
        initializeCamera();
        setupEventListeners();
        startCamera();
    }

    private void initializeViews() {
        previewView = findViewById(R.id.previewView);
        statusTextView = findViewById(R.id.statusTextView);
        employeeNameEditText = findViewById(R.id.employeeNameEditText);
        captureButton = findViewById(R.id.captureButton);
        backButton = findViewById(R.id.backButton);
    }

    private void initializeController() {
        controller = new AddEmployeeController(this, this);
    }

    private void initializeCamera() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking()
                .build();
        faceDetector = FaceDetection.getClient(options);

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void setupEventListeners() {
        captureButton.setOnClickListener(v -> {
            String employeeName = employeeNameEditText.getText().toString().trim();
            if (currentFace != null && currentBitmap != null) {
                controller.captureFaceData(currentFace, currentBitmap, currentRotationDegrees, employeeName);
            }
            controller.onCaptureButtonClicked(employeeName);
        });

        backButton.setOnClickListener(v -> controller.onBackButtonClicked());
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
                            controller.onNoFaceDetected();
                            currentFace = null;
                            currentBitmap = null;
                        } else if (faces.size() > 1) {
                            controller.onMultipleFacesDetected();
                            currentFace = null;
                            currentBitmap = null;
                        } else {
                            currentFace = faces.get(0);
                            currentBitmap = bitmap;
                            currentRotationDegrees = rotationDegrees;
                            controller.processFaceDetection(currentFace, currentBitmap, currentRotationDegrees);
                        }
                        imageProxy.close();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Face detection failed", e);
                        imageProxy.close();
                    });
        }
    }

    // Success Dialog
    private void showSuccessDialog(String employeeName, String employeeId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_attendance_result, null);

        TextView iconTextView = dialogView.findViewById(R.id.iconTextView);
        TextView titleTextView = dialogView.findViewById(R.id.titleTextView);
        TextView messageTextView = dialogView.findViewById(R.id.messageTextView);
        Button actionButton = dialogView.findViewById(R.id.actionButton);

        // Setup success dialog
        iconTextView.setText("✓");
        iconTextView.setTextColor(Color.GREEN);
        titleTextView.setText("Thêm nhân viên thành công!");
        titleTextView.setTextColor(Color.GREEN);
        messageTextView.setText("Nhân viên: " + employeeName + "\nID: " + employeeId + "\nĐã được đăng ký thành công!");
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

    // Failure Dialog
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
        titleTextView.setText("Thêm nhân viên thất bại!");
        titleTextView.setTextColor(Color.RED);
        messageTextView.setText("Lỗi: " + reason);
        actionButton.setText("Thử lại");
        actionButton.setBackgroundColor(Color.RED);

        actionButton.setOnClickListener(v -> {
            if (resultDialog != null) {
                resultDialog.dismiss();
            }
            controller.resetForRetry();
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

    // Implementation of AddEmployeeController.AddEmployeeView interface
    @Override
    public void updateStatus(String message) {
        runOnUiThread(() -> statusTextView.setText(message));
    }

    @Override
    public void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void showCustomToast(String message, int durationMs) {
        runOnUiThread(() -> {
            Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
            toast.show();
            new Handler().postDelayed(toast::cancel, durationMs);
        });
    }

    @Override
    public void clearEmployeeName() {
        runOnUiThread(() -> employeeNameEditText.setText(""));
    }

    @Override
    public void finishActivity() {
        finish();
    }

    @Override
    public void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    public void showPinDialog() {
        runOnUiThread(() -> {
            new PinInputDialog(this, "Nhập mã PIN để thêm nhân viên", 6)
                    .setListener(new PinInputDialog.PinInputListener() {
                        @Override
                        public void onPinEntered(String pin) {
                            controller.handlePinInput(pin);
                        }

                        @Override
                        public void onPinCancelled() {
                            controller.onPinCancelled();
                        }
                    })
                    .show();
        });
    }

    @Override
    public void showRegistrationSuccess(String employeeName, String employeeId) {
        runOnUiThread(() -> showSuccessDialog(employeeName, employeeId));
    }

    @Override
    public void showRegistrationFailure(String reason) {
        runOnUiThread(() -> showFailureDialog(reason));
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resultDialog != null && resultDialog.isShowing()) {
            resultDialog.dismiss();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (controller != null) {
            controller.onDestroy();
        }
    }
}