package com.shrimpfarm.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SupabaseAuthManager {

    private static final String SUPABASE_URL = "https://apumkkayconibhkaawdn.supabase.co";
    private static final String ANON_KEY = "sb_publishable_Tn8FsSUL4iDqUsNQGzos6Q_6zMKytC5";
    private static final String PREF_NAME = "supabase_auth";
    private static final String KEY_TOKEN = "access_token";
    private static final String KEY_EMAIL = "user_email";
    private static final String KEY_NICKNAME = "user_nickname";

    private final Context context;
    private final OkHttpClient client;

    public SupabaseAuthManager(Context context) {
        this.context = context;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    public boolean isLoggedIn() {
        return !getToken().isEmpty();
    }

    public String getToken() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TOKEN, "");
    }

    public String getEmail() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_EMAIL, "");
    }

    public String getNickname() {
        String nickname = getNicknameFromStorage();
        if (nickname.contains("@")) return nickname.split("@")[0];
        return nickname;
    }

    private String getNicknameFromStorage() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_NICKNAME, "");
    }

    public String getWebDavAccount() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString("webdav_account", "");
    }

    public String getWebDavPassword() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString("webdav_password", "");
    }

    public void cacheWebDavLocally(String account, String password) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("webdav_account", account)
                .putString("webdav_password", password)
                .apply();
    }

    public void saveNickname(String nickname) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_NICKNAME, nickname)
                .apply();
        syncRecorderToPrefs();
    }

    public void syncRecorderToPrefs() {
        String nickname = getNickname();
        if (!nickname.isEmpty()) {
            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("login_user_name", nickname)
                    .apply();
        }
    }

    public AuthResult login(String email, String password) {
        return loginWithNickname(email, password, null);
    }

    public AuthResult loginWithNickname(String email, String password, String nickname) {
        try {
            JSONObject body = new JSONObject();
            body.put("email", email);
            body.put("password", password);

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/token?grant_type=password")
                    .header("apikey", ANON_KEY)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(MediaType.parse("application/json"), body.toString()))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                android.util.Log.i("SupabaseAuth", "登录响应: " + respBody);
                JSONObject json = new JSONObject(respBody);

                if (!response.isSuccessful()) {
                    String msg = json.optString("error_description", json.optString("msg", "登录失败"));
                    return new AuthResult(false, msg, null, null);
                }

                String accessToken = json.getString("access_token");
                JSONObject user = json.getJSONObject("user");
                String userEmail = user.optString("email", "");
                JSONObject metadata = user.optJSONObject("user_metadata");
                String currentNickname = metadata != null ? metadata.optString("nickname", "") : "";

                // preserve existing local data before login response overwrites it
                String prevNickname = getNicknameFromStorage();
                String prevWebdavAccount = getWebDavAccount();
                String prevWebdavPassword = getWebDavPassword();

                // save token + fresh login data
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_TOKEN, accessToken)
                        .putString(KEY_EMAIL, userEmail)
                        .putString(KEY_NICKNAME, currentNickname)
                        .apply();

                if (nickname != null && !nickname.isEmpty() && !nickname.equals(currentNickname)) {
                    currentNickname = nickname;
                    saveNickname(currentNickname);
                    updateMetadataField("nickname", nickname);
                } else if (currentNickname.isEmpty() && !prevNickname.isEmpty()) {
                    currentNickname = prevNickname;
                    saveNickname(currentNickname);
                } else if (currentNickname.isEmpty()) {
                    currentNickname = userEmail.contains("@") ? userEmail.split("@")[0] : userEmail;
                    saveNickname(currentNickname);
                }

                // restore webdav from previous session if login response didn't carry it
                if (prevWebdavAccount.isEmpty() && prevWebdavPassword.isEmpty()) {
                    if (metadata != null) {
                        String wa = metadata.optString("webdav_account", "");
                        String wp = metadata.optString("webdav_password", "");
                        if (!wa.isEmpty() && !wp.isEmpty()) {
                            cacheWebDavLocally(wa, wp);
                        }
                    }
                } else {
                    cacheWebDavLocally(prevWebdavAccount, prevWebdavPassword);
                }

                syncRecorderToPrefs();
                // try to restore latest data from cloud
                new Thread(() -> restoreFromCloud()).start();
                return new AuthResult(true, "登录成功", currentNickname, userEmail);
            }
        } catch (Exception e) {
            return new AuthResult(false, "网络错误: " + e.getMessage(), null, null);
        }
    }

    public String updateNickname(String nickname) {
        return updateMetadataField("nickname", nickname);
    }

    private SecretKeySpec deriveKey() {
        try {
            String email = getEmail();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(email.getBytes("UTF-8"));
            return new SecretKeySpec(hash, "AES");
        } catch (Exception e) {
            return null;
        }
    }

    private String encrypt(String plaintext) {
        try {
            SecretKeySpec key = deriveKey();
            if (key == null) return plaintext;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            return plaintext;
        }
    }

    private String decrypt(String ciphertext) {
        try {
            SecretKeySpec key = deriveKey();
            if (key == null) return ciphertext;
            byte[] combined = Base64.decode(ciphertext, Base64.NO_WRAP);
            if (combined.length < 13) return ciphertext;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, combined, 0, 12));
            byte[] plaintext = cipher.doFinal(combined, 12, combined.length - 12);
            return new String(plaintext, "UTF-8");
        } catch (Exception e) {
            return ciphertext;
        }
    }

    public String saveWebDavToCloud(String account, String password) {
        cacheWebDavLocally(account, password);
        try {
            JSONObject fields = new JSONObject();
            fields.put("webdav_account", account);
            fields.put("webdav_password", encrypt(password));
            return updateMetadataFields(fields);
        } catch (Exception e) {
            return "网络错误: " + e.getMessage();
        }
    }

    public String clearWebDavFromCloud() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove("webdav_account").remove("webdav_password").apply();

        try {
            JSONObject cur = getCurrentUser();
            JSONObject meta = cur != null ? cur.optJSONObject("user_metadata") : null;
            if (meta == null) return null;
            meta.remove("webdav_account");
            meta.remove("webdav_password");
            return putUserMetadata(meta);
        } catch (Exception e) {
            return "网络错误: " + e.getMessage();
        }
    }

    public String loadWebDavFromCloud() {
        try {
            JSONObject cur = getCurrentUser();
            if (cur == null) return "获取用户信息失败";
            JSONObject meta = cur.optJSONObject("user_metadata");
            if (meta != null) {
                String wa = meta.optString("webdav_account", "");
                String wp = decrypt(meta.optString("webdav_password", ""));
                String nick = meta.optString("nickname", "");
                if (!wa.isEmpty() && !wp.isEmpty()) {
                    context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putString("webdav_account", wa)
                            .putString("webdav_password", wp)
                            .apply();
                }
                if (!nick.isEmpty()) {
                    context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putString(KEY_NICKNAME, nick)
                            .apply();
                    syncRecorderToPrefs();
                }
            }
            return null;
        } catch (Exception e) {
            return "网络错误: " + e.getMessage();
        }
    }

    public void restoreFromCloud() {
        try {
            JSONObject cur = getCurrentUser();
            if (cur == null) return;
            JSONObject meta = cur.optJSONObject("user_metadata");
            if (meta == null) return;
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String nick = meta.optString("nickname", "");
            if (!nick.isEmpty()) {
                prefs.edit().putString(KEY_NICKNAME, nick).apply();
                syncRecorderToPrefs();
            }
            String wa = meta.optString("webdav_account", "");
            String wp = decrypt(meta.optString("webdav_password", ""));
            if (!wa.isEmpty() && !wp.isEmpty()) {
                prefs.edit().putString("webdav_account", wa).putString("webdav_password", wp).apply();
            }
        } catch (Exception ignored) {
        }
    }

    private String updateMetadataField(String key, String value) {
        try {
            JSONObject fields = new JSONObject();
            fields.put(key, value);
            return updateMetadataFields(fields);
        } catch (Exception e) {
            return "网络错误: " + e.getMessage();
        }
    }

    private String updateMetadataFields(JSONObject fields) {
        try {
            JSONObject cur = getCurrentUser();
            if (cur == null) return "无法获取当前用户信息，请检查网络";
            JSONObject meta = cur.optJSONObject("user_metadata");
            android.util.Log.i("SupabaseAuth", "当前user_metadata: " + (meta != null ? meta.toString() : "null"));
            if (meta == null) meta = new JSONObject();
            JSONArray keys = fields.names();
            if (keys != null) {
                for (int i = 0; i < keys.length(); i++) {
                    String k = keys.getString(i);
                    meta.put(k, fields.get(k));
                }
            }
            android.util.Log.i("SupabaseAuth", "PUT新的user_metadata: " + meta.toString());
            return putUserMetadata(meta);
        } catch (Exception e) {
            return "网络错误: " + e.getMessage();
        }
    }

    private JSONObject getCurrentUser() {
        try {
            String token = getToken();
            if (token.isEmpty()) {
                android.util.Log.w("SupabaseAuth", "getCurrentUser: token为空");
                return null;
            }
            Request req = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/user")
                    .header("apikey", ANON_KEY)
                    .header("Authorization", "Bearer " + token)
                    .get()
                    .build();
            try (Response response = client.newCall(req).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                android.util.Log.i("SupabaseAuth", "GET /auth/v1/user 状态码: " + response.code() + " 响应: " + body);
                if (!response.isSuccessful() || body.isEmpty()) return null;
                return new JSONObject(body);
            }
        } catch (Exception e) {
            android.util.Log.e("SupabaseAuth", "getCurrentUser异常", e);
            return null;
        }
    }

    private String putUserMetadata(JSONObject metadata) {
        try {
            JSONObject body = new JSONObject();
            body.put("data", metadata);

            Request req = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/user")
                    .header("apikey", ANON_KEY)
                    .header("Authorization", "Bearer " + getToken())
                    .header("Content-Type", "application/json")
                    .put(RequestBody.create(MediaType.parse("application/json"), body.toString()))
                    .build();

            try (Response response = client.newCall(req).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                android.util.Log.i("SupabaseAuth", "PUT /auth/v1/user 状态码: " + response.code() + " 响应: " + respBody);
                if (!response.isSuccessful()) {
                    JSONObject json = new JSONObject(respBody);
                    return json.optString("msg", json.optString("error_description", "保存失败"));
                }
                return null;
            }
        } catch (Exception e) {
            return "网络错误: " + e.getMessage();
        }
    }

    public AuthResult register(String email, String password, String nickname) {
        try {
            JSONObject metadata = new JSONObject();
            metadata.put("nickname", nickname);

            JSONObject body = new JSONObject();
            body.put("email", email);
            body.put("password", password);
            body.put("user_metadata", metadata);

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/signup")
                    .header("apikey", ANON_KEY)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(MediaType.parse("application/json"), body.toString()))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                JSONObject json = new JSONObject(respBody);

                if (!response.isSuccessful()) {
                    String msg = json.optString("msg", json.optString("error_description", "注册失败"));
                    return new AuthResult(false, msg, null, null);
                }

                if (json.has("access_token")) {
                    String accessToken = json.getString("access_token");
                    context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putString(KEY_TOKEN, accessToken)
                            .putString(KEY_EMAIL, email)
                            .putString(KEY_NICKNAME, nickname)
                            .apply();
                    syncRecorderToPrefs();
                    return new AuthResult(true, "注册成功", nickname, email);
                } else {
                    return new AuthResult(true, "注册成功，请登录", null, email);
                }
            }
        } catch (Exception e) {
            return new AuthResult(false, "网络错误: " + e.getMessage(), null, null);
        }
    }

    public String forgotPassword(String email) {
        try {
            JSONObject body = new JSONObject();
            body.put("email", email);

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/recover")
                    .header("apikey", ANON_KEY)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(MediaType.parse("application/json"), body.toString()))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    return null;
                }
                String respBody = response.body() != null ? response.body().string() : "";
                JSONObject json = new JSONObject(respBody);
                return json.optString("msg", json.optString("error_description", "发送失败"));
            }
        } catch (Exception e) {
            return "网络错误: " + e.getMessage();
        }
    }

    public void logout() {
        // clear webdav from cloud first
        try {
            String token = getToken();
            if (!token.isEmpty()) {
                JSONObject cur = getCurrentUser();
                JSONObject meta = cur != null ? cur.optJSONObject("user_metadata") : null;
                if (meta != null) {
                    meta.remove("webdav_account");
                    meta.remove("webdav_password");
                    putUserMetadata(meta);
                }
            }
        } catch (Exception ignored) {
        }

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("login_user_name", "未登录")
                .apply();
    }

    public static class AuthResult {
        public final boolean success;
        public final String message;
        public final String nickname;
        public final String email;

        AuthResult(boolean success, String message, String nickname, String email) {
            this.success = success;
            this.message = message;
            this.nickname = nickname;
            this.email = email;
        }
    }
}
