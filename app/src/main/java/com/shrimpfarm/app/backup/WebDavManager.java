package com.shrimpfarm.app.backup;

import android.content.Context;
import android.content.SharedPreferences;

import com.shrimpfarm.app.DatabaseHelper;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WebDavManager {

    private static final String WEBDAV_URL = "https://dav.jianguoyun.com/dav/";
    private static final String BACKUP_DIR = "小棚养虾备份/";
    private static final String DB_NAME = "FeedingRecord.db";
    private static final int MAX_BACKUPS = 20;

    private static final String PREF_NAME = "webdav_config";
    private static final String KEY_USERNAME = "webdav_username";
    private static final String KEY_PASSWORD = "webdav_password";
    private static final String KEY_CONNECTED = "webdav_connected";

    private final Context context;
    private final OkHttpClient client;
    private String username;
    private String password;

    public WebDavManager(Context context) {
        this.context = context;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    public void initConnection(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public boolean isConfigured() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String u = prefs.getString(KEY_USERNAME, "");
        String p = prefs.getString(KEY_PASSWORD, "");
        return !u.isEmpty() && !p.isEmpty();
    }

    public boolean isConnected() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_CONNECTED, false);
    }

    public String getSavedUsername() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_USERNAME, "");
    }

    public String getSavedPassword() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_PASSWORD, "");
    }

    public void saveConfig(String username, String password) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .putBoolean(KEY_CONNECTED, true)
                .apply();
        initConnection(username, password);
    }

    public void clearConfig() {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
        username = null;
        password = null;
    }

    public String testConnection() throws Exception {
        String response = execPropfind(WEBDAV_URL);
        int count = 0;
        int idx = 0;
        while ((idx = response.indexOf("<d:response>", idx)) != -1) {
            count++;
            idx += 12;
        }
        return "连接成功，根目录共 " + count + " 个文件/文件夹";
    }

    public void ensureBackupDir() throws Exception {
        String dirUrl = WEBDAV_URL + BACKUP_DIR;
        try {
            execPropfind(dirUrl);
        } catch (Exception e) {
            execMkcol(dirUrl);
        }
    }

    public String uploadBackup() throws Exception {
        File dbFile = context.getDatabasePath(DB_NAME);
        if (dbFile == null || !dbFile.exists()) {
            dbFile = new File(context.getFilesDir().getParent() + "/databases/" + DB_NAME);
            if (!dbFile.exists()) throw new Exception("数据库文件不存在");
        }

        try {
            com.shrimpfarm.app.DatabaseHelper helper = new com.shrimpfarm.app.DatabaseHelper(context);
            android.database.sqlite.SQLiteDatabase db = helper.getWritableDatabase();
            if (db != null) {
                db.execSQL("PRAGMA wal_checkpoint(TRUNCATE)");
            }
            helper.close();
        } catch (Exception ignored) {
        } finally {
            com.shrimpfarm.app.DatabaseHelper.closeInstance();
        }

        ensureBackupDir();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault());
        String fileName = "DataBackup_" + sdf.format(new Date()) + ".db";
        String remotePath = WEBDAV_URL + BACKUP_DIR + fileName;

        execPut(remotePath, dbFile);
        cleanupOldBackups();

        return fileName;
    }

    public List<String> listBackups() throws Exception {
        String xml = execPropfind(WEBDAV_URL + BACKUP_DIR);
        return parsePropfindResponse(xml);
    }

    public void downloadBackup(String fileName, File localFile) throws Exception {
        String remotePath = WEBDAV_URL + BACKUP_DIR + fileName;
        execGet(remotePath, localFile);
    }

    public void deleteBackup(String fileName) throws Exception {
        execDelete(WEBDAV_URL + BACKUP_DIR + fileName);
    }

    private void cleanupOldBackups() throws Exception {
        List<String> backups = listBackups();
        if (backups.size() <= MAX_BACKUPS) return;
        java.util.Collections.sort(backups);
        int toDelete = backups.size() - MAX_BACKUPS;
        for (int i = 0; i < toDelete; i++) {
            try { execDelete(WEBDAV_URL + BACKUP_DIR + backups.get(i)); } catch (Exception ignored) {}
        }
    }

    private List<String> parsePropfindResponse(String xml) {
        List<String> names = new ArrayList<>();
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(xml));

            String currentName = null;
            boolean isCollection = false;
            int eventType = parser.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tag = parser.getName();
                if (eventType == XmlPullParser.START_TAG) {
                    String localName = tag != null && tag.contains(":") ? tag.substring(tag.indexOf(":") + 1) : tag;
                    if ("displayname".equalsIgnoreCase(localName)) {
                        eventType = parser.next();
                        if (eventType == XmlPullParser.TEXT) {
                            currentName = parser.getText();
                        }
                    }
                    if ("resourcetype".equalsIgnoreCase(localName)) {
                        int depth = 1;
                        while (depth > 0) {
                            eventType = parser.next();
                            if (eventType == XmlPullParser.START_TAG) {
                                depth++;
                                String inner = parser.getName();
                                String localInner = inner != null && inner.contains(":") ? inner.substring(inner.indexOf(":") + 1) : inner;
                                if ("collection".equalsIgnoreCase(localInner)) isCollection = true;
                            } else if (eventType == XmlPullParser.END_TAG) {
                                depth--;
                            }
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    String localName = tag != null && tag.contains(":") ? tag.substring(tag.indexOf(":") + 1) : tag;
                    if ("response".equalsIgnoreCase(localName)) {
                        if (currentName != null && !isCollection && currentName.startsWith("DataBackup_")) {
                            names.add(currentName);
                        }
                        currentName = null;
                        isCollection = false;
                    }
                }
                eventType = parser.next();
            }
        } catch (Exception ignored) {}
        return names;
    }

    private Request.Builder authRequest(String url) {
        String credential = okhttp3.Credentials.basic(username, password);
        return new Request.Builder().url(url).header("Authorization", credential);
    }

    private String execPropfind(String url) throws Exception {
        String bodyXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<d:propfind xmlns:d=\"DAV:\"><d:prop>" +
                "<d:displayname/><d:resourcetype/><d:getcontentlength/><d:getlastmodified/>" +
                "</d:prop></d:propfind>";

        Request request = authRequest(url)
                .method("PROPFIND", RequestBody.create(MediaType.parse("application/xml"), bodyXml))
                .header("Depth", "1")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 401) throw new Exception("401 Unauthorized");
            if (!response.isSuccessful()) throw new Exception("HTTP " + response.code());
            return response.body() != null ? response.body().string() : "";
        }
    }

    private void execMkcol(String url) throws Exception {
        Request request = authRequest(url).method("MKCOL", RequestBody.create(null, new byte[0])).build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 401) throw new Exception("401 Unauthorized");
            if (!response.isSuccessful() && response.code() != 405) throw new Exception("MKCOL failed: " + response.code());
        }
    }

    private void execPut(String url, File file) throws Exception {
        Request request = authRequest(url)
                .put(RequestBody.create(MediaType.parse("application/octet-stream"), file))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 401) throw new Exception("401 Unauthorized");
            if (!response.isSuccessful()) throw new Exception("PUT failed: " + response.code());
        }
    }

    private void execGet(String url, File localFile) throws Exception {
        Request request = authRequest(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 401) throw new Exception("401 Unauthorized");
            if (!response.isSuccessful()) throw new Exception("GET failed: " + response.code());
            if (response.body() != null) {
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(localFile)) {
                    fos.write(response.body().bytes());
                    fos.flush();
                }
            }
        }
    }

    private void execDelete(String url) throws Exception {
        Request request = authRequest(url).delete().build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 401) throw new Exception("401 Unauthorized");
            if (!response.isSuccessful() && response.code() != 404) throw new Exception("DELETE failed: " + response.code());
        }
    }
}
