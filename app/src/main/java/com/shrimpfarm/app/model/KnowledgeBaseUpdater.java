package com.shrimpfarm.app.model;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class KnowledgeBaseUpdater {

    private static final String TAG = "KBUpdater";
    private static final String DB_NAME = "knowledge_base.db";
    private static final String VERSION_URL = "https://dtxiaweibing.github.io/TIMU/knowledge_base_version.json";
    private static final String DB_URL = "https://dtxiaweibing.github.io/TIMU/knowledge_base.db";

    public static void checkUpdate(Context context) {
        new Thread(() -> {
            try {
                int localVersion = getLocalVersion(context);
                int remoteVersion = fetchRemoteVersion();
                if (remoteVersion > localVersion) {
                    Log.i(TAG, "New version available: " + remoteVersion + " (local: " + localVersion + ")");
                    downloadDatabase(context);
                } else {
                    Log.i(TAG, "KB up to date (v" + localVersion + ")");
                }
            } catch (Exception e) {
                Log.w(TAG, "KB update check failed: " + e.getMessage());
            }
        }).start();
    }

    private static int getLocalVersion(Context context) {
        File dbFile = new File(context.getFilesDir(), DB_NAME);
        if (!dbFile.exists()) return 0;
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
            try (Cursor c = db.rawQuery("SELECT value FROM metadata WHERE key='version'", null)) {
                if (c.moveToFirst()) return Integer.parseInt(c.getString(0));
            }
        } catch (Exception e) {
            Log.w(TAG, "No version in local DB, assuming v0");
        }
        return 0;
    }

    private static int fetchRemoteVersion() throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build();
        Request req = new Request.Builder().url(VERSION_URL).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) throw new Exception("HTTP " + resp.code());
            String body = resp.body().string();
            org.json.JSONObject json = new org.json.JSONObject(body);
            return json.getInt("version");
        }
    }

    private static void downloadDatabase(Context context) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
                .build();
        Request req = new Request.Builder().url(DB_URL).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) throw new Exception("HTTP " + resp.code());
            File dbFile = new File(context.getFilesDir(), DB_NAME);
            try (InputStream is = resp.body().byteStream();
                 FileOutputStream os = new FileOutputStream(dbFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) != -1) os.write(buf, 0, len);
            }
            Log.i(TAG, "KB updated: " + dbFile.length() + " bytes");
        }
    }
}
