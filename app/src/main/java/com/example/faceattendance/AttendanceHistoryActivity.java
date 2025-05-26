package com.example.faceattendance;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.example.faceattendance.adapter.AttendanceLogAdapter;
import com.example.faceattendance.model.FaceDatabase;
import com.example.faceattendance.model.AttendanceLog;

import java.util.List;

public class AttendanceHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private AttendanceLogAdapter adapter;
    private FaceDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_history);

        recyclerView = findViewById(R.id.recyclerViewLogs);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        db = Room.databaseBuilder(getApplicationContext(),
                        FaceDatabase.class, "face_attendance_db")
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build();
        List<AttendanceLog> logs = db.attendanceLogDao().getAllLogs();

        adapter = new AttendanceLogAdapter(logs);
        recyclerView.setAdapter(adapter);
    }
}