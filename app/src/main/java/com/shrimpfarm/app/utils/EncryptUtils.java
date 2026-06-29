package com.shrimpfarm.app.utils;

import android.util.Base64;

import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class EncryptUtils {

    private static final String KEY = "ShrimpFarm2024!!";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final byte VERSION_LEGACY = 0;
    private static final byte VERSION_GCM = 1;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes("UTF-8"), "AES");

            byte[] iv = new byte[GCM_IV_LENGTH];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));

            byte[] combined = new byte[1 + GCM_IV_LENGTH + encrypted.length];
            combined[0] = VERSION_GCM;
            System.arraycopy(iv, 0, combined, 1, GCM_IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, 1 + GCM_IV_LENGTH, encrypted.length);

            return Base64.encodeToString(combined, Base64.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
            return plainText;
        }
    }

    public static String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        try {
            byte[] decoded = Base64.decode(encryptedText, Base64.DEFAULT);
            if (decoded.length > 0 && decoded[0] == VERSION_GCM) {
                return decryptGcm(decoded);
            }
            return decryptLegacy(encryptedText);
        } catch (Exception e) {
            try {
                return decryptLegacy(encryptedText);
            } catch (Exception ex) {
                return encryptedText;
            }
        }
    }

    private static String decryptGcm(byte[] data) throws Exception {
        if (data.length < 1 + GCM_IV_LENGTH + 1) {
            throw new IllegalArgumentException("数据太短");
        }
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(data, 1, iv, 0, GCM_IV_LENGTH);

        byte[] encrypted = new byte[data.length - 1 - GCM_IV_LENGTH];
        System.arraycopy(data, 1 + GCM_IV_LENGTH, encrypted, 0, encrypted.length);

        SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes("UTF-8"), "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, "UTF-8");
    }

    @android.annotation.SuppressLint("GetInstance")
    private static String decryptLegacy(String encryptedText) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes("UTF-8"), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        byte[] decoded = Base64.decode(encryptedText, Base64.DEFAULT);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted, "UTF-8");
    }
}
