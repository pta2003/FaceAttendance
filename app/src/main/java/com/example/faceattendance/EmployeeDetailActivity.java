package com.example.faceattendance;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import com.example.faceattendance.model.Employee;
import com.example.faceattendance.model.FaceDatabase;

public class EmployeeDetailActivity extends AppCompatActivity {

    private TextView nameTextView, idTextView, dateTextView;
    private ImageView faceImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee_detail);

        nameTextView = findViewById(R.id.nameTextView);
        idTextView = findViewById(R.id.idTextView);
        dateTextView = findViewById(R.id.dateTextView);
        faceImageView = findViewById(R.id.faceImageView);

        String employeeId = getIntent().getStringExtra("employeeId");
        if (employeeId != null) {
            FaceDatabase db = Room.databaseBuilder(getApplicationContext(),
                            FaceDatabase.class, "face_attendance_db")
                    .allowMainThreadQueries()
                    .build();

            Employee employee = db.employeeDao().getEmployeeById(employeeId);
            if (employee != null) {
                nameTextView.setText(employee.getEmployeeName());
                idTextView.setText("ID: " + employee.getEmployeeId());
                dateTextView.setText("Ngày đăng ký: " + employee.getRegistrationDate());

                Bitmap bitmap = generateBitmapFromEmbedding(employee.getFaceEmbedding());
                faceImageView.setImageBitmap(bitmap);
            }
        }
    }

    /**
     * Giải mã faceEmbedding thành ảnh demo (chỉ để minh họa)
     * Ở đây sẽ giả định embedding là grayscale bitmap 10x10
     */
    private Bitmap generateBitmapFromEmbedding(float[] embedding) {
        int size = (int) Math.sqrt(embedding.length);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float value = embedding[y * size + x]; // giá trị từ 0..1
                int gray = (int) (value * 255);
                int color = Color.rgb(gray, gray, gray);
                bmp.setPixel(x, y, color);
            }
        }
        return bmp;
    }
}
