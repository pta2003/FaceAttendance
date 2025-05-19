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
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA
    };

    private FaceDatabase faceDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize database
        faceDatabase = Room.databaseBuilder(getApplicationContext(),
                        FaceDatabase.class, "face_attendance_db")
                .fallbackToDestructiveMigration() // reset nếu version thay đổi
                .allowMainThreadQueries() // Just for simplicity, in production use AsyncTask or coroutines
                .build();

        // Setup UI elements
        Button startAttendanceButton = findViewById(R.id.startAttendanceButton);
        Button addEmployeeButton = findViewById(R.id.addEmployeeButton);

        // Set click listeners
        startAttendanceButton.setOnClickListener(v -> {
            if (checkCameraPermission()) {
                startActivity(new Intent(MainActivity.this, FaceDetectionActivity.class));
            } else {
                requestCameraPermission();
            }
        });

        addEmployeeButton.setOnClickListener(v -> {
            if (checkCameraPermission()) {
                startActivity(new Intent(MainActivity.this, AddEmployeeActivity.class));
            } else {
                requestCameraPermission();
            }
        });
    }

    /**
     * Check if camera permission is granted
     */
    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request camera permission
     */
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