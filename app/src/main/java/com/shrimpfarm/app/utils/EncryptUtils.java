package com.shrimpfarm.app.utils;

public class EncryptUtils {

    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) return plainText;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < plainText.length(); i++) {
            sb.append((char) (plainText.charAt(i) - 1));
        }
        return sb.toString();
    }

    public static String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) return encryptedText;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < encryptedText.length(); i++) {
            sb.append((char) (encryptedText.charAt(i) + 1));
        }
        return sb.toString();
    }
}
