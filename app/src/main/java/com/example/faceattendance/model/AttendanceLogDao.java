package com.example.faceattendance.model;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AttendanceLogDao {
    @Insert
    void insert(AttendanceLog log);

    @Query("SELECT * FROM AttendanceLog ORDER BY timestamp DESC")
    List<AttendanceLog> getAllLogs();
    @Query("SELECT * FROM AttendanceLog WHERE isSynced = 0")
    List<AttendanceLog> getUnsyncedLogs();

    @Query("UPDATE AttendanceLog SET isSynced = 1 WHERE id = :logId")
    void markAsSynced(int logId);
}
