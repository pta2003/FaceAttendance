package com.example.faceattendance;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.faceattendance.controller.MainController;
import com.example.faceattendance.utils.NetworkUtils;
import com.example.faceattendance.utils.PinInputDialog;

public class MainActivity extends AppCompatActivity implements MainController.MainControllerListener {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

    private MainController mainController;

    private Button startAttendanceButton;
    private Button manageButton;
    private Button btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Đăng ký phát hiện mạng khi app chạy
        NetworkUtils.registerNetworkReceiver(this);


        initController();
        initViews();
        setupClickListeners();
        mainController.checkLockStatus();
    }

    private void initController() {
        mainController = new MainController(this, this);
    }

    private void initViews() {
        startAttendanceButton = findViewById(R.id.btnAttendance);
        manageButton = findViewById(R.id.btnManage);
        btnLogin = findViewById(R.id.btnLogin);

        // Hiển thị device ID thay vì nút đăng nhập
        btnLogin.setText("Device ID: " + MainController.DEVICE_ID);
        btnLogin.setEnabled(false);
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
        if (mainController.isLocked()) {
            Toast.makeText(this, "Đã bị khóa, vui lòng chờ", Toast.LENGTH_SHORT).show();
            return;
        }

        new PinInputDialog(this, "Nhập mã PIN admin", 6)
                .setListener(new PinInputDialog.PinInputListener() {
                    @Override
                    public void onPinEntered(String pin) {
                        mainController.handlePinInput(pin);
                    }

                    @Override
                    public void onPinCancelled() {
                        // Không cần xử lý gì khi hủy
                    }
                })
                .show();
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

    // Các callback từ MainController
    @Override
    public void onPinCorrect() {
        Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(MainActivity.this, AdminDashboardActivity.class));
    }

    @Override
    public void onPinIncorrect(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        // Hiện lại dialog nếu chưa đạt số lần tối đa
        if (mainController.getFailedAttempts() < 5) {
            showPinDialog();
        }
    }

    @Override
    public void onMaxAttemptsReached() {
        Toast.makeText(this, "Đã nhập sai quá nhiều lần. Tạm khóa 1 phút.", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onButtonLocked(long durationMillis) {
        manageButton.setEnabled(false);
        manageButton.setBackgroundColor(ContextCompat.getColor(this, R.color.button_disabled));
        manageButton.setTextColor(ContextCompat.getColor(this, android.R.color.white));
    }

    @Override
    public void onButtonUnlocked() {
        manageButton.setEnabled(true);
        manageButton.setText("Quản lý");
        manageButton.setBackgroundColor(ContextCompat.getColor(this, R.color.button_enabled));
        manageButton.setTextColor(ContextCompat.getColor(this, android.R.color.white));
    }

    @Override
    public void onLockCountdownTick(String timeLeft) {
        manageButton.setText("Quản lý (" + timeLeft + ")");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mainController != null) {
            mainController.destroy();
        }
    }
}