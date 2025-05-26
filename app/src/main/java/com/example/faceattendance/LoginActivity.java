package com.example.faceattendance;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText edtAdminId = findViewById(R.id.edtAdminId);
        EditText edtPassword = findViewById(R.id.edtPassword);
        Button btnSubmit = findViewById(R.id.btnSubmitLogin);

        btnSubmit.setOnClickListener(v -> {
            String id = edtAdminId.getText().toString();
            String pass = edtPassword.getText().toString();
            if (id.equals("admin") && pass.equals("123456")) {
                Intent result = new Intent();
                result.putExtra("admin_id", id);
                setResult(RESULT_OK, result);
                finish();
            } else {
                Toast.makeText(this, "Sai tài khoản hoặc mật khẩu", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
