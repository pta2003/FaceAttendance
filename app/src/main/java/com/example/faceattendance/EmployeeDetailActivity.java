package com.example.faceattendance;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import com.example.faceattendance.model.Employee;
import com.example.faceattendance.model.FaceDatabase;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class EmployeeDetailActivity extends AppCompatActivity {

    private static final int UPDATE_FACE_REQUEST_CODE = 1001;

    private TextView nameTextView, idTextView, dateTextView;
    private EditText nameEditText, idEditText, dateEditText;
    private ImageView faceImageView;
    private Button editButton, saveButton, cancelButton, updateFaceButton;
    private ImageButton backButton;
    private LinearLayout editButtonsLayout;

    private Employee currentEmployee;
    private FaceDatabase db;
    private boolean isEditMode = false;
    private Calendar calendar;
    private SimpleDateFormat dateFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee_detail);

        initViews();
        setupDatabase();
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
    }

    private void setupDatabase() {
        db = Room.databaseBuilder(getApplicationContext(),
                        FaceDatabase.class, "face_attendance_db")
                .allowMainThreadQueries()
                .build();
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> {
            if (isEditMode) {
                showCancelConfirmation();
            } else {
                finish();
            }
        });

        editButton.setOnClickListener(v -> enterEditMode());

        saveButton.setOnClickListener(v -> saveChanges());

        cancelButton.setOnClickListener(v -> showCancelConfirmation());

        updateFaceButton.setOnClickListener(v -> openUpdateFaceActivity());

        // Removed date picker listener since date is not editable

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
        idEditText.setAlpha(0.6f); // Make it appear disabled

        // Make date field non-editable
        dateEditText.setEnabled(false);
        dateEditText.setAlpha(0.6f); // Make it appear disabled
    }

    private void loadEmployeeData() {
        String employeeId = getIntent().getStringExtra("employeeId");
        if (employeeId != null) {
            currentEmployee = db.employeeDao().getEmployeeById(employeeId);
            if (currentEmployee != null) {
                displayEmployeeInfo();
                loadEmployeeImage();
            } else {
                Toast.makeText(this, "Không tìm thấy thông tin nhân viên", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Toast.makeText(this, "ID nhân viên không hợp lệ", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void displayEmployeeInfo() {
        nameTextView.setText(currentEmployee.getEmployeeName());
        idTextView.setText(currentEmployee.getEmployeeId());
        dateTextView.setText(currentEmployee.getRegistrationDate());

        // Set EditText values for edit mode
        nameEditText.setText(currentEmployee.getEmployeeName());
        idEditText.setText(currentEmployee.getEmployeeId());
        dateEditText.setText(currentEmployee.getRegistrationDate());
    }

    private void loadEmployeeImage() {
        String faceBase64 = currentEmployee.getFaceBase64();
        if (faceBase64 != null && !faceBase64.isEmpty()) {
            try {
                byte[] decodedBytes = Base64.decode(faceBase64, Base64.NO_WRAP);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                faceImageView.setImageBitmap(bitmap);
            } catch (Exception e) {
                // Fallback to generated bitmap from embedding if Base64 fails
                if (currentEmployee.getFaceEmbedding() != null) {
                    Bitmap bitmap = generateBitmapFromEmbedding(currentEmployee.getFaceEmbedding());
                    faceImageView.setImageBitmap(bitmap);
                }
            }
        } else if (currentEmployee.getFaceEmbedding() != null) {
            Bitmap bitmap = generateBitmapFromEmbedding(currentEmployee.getFaceEmbedding());
            faceImageView.setImageBitmap(bitmap);
        }
    }

    private void openUpdateFaceActivity() {
        Intent intent = new Intent(this, UpdateFaceActivity.class);
        intent.putExtra(UpdateFaceActivity.EXTRA_EMPLOYEE_ID, currentEmployee.getEmployeeId());
        intent.putExtra(UpdateFaceActivity.EXTRA_EMPLOYEE_NAME, currentEmployee.getEmployeeName());
        startActivityForResult(intent, UPDATE_FACE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == UPDATE_FACE_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null && data.getBooleanExtra("updated", false)) {
                // Reload employee data to get updated face information
                currentEmployee = db.employeeDao().getEmployeeById(currentEmployee.getEmployeeId());
                loadEmployeeImage();
                Toast.makeText(this, "Đã cập nhật thông tin khuôn mặt", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void enterEditMode() {
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
        editButtonsLayout.setVisibility(View.VISIBLE);

        // Show update face button in edit mode
        updateFaceButton.setVisibility(View.VISIBLE);
    }

    private void exitEditMode() {
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
        editButtonsLayout.setVisibility(View.GONE);

        // Hide update face button when not in edit mode
        updateFaceButton.setVisibility(View.GONE);
    }

    private void saveChanges() {
        String newName = nameEditText.getText().toString().trim();
        // Removed date validation since it's no longer editable

        // Validate input
        if (TextUtils.isEmpty(newName)) {
            nameEditText.setError("Tên không được để trống");
            nameEditText.requestFocus();
            return;
        }

        // Update employee object (only name can be changed)
        currentEmployee.setEmployeeName(newName);
        // Date and ID remain unchanged

        try {
            // Update in database
            db.employeeDao().update(currentEmployee);

            // Update display
            displayEmployeeInfo();
            exitEditMode();

            // Hide keyboard
            hideKeyboard();

            Toast.makeText(this, "Cập nhật thành công", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi cập nhật: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void showCancelConfirmation() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Hủy chỉnh sửa")
                .setMessage("Bạn có chắc chắn muốn hủy? Các thay đổi sẽ không được lưu.")
                .setPositiveButton("Hủy", (dialog, which) -> {
                    // Reset EditText values (only name needs to be reset)
                    nameEditText.setText(currentEmployee.getEmployeeName());

                    // Hide keyboard
                    hideKeyboard();

                    exitEditMode();
                })
                .setNegativeButton("Tiếp tục chỉnh sửa", null)
                .show();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }

    @Override
    public void onBackPressed() {
        if (isEditMode) {
            showCancelConfirmation();
        } else {
            // Set result to notify parent activity that data might have changed
            setResult(RESULT_OK);
            super.onBackPressed();
        }
    }

    @Override
    public void finish() {
        // Set result to notify parent activity that data might have changed
        setResult(RESULT_OK);
        super.finish();
    }

    /**
     * Giải mã faceEmbedding thành ảnh demo (chỉ để minh họa)
     * Ở đây sẽ giả định embedding là grayscale bitmap
     */
    private Bitmap generateBitmapFromEmbedding(float[] embedding) {
        int size = (int) Math.sqrt(embedding.length);
        if (size * size != embedding.length) {
            size = 10; // Default size if embedding length is not a perfect square
        }

        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < size && y * size < embedding.length; y++) {
            for (int x = 0; x < size && y * size + x < embedding.length; x++) {
                float value = Math.abs(embedding[y * size + x]); // Ensure positive value
                value = Math.min(value, 1.0f); // Clamp to 1.0
                int gray = (int) (value * 255);
                int color = Color.rgb(gray, gray, gray);
                bmp.setPixel(x, y, color);
            }
        }
        return bmp;
    }
}