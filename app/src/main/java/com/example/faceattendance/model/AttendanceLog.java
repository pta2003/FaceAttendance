package com.example.faceattendance.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "AttendanceLog")
public class AttendanceLog {
    @PrimaryKey
    @NonNull
    public String id;
    public String employeeId;
    public String employeeName;
    public String timestamp;
    public String faceBase64; // Lưu ảnh dạng chuỗi Base64
    public boolean isSynced;  // Đã gửi lên MQTT hay chưa

    public AttendanceLog(@NonNull String id,String employeeId,String employeeName, String timestamp, String faceBase64, boolean isSynced) {
        this.id = id;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.timestamp = timestamp;
        this.faceBase64 = faceBase64;
        this.isSynced = isSynced;
    }
}
