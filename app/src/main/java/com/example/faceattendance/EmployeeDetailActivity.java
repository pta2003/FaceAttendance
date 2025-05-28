package com.example.faceattendance;

import android.app.DatePickerDialog;
import android.content.Context;
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
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import com.example.faceattendance.model.Employee;
import com.example.faceattendance.model.FaceDatabase;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class EmployeeDetailActivity extends AppCompatActivity {

    private TextView nameTextView, idTextView, dateTextView;
    private EditText nameEditText, idEditText, dateEditText;
    private ImageView faceImageView;
    private Button editButton, saveButton, cancelButton;
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

        dateEditText.setOnClickListener(v -> showDatePicker());

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
    }

    private void saveChanges() {
        String newName = nameEditText.getText().toString().trim();
        String newId = idEditText.getText().toString().trim(); // ID won't change since it's disabled
        String newDate = dateEditText.getText().toString().trim();

        // Validate input
        if (TextUtils.isEmpty(newName)) {
            nameEditText.setError("Tên không được để trống");
            nameEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(newDate)) {
            dateEditText.setError("Ngày đăng ký không được để trống");
            dateEditText.requestFocus();
            return;
        }

        // Update employee object (ID remains the same)
        currentEmployee.setEmployeeName(newName);
        currentEmployee.setRegistrationDate(newDate);

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
                    // Reset EditText values
                    nameEditText.setText(currentEmployee.getEmployeeName());
                    dateEditText.setText(currentEmployee.getRegistrationDate());

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

    private void showDatePicker() {
        // Parse current date if available
        try {
            String currentDateStr = dateEditText.getText().toString();
            if (!TextUtils.isEmpty(currentDateStr)) {
                String[] parts = currentDateStr.split("/");
                if (parts.length == 3) {
                    int day = Integer.parseInt(parts[0]);
                    int month = Integer.parseInt(parts[1]) - 1; // Month is 0-based
                    int year = Integer.parseInt(parts[2]);
                    calendar.set(year, month, day);
                }
            }
        } catch (Exception e) {
            // Use current date if parsing fails
            calendar = Calendar.getInstance();
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    String selectedDate = dateFormat.format(calendar.getTime());
                    dateEditText.setText(selectedDate);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        datePickerDialog.show();
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