package com.example.faceattendance;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class AdminDashboardActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        Button btnAdd = findViewById(R.id.btnAddEmployee);
        Button btnList = findViewById(R.id.btnListEmployee);
        Button btnHistory = findViewById(R.id.btnViewHistory);

        btnAdd.setOnClickListener(v -> startActivity(new Intent(this, AddEmployeeActivity.class)));
        btnList.setOnClickListener(v -> startActivity(new Intent(this, ManageEmployeesActivity.class)));
        btnHistory.setOnClickListener(v -> startActivity(new Intent(this, AttendanceHistoryActivity.class)));
    }
}
