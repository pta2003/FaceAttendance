package com.example.faceattendance.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "AttendanceLog")
public class AttendanceLog {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String employeeId;
    public String timestamp;
    public String faceBase64; // Lưu ảnh dạng chuỗi Base64
    public boolean isSynced;  // Đã gửi lên MQTT hay chưa

    public AttendanceLog(String employeeId, String timestamp, String faceBase64, boolean isSynced) {
        this.employeeId = employeeId;
        this.timestamp = timestamp;
        this.faceBase64 = faceBase64;
        this.isSynced = isSynced;
    }
}
