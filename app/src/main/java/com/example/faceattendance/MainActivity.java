package com.example.faceattendance;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_CODE = 1001;
    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;

    private PreviewView previewView;
    private GraphicOverlay graphicOverlay;
    private ImageView previewImg;
    private TextView detectionTextView;

    private CameraManager cameraManager;
    private FaceDetectionProcessor faceProcessor;
    private FaceRecognizer faceRecognizer;
    private ModelManager modelManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        previewView = findViewById(R.id.previewView);
        previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        graphicOverlay = findViewById(R.id.graphic_overlay);
        previewImg = findViewById(R.id.preview_img);
        detectionTextView = findViewById(R.id.detection_text);

        // Initialize components
        modelManager = new ModelManager(this);
        faceRecognizer = new FaceRecognizer(modelManager, previewImg);
        faceProcessor = new FaceDetectionProcessor(this, graphicOverlay, previewImg, detectionTextView, faceRecognizer);
        cameraManager = new CameraManager(this, previewView, faceProcessor);

        // Setup buttons
        ImageButton addBtn = findViewById(R.id.add_btn);
        addBtn.setOnClickListener((v -> addFace()));

        ImageButton switchCamBtn = findViewById(R.id.switch_camera);
        switchCamBtn.setOnClickListener((view -> cameraManager.switchCamera()));

        // Load face recognition model
        modelManager.loadModel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCamera();
    }

    private void startCamera() {
        if(ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            cameraManager.setupCamera();
        } else {
            getPermissions();
        }
    }

    private void getPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{CAMERA_PERMISSION}, PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, int[] grantResults) {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (requestCode == PERMISSION_CODE) {
            cameraManager.setupCamera();
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void addFace() {
        // Tạm dừng nhận diện khuôn mặt
        faceProcessor.pauseDetection();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Name");

        // Set up the input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setMaxWidth(200);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("ADD", (dialog, which) -> {
            faceRecognizer.registerFace(input.getText().toString());
            faceProcessor.resumeDetection();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            faceProcessor.resumeDetection();
            dialog.cancel();
        });

        builder.show();
    }
}