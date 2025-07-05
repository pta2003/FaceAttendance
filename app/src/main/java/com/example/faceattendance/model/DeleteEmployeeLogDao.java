package com.example.faceattendance.model;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;
@Dao
public interface DeleteEmployeeLogDao {
    @Insert
    void insert(DeleteEmployeeLog log);

    @Update
    void update(DeleteEmployeeLog log);
    @Query("SELECT * FROM DeleteEmployeeLog WHERE isSynced = false")
    List<DeleteEmployeeLog> getUnsyncedLogs();

    @Query("DELETE FROM DeleteEmployeeLog WHERE isSynced = true")
    void deleteAllSynced();

    @Query("DELETE FROM DeleteEmployeeLog WHERE employeeId = :employeeId")
    void deleteLogById(String employeeId);
}
