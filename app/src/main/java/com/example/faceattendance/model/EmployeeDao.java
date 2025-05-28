package com.example.faceattendance.model;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Data Access Object for Employee entities
 */
@Dao
public interface EmployeeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Employee employee);

    @Update
    void update(Employee employee);

    @Query("SELECT * FROM employees")
    List<Employee> getAllEmployees();

    @Query("SELECT * FROM employees WHERE employeeId = :employeeId")
    Employee getEmployeeById(String employeeId);

    @Query("DELETE FROM employees WHERE employeeId = :employeeId")
    void deleteById(String employeeId);
}