package com.example.faceattendance.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "admins")
public class Admin {
    @PrimaryKey
    @NonNull
    private String adminId;
    private String adminName;
    private String username;
    private String password;
    private float[] faceEmbedding;
    private String registrationDate;

    public Admin(@NonNull String adminId, String adminName, String username, String password, float[] faceEmbedding, String registrationDate) {
        this.adminId = adminId;
        this.adminName = adminName;
        this.username = username;
        this.password = password;
        this.faceEmbedding = faceEmbedding;
        this.registrationDate = registrationDate;
    }

    @NonNull
    public String getAdminId() {
        return adminId;
    }

    public void setAdminId(@NonNull String adminId) {
        this.adminId = adminId;
    }

    public String getAdminName() {
        return adminName;
    }

    public void setAdminName(String adminName) {
        this.adminName = adminName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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
