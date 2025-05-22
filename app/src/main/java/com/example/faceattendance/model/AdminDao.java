package com.example.faceattendance.model;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface AdminDao {
    @Insert
    void insert(Admin admin);

    @Query("SELECT * FROM admins WHERE username = :username AND password = :password LIMIT 1")
    Admin getAdminByUsernameAndPassword(String username, String password);
}
