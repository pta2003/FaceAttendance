package com.example.faceattendance;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.faceattendance.controller.EmployeeDetailController;
import com.example.faceattendance.model.Employee;
import com.example.faceattendance.utils.PinInputDialog;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class EmployeeDetailActivity extends AppCompatActivity implements EmployeeDetailController.EmployeeDetailView {
    private static final int UPDATE_FACE_REQUEST_CODE = 1001;

    // UI Components
    private TextView nameTextView, idTextView, dateTextView;
    private EditText nameEditText, idEditText, dateEditText;
    private ImageView faceImageView;
    private Button editButton, saveButton, cancelButton, updateFaceButton, deleteButton;
    private ImageButton backButton;
    private LinearLayout editButtonsLayout;

    // Controller
    private EmployeeDetailController controller;

    // State
    private boolean isEditMode = false;
    private Calendar calendar;
    private SimpleDateFormat dateFormat;
    private AlertDialog resultDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee_detail);

        initViews();
        initController();
        setupListeners();
        loadEmployeeData();

        calendar = Calendar.getInstance();
        dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    }

    private void initViews() {
        // TextViews for display mode
        nameTextView = findViewById(R.id.nameTextView);
        idTextView = findViewById(R.id.idTextView);
        dateTextView = findViewById(R.id.dateTextView);

        // EditTexts for edit mode
        nameEditText = findViewById(R.id.nameEditText);
        idEditText = findViewById(R.id.idEditText);
        dateEditText = findViewById(R.id.dateEditText);

        // Other views
        faceImageView = findViewById(R.id.faceImageView);
        editButton = findViewById(R.id.editButton);
        saveButton = findViewById(R.id.saveButton);
        cancelButton = findViewById(R.id.cancelButton);
        backButton = findViewById(R.id.backButton);
        editButtonsLayout = findViewById(R.id.editButtonsLayout);
        updateFaceButton = findViewById(R.id.updateFaceButton);
        deleteButton = findViewById(R.id.deleteButton);
    }

    private void initController() {
        controller = new EmployeeDetailController(this, this);
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> controller.onBackPressed(isEditMode));

        editButton.setOnClickListener(v -> controller.onEditButtonClicked());

        saveButton.setOnClickListener(v -> {
            String newName = nameEditText.getText().toString().trim();
            if (newName.isEmpty()) {
                nameEditText.setError("Tên không được để trống");
                nameEditText.requestFocus();
                return;
            }
            controller.onSaveButtonClicked(newName);
        });

        cancelButton.setOnClickListener(v -> showCancelConfirmation());

        updateFaceButton.setOnClickListener(v -> openUpdateFaceActivity());

        deleteButton.setOnClickListener(v -> controller.onDeleteButtonClicked());

        // Setup IME action for EditTexts
        nameEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        nameEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard();
                return true;
            }
            return false;
        });

        // Make ID field non-editable but keep it focusable for UI consistency
        idEditText.setEnabled(false);
        idEditText.setAlpha(0.6f);

        // Make date field non-editable
        dateEditText.setEnabled(false);
        dateEditText.setAlpha(0.6f);
    }

    private void loadEmployeeData() {
        String employeeId = getIntent().getStringExtra("employeeId");
        controller.loadEmployee(employeeId);
    }

    private void openUpdateFaceActivity() {
        Employee currentEmployee = controller.getCurrentEmployee();
        if (currentEmployee != null) {
            Intent intent = new Intent(this, UpdateFaceActivity.class);
            intent.putExtra(UpdateFaceActivity.EXTRA_EMPLOYEE_ID, currentEmployee.getEmployeeId());
            intent.putExtra(UpdateFaceActivity.EXTRA_EMPLOYEE_NAME, currentEmployee.getEmployeeName());
            startActivityForResult(intent, UPDATE_FACE_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == UPDATE_FACE_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null && data.getBooleanExtra("updated", false)) {
                float[] newFaceEmbedding = data.getFloatArrayExtra("newFaceEmbedding");
                String newFaceBase64 = data.getStringExtra("newFaceBase64");
                controller.onUpdateFaceResult(newFaceEmbedding, newFaceBase64);
            }
        }
    }

    private void showCancelConfirmation() {
        String message = "Bạn có chắc chắn muốn hủy? Các thay đổi sẽ không được lưu.";
        if (controller.hasTempFaceData()) {
            message = "Bạn có chắc chắn muốn hủy? Các thay đổi bao gồm ảnh khuôn mặt mới sẽ không được lưu.";
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Hủy chỉnh sửa")
                .setMessage(message)
                .setPositiveButton("Hủy", (dialog, which) -> {
                    Employee currentEmployee = controller.getCurrentEmployee();
                    if (currentEmployee != null) {
                        nameEditText.setText(currentEmployee.getEmployeeName());
                    }
                    controller.onCancelEdit();
                })
                .setNegativeButton("Tiếp tục chỉnh sửa", null)
                .show();
    }

    private void showUpdateSuccessDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_attendance_result, null);

        TextView iconTextView = dialogView.findViewById(R.id.iconTextView);
        TextView titleTextView = dialogView.findViewById(R.id.titleTextView);
        TextView messageTextView = dialogView.findViewById(R.id.messageTextView);
        Button actionButton = dialogView.findViewById(R.id.actionButton);

        // Setup success dialog
        iconTextView.setText("✓");
        iconTextView.setTextColor(Color.GREEN);
        titleTextView.setText("Cập nhật thành công!");
        titleTextView.setTextColor(Color.GREEN);
        messageTextView.setText("Thông tin nhân viên đã được cập nhật thành công.");
        actionButton.setText("Trở về");
        actionButton.setBackgroundColor(Color.GREEN);

        actionButton.setOnClickListener(v -> {
            if (resultDialog != null) {
                resultDialog.dismiss();
            }
        });

        builder.setView(dialogView);
        resultDialog = builder.create();

        // Make dialog background transparent for custom styling
        if (resultDialog.getWindow() != null) {
            resultDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        resultDialog.setCancelable(false);
        resultDialog.show();
    }

    private void showUpdateFailureDialog(String reason) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_attendance_result, null);

        TextView iconTextView = dialogView.findViewById(R.id.iconTextView);
        TextView titleTextView = dialogView.findViewById(R.id.titleTextView);
        TextView messageTextView = dialogView.findViewById(R.id.messageTextView);
        Button actionButton = dialogView.findViewById(R.id.actionButton);

        // Setup failure dialog
        iconTextView.setText("✗");
        iconTextView.setTextColor(Color.RED);
        titleTextView.setText("Cập nhật thất bại!");
        titleTextView.setTextColor(Color.RED);
        messageTextView.setText("Lỗi: " + reason);
        actionButton.setText("Thử lại");
        actionButton.setBackgroundColor(Color.RED);

        actionButton.setOnClickListener(v -> {
            if (resultDialog != null) {
                resultDialog.dismiss();
            }
        });

        builder.setView(dialogView);
        resultDialog = builder.create();

        // Make dialog background transparent for custom styling
        if (resultDialog.getWindow() != null) {
            resultDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        resultDialog.setCancelable(false);
        resultDialog.show();
    }

    private void showDeleteSuccessDialog(String employeeName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_attendance_result, null);

        TextView iconTextView = dialogView.findViewById(R.id.iconTextView);
        TextView titleTextView = dialogView.findViewById(R.id.titleTextView);
        TextView messageTextView = dialogView.findViewById(R.id.messageTextView);
        Button actionButton = dialogView.findViewById(R.id.actionButton);

        // Setup success dialog
        iconTextView.setText("✓");
        iconTextView.setTextColor(Color.GREEN);
        titleTextView.setText("Xóa thành công!");
        titleTextView.setTextColor(Color.GREEN);
        messageTextView.setText("Nhân viên " + employeeName + " đã được xóa thành công.");
        actionButton.setText("Trở về danh sách");
        actionButton.setBackgroundColor(Color.GREEN);

        actionButton.setOnClickListener(v -> {
            if (resultDialog != null) {
                resultDialog.dismiss();
            }
            // Navigate back to employee list
            Intent intent = new Intent(this, ManageEmployeesActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        builder.setView(dialogView);
        resultDialog = builder.create();

        // Make dialog background transparent for custom styling
        if (resultDialog.getWindow() != null) {
            resultDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        resultDialog.setCancelable(false);
        resultDialog.show();
    }

    private void showDeleteFailureDialog(String reason) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_attendance_result, null);

        TextView iconTextView = dialogView.findViewById(R.id.iconTextView);
        TextView titleTextView = dialogView.findViewById(R.id.titleTextView);
        TextView messageTextView = dialogView.findViewById(R.id.messageTextView);
        Button actionButton = dialogView.findViewById(R.id.actionButton);

        // Setup failure dialog
        iconTextView.setText("✗");
        iconTextView.setTextColor(Color.RED);
        titleTextView.setText("Xóa thất bại!");
        titleTextView.setTextColor(Color.RED);
        messageTextView.setText("Lỗi: " + reason);
        actionButton.setText("Thử lại");
        actionButton.setBackgroundColor(Color.RED);

        actionButton.setOnClickListener(v -> {
            if (resultDialog != null) {
                resultDialog.dismiss();
            }
            controller.onDeleteButtonClicked();
        });

        builder.setView(dialogView);
        resultDialog = builder.create();

        // Make dialog background transparent for custom styling
        if (resultDialog.getWindow() != null) {
            resultDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        resultDialog.setCancelable(false);
        resultDialog.show();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        controller.onBackPressed(isEditMode);
    }

    // Implementation of EmployeeDetailView interface
    @Override
    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showDeleteConfirmation() {
        Employee currentEmployee = controller.getCurrentEmployee();
        if (currentEmployee != null) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Xác nhận xóa")
                    .setMessage("Bạn có chắc chắn muốn xóa nhân viên " + currentEmployee.getEmployeeName() + "?\n\nHành động này không thể hoàn tác.")
                    .setPositiveButton("Xóa", (dialog, which) -> controller.onDeleteConfirmed())
                    .setNegativeButton("Hủy", null)
                    .show();
        }
    }

    @Override
    public void showPinDialog(String title, EmployeeDetailController.PinDialogCallback callback) {
        new PinInputDialog(this, title, 6)
                .setListener(new PinInputDialog.PinInputListener() {
                    @Override
                    public void onPinEntered(String pin) {
                        callback.onPinEntered(pin);
                    }

                    @Override
                    public void onPinCancelled() {
                        callback.onPinCancelled();
                    }
                })
                .show();
    }

    @Override
    public void displayEmployeeInfo(Employee employee) {
        nameTextView.setText(employee.getEmployeeName());
        idTextView.setText(employee.getEmployeeId());
        dateTextView.setText(employee.getRegistrationDate());

        // Set EditText values for edit mode
        nameEditText.setText(employee.getEmployeeName());
        idEditText.setText(employee.getEmployeeId());
        dateEditText.setText(employee.getRegistrationDate());
    }

    @Override
    public void loadEmployeeImage(String faceBase64) {
        if (faceBase64 != null && !faceBase64.isEmpty()) {
            try {
                byte[] decodedBytes = Base64.decode(faceBase64, Base64.NO_WRAP);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                faceImageView.setImageBitmap(bitmap);
            } catch (Exception e) {
                faceImageView.setImageResource(R.drawable.ic_person_placeholder);
            }
        } else {
            faceImageView.setImageResource(R.drawable.ic_person_placeholder);
        }
    }

    @Override
    public void loadTempFaceImage(String faceBase64, float[] embedding) {
        if (faceBase64 != null && !faceBase64.isEmpty()) {
            try {
                byte[] decodedBytes = Base64.decode(faceBase64, Base64.NO_WRAP);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                faceImageView.setImageBitmap(bitmap);
            } catch (Exception e) {
                if (embedding != null) {
                    Bitmap bitmap = controller.generateBitmapFromEmbedding(embedding);
                    faceImageView.setImageBitmap(bitmap);
                }
            }
        }
    }

    @Override
    public void enterEditMode() {
        isEditMode = true;

        // Hide TextViews, show EditTexts
        nameTextView.setVisibility(View.GONE);
        nameEditText.setVisibility(View.VISIBLE);

        idTextView.setVisibility(View.GONE);
        idEditText.setVisibility(View.VISIBLE);

        dateTextView.setVisibility(View.GONE);
        dateEditText.setVisibility(View.VISIBLE);

        // Hide edit button, show save/cancel buttons
        editButton.setVisibility(View.GONE);
        deleteButton.setVisibility(View.GONE);
        editButtonsLayout.setVisibility(View.VISIBLE);

        // Show update face button in edit mode
        updateFaceButton.setVisibility(View.VISIBLE);
    }

    @Override
    public void exitEditMode() {
        isEditMode = false;

        // Show TextViews, hide EditTexts
        nameTextView.setVisibility(View.VISIBLE);
        nameEditText.setVisibility(View.GONE);

        idTextView.setVisibility(View.VISIBLE);
        idEditText.setVisibility(View.GONE);

        dateTextView.setVisibility(View.VISIBLE);
        dateEditText.setVisibility(View.GONE);

        // Show edit button, hide save/cancel buttons
        editButton.setVisibility(View.VISIBLE);
        deleteButton.setVisibility(View.VISIBLE);
        editButtonsLayout.setVisibility(View.GONE);

        // Hide update face button when not in edit mode
        updateFaceButton.setVisibility(View.GONE);
    }

    @Override
    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }

    @Override
    public void finishActivity() {
        finish();
    }

    @Override
    public void navigateToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void clearTempFaceData() {
        // This is handled by the controller, no additional UI logic needed
    }

    @Override
    public void restoreOriginalImage() {
        Employee currentEmployee = controller.getCurrentEmployee();
        if (currentEmployee != null) {
            loadEmployeeImage(currentEmployee.getFaceBase64());
        }
    }

    @Override
    public void setActivityResult(int resultCode) {
        setResult(resultCode);
    }

    @Override
    public void showUpdateSuccess() {
        showUpdateSuccessDialog();
    }

    @Override
    public void showUpdateFailure(String reason) {
        showUpdateFailureDialog(reason);
    }

    @Override
    public void showDeleteSuccess(String employeeName) {
        showDeleteSuccessDialog(employeeName);
    }

    @Override
    public void showDeleteFailure(String reason) {
        showDeleteFailureDialog(reason);
    }

    @Override
    public void finish() {
        setResult(RESULT_OK);
        super.finish();
    }
}