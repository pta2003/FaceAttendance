package com.example.faceattendance;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddEmployeeActivity extends AppCompatActivity {
    private static final String TAG = "AddEmployeeActivity";

    private PreviewView previewView;
    private TextView statusTextView;
    private EditText employeeNameEditText;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_employee);

        previewView = findViewById(R.id.previewView);
        statusTextView = findViewById(R.id.statusTextView);
        employeeNameEditText = findViewById(R.id.employeeNameEditText);
        captureButton = findViewById(R.id.captureButton);
        backButton = findViewById(R.id.backButton);

        captureButton.setOnClickListener(v -> captureAndRegisterFace());
        backButton.setOnClickListener(v -> finish());

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
        startCamera();
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
                            updateStatus("No face detected. Position your face within the oval.");
                            currentFace = null;
                        } else if (faces.size() > 1) {
                            updateStatus("Multiple faces detected. Please ensure only one face is visible.");
                            currentFace = null;
                        } else {
                            currentFace = faces.get(0);
                            currentBitmap = bitmap;
                            currentRotationDegrees = rotationDegrees;

                            updateStatus("Face detected. Smile and blink for liveness check.");

                            livenessDetector.processFace(currentFace);
                            if (livenessDetector.isSmileDetected()) {
                                updateStatus("Smile detected! " +
                                        (livenessDetector.isBlinkDetected() ? "Blink detected!" : "Please blink your eyes."));
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

    private void captureAndRegisterFace() {
        String employeeName = employeeNameEditText.getText().toString().trim();
        if (employeeName.isEmpty()) {
            Toast.makeText(this, "Please enter employee name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentFace == null || currentBitmap == null) {
            Toast.makeText(this, "No face detected. Please position your face correctly.", Toast.LENGTH_LONG).show();
            return;
        }

        isCapturing = true;

        Rect bounds = currentFace.getBoundingBox();
        Bitmap faceBitmap = faceRecognitionHelper.cropFace(currentBitmap, bounds, currentRotationDegrees);

        float[] faceEmbedding = faceRecognitionHelper.getFaceEmbedding(faceBitmap);
        if (faceEmbedding == null) {
            Toast.makeText(this, "Failed to extract face features. Please try again.", Toast.LENGTH_LONG).show();
            isCapturing = false;
            return;
        }

        // Tạo ID ngẫu nhiên
        String employeeId = "EMP" + System.currentTimeMillis();

        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Employee employee = new Employee(employeeId,employeeName, faceEmbedding, currentDate);
//        employee.setEmployeeName(employeeName); // Bạn cần thêm trường "name" vào class Employee nếu chưa có

        faceDatabase.employeeDao().insertEmployee(employee);

        Toast.makeText(this, "Employee registered successfully", Toast.LENGTH_LONG).show();
        updateStatus("Employee " + employeeName + " registered!");

        employeeNameEditText.setText("");

        statusTextView.postDelayed(() -> {
            isCapturing = false;
            livenessDetector.reset();
            updateStatus("Ready for next registration. Position face within the oval.");
            finish();
        }, 2000);
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
