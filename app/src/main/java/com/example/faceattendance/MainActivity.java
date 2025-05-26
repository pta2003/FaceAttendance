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
import androidx.room.Room;

import com.example.faceattendance.model.FaceDatabase;

public class MainActivity extends AppCompatActivity {
    public static final String DEVICE_ID = "DEVICE_123";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final int LOGIN_REQUEST_CODE = 101;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA
    };

    private FaceDatabase faceDatabase;

    private boolean isLoggedIn = false;
    private String adminId = "";

    private Button startAttendanceButton;
    private Button ManageButton;
    private Button btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize database
        faceDatabase = Room.databaseBuilder(getApplicationContext(),
                        FaceDatabase.class, "face_attendance_db")
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build();

        // Setup UI
        startAttendanceButton = findViewById(R.id.btnAttendance);
        ManageButton = findViewById(R.id.btnManage);
        btnLogin = findViewById(R.id.btnLogin);

        updateUIState();

        startAttendanceButton.setOnClickListener(v -> {
            if (checkCameraPermission()) {
                startActivity(new Intent(MainActivity.this, FaceDetectionActivity.class));
            } else {
                requestCameraPermission();
            }
        });

        ManageButton.setOnClickListener(v -> {
            if (!isLoggedIn) {
                Toast.makeText(this, "Vui lòng đăng nhập để truy cập chức năng này", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                startActivityForResult(intent, LOGIN_REQUEST_CODE);
            } else if (checkCameraPermission()) {
                startActivity(new Intent(MainActivity.this, AdminDashboardActivity.class));
            } else {
                requestCameraPermission();
            }
        });

        btnLogin.setOnClickListener(v -> {
            if (!isLoggedIn) {
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                startActivityForResult(intent, LOGIN_REQUEST_CODE);
            }
        });
    }

    /**
     * Cập nhật UI theo trạng thái đăng nhập
     */
    private void updateUIState() {
        ManageButton.setEnabled(isLoggedIn);
        ManageButton.setAlpha(isLoggedIn ? 1f : 0.5f);
        btnLogin.setText(isLoggedIn ? "Admin: " + adminId : "Đăng nhập");
    }

    /**
     * Nhận kết quả đăng nhập từ LoginActivity
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LOGIN_REQUEST_CODE && resultCode == RESULT_OK) {
            isLoggedIn = true;
            adminId = data.getStringExtra("admin_id");
            updateUIState();
        }
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, CAMERA_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Camera permission is required for this app", Toast.LENGTH_LONG).show();
            }
        }
    }
}
