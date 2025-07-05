package com.example.faceattendance.model;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AttendanceLogDao {
    @Insert
    void insert(AttendanceLog log);

    @Update
    void update(AttendanceLog log);

    @Query("SELECT * FROM AttendanceLog ORDER BY timestamp DESC")
    List<AttendanceLog> getAllLogs();

    @Query("SELECT * FROM AttendanceLog WHERE isSynced = 0")
    List<AttendanceLog> getUnsyncedLogs();

    @Query("UPDATE AttendanceLog SET isSynced = 1 WHERE id = :logId")
    void markAsSynced(int logId);

    // New queries for filtering
    @Query("SELECT * FROM AttendanceLog WHERE employeeName LIKE '%' || :name || '%' ORDER BY timestamp DESC")
    List<AttendanceLog> searchByName(String name);

    @Query("SELECT * FROM AttendanceLog WHERE timestamp LIKE :date || '%' ORDER BY timestamp DESC")
    List<AttendanceLog> getLogsByDate(String date);

    @Query("SELECT * FROM AttendanceLog WHERE employeeName LIKE '%' || :name || '%' AND timestamp LIKE :date || '%' ORDER BY timestamp DESC")
    List<AttendanceLog> searchByNameAndDate(String name, String date);

    @Query("SELECT COUNT(*) FROM AttendanceLog")
    int getTotalCount();

    @Query("SELECT COUNT(*) FROM AttendanceLog WHERE timestamp LIKE :date || '%'")
    int getCountByDate(String date);
}