package com.shrimpfarm.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateManager {

    private static final String TAG = "UpdateManager";
    private static final String PREFS_NAME = "update_mgr";
    private static final String KEY_TOKEN = "github_token";
    private static final String KEY_TOKEN_INIT = "token_inited";
    private static final String KEY_UPDATE_SEEN = "update_version_seen";
    private static final String KEY_IGNORED_VERSION = "ignored_version";
    private static final String KEY_REMIND_LATER_UNTIL = "remind_later_until";
    private static final long REMIND_DELAY_MS = 24 * 60 * 60 * 1000L;

    private static final String RAW_12345_URL = "https://raw.githubusercontent.com/DTxiaweibing/TIMU/refs/heads/main/12345.cfg";
    private static final String GITHUB_API_UPDATE_URL = "https://api.github.com/repos/DTxiaweibing/TIMU/contents/update.json?ref=main";
    private static final String UPDATE_PAGE_URL = "https://dtxiaweibing.github.io/TIMU/";

    private static final int SHIFT = 1;

    private Context context;
    private SharedPreferences prefs;
    private Handler mainHandler;
    private ExecutorService executor;
    private UpdateCallback callback;

    public interface UpdateCallback {
        void onUpdateAvailable(String latestVersion, String updateLog);
        void onUpdateChecked(boolean hasUpdate);
        void onError(String error);
    }

    public UpdateManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void setCallback(UpdateCallback callback) {
        this.callback = callback;
    }

    public boolean hasUnseenUpdate() {
        if (!prefs.getBoolean("has_update", false)) return false;
        long remindUntil = prefs.getLong(KEY_REMIND_LATER_UNTIL, 0);
        return System.currentTimeMillis() >= remindUntil;
    }

    public void remindLater() {
        long until = System.currentTimeMillis() + REMIND_DELAY_MS;
        prefs.edit().putLong(KEY_REMIND_LATER_UNTIL, until).apply();
    }

    public void clearRemindLater() {
        prefs.edit().remove(KEY_REMIND_LATER_UNTIL).apply();
    }

    public String getUpdateVersion() {
        return prefs.getString("latest_version", "");
    }

    public String getUpdateLog() {
        return prefs.getString("update_log", "");
    }

    public void markUpdateSeen() {
        prefs.edit()
            .putBoolean("has_update", false)
            .putString(KEY_UPDATE_SEEN, getUpdateVersion())
            .apply();
    }

    public void ignoreVersion(String version) {
        prefs.edit()
            .putString(KEY_IGNORED_VERSION, version)
            .putBoolean("has_update", false)
            .apply();
    }

    public String getIgnoredVersion() {
        return prefs.getString(KEY_IGNORED_VERSION, "");
    }

    public boolean isVersionIgnored(String version) {
        String ignored = getIgnoredVersion();
        return !ignored.isEmpty() && ignored.equals(version);
    }

    public void checkForUpdate(final String currentVersion) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    try {
                        ensureToken();
                    } catch (Exception e) {
                        Log.w(TAG, "令牌获取失败，尝试无令牌访问: " + e.getMessage());
                    }

                    String token = prefs.getString(KEY_TOKEN, null);
                    String json = fetchUpdateJson(token);

                    if (json == null) {
                        notifyError("获取更新信息失败");
                        return;
                    }

                    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                    JsonObject fileInfo = root.getAsJsonObject("file_info");
                    String latestVersion = fileInfo.get("version").getAsString();
                    String updateLog = root.has("update_log") ? root.get("update_log").getAsString() : "新版本已发布";

                    if (isNewerVersion(latestVersion, currentVersion)) {
                        if (!isVersionIgnored(latestVersion)) {
                            prefs.edit()
                                .putBoolean("has_update", true)
                                .putString("latest_version", latestVersion)
                                .putString("update_log", updateLog)
                                .apply();
                            notifyUpdateAvailable(latestVersion, updateLog);
                        } else {
                            prefs.edit().putBoolean("has_update", false).apply();
                            notifyChecked(false);
                        }
                    } else {
                        prefs.edit().putBoolean("has_update", false).apply();
                        notifyChecked(false);
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "检查更新异常: " + e.getMessage());
                    notifyError(e.getMessage());
                }
            }
        });
    }

    private void ensureToken() throws Exception {
        if (prefs.getBoolean(KEY_TOKEN_INIT, false)) return;

        String raw = fetchRawUrl(RAW_12345_URL);
        if (raw == null) throw new Exception("无法获取令牌文件");

        String token = deobfuscate(raw.trim());
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putBoolean(KEY_TOKEN_INIT, true)
            .apply();
        Log.d(TAG, "令牌已获取并缓存");
    }

    public void clearTokenAndReinit() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_TOKEN_INIT)
            .apply();
    }

    private String fetchRawUrl(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)");

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                return sb.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "读取raw URL失败: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private String fetchUpdateJson(String token) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(GITHUB_API_UPDATE_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)");
            conn.setRequestProperty("Accept", "application/json");
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "token " + token);
            }

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JsonObject apiJson = JsonParser.parseString(sb.toString()).getAsJsonObject();
                String base64Content = apiJson.get("content").getAsString().replaceAll("\\s", "");
                byte[] decoded = android.util.Base64.decode(base64Content, android.util.Base64.DEFAULT);
                return new String(decoded, "UTF-8");
            }
        } catch (Exception e) {
            Log.e(TAG, "API请求失败: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private String deobfuscate(String obfuscated) {
        if (obfuscated == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : obfuscated.toCharArray()) {
            sb.append((char) (c - SHIFT));
        }
        return sb.toString();
    }

    public static boolean isNewerVersion(String latest, String current) {
        if (latest == null || current == null) return false;
        if (latest.equals(current)) return false;
        try {
            String[] lp = latest.split("\\.");
            String[] cp = current.split("\\.");
            int max = Math.max(lp.length, cp.length);
            for (int i = 0; i < max; i++) {
                int l = i < lp.length ? Integer.parseInt(lp[i]) : 0;
                int c = i < cp.length ? Integer.parseInt(cp[i]) : 0;
                if (l > c) return true;
                if (l < c) return false;
            }
            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static String getUpdatePageUrl() {
        return UPDATE_PAGE_URL;
    }

    private void notifyUpdateAvailable(final String version, final String log) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (callback != null) callback.onUpdateAvailable(version, log);
            }
        });
    }

    private void notifyChecked(final boolean hasUpdate) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (callback != null) callback.onUpdateChecked(hasUpdate);
            }
        });
    }

    private void notifyError(final String error) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (callback != null) callback.onError(error);
            }
        });
    }

    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
