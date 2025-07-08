package com.example.faceattendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AdminDashboardActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        Button btnAdd = findViewById(R.id.btnAddEmployee);
        Button btnList = findViewById(R.id.btnListEmployee);
        Button btnHistory = findViewById(R.id.btnViewHistory);
        ImageButton btnBack = findViewById(R.id.btnBack);

        btnAdd.setOnClickListener(v -> startActivity(new Intent(this, AddEmployeeActivity.class)));
        btnList.setOnClickListener(v -> startActivity(new Intent(this, ManageEmployeesActivity.class)));
        btnHistory.setOnClickListener(v -> startActivity(new Intent(this, AttendanceHistoryActivity.class)));

        // Xử lý nút quay lại
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Quay về MainActivity
                Intent intent = new Intent(AdminDashboardActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
        });
    }

    @Override
    public void onBackPressed() {
        // Xử lý khi nhấn nút back của hệ thống
        Intent intent = new Intent(AdminDashboardActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}