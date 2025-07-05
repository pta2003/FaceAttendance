package com.example.faceattendance.controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.CountDownTimer;
import android.provider.Settings;

import androidx.room.Room;

import com.example.faceattendance.model.FaceDatabase;

public class MainController {
    public static String DEVICE_ID;
    private static final String ADMIN_PIN = "123456";
    private static final String PREFS_NAME = "AdminPrefs";
    private static final String KEY_UNLOCK_TIME = "unlock_time";
    private static final long LOCKOUT_DURATION = 1 * 60 * 1000; // 1 phút
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private Context context;
    private FaceDatabase faceDatabase;

    private int failedAttempts = 0;
    private boolean isLocked = false;
    private long unlockTime = 0;
    private CountDownTimer lockoutTimer;

    private MainControllerListener listener;

    public interface MainControllerListener {
        void onPinCorrect();
        void onPinIncorrect(String message);
        void onMaxAttemptsReached();
        void onButtonLocked(long durationMillis);
        void onButtonUnlocked();
        void onLockCountdownTick(String timeLeft);
    }

    public MainController(Context context, MainControllerListener listener) {
        this.context = context;
        this.listener = listener;
        this.faceDatabase = Room.databaseBuilder(context.getApplicationContext(),
                        FaceDatabase.class, "face_attendance_db")
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build();
        String androidId = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        if (androidId != null && !androidId.isEmpty() && !androidId.equals("9774d56d682e549c")) {
            DEVICE_ID = "AID_" + androidId; // Thêm prefix để phân biệt
        }
    }

    public void checkLockStatus() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        unlockTime = prefs.getLong(KEY_UNLOCK_TIME, 0);
        if (System.currentTimeMillis() < unlockTime) {
            lockManageButton(unlockTime - System.currentTimeMillis());
        }
    }

    public void handlePinInput(String enteredPin) {
        if (isLocked) {
            return; // Không xử lý nếu đang bị khóa
        }

        if (ADMIN_PIN.equals(enteredPin)) {
            // PIN đúng
            failedAttempts = 0;
            if (listener != null) {
                listener.onPinCorrect();
            }
        } else {
            // PIN sai
            failedAttempts++;
            String message = "Mã PIN sai! (" + failedAttempts + "/" + MAX_FAILED_ATTEMPTS + ")";

            if (listener != null) {
                listener.onPinIncorrect(message);
            }

            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                lockManageButton(LOCKOUT_DURATION);
                if (listener != null) {
                    listener.onMaxAttemptsReached();
                }
            }
        }
    }

    public void lockManageButton(long durationMillis) {
        isLocked = true;
        failedAttempts = 0;
        unlockTime = System.currentTimeMillis() + durationMillis;

        // Lưu trạng thái khóa
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putLong(KEY_UNLOCK_TIME, unlockTime);
        editor.apply();

        if (listener != null) {
            listener.onButtonLocked(durationMillis);
        }

        // Bắt đầu đếm ngược
        lockoutTimer = new CountDownTimer(durationMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                String timeLeft = String.format("%02d:%02d",
                        (millisUntilFinished / 1000) / 60,
                        (millisUntilFinished / 1000) % 60);
                if (listener != null) {
                    listener.onLockCountdownTick(timeLeft);
                }
            }

            @Override
            public void onFinish() {
                unlockManageButton();
            }
        }.start();
    }

    public void unlockManageButton() {
        isLocked = false;

        // Xóa trạng thái khóa
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.remove(KEY_UNLOCK_TIME);
        editor.apply();

        if (listener != null) {
            listener.onButtonUnlocked();
        }
    }

    public boolean isLocked() {
        return isLocked;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public FaceDatabase getFaceDatabase() {
        return faceDatabase;
    }

    public void destroy() {
        if (lockoutTimer != null) {
            lockoutTimer.cancel();
            lockoutTimer = null;
        }
    }
}