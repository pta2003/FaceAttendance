package com.example.faceattendance.model;

import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverter;
import androidx.room.TypeConverters;

import java.util.List;

/**
 * Type converter for float[] to store face embeddings in the database
 */
class Converters {
    @TypeConverter
    public static String fromFloatArray(float[] array) {
        if (array == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            sb.append(array[i]);
            if (i < array.length - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    @TypeConverter
    public static float[] toFloatArray(String string) {
        if (string == null) {
            return null;
        }

        String[] parts = string.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i]);
        }
        return result;
    }
}

/**
 * Room database for the application
 */
@Database(entities = {Employee.class,AttendanceLog.class,Admin.class}, version = 1, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class FaceDatabase extends RoomDatabase {
    public abstract EmployeeDao employeeDao();
    public abstract AttendanceLogDao attendanceLogDao();
    public abstract AdminDao adminDao();
}