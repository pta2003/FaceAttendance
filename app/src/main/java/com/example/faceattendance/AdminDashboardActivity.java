package com.example.faceattendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.faceattendance.utils.PinInputDialog;
import com.example.faceattendance.utils.PinManager;

public class AdminDashboardActivity extends AppCompatActivity {

    private PinManager pinManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        // Khởi tạo PinManager
        pinManager = new PinManager(this);

        Button btnAdd = findViewById(R.id.btnAddEmployee);
        Button btnList = findViewById(R.id.btnListEmployee);
        Button btnHistory = findViewById(R.id.btnViewHistory);
        Button btnChangePin = findViewById(R.id.btnChangePin); // Thêm nút đổi PIN
        ImageButton btnBack = findViewById(R.id.btnBack);

        btnAdd.setOnClickListener(v -> startActivity(new Intent(this, AddEmployeeActivity.class)));
        btnList.setOnClickListener(v -> startActivity(new Intent(this, ManageEmployeesActivity.class)));
        btnHistory.setOnClickListener(v -> startActivity(new Intent(this, AttendanceHistoryActivity.class)));

        // Xử lý nút đổi PIN
        btnChangePin.setOnClickListener(v -> showChangePinDialog());

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

    private void showChangePinDialog() {
        // Bước 1: Xác nhận PIN hiện tại
        PinInputDialog currentPinDialog = new PinInputDialog(this, "Nhập PIN hiện tại", 6);
        currentPinDialog.setListener(new PinInputDialog.PinInputListener() {
            @Override
            public void onPinEntered(String pin) {
                if (pinManager.verifyPin(pin)) {
                    // PIN đúng, chuyển sang nhập PIN mới
                    showNewPinDialog();
                } else {
                    Toast.makeText(AdminDashboardActivity.this,
                            "PIN không đúng!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onPinCancelled() {
                // Không làm gì
            }
        });
        currentPinDialog.show();
    }

    private void showNewPinDialog() {
        // Bước 2: Nhập PIN mới
        PinInputDialog newPinDialog = new PinInputDialog(this, "Nhập PIN mới", 6);
        newPinDialog.setListener(new PinInputDialog.PinInputListener() {
            @Override
            public void onPinEntered(String newPin) {
                // Kiểm tra PIN mới không được trùng với PIN hiện tại
                if (pinManager.verifyPin(newPin)) {
                    Toast.makeText(AdminDashboardActivity.this,
                            "PIN mới không được trùng với PIN hiện tại!",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                // Chuyển sang xác nhận PIN mới
                showConfirmPinDialog(newPin);
            }

            @Override
            public void onPinCancelled() {
                // Không làm gì
            }
        });
        newPinDialog.show();
    }

    private void showConfirmPinDialog(String newPin) {
        // Bước 3: Xác nhận PIN mới
        PinInputDialog confirmPinDialog = new PinInputDialog(this, "Xác nhận PIN mới", 6);
        confirmPinDialog.setListener(new PinInputDialog.PinInputListener() {
            @Override
            public void onPinEntered(String confirmPin) {
                if (newPin.equals(confirmPin)) {
                    // PIN khớp, cập nhật PIN mới
                    if (pinManager.updateAdminPin(newPin)) {
                        Toast.makeText(AdminDashboardActivity.this,
                                "Đổi PIN thành công!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(AdminDashboardActivity.this,
                                "Có lỗi xảy ra khi đổi PIN!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(AdminDashboardActivity.this,
                            "PIN xác nhận không khớp!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onPinCancelled() {
                // Không làm gì
            }
        });
        confirmPinDialog.show();
    }


    @Override
    public void onBackPressed() {
        // Xử lý khi nhấn nút back của hệ thống
        super.onBackPressed();
        Intent intent = new Intent(AdminDashboardActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}