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

    private float[] faceEmbedding;

    private String registrationDate;

    public Employee(@NonNull String employeeId, float[] faceEmbedding, String registrationDate) {
        this.employeeId = employeeId;
        this.faceEmbedding = faceEmbedding;
        this.registrationDate = registrationDate;
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

    public void setRegistrationDate(String registrationDate) {
        this.registrationDate = registrationDate;
    }
}