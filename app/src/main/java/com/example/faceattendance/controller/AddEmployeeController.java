package com.example.faceattendance.controller;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import androidx.room.Room;

import com.example.faceattendance.MainActivity;
import com.example.faceattendance.model.Employee;
import com.example.faceattendance.model.FaceDatabase;
import com.example.faceattendance.mqtt.MqttCallbackListener;
import com.example.faceattendance.mqtt.MqttManager;
import com.example.faceattendance.utils.FaceRecognitionHelper;
import com.example.faceattendance.utils.LivenessDetector;
import com.google.mlkit.vision.face.Face;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AddEmployeeController {
    private static final String TAG = "AddEmployeeController";
    private static final String ADMIN_PIN = "123456";
    private static final String PREFS_NAME = "AdminPrefs";
    private static final String KEY_UNLOCK_TIME = "unlock_time";

    private Context context;
    private AddEmployeeView view;
    private FaceRecognitionHelper faceRecognitionHelper;
    private LivenessDetector livenessDetector;
    private FaceDatabase faceDatabase;

    // Biến cho PIN security
    private int failedAttempts = 0;

    // Biến để lưu trữ dữ liệu đã capture
    private float[] capturedFaceEmbedding = null;
    private String capturedBase64Image = null;
    private String capturedEmployeeName = null;

    public interface AddEmployeeView {
        void updateStatus(String message);
        void showToast(String message);
        void showCustomToast(String message, int durationMs);
        void clearEmployeeName();
        void finishActivity();
        void startMainActivity();
        void showPinDialog();
        void showRegistrationSuccess(String employeeName, String employeeId);
        void showRegistrationFailure(String reason);
        Context getContext();
    }

    public AddEmployeeController(Context context, AddEmployeeView view) {
        this.context = context;
        this.view = view;
        initializeComponents();
    }

    private void initializeComponents() {
        faceRecognitionHelper = new FaceRecognitionHelper(context);
        livenessDetector = new LivenessDetector();

        faceDatabase = Room.databaseBuilder(context.getApplicationContext(),
                        FaceDatabase.class, "face_attendance_db")
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build();
    }

    public void onCaptureButtonClicked(String employeeName) {
        captureAndRegisterFace(employeeName);
    }

    public void onBackButtonClicked() {
        view.finishActivity();
    }

    public void processFaceDetection(Face face, Bitmap bitmap, int rotationDegrees) {
        livenessDetector.processFace(face);

        String statusMessage = "Face detected. Enter name and click capture to add employee.";

        if (livenessDetector.isSmileDetected()) {
            statusMessage = "Smile detected! " +
                    (livenessDetector.isBlinkDetected() ? "Blink detected!" : "Please blink your eyes.");
        }

        view.updateStatus(statusMessage);
    }

    public void onNoFaceDetected() {
        view.updateStatus("No face detected. Position your face within the oval.");
    }

    public void onMultipleFacesDetected() {
        view.updateStatus("Multiple faces detected. Please ensure only one face is visible.");
    }

    private void captureAndRegisterFace(String employeeName) {
        if (employeeName.isEmpty()) {
            view.showToast("Please enter employee name");
            return;
        }

        // Kiểm tra xem có dữ liệu face đã capture hay chưa
        if (capturedFaceEmbedding == null || capturedBase64Image == null) {
            view.showRegistrationFailure("Không có dữ liệu khuôn mặt. Vui lòng đảm bảo khuôn mặt được phát hiện trước khi capture.");
            return;
        }

        view.showCustomToast("Face captured successfully! Please enter PIN to register.", 1200);
        view.updateStatus("Face captured! Enter PIN to complete registration.");
        view.showPinDialog();
    }

    public void captureFaceData(Face face, Bitmap bitmap, int rotationDegrees, String employeeName) {
        if (face == null || bitmap == null) {
            view.showRegistrationFailure("Không phát hiện được khuôn mặt. Vui lòng đặt khuôn mặt đúng vị trí.");
            return;
        }

        try {
            Rect bounds = face.getBoundingBox();
            Bitmap faceBitmap = faceRecognitionHelper.cropFace(bitmap, bounds, rotationDegrees);

            float[] faceEmbedding = faceRecognitionHelper.getFaceEmbedding(faceBitmap);
            if (faceEmbedding == null) {
                view.showRegistrationFailure("Không thể trích xuất đặc điểm khuôn mặt. Vui lòng thử lại.");
                return;
            }

            // Lấy ảnh base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            faceBitmap.compress(Bitmap.CompressFormat.WEBP, 10, baos);
            String base64Image = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP);

            // Lưu dữ liệu đã capture
            capturedFaceEmbedding = faceEmbedding;
            capturedBase64Image = base64Image;
            capturedEmployeeName = employeeName;

        } catch (Exception e) {
            Log.e(TAG, "Error processing face data", e);
            view.showRegistrationFailure("Lỗi xử lý dữ liệu khuôn mặt: " + e.getMessage());
        }
    }

    public void handlePinInput(String enteredPin) {
        if (ADMIN_PIN.equals(enteredPin)) {
            // PIN đúng - tiến hành đăng ký với dữ liệu đã capture
            failedAttempts = 0;
            view.showCustomToast("Xác thực thành công!", 800);
            registerEmployeeWithCapturedData();
        } else {
            // PIN sai
            failedAttempts++;
            String message = "Mã PIN sai! (" + failedAttempts + "/3)";
            view.showCustomToast(message, 1000);

            if (failedAttempts >= 3) {
                lockManageButtonAndReturn();
            } else {
                // Hiển thị lại dialog PIN
                new android.os.Handler().postDelayed(() -> view.showPinDialog(), 500);
            }
        }
    }

    public void onPinCancelled() {
        resetCaptureState();
        view.updateStatus("Registration cancelled. Ready for next registration.");
    }

    private void lockManageButtonAndReturn() {
        // Lưu trạng thái khóa cho MainActivity
        long unlockTime = System.currentTimeMillis() + (1 * 60 * 1000);
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putLong(KEY_UNLOCK_TIME, unlockTime);
        editor.apply();

        view.showRegistrationFailure("Đã nhập sai PIN quá 3 lần. Nút quản lý bị khóa 1 phút.");

        // Delay trước khi quay về MainActivity
        new android.os.Handler().postDelayed(() -> {
            view.startMainActivity();
        }, 2000);
    }

    private void registerEmployeeWithCapturedData() {
        if (capturedFaceEmbedding == null || capturedBase64Image == null || capturedEmployeeName == null) {
            view.showRegistrationFailure("Không tìm thấy dữ liệu đã capture. Vui lòng thử lại.");
            resetCaptureState();
            return;
        }

        try {
            // Tạo ID ngẫu nhiên
            String id = "ADD" + System.currentTimeMillis();
            String employeeId = "EMP" + System.currentTimeMillis();
            String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());

            JSONObject json = new JSONObject();
            json.put("cmd","add_employee");
            json.put("id", id);
            json.put("deviceId", MainController.DEVICE_ID);
            json.put("employeeId", employeeId);
            json.put("employeeName", capturedEmployeeName);
            json.put("faceEmbedding", capturedFaceEmbedding);
            json.put("faceBase64", capturedBase64Image);
            json.put("timestamp", currentTime);

            Employee employee = new Employee(employeeId, capturedEmployeeName, capturedFaceEmbedding, currentTime, capturedBase64Image);

            // Lưu vào database trước
            faceDatabase.employeeDao().insert(employee);

            // Gửi MQTT
            MqttManager mqttManager = new MqttManager();
            String topic = "attendance/logs";

            mqttManager.connectAndSend(topic, json.toString(), new MqttCallbackListener() {
                @Override
                public void onSendSuccess() {
                    Log.d(TAG, "MQTT send add_employee success");
                    // Cập nhật trạng thái sync
                    employee.setSynced(true);
                    faceDatabase.employeeDao().update(employee);

                    // Hiển thị dialog thành công
                    view.showRegistrationSuccess(capturedEmployeeName, employeeId);
                    resetCaptureState();
                }

                @Override
                public void onSendFailure(Exception e) {
                    Log.e(TAG, "MQTT send add_employee failed", e);
                    employee.setSynced(false);
                    faceDatabase.employeeDao().update(employee);

                    // Vẫn hiển thị thành công vì đã lưu local
                    view.showRegistrationSuccess(capturedEmployeeName, employeeId);
                    resetCaptureState();
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error registering employee", e);
            view.showRegistrationFailure("Lỗi đăng ký nhân viên: " + e.getMessage());
            resetCaptureState();
        }
    }

    public void resetForRetry() {
        resetCaptureState();
        view.updateStatus("Ready for next registration. Position face within the oval.");
        view.clearEmployeeName();
    }

    private void resetCaptureState() {
        capturedFaceEmbedding = null;
        capturedBase64Image = null;
        capturedEmployeeName = null;
        livenessDetector.reset();
        failedAttempts = 0;
    }

    public void onDestroy() {
        if (faceRecognitionHelper != null) {
            faceRecognitionHelper.close();
        }
    }

    public LivenessDetector getLivenessDetector() {
        return livenessDetector;
    }
}