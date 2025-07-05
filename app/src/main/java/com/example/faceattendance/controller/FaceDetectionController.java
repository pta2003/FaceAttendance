package com.example.faceattendance.controller;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Log;

import androidx.camera.core.ImageProxy;
import androidx.room.Room;

import com.example.faceattendance.MainActivity;
import com.example.faceattendance.model.AttendanceLog;
import com.example.faceattendance.model.Employee;
import com.example.faceattendance.model.FaceDatabase;
import com.example.faceattendance.mqtt.MqttCallbackListener;
import com.example.faceattendance.mqtt.MqttManager;
import com.example.faceattendance.utils.FaceRecognitionHelper;
import com.example.faceattendance.utils.LivenessDetector;
import com.google.mlkit.vision.face.Face;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FaceDetectionController {
    private static final String TAG = "FaceDetectionController";

    public enum DetectionState {
        WAITING_FOR_FACE,
        CHECKING_LIVENESS,
        IDENTIFYING_FACE,
        COMPLETED
    }

    public interface FaceDetectionListener {
        void onStatusUpdate(String message);
        void onDetectionCompleted();
        void onDetectionReset();
        void onAttendanceSuccess(String employeeName, String employeeId, String time);
        void onAttendanceFailure(String reason);
    }

    private Context context;
    private FaceDetectionListener listener;
    private FaceRecognitionHelper faceRecognitionHelper;
    private LivenessDetector livenessDetector;
    private FaceDatabase faceDatabase;

    private DetectionState currentState = DetectionState.WAITING_FOR_FACE;
    private boolean processingFrame = false;

    public FaceDetectionController(Context context, FaceDetectionListener listener) {
        this.context = context;
        this.listener = listener;

        faceRecognitionHelper = new FaceRecognitionHelper(context);
        livenessDetector = new LivenessDetector();

        faceDatabase = Room.databaseBuilder(context.getApplicationContext(),
                        FaceDatabase.class, "face_attendance_db")
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build();
    }

    public DetectionState getCurrentState() {
        return currentState;
    }

    public boolean isProcessingFrame() {
        return processingFrame;
    }

    public void setProcessingFrame(boolean processing) {
        this.processingFrame = processing;
    }

    public void handleNoFaceDetected() {
        listener.onStatusUpdate("No face detected. Position your face within the oval.");
        currentState = DetectionState.WAITING_FOR_FACE;
    }

    public void handleMultipleFaces() {
        listener.onStatusUpdate("Multiple faces detected. Please ensure only one face is visible.");
    }

    public void processFace(Face face, ImageProxy imageProxy) {
        switch (currentState) {
            case WAITING_FOR_FACE:
                currentState = DetectionState.CHECKING_LIVENESS;
                livenessDetector.reset();
                listener.onStatusUpdate("Liveness check: " + livenessDetector.getStatusMessage());
                break;

            case CHECKING_LIVENESS:
                livenessDetector.processFace(face);
                listener.onStatusUpdate("Liveness check: " + livenessDetector.getStatusMessage());

                if (livenessDetector.isLivenessVerified()) {
                    currentState = DetectionState.IDENTIFYING_FACE;
                    listener.onStatusUpdate("Liveness verified. Identifying face...");
                    identifyFace(face, imageProxy);
                }
                break;

            case IDENTIFYING_FACE:
                break;

            case COMPLETED:
                reset();
                break;
        }
    }

    private void identifyFace(Face face, ImageProxy imageProxy) {
        Bitmap originalBitmap = imageProxyToBitmap(imageProxy);
        if (originalBitmap == null) {
            listener.onAttendanceFailure("Failed to process image");
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
            listener.onAttendanceFailure("Failed to extract face features");
            reset();
            return;
        }

        List<Employee> employees = faceDatabase.employeeDao().getAllEmployees();
        if (employees.isEmpty()) {
            listener.onAttendanceFailure("No registered employees found");
            currentState = DetectionState.COMPLETED;
            return;
        }

        String matchedEmployeeId = null;
        String matchedEmployeeName = null;
        float bestSimilarity = 0;

        for (Employee employee : employees) {
            float similarity = faceRecognitionHelper.calculateSimilarity(faceEmbedding, employee.getFaceEmbedding());
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                matchedEmployeeId = employee.getEmployeeId();
                matchedEmployeeName = employee.getEmployeeName();
            }
        }

        if (matchedEmployeeId != null && bestSimilarity > 0.6) {
            recordAttendance(matchedEmployeeId, matchedEmployeeName, faceBitmap);
        } else {
            listener.onAttendanceFailure("Face not recognized");
        }

        currentState = DetectionState.COMPLETED;
    }

    private void recordAttendance(String employeeId, String employeeName, Bitmap faceBitmap) {
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        faceBitmap.compress(Bitmap.CompressFormat.WEBP, 5, baos);
        String base64Image = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP);
        Log.d("ImageSize", "Khối lượng ảnh: " + base64Image.length());

        String logId = "LOG" + System.currentTimeMillis();
        JSONObject json = new JSONObject();
        try {
            json.put("cmd","log");
            json.put("id", logId);
            json.put("deviceId", MainController.DEVICE_ID);
            json.put("employeeId", employeeId);
            json.put("employeeName", employeeName);
            json.put("timestamp", currentTime);
            json.put("faceBase64", base64Image);
        } catch (Exception e) {
            Log.e("MQTT_JSON", "JSON creation failed", e);
            listener.onAttendanceFailure("Failed to create attendance record");
            return;
        }

        final String logIdFinal = logId;
        final String employeeIdFinal = employeeId;
        final String employeeNameFinal = employeeName;
        final String timeFinal = currentTime;
        final String imageFinal = base64Image;

        MqttManager mqttManager = new MqttManager();
        String topic = "attendance/logs";
        mqttManager.connectAndSend(topic, json.toString(), new MqttCallbackListener() {
            @Override
            public void onSendSuccess() {
                Log.d(TAG, "MQTT send success");
                AttendanceLog log = new AttendanceLog(
                        logIdFinal,
                        employeeIdFinal,
                        employeeNameFinal,
                        timeFinal,
                        imageFinal,
                        true
                );
                faceDatabase.attendanceLogDao().insert(log);
                listener.onAttendanceSuccess(employeeNameFinal, employeeIdFinal, timeFinal);
            }

            @Override
            public void onSendFailure(Exception e) {
                Log.e(TAG, "MQTT send failed, saving log", e);
                AttendanceLog log = new AttendanceLog(
                        logIdFinal,
                        employeeIdFinal,
                        employeeNameFinal,
                        timeFinal,
                        imageFinal,
                        false
                );
                faceDatabase.attendanceLogDao().insert(log);
                // Vẫn coi là thành công vì đã lưu local
                listener.onAttendanceSuccess(employeeNameFinal, employeeIdFinal, timeFinal);
            }
        });
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

    public void reset() {
        currentState = DetectionState.WAITING_FOR_FACE;
        listener.onDetectionReset();
    }

    public void onDestroy() {
        if (faceRecognitionHelper != null) {
            faceRecognitionHelper.close();
        }
    }
}