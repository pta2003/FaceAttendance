package com.example.faceattendance;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.example.faceattendance.model.Employee;
import com.example.faceattendance.model.FaceDatabase;
import com.example.faceattendance.adapter.EmployeeAdapter;
import java.util.List;

public class ManageEmployeesActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_EMPLOYEE_DETAIL = 1001;

    private RecyclerView recyclerView;
    private EmployeeAdapter adapter;
    private FaceDatabase faceDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_employees);

        recyclerView = findViewById(R.id.recyclerViewEmployees);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Initialize database
        faceDatabase = Room.databaseBuilder(getApplicationContext(),
                        FaceDatabase.class, "face_attendance_db")
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build();

        loadEmployeeList();
    }

    private void loadEmployeeList() {
        // Load employee list
        List<Employee> employees = faceDatabase.employeeDao().getAllEmployees();

        // Setup adapter
        adapter = new EmployeeAdapter(this, employees, this::openEmployeeDetail);
        recyclerView.setAdapter(adapter);
    }

    private void openEmployeeDetail(String employeeId) {
        Intent intent = new Intent(this, EmployeeDetailActivity.class);
        intent.putExtra("employeeId", employeeId);
        startActivityForResult(intent, REQUEST_CODE_EMPLOYEE_DETAIL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_EMPLOYEE_DETAIL && resultCode == RESULT_OK) {
            // Reload danh sách nhân viên khi có thay đổi
            loadEmployeeList();
        }
    }
}