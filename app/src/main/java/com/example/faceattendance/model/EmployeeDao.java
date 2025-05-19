package com.example.faceattendance.model;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List; /**
 * Data Access Object for Employee entities
 */
@Dao
public interface EmployeeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertEmployee(Employee employee);

    @Query("SELECT * FROM employees")
    List<Employee> getAllEmployees();

    @Query("SELECT * FROM employees WHERE employeeId = :employeeId")
    Employee getEmployeeById(String employeeId);
}
