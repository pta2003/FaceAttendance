package com.example.faceattendance;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
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

import com.example.faceattendance.model.AttendanceLog;
import com.example.faceattendance.model.Employee;
import com.example.faceattendance.model.FaceDatabase;
import com.example.faceattendance.mqtt.MqttCallbackListener;
import com.example.faceattendance.mqtt.MqttManager;
import com.example.faceattendance.utils.FaceRecognitionHelper;
import com.example.faceattendance.utils.LivenessDetector;
import com.example.faceattendance.utils.PinInputDialog;
import com.example.faceattendance.utils.YuvToRgbConverter;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddEmployeeActivity extends AppCompatActivity {
    private static final String TAG = "AddEmployeeActivity";
    private static final String ADMIN_PIN = "123456"; // PIN admin giống MainActivity
    private static final String PREFS_NAME = "AdminPrefs";
    private static final String KEY_UNLOCK_TIME = "unlock_time";

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

    // Biến cho PIN security
    private int failedAttempts = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_employee);

        previewView = findViewById(R.id.previewView);
        statusTextView = findViewById(R.id.statusTextView);
        employeeNameEditText = findViewById(R.id.employeeNameEditText);
        captureButton = findViewById(R.id.captureButton);
        backButton = findViewById(R.id.backButton);

        // Sửa đổi click listener cho captureButton để yêu cầu PIN trước
        captureButton.setOnClickListener(v -> showPinDialog());
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

    private void showPinDialog() {
        new PinInputDialog(this, "Nhập mã PIN để thêm nhân viên", 6)
                .setListener(new PinInputDialog.PinInputListener() {
                    @Override
                    public void onPinEntered(String pin) {
                        handlePinInput(pin);
                    }

                    @Override
                    public void onPinCancelled() {
                        // Không cần xử lý gì khi hủy
                    }
                })
                .show();
    }

    private void handlePinInput(String enteredPin) {
        if (ADMIN_PIN.equals(enteredPin)) {
            // PIN đúng - tiến hành chụp và đăng ký
            failedAttempts = 0;
            Toast.makeText(this, "Xác thực thành công!", Toast.LENGTH_SHORT).show();
            captureAndRegisterFace();
        } else {
            // PIN sai
            failedAttempts++;
            String message = "Mã PIN sai! (" + failedAttempts + "/3)";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

            if (failedAttempts >= 3) {
                // Nhập sai 3 lần - khóa nút quản lý và quay về MainActivity
                lockManageButtonAndReturn();
            }
        }
    }

    private void lockManageButtonAndReturn() {
        // Lưu trạng thái khóa cho MainActivity
        long unlockTime = System.currentTimeMillis() + (1 * 60 * 1000); // khóa 1 phút
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putLong(KEY_UNLOCK_TIME, unlockTime);
        editor.apply();

        Toast.makeText(this,
                "Đã nhập sai PIN quá 3 lần. Nút quản lý bị khóa 1 phút.",
                Toast.LENGTH_LONG).show();

        // Quay về MainActivity
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
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

                            updateStatus("Face detected. Enter name and click capture to add employee.");

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

        // Tạo ID ngẫu nhiên cho nhân viên
        String employeeId = "EMP" + System.currentTimeMillis();

        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());

        //lấy ảnh base64
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        faceBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        String base64Image = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP);
        // Tạo ID ngẫu nhiên
        String id = "ADD" + System.currentTimeMillis();
        JSONObject json = new JSONObject();//id,deviceId,employeeId,employeeName,faceEmbedding,timestamp
        try {
            json.put("id",id);
            json.put("deviceId",MainActivity.DEVICE_ID);
            json.put("employeeId", employeeId);
            json.put("employeeName",employeeName);
            json.put("faceEmbedding", faceEmbedding);
            json.put("timestamp", currentTime);
        } catch (Exception e) {
            Log.e("MQTT_JSON", "JSON creation failed", e);
        }

        final String employeeIdFinal = employeeId;
        final String employeeNameFinal = employeeName;
        final String timeFinal = currentTime;
        final String imageFinal = base64Image;

        MqttManager mqttManager = new MqttManager();
        String topic = "attendance/add_employee";

        mqttManager.connectAndSend(topic, json.toString(), new MqttCallbackListener() {
            @Override
            public void onSendSuccess() {
                Log.d(TAG, "MQTT send add_employee success");
            }
            @Override
            public void onSendFailure(Exception e) {
                Log.e(TAG, "MQTT send add_employee failed", e);
            }
        });

        //String employeeId, String employeeName, float[] faceEmbedding, String registrationDate, String faceBase64
        Employee employee = new Employee(employeeIdFinal, employeeNameFinal, faceEmbedding, timeFinal, imageFinal);
        faceDatabase.employeeDao().insert(employee);

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