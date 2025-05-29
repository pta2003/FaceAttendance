package com.example.faceattendance.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName = "DeleteEmployeeLog")
public class DeleteEmployeeLog {
    @PrimaryKey
    @NonNull
    public String id;
    public String deviceId;
    public String employeeId;
    public String employeeName;
    public String timestamp;
    public boolean isSynced;  // Đã gửi lên MQTT hay chưa

    public DeleteEmployeeLog(@NonNull String id,String deviceId, String employeeId,String employeeName, String timestamp, boolean isSynced) {
        this.id = id;
        this.deviceId = deviceId;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.timestamp = timestamp;
        this.isSynced = isSynced;
    }
}