package com.example.faceattendance.controller;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.room.Room;

import com.example.faceattendance.MainActivity;
import com.example.faceattendance.ManageEmployeesActivity;
import com.example.faceattendance.model.DeleteEmployeeLog;
import com.example.faceattendance.model.Employee;
import com.example.faceattendance.model.FaceDatabase;
import com.example.faceattendance.mqtt.MqttCallbackListener;
import com.example.faceattendance.mqtt.MqttManager;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class EmployeeDetailController {
    private static final String TAG = "EmployeeDetailController";
    private static final String ADMIN_PIN = "123456";
    private static final String PREFS_NAME = "AdminPrefs";
    private static final String KEY_UNLOCK_TIME = "unlock_time";

    private Context context;
    private FaceDatabase db;
    private EmployeeDetailView view;
    private Employee currentEmployee;
    private int failedPinAttempts = 0;

    // Temporary data for face update
    private float[] tempFaceEmbedding = null;
    private String tempFaceBase64 = null;
    private boolean hasTempFaceData = false;

    public interface EmployeeDetailView {
        void showToast(String message);
        void showDeleteConfirmation();
        void showPinDialog(String title, PinDialogCallback callback);
        void displayEmployeeInfo(Employee employee);
        void loadEmployeeImage(String faceBase64);
        void loadTempFaceImage(String faceBase64, float[] embedding);
        void enterEditMode();
        void exitEditMode();
        void hideKeyboard();
        void finishActivity();
        void navigateToMainActivity();
        void clearTempFaceData();
        void restoreOriginalImage();
        void setActivityResult(int resultCode);
        void showUpdateSuccess();
        void showUpdateFailure(String reason);
        void showDeleteSuccess(String employeeName);
        void showDeleteFailure(String reason);
    }

    public interface PinDialogCallback {
        void onPinEntered(String pin);
        void onPinCancelled();
    }

    public EmployeeDetailController(Context context, EmployeeDetailView view) {
        this.context = context;
        this.view = view;
        setupDatabase();
    }

    private void setupDatabase() {
        db = Room.databaseBuilder(context.getApplicationContext(),
                        FaceDatabase.class, "face_attendance_db")
                .allowMainThreadQueries()
                .build();
    }

    public void loadEmployee(String employeeId) {
        if (employeeId != null) {
            currentEmployee = db.employeeDao().getEmployeeById(employeeId);
            if (currentEmployee != null) {
                view.displayEmployeeInfo(currentEmployee);
                view.loadEmployeeImage(currentEmployee.getFaceBase64());
            } else {
                view.showToast("Không tìm thấy thông tin nhân viên");
                view.finishActivity();
            }
        } else {
            view.showToast("ID nhân viên không hợp lệ");
            view.finishActivity();
        }
    }

    public void onEditButtonClicked() {
        view.enterEditMode();
    }

    public void onSaveButtonClicked(String newName) {
        // Validate input
        if (TextUtils.isEmpty(newName)) {
            view.showToast("Tên không được để trống");
            return;
        }

        view.showPinDialog("Nhập mã PIN admin để lưu thay đổi", new PinDialogCallback() {
            @Override
            public void onPinEntered(String pin) {
                handlePinInputForSave(pin, newName);
            }

            @Override
            public void onPinCancelled() {
                view.showToast("Đã hủy lưu thay đổi");
            }
        });
    }

    public void onDeleteButtonClicked() {
        view.showDeleteConfirmation();
    }

    public void onDeleteConfirmed() {
        view.showPinDialog("Nhập mã PIN admin để xóa nhân viên", new PinDialogCallback() {
            @Override
            public void onPinEntered(String pin) {
                handlePinInputForDelete(pin);
            }

            @Override
            public void onPinCancelled() {
                view.showToast("Đã hủy xóa nhân viên");
            }
        });
    }

    public void onUpdateFaceResult(float[] newFaceEmbedding, String newFaceBase64) {
        tempFaceEmbedding = newFaceEmbedding;
        tempFaceBase64 = newFaceBase64;
        hasTempFaceData = true;

        view.loadTempFaceImage(tempFaceBase64, tempFaceEmbedding);
        view.showToast("Đã chụp ảnh mới. Nhấn Lưu để hoàn tất.");
    }

    public void onCancelEdit() {
        if (hasTempFaceData) {
            clearTempFaceData();
            view.restoreOriginalImage();
        }
        view.hideKeyboard();
        view.exitEditMode();
    }

    public void onBackPressed(boolean isEditMode) {
        if (isEditMode) {
            // Handle in activity - will show cancel confirmation
        } else {
            view.setActivityResult(-1); // RESULT_OK
            view.finishActivity();
        }
    }

    private void handlePinInputForSave(String enteredPin, String newName) {
        if (ADMIN_PIN.equals(enteredPin)) {
            failedPinAttempts = 0;
            saveChanges(newName);
        } else {
            failedPinAttempts++;
            String message = "Mã PIN sai! (" + failedPinAttempts + "/3)";
            view.showToast(message);

            if (failedPinAttempts >= 3) {
                lockManageButtonInMainActivity();
                view.showToast("Đã nhập sai quá nhiều lần. Tạm khóa chức năng quản lý 1 phút.");
                view.navigateToMainActivity();
            } else {
                onSaveButtonClicked(newName);
            }
        }
    }

    private void handlePinInputForDelete(String enteredPin) {
        if (ADMIN_PIN.equals(enteredPin)) {
            failedPinAttempts = 0;
            deleteEmployee();
        } else {
            failedPinAttempts++;
            String message = "Mã PIN sai! (" + failedPinAttempts + "/3)";
            view.showToast(message);

            if (failedPinAttempts >= 3) {
                lockManageButtonInMainActivity();
                view.showToast("Đã nhập sai quá nhiều lần. Tạm khóa chức năng quản lý 1 phút.");
                view.navigateToMainActivity();
            } else {
                onDeleteConfirmed();
            }
        }
    }

    private void saveChanges(String newName) {
        currentEmployee.setEmployeeName(newName);

        if (hasTempFaceData) {
            currentEmployee.setFaceEmbedding(tempFaceEmbedding);
            currentEmployee.setFaceBase64(tempFaceBase64);
        }

        try {
            db.employeeDao().update(currentEmployee);
            clearTempFaceData();
            view.displayEmployeeInfo(currentEmployee);
            view.loadEmployeeImage(currentEmployee.getFaceBase64());
            view.exitEditMode();
            view.hideKeyboard();

            view.showUpdateSuccess();
            view.setActivityResult(-1); // RESULT_OK
        } catch (Exception e) {
            view.showUpdateFailure("Lỗi cơ sở dữ liệu: " + e.getMessage());
            Log.e(TAG, "Save error", e);
        }

        sendUpdateEmployeeMqtt(newName);
    }

    private void deleteEmployee() {
        String id = "DEL" + System.currentTimeMillis();
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());

        JSONObject json = new JSONObject();
        try {
            json.put("cmd","delete_employee");
            json.put("id", id);
            json.put("deviceId", MainController.DEVICE_ID);
            json.put("employeeId", currentEmployee.getEmployeeId());
            json.put("employeeName", currentEmployee.getEmployeeName());
            json.put("timestamp", currentTime);
        } catch (Exception e) {
            Log.e("MQTT_JSON", "JSON creation failed", e);
        }

        MqttManager mqttManager = new MqttManager();
        String topic = "attendance/logs";

        mqttManager.connectAndSend(topic, json.toString(), new MqttCallbackListener() {
            @Override
            public void onSendSuccess() {
                Log.d(TAG, "MQTT send delete_employee success");
                try {
                    db.employeeDao().deleteById(currentEmployee.getEmployeeId());
                    view.showDeleteSuccess(currentEmployee.getEmployeeName());
                } catch (Exception e) {
                    view.showDeleteFailure("Lỗi cơ sở dữ liệu: " + e.getMessage());
                    Log.e(TAG, "Delete employee error", e);
                }
            }

            @Override
            public void onSendFailure(Exception e) {
                Log.e(TAG, "MQTT send delete_employee failed", e);
                DeleteEmployeeLog deleteEmployeeLog = new DeleteEmployeeLog(
                        id,
                        MainController.DEVICE_ID,
                        currentEmployee.getEmployeeId(),
                        currentEmployee.getEmployeeName(),
                        currentTime,
                        false);
                db.deleteEmployeeLogDao().insert(deleteEmployeeLog);

                view.showDeleteFailure("Lỗi kết nối mạng: " + e.getMessage());
            }
        });
    }

    private void sendUpdateEmployeeMqtt(String newName) {
        String id = "EDIT" + System.currentTimeMillis();

        JSONObject json = new JSONObject();
        try {
            json.put("cmd","edit_employee");
            json.put("id", id);
            json.put("deviceId", MainController.DEVICE_ID);
            json.put("employeeId", currentEmployee.getEmployeeId());
            json.put("employeeName", newName);
            if (hasTempFaceData) {
                json.put("faceEmbedding", tempFaceEmbedding);
                json.put("faceBase64", tempFaceBase64);
            }
        } catch (Exception e) {
            Log.e("MQTT_JSON", "JSON creation failed", e);
        }

        MqttManager mqttManager = new MqttManager();
        String topic = "attendance/logs";

        mqttManager.connectAndSend(topic, json.toString(), new MqttCallbackListener() {
            @Override
            public void onSendSuccess() {
                Log.d(TAG, "MQTT send edit_employee success");
            }

            @Override
            public void onSendFailure(Exception e) {
                Log.e(TAG, "MQTT send edit_employee failed", e);
                currentEmployee.setSynced(false);
                db.employeeDao().update(currentEmployee);
            }
        });
    }

    private void lockManageButtonInMainActivity() {
        long lockDuration = 1 * 60 * 1000; // 1 minute
        long unlockTime = System.currentTimeMillis() + lockDuration;

        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putLong(KEY_UNLOCK_TIME, unlockTime);
        editor.apply();
    }

    private void clearTempFaceData() {
        tempFaceEmbedding = null;
        tempFaceBase64 = null;
        hasTempFaceData = false;
        view.clearTempFaceData();
    }

    public Employee getCurrentEmployee() {
        return currentEmployee;
    }

    public boolean hasTempFaceData() {
        return hasTempFaceData;
    }

    public Bitmap generateBitmapFromEmbedding(float[] embedding) {
        int size = (int) Math.sqrt(embedding.length);
        if (size * size != embedding.length) {
            size = 10;
        }

        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < size && y * size < embedding.length; y++) {
            for (int x = 0; x < size && y * size + x < embedding.length; x++) {
                float value = Math.abs(embedding[y * size + x]);
                value = Math.min(value, 1.0f);
                int gray = (int) (value * 255);
                int color = Color.rgb(gray, gray, gray);
                bmp.setPixel(x, y, color);
            }
        }
        return bmp;
    }
}