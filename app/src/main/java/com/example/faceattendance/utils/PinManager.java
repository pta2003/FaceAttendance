package com.example.faceattendance.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.security.KeyStore;
import java.security.SecureRandom;

public class PinManager {
    private static final String PREFS_NAME = "AdminPinPrefs";
    private static final String KEY_ADMIN_PIN = "admin_pin";
    private static final String KEY_PIN_SALT = "pin_salt";
    private static final String DEFAULT_ADMIN_PIN = "123456";
    private static final String KEYSTORE_ALIAS = "AdminPinKey";

    private Context context;
    private SharedPreferences prefs;

    public PinManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Lấy admin PIN hiện tại
     */
    public String getAdminPin() {
        String encryptedPin = prefs.getString(KEY_ADMIN_PIN, null);
        if (encryptedPin != null) {
            try {
                return decryptPin(encryptedPin);
            } catch (Exception e) {
                e.printStackTrace();
                // Nếu không thể giải mã, trả về PIN mặc định
                return DEFAULT_ADMIN_PIN;
            }
        }
        return DEFAULT_ADMIN_PIN;
    }

    /**
     * Cập nhật admin PIN
     */
    public boolean updateAdminPin(String newPin) {
        try {
            String encryptedPin = encryptPin(newPin);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_ADMIN_PIN, encryptedPin);
            return editor.commit();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Kiểm tra PIN có đúng không
     */
    public boolean verifyPin(String inputPin) {
        String currentPin = getAdminPin();
        return currentPin.equals(inputPin);
    }

    /**
     * Kiểm tra xem đã thiết lập PIN chưa
     */
    public boolean isPinSet() {
        return prefs.contains(KEY_ADMIN_PIN);
    }

    /**
     * Reset PIN về mặc định
     */
    public boolean resetToDefault() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_ADMIN_PIN);
        editor.remove(KEY_PIN_SALT);
        return editor.commit();
    }

    /**
     * Mã hóa PIN
     */
    private String encryptPin(String pin) throws Exception {
        // Tạo hoặc lấy secret key từ Android Keystore
        SecretKey secretKey = getOrCreateSecretKey();

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        // Lấy IV (Initialization Vector)
        byte[] iv = cipher.getIV();

        // Mã hóa PIN
        byte[] encryptedPin = cipher.doFinal(pin.getBytes());

        // Kết hợp IV và encrypted data
        byte[] combined = new byte[iv.length + encryptedPin.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedPin, 0, combined, iv.length, encryptedPin.length);

        return Base64.encodeToString(combined, Base64.DEFAULT);
    }

    /**
     * Giải mã PIN
     */
    private String decryptPin(String encryptedPin) throws Exception {
        SecretKey secretKey = getOrCreateSecretKey();

        byte[] combined = Base64.decode(encryptedPin, Base64.DEFAULT);

        // Tách IV và encrypted data
        byte[] iv = new byte[16]; // AES block size
        byte[] encryptedData = new byte[combined.length - 16];
        System.arraycopy(combined, 0, iv, 0, 16);
        System.arraycopy(combined, 16, encryptedData, 0, encryptedData.length);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));

        byte[] decryptedPin = cipher.doFinal(encryptedData);
        return new String(decryptedPin);
    }

    /**
     * Tạo hoặc lấy secret key từ Android Keystore
     */
    private SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEYSTORE_ALIAS, null);
        }

        // Tạo key mới
        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .build();

        keyGenerator.init(keyGenParameterSpec);
        return keyGenerator.generateKey();
    }
}