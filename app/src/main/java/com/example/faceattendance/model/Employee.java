package com.example.faceattendance.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity class representing an employee in the database
 */
@Entity(tableName = "employees")
public class Employee {
    @PrimaryKey
    @NonNull
    private String employeeId;

    private String employeeName;

    private float[] faceEmbedding;

    private String registrationDate;

    private String faceBase64;

    public Employee(@NonNull String employeeId, String employeeName, float[] faceEmbedding, String registrationDate, String faceBase64) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.faceEmbedding = faceEmbedding;
        this.registrationDate = registrationDate;
        this.faceBase64 = faceBase64;
    }

    @NonNull
    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(@NonNull String employeeId) {
        this.employeeId = employeeId;
    }

    public float[] getFaceEmbedding() {
        return faceEmbedding;
    }

    public void setFaceEmbedding(float[] faceEmbedding) {
        this.faceEmbedding = faceEmbedding;
    }

    public String getRegistrationDate() {
        return registrationDate;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public void setRegistrationDate(String registrationDate) {
        this.registrationDate = registrationDate;
    }
    public String getFaceBase64() {
        return faceBase64;
    }
    public void setFaceBase64(String faceBase64) {
        this.faceBase64 = faceBase64;
    }
}