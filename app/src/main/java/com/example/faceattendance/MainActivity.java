package com.example.faceattendance;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.room.Room;

import com.example.faceattendance.model.FaceDatabase;
import com.example.faceattendance.utils.PinInputDialog;

public class MainActivity extends AppCompatActivity {
    public static final String DEVICE_ID = "DEVICE_123";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final String ADMIN_PIN = "123456"; // Có thể di chuyển vào config hoặc database

    private static final String PREFS_NAME = "AdminPrefs";
    private static final String KEY_UNLOCK_TIME = "unlock_time";

    private FaceDatabase faceDatabase;

    private Button startAttendanceButton;
    private Button manageButton;
    private Button btnLogin;

    private int failedAttempts = 0;
    private boolean isLocked = false;
    private long unlockTime = 0;
    private CountDownTimer lockoutTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        faceDatabase = Room.databaseBuilder(getApplicationContext(),
                        FaceDatabase.class, "face_attendance_db")
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build();

        initViews();
        checkLockStatus();
        setupClickListeners();
    }

    private void initViews() {
        startAttendanceButton = findViewById(R.id.btnAttendance);
        manageButton = findViewById(R.id.btnManage);
        btnLogin = findViewById(R.id.btnLogin);

        // Hiển thị device ID thay vì nút đăng nhập
        btnLogin.setText("Device ID: " + DEVICE_ID);
        btnLogin.setEnabled(false);
    }

    private void checkLockStatus() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        unlockTime = prefs.getLong(KEY_UNLOCK_TIME, 0);
        if (System.currentTimeMillis() < unlockTime) {
            lockManageButton(unlockTime - System.currentTimeMillis());
        }
    }

    private void setupClickListeners() {
        startAttendanceButton.setOnClickListener(v -> {
            if (checkCameraPermission()) {
                startActivity(new Intent(MainActivity.this, FaceDetectionActivity.class));
            } else {
                requestCameraPermission();
            }
        });

        manageButton.setOnClickListener(v -> showPinDialog());
    }

    private void showPinDialog() {
        if (isLocked) {
            Toast.makeText(this, "Đã bị khóa, vui lòng chờ", Toast.LENGTH_SHORT).show();
            return;
        }

        new PinInputDialog(this, "Nhập mã PIN admin", 6)
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
            // PIN đúng
            failedAttempts = 0;
            Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(MainActivity.this, AdminDashboardActivity.class));
        } else {
            // PIN sai
            failedAttempts++;
            String message = "Mã PIN sai! (" + failedAttempts + "/5)";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

            if (failedAttempts >= 5) {
                lockManageButton(1 * 60 * 1000); // khóa 1 phút
                Toast.makeText(this, "Đã nhập sai quá nhiều lần. Tạm khóa 1 phút.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void lockManageButton(long durationMillis) {
        isLocked = true;
        failedAttempts = 0;
        unlockTime = System.currentTimeMillis() + durationMillis;

        // Lưu trạng thái khóa
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putLong(KEY_UNLOCK_TIME, unlockTime);
        editor.apply();

        // Cập nhật UI
        manageButton.setEnabled(false);
        manageButton.setBackgroundColor(ContextCompat.getColor(this, R.color.button_disabled));
        manageButton.setTextColor(ContextCompat.getColor(this, android.R.color.white));

        // Bắt đầu đếm ngược
        lockoutTimer = new CountDownTimer(durationMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                String timeLeft = String.format("%02d:%02d",
                        (millisUntilFinished / 1000) / 60,
                        (millisUntilFinished / 1000) % 60);
                manageButton.setText("Quản lý (" + timeLeft + ")");
            }

            @Override
            public void onFinish() {
                unlockManageButton();
            }
        }.start();
    }

    private void unlockManageButton() {
        isLocked = false;
        manageButton.setEnabled(true);
        manageButton.setText("Quản lý");
        manageButton.setBackgroundColor(ContextCompat.getColor(this, R.color.button_enabled));
        manageButton.setTextColor(ContextCompat.getColor(this, android.R.color.white));

        // Xóa trạng thái khóa
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.remove(KEY_UNLOCK_TIME);
        editor.apply();
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Đã cấp quyền camera", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Ứng dụng cần quyền camera để hoạt động", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lockoutTimer != null) {
            lockoutTimer.cancel();
        }
    }
}