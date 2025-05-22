package com.example.faceattendance;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
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
import androidx.room.Room;

import com.example.faceattendance.model.AttendanceLog;
import com.example.faceattendance.model.Employee;
import com.example.faceattendance.model.FaceDatabase;
import com.example.faceattendance.mqtt.MqttCallbackListener;
import com.example.faceattendance.mqtt.MqttManager;
import com.example.faceattendance.utils.FaceRecognitionHelper;
import com.example.faceattendance.utils.LivenessDetector;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FaceDetectionActivity extends AppCompatActivity {
    private static final String TAG = "FaceDetectionTAG";

    private PreviewView previewView;
    private TextView statusTextView;
    private FaceDetector faceDetector;
    private ExecutorService cameraExecutor;
    private FaceRecognitionHelper faceRecognitionHelper;
    private LivenessDetector livenessDetector;
    private FaceDatabase faceDatabase;
    private Handler handler = new Handler();
    private Runnable returnToMainRunnable;

    private enum DetectionState {
        WAITING_FOR_FACE,
        CHECKING_LIVENESS,
        IDENTIFYING_FACE,
        COMPLETED
    }

    private DetectionState currentState = DetectionState.WAITING_FOR_FACE;
    private boolean processingFrame = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_detection);

        previewView = findViewById(R.id.previewView);
        statusTextView = findViewById(R.id.statusTextView);
        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            handler.removeCallbacks(returnToMainRunnable);
            finish();
        });

        // Xử lý nút back hệ thống bằng dispatcher mới
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handler.removeCallbacks(returnToMainRunnable);
                finish();
            }
        });

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
            if (processingFrame || currentState == DetectionState.COMPLETED) {
                imageProxy.close();
                return;
            }
            processingFrame = true;

            InputImage inputImage = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees());

            faceDetector.process(inputImage)
                    .addOnSuccessListener(faces -> {
                        if (faces.isEmpty()) {
                            updateStatus("No face detected. Position your face within the oval.");
                            currentState = DetectionState.WAITING_FOR_FACE;
                            processingFrame = false;
                            imageProxy.close();
                        } else if (faces.size() > 1) {
                            updateStatus("Multiple faces detected. Please ensure only one face is visible.");
                            processingFrame = false;
                            imageProxy.close();
                        } else {
                            processFace(faces.get(0), imageProxy);
                        }
                        processingFrame = false;
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Face detection failed", e);
                        processingFrame = false;
                        imageProxy.close();
                    });
        }
    }

    private void processFace(Face face, ImageProxy imageProxy) {
        switch (currentState) {
            case WAITING_FOR_FACE:
                currentState = DetectionState.CHECKING_LIVENESS;
                livenessDetector.reset();
                updateStatus("Liveness check: " + livenessDetector.getStatusMessage());
                break;

            case CHECKING_LIVENESS:
                livenessDetector.processFace(face);
                updateStatus("Liveness check: " + livenessDetector.getStatusMessage());

                if (livenessDetector.isLivenessVerified()) {
                    currentState = DetectionState.IDENTIFYING_FACE;
                    updateStatus("Liveness verified. Identifying face...");
                    identifyFace(face, imageProxy);
                }
                break;

            case IDENTIFYING_FACE:
                break;

            case COMPLETED:
                reset();
                break;
        }
        imageProxy.close();  // Chỉ đóng khi xử lý xong
    }

    private void identifyFace(Face face, ImageProxy imageProxy) {
        Bitmap originalBitmap = imageProxyToBitmap(imageProxy);
        if (originalBitmap == null) {
            updateStatus("Failed to process image. Please try again.");
            reset();
            return;
        }

        Rect bounds = face.getBoundingBox();
        Bitmap faceBitmap = faceRecognitionHelper.cropFace(
                originalBitmap,
                bounds,
                imageProxy.getImageInfo().getRotationDegrees()
        );

        float[] faceEmbedding = faceRecognitionHelper.getFaceEmbedding(faceBitmap);
        if (faceEmbedding == null) {
            updateStatus("Failed to extract face features. Please try again.");
            reset();
            return;
        }

        List<Employee> employees = faceDatabase.employeeDao().getAllEmployees();
        if (employees.isEmpty()) {
            updateStatus("No registered employees found. Please register faces first.");
            currentState = DetectionState.COMPLETED;
            return;
        }

        String matchedEmployeeId = null;
        String matchedEmployeeName = null;
        Employee matchedEmployee = null;
        float bestSimilarity = 0;

        for (Employee employee : employees) {
            float similarity = faceRecognitionHelper.calculateSimilarity(faceEmbedding, employee.getFaceEmbedding());
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                matchedEmployeeId = employee.getEmployeeId();
                matchedEmployeeName = employee.getEmployeeName();
                //matchedEmployee = employee;
            }
        }

        if (matchedEmployeeId != null && bestSimilarity > 0.7) {
            String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            faceBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            String base64Image = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP);

            JSONObject json = new JSONObject();
            try {
                json.put("employeeId", matchedEmployeeId);
                json.put("employeeName",matchedEmployeeName);
                json.put("timestamp", currentTime);
                json.put("faceBase64", base64Image);
            } catch (Exception e) {
                Log.e("MQTT_JSON", "JSON creation failed", e);
            }

            final String employeeIdFinal = matchedEmployeeId;
            final String employeeNameFinal = matchedEmployeeName;
            final String timeFinal = currentTime;
            final String imageFinal = base64Image;

            MqttManager mqttManager = new MqttManager();
            mqttManager.connectAndSend(json.toString(), new MqttCallbackListener() {
                @Override
                public void onSendSuccess() {
                    Log.d(TAG, "MQTT send success");
                }

                @Override
                public void onSendFailure(Exception e) {
                    Log.e(TAG, "MQTT send failed, saving log", e);
                    AttendanceLog log = new AttendanceLog(
                            employeeIdFinal,
                            employeeNameFinal,
                            timeFinal,
                            imageFinal,
                            false
                    );
                    faceDatabase.attendanceLogDao().insert(log);
                }
            });


            updateStatus("Attendance recorded for employee " + matchedEmployeeName + "(ID: " + matchedEmployeeId + " ) at " + currentTime);
        } else {
            updateStatus("Face not recognized. Please register or try again.");
        }


        currentState = DetectionState.COMPLETED;
        returnToMainRunnable = this::finish;
        handler.postDelayed(returnToMainRunnable, 2000);
    }


    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

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
                imageProxy.getWidth(),
                imageProxy.getHeight(),
                null
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(
                new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()),
                100,
                out
        );
        byte[] jpegBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
    }

    private void updateStatus(String message) {
        runOnUiThread(() -> statusTextView.setText(message));
    }

    private void reset() {
        statusTextView.postDelayed(() -> {
            currentState = DetectionState.WAITING_FOR_FACE;
            updateStatus("Position your face within the oval");
        }, 3000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(returnToMainRunnable);
        cameraExecutor.shutdown();
        faceRecognitionHelper.close();
    }
}