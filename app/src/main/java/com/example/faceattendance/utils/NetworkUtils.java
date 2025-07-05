// NetworkUtils.java
package com.example.faceattendance.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;

import androidx.room.Room;

import com.example.faceattendance.MainActivity;
import com.example.faceattendance.controller.MainController;
import com.example.faceattendance.model.AttendanceLog;
import com.example.faceattendance.model.DeleteEmployeeLog;
import com.example.faceattendance.model.FaceDatabase;
import com.example.faceattendance.model.Employee;
import com.example.faceattendance.mqtt.MqttCallbackListener;
import com.example.faceattendance.mqtt.MqttManager;

import org.json.JSONObject;

import java.util.List;

public class NetworkUtils {
    private static final MqttManager mqttManager = new MqttManager();
    private static final String TAG = "NetworkUtils";

    // Kiểm tra mạng có sẵn không
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        Network network = cm.getActiveNetwork();
        if (network == null) return false;

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }

    public static void syncUnsyncedEmployees(Context context) {
        FaceDatabase db = Room.databaseBuilder(context.getApplicationContext(),
                        FaceDatabase.class, "face_attendance_db")
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build();

        List<Employee> unsyncedList = db.employeeDao().getUnsyncedEmployees();

        if (unsyncedList.isEmpty()) {
            Log.d(TAG, "No unsynced employees to send.");
            return;
        }

        //MqttManager mqttManager = new MqttManager();

        // Kết nối MQTT trước
        mqttManager.connect(new MqttCallbackListener() {
            @Override
            public void onSendSuccess() {
                // Sau khi kết nối thành công, gửi tuần tự
                sendNext(0);
            }

            @Override
            public void onSendFailure(Exception e) {
                Log.e(TAG, "MQTT connection failed. Cannot sync employees.", e);
            }

            // Hàm gửi từng bản ghi theo index
            private void sendNext(int index) {
                if (index >= unsyncedList.size()) {
                    Log.d(TAG, "All unsynced employees sent.");
                    return;
                }

                Employee employee = unsyncedList.get(index);
                try {
                    String id = "RETRY" + employee.getEmployeeId().substring(3);
                    JSONObject json = new JSONObject();
                    json.put("cmd", "retry_employee");
                    json.put("id", id);
                    json.put("deviceId", MainController.DEVICE_ID);
                    json.put("employeeId", employee.getEmployeeId());
                    json.put("employeeName", employee.getEmployeeName());
                    json.put("faceEmbedding", employee.getFaceEmbedding());
                    json.put("faceBase64", employee.getFaceBase64());
                    json.put("timestamp", employee.getRegistrationDate());

                    mqttManager.sendMessage("attendance/logs", json.toString(), new MqttCallbackListener() {
                        @Override
                        public void onSendSuccess() {
                            Log.d(TAG, "Resend success for " + employee.getEmployeeId());
                            employee.setSynced(true);
                            db.employeeDao().update(employee);

                            // Gửi bản ghi tiếp theo
                            sendNext(index + 1);
                        }

                        @Override
                        public void onSendFailure(Exception e) {
                            Log.e(TAG, "Resend failed for " + employee.getEmployeeId(), e);
                            // Vẫn gửi tiếp các bản ghi sau
                            sendNext(index + 1);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "JSON error", e);
                    sendNext(index + 1);
                }
            }
        });
    }

    public static void syncUnsyncedDeleteLogs(Context context) {
        FaceDatabase db = Room.databaseBuilder(context.getApplicationContext(),
                        FaceDatabase.class, "face_attendance_db")
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build();

        List<DeleteEmployeeLog> unsyncedList = db.deleteEmployeeLogDao().getUnsyncedLogs();

        if (unsyncedList.isEmpty()) {
            Log.d(TAG, "No unsynced employees to send.");
            return;
        }

        //MqttManager mqttManager = new MqttManager();

        // Kết nối MQTT trước
        mqttManager.connect(new MqttCallbackListener() {
            @Override
            public void onSendSuccess() {
                // Sau khi kết nối thành công, gửi tuần tự
                sendNext(0);
            }

            @Override
            public void onSendFailure(Exception e) {
                Log.e(TAG, "MQTT connection failed. Cannot sync employees.", e);
            }

            // Hàm gửi từng bản ghi theo index
            private void sendNext(int index) {
                if (index >= unsyncedList.size()) {
                    Log.d(TAG, "All unsynced employees sent.");
                    return;
                }

                DeleteEmployeeLog deleteEmployeeLog = unsyncedList.get(index);
                try {
                    JSONObject json = new JSONObject();
                    json.put("cmd","delete_employee");
                    json.put("id", deleteEmployeeLog.id);
                    json.put("deviceId", MainController.DEVICE_ID);
                    json.put("employeeId", deleteEmployeeLog.employeeId);
                    json.put("employeeName", deleteEmployeeLog.employeeName);
                    json.put("timestamp", deleteEmployeeLog.timestamp);

                    mqttManager.sendMessage("attendance/logs", json.toString(), new MqttCallbackListener() {
                        @Override
                        public void onSendSuccess() {
                            Log.d(TAG, "Resend success for " + deleteEmployeeLog.id);
                            deleteEmployeeLog.isSynced = true;
                            db.deleteEmployeeLogDao().update(deleteEmployeeLog);

                            // Gửi bản ghi tiếp theo
                            sendNext(index + 1);
                        }

                        @Override
                        public void onSendFailure(Exception e) {
                            Log.e(TAG, "Resend failed for " + deleteEmployeeLog.id, e);
                            // Vẫn gửi tiếp các bản ghi sau
                            sendNext(index + 1);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "JSON error", e);
                    sendNext(index + 1);
                }
            }
        });
    }

    public static void syncUnsyncedAttendanceLogs(Context context) {
        FaceDatabase db = Room.databaseBuilder(context.getApplicationContext(),
                        FaceDatabase.class, "face_attendance_db")
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build();

        List<AttendanceLog> unsyncedList = db.attendanceLogDao().getUnsyncedLogs();

        if (unsyncedList.isEmpty()) {
            Log.d(TAG, "No unsynced employees to send.");
            return;
        }

        //MqttManager mqttManager = new MqttManager();

        // Kết nối MQTT trước
        mqttManager.connect(new MqttCallbackListener() {
            @Override
            public void onSendSuccess() {
                // Sau khi kết nối thành công, gửi tuần tự
                sendNext(0);
            }

            @Override
            public void onSendFailure(Exception e) {
                Log.e(TAG, "MQTT connection failed. Cannot sync employees.", e);
            }

            // Hàm gửi từng bản ghi theo index
            private void sendNext(int index) {
                if (index >= unsyncedList.size()) {
                    Log.d(TAG, "All unsynced employees sent.");
                    return;
                }

                AttendanceLog attendanceLog = unsyncedList.get(index);
                try {
                    JSONObject json = new JSONObject();
                    String logId = "LOG" + attendanceLog.employeeId.substring(3);
                    json.put("cmd","log");
                    json.put("id", logId);
                    json.put("deviceId", MainController.DEVICE_ID);
                    json.put("employeeId", attendanceLog.employeeId);
                    json.put("employeeName", attendanceLog.employeeName);
                    json.put("timestamp", attendanceLog.timestamp);
                    json.put("faceBase64", attendanceLog.faceBase64);

                    mqttManager.sendMessage("attendance/logs", json.toString(), new MqttCallbackListener() {
                        @Override
                        public void onSendSuccess() {
                            Log.d(TAG, "Resend success for " + attendanceLog.id);
                            attendanceLog.isSynced = true;
                            db.attendanceLogDao().update(attendanceLog);

                            // Gửi bản ghi tiếp theo
                            sendNext(index + 1);
                        }

                        @Override
                        public void onSendFailure(Exception e) {
                            Log.e(TAG, "Resend failed for " + attendanceLog.id, e);
                            // Vẫn gửi tiếp các bản ghi sau
                            sendNext(index + 1);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "JSON error", e);
                    sendNext(index + 1);
                }
            }
        });
    }


    // Đăng ký lắng nghe khi có mạng trở lại
    public static void registerNetworkReceiver(Context context) {
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (isNetworkAvailable(ctx)) {
                    Log.d(TAG, "Network is back, syncing unsynced employees...");
                    syncUnsyncedEmployees(ctx);
                    syncUnsyncedDeleteLogs(ctx);
                    syncUnsyncedAttendanceLogs(ctx);
                } else {
                    Log.d(TAG, "Network is unavailable");
                }
            }
        }, filter);
    }
}
