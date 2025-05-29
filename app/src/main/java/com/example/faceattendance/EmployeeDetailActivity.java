package com.example.faceattendance;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
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

import com.example.faceattendance.model.DeleteEmployeeLog;
import com.example.faceattendance.model.Employee;
import com.example.faceattendance.model.FaceDatabase;
import com.example.faceattendance.mqtt.MqttCallbackListener;
import com.example.faceattendance.mqtt.MqttManager;
import com.example.faceattendance.utils.PinInputDialog;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class EmployeeDetailActivity extends AppCompatActivity {
    private static final String TAG = "EmployeeDetailActivity";

    private static final int UPDATE_FACE_REQUEST_CODE = 1001;
    private static final String ADMIN_PIN = "123456"; // Cùng PIN với MainActivity
    private static final String PREFS_NAME = "AdminPrefs";
    private static final String KEY_UNLOCK_TIME = "unlock_time";

    private TextView nameTextView, idTextView, dateTextView;
    private EditText nameEditText, idEditText, dateEditText;
    private ImageView faceImageView;
    private Button editButton, saveButton, cancelButton, updateFaceButton, deleteButton;
    private ImageButton backButton;
    private LinearLayout editButtonsLayout;

    private Employee currentEmployee;
    private FaceDatabase db;
    private boolean isEditMode = false;
    private Calendar calendar;
    private SimpleDateFormat dateFormat;

    // Thêm các biến để lưu trữ tạm thời
    private float[] tempFaceEmbedding = null;
    private String tempFaceBase64 = null;
    private boolean hasTempFaceData = false;

    // Biến cho việc xử lý PIN
    private int failedPinAttempts = 0;

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
        deleteButton = findViewById(R.id.deleteButton);
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

        // Thay đổi listener của nút Save để yêu cầu PIN
        saveButton.setOnClickListener(v -> showPinDialogBeforeSave());
        cancelButton.setOnClickListener(v -> showCancelConfirmation());
        updateFaceButton.setOnClickListener(v -> openUpdateFaceActivity());
        deleteButton.setOnClickListener(v -> showDeleteConfirmation());

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

    private void showDeleteConfirmation() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Xác nhận xóa")
                .setMessage("Bạn có chắc chắn muốn xóa nhân viên " + currentEmployee.getEmployeeName() + "?\n\nHành động này không thể hoàn tác.")
                .setPositiveButton("Xóa", (dialog, which) -> showPinDialogBeforeDelete())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showPinDialogBeforeDelete() {
        new PinInputDialog(this, "Nhập mã PIN admin để xóa nhân viên", 6)
                .setListener(new PinInputDialog.PinInputListener() {
                    @Override
                    public void onPinEntered(String pin) {
                        handlePinInputForDelete(pin);
                    }

                    @Override
                    public void onPinCancelled() {
                        Toast.makeText(EmployeeDetailActivity.this, "Đã hủy xóa nhân viên", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void handlePinInputForDelete(String enteredPin) {
        if (ADMIN_PIN.equals(enteredPin)) {
            // PIN đúng, thực hiện xóa
            failedPinAttempts = 0;
            deleteEmployee();
        } else {
            // PIN sai
            failedPinAttempts++;
            String message = "Mã PIN sai! (" + failedPinAttempts + "/3)";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

            if (failedPinAttempts >= 3) {
                // Khóa nút quản lý trong MainActivity và quay về trang chính
                lockManageButtonInMainActivity();
                Toast.makeText(this, "Đã nhập sai quá nhiều lần. Tạm khóa chức năng quản lý 1 phút.", Toast.LENGTH_LONG).show();

                // Quay về MainActivity
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            } else {
                showPinDialogBeforeDelete();
            }
        }
    }

    private void deleteEmployee() {
        // Tạo ID ngẫu nhiên
        String id = "DEL" + System.currentTimeMillis();
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("deviceId", MainActivity.DEVICE_ID);
            json.put("employeeId", currentEmployee.getEmployeeId());
            json.put("employeeName", currentEmployee.getEmployeeName());
            json.put("timestamp",currentTime);
        } catch (Exception e) {
            Log.e("MQTT_JSON", "JSON creation failed", e);
        }

        MqttManager mqttManager = new MqttManager();
        String topic = "attendance/delete_employee";

        mqttManager.connectAndSend(topic, json.toString(), new MqttCallbackListener() {
            @Override
            public void onSendSuccess() {
                Log.d(TAG, "MQTT send delete_employee success");
                try {
                    // Xóa khỏi database
                    db.employeeDao().deleteById(currentEmployee.getEmployeeId());
                    Toast.makeText(EmployeeDetailActivity.this, "Đã xóa nhân viên thành công", Toast.LENGTH_SHORT).show();
                    // Đóng activity và quay về danh sách
                    setResult(RESULT_OK);
                    finish();

                } catch (Exception e) {
                    Toast.makeText(EmployeeDetailActivity.this, "Lỗi khi xóa nhân viên: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Delete employee error", e);
                }
            }
            @Override
            public void onSendFailure(Exception e) {
                Log.e(TAG, "MQTT send delete_employee failed", e);
                DeleteEmployeeLog  deleteEmployeeLog = new DeleteEmployeeLog(
                        id,
                        MainActivity.DEVICE_ID,
                        currentEmployee.getEmployeeId(),
                        currentEmployee.getEmployeeName(),
                        currentTime,
                        false);
                db.deleteEmployeeLogDao().insert(deleteEmployeeLog);
            }
        });
    }


    private void showPinDialogBeforeSave() {
        String newName = nameEditText.getText().toString().trim();

        // Validate input
        if (TextUtils.isEmpty(newName)) {
            nameEditText.setError("Tên không được để trống");
            nameEditText.requestFocus();
            return;
        }
        new PinInputDialog(this, "Nhập mã PIN admin để lưu thay đổi", 6)
                .setListener(new PinInputDialog.PinInputListener() {
                    @Override
                    public void onPinEntered(String pin) {
                        handlePinInputForSave(pin);
                    }

                    @Override
                    public void onPinCancelled() {
                        Toast.makeText(EmployeeDetailActivity.this, "Đã hủy lưu thay đổi", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void handlePinInputForSave(String enteredPin) {
        if (ADMIN_PIN.equals(enteredPin)) {
            // PIN đúng, thực hiện lưu
            failedPinAttempts = 0;
            //Toast.makeText(this, "Xác thực thành công!", Toast.LENGTH_SHORT).show();
            saveChanges();
        } else {
            // PIN sai
            failedPinAttempts++;
            String message = "Mã PIN sai! (" + failedPinAttempts + "/3)";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

            if (failedPinAttempts >= 3) {
                // Khóa nút quản lý trong MainActivity và quay về trang chính
                lockManageButtonInMainActivity();
                Toast.makeText(this, "Đã nhập sai quá nhiều lần. Tạm khóa chức năng quản lý 1 phút.", Toast.LENGTH_LONG).show();

                // Quay về MainActivity
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }else{
                showPinDialogBeforeSave();
            }
        }
    }

    private void lockManageButtonInMainActivity() {
        // Lưu trạng thái khóa vào SharedPreferences
        long lockDuration = 1 * 60 * 1000; // 1 phút
        long unlockTime = System.currentTimeMillis() + lockDuration;

        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putLong(KEY_UNLOCK_TIME, unlockTime);
        editor.apply();
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
                // Handle Base64 decode error
                faceImageView.setImageResource(R.drawable.ic_person_placeholder);
            }
        } else {
            faceImageView.setImageResource(R.drawable.ic_person_placeholder);
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
                // Lưu dữ liệu tạm thời thay vì cập nhật database ngay
                tempFaceEmbedding = data.getFloatArrayExtra("newFaceEmbedding");
                tempFaceBase64 = data.getStringExtra("newFaceBase64");
                hasTempFaceData = true;

                // Hiển thị ảnh mới
                loadTempFaceImage();
                Toast.makeText(this, "Đã chụp ảnh mới. Nhấn Lưu để hoàn tất.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadTempFaceImage() {
        if (hasTempFaceData && tempFaceBase64 != null && !tempFaceBase64.isEmpty()) {
            try {
                byte[] decodedBytes = Base64.decode(tempFaceBase64, Base64.NO_WRAP);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                faceImageView.setImageBitmap(bitmap);
            } catch (Exception e) {
                // Fallback
                if (tempFaceEmbedding != null) {
                    Bitmap bitmap = generateBitmapFromEmbedding(tempFaceEmbedding);
                    faceImageView.setImageBitmap(bitmap);
                }
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
        deleteButton.setVisibility(View.GONE);
        editButtonsLayout.setVisibility(View.VISIBLE);

        // Show update face button in edit mode
        updateFaceButton.setVisibility(View.VISIBLE);
    }

    private void exitEditMode() {
        isEditMode = false;

        // Clear temp data when exiting edit mode without saving
        if (hasTempFaceData) {
            clearTempFaceData();
            loadEmployeeImage(); // Restore original image
        }

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

    private void saveChanges() {
        String newName = nameEditText.getText().toString().trim();

        // Update employee object
        currentEmployee.setEmployeeName(newName);

        // Cập nhật ảnh khuôn mặt nếu có dữ liệu tạm thời
        if (hasTempFaceData) {
            currentEmployee.setFaceEmbedding(tempFaceEmbedding);
            currentEmployee.setFaceBase64(tempFaceBase64);
        }
        try {
            // Update in database
            db.employeeDao().update(currentEmployee);
            // Clear temp data
            clearTempFaceData();
            // Update display
            displayEmployeeInfo();
            loadEmployeeImage(); // Load from database
            exitEditMode();
            // Hide keyboard
            hideKeyboard();
            Toast.makeText(this, "Cập nhật thành công", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi cập nhật: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
        // Tạo ID ngẫu nhiên
        String id = "EDIT" + System.currentTimeMillis();

        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("deviceId", MainActivity.DEVICE_ID);
            json.put("employeeId", currentEmployee.getEmployeeId());
            json.put("employeeName", newName);
            if (hasTempFaceData) {
                json.put("faceEmbedding", tempFaceEmbedding);
                json.put("faceBase64", tempFaceBase64);
            }
        } catch (Exception e) {
            Log.e("MQTT_JSON", "JSON creation failed", e);
        }

        MqttManager mqttManager = new MqttManager();
        String topic = "attendance/edit_employee";

        mqttManager.connectAndSend(topic, json.toString(), new MqttCallbackListener() {
            @Override
            public void onSendSuccess() {
                Log.d(TAG, "MQTT send add_employee success");
            }
            @Override
            public void onSendFailure(Exception e) {
                Log.e(TAG, "MQTT send add_employee failed", e);
                currentEmployee.setSynced(false);
            }
        });
    }

    private void showCancelConfirmation() {
        String message = "Bạn có chắc chắn muốn hủy? Các thay đổi sẽ không được lưu.";
        if (hasTempFaceData) {
            message = "Bạn có chắc chắn muốn hủy? Các thay đổi bao gồm ảnh khuôn mặt mới sẽ không được lưu.";
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Hủy chỉnh sửa")
                .setMessage(message)
                .setPositiveButton("Hủy", (dialog, which) -> {
                    // Reset EditText values
                    nameEditText.setText(currentEmployee.getEmployeeName());

                    // Clear temp face data và restore ảnh gốc
                    clearTempFaceData();
                    loadEmployeeImage(); // Load original image from database

                    // Hide keyboard
                    hideKeyboard();

                    exitEditMode();
                })
                .setNegativeButton("Tiếp tục chỉnh sửa", null)
                .show();
    }

    private void clearTempFaceData() {
        tempFaceEmbedding = null;
        tempFaceBase64 = null;
        hasTempFaceData = false;
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