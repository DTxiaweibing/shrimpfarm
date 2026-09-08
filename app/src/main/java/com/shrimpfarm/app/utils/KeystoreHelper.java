package com.shrimpfarm.app.utils;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 基于 Android Keystore 的本地密钥管理。
 * 密钥不可导出，由系统硬件级安全存储保护，用于加密本地保存的敏感凭证。
 */
public final class KeystoreHelper {

    private static final String TAG = "KeystoreHelper";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "shrimpfarm_local_secrets";

    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LEN = 12;

    private KeystoreHelper() {
    }

    private static SecretKey getOrCreateKey() {
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            SecretKey key = (SecretKey) ks.getKey(KEY_ALIAS, null);
            if (key != null) {
                return key;
            }
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            kg.init(new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return kg.generateKey();
        } catch (Exception e) {
            Log.e(TAG, "getOrCreateKey failed", e);
            return null;
        }
    }

    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        try {
            SecretKey key = getOrCreateKey();
            if (key == null) {
                return "";
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "encrypt failed", e);
            return "";
        }
    }

    public static String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        try {
            SecretKey key = getOrCreateKey();
            if (key == null) {
                return "";
            }
            byte[] combined = Base64.decode(ciphertext, Base64.NO_WRAP);
            if (combined.length < IV_LEN + 1) {
                return "";
            }
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(combined, 0, iv, 0, IV_LEN);
            byte[] ct = new byte[combined.length - IV_LEN];
            System.arraycopy(combined, IV_LEN, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plain = cipher.doFinal(ct);
            return new String(plain, "UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "decrypt failed", e);
            return "";
        }
    }
}
