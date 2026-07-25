package com.shrimpfarm.app;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;

import java.security.MessageDigest;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AppIntegrityChecker {

    private static final String SUPABASE_URL = "https://apumkkayconibhkaawdn.supabase.co";
    private static final String ANON_KEY = "sb_publishable_Tn8FsSUL4iDqUsNQGzos6Q_6zMKytC5";
    private static final String TAG = "Integrity";

    public static volatile boolean verified = true;
    public static volatile boolean checkEverStarted = false;

    public interface IntegrityCallback {
        void onResult(boolean verified);
    }

    public static void startCheck(Context context) {
        startCheck(context, null);
    }

    public static void startCheck(Context context, IntegrityCallback callback) {
        checkEverStarted = true;
        Thread t = new Thread(() -> {
            try {
                String fingerprint = computeFingerprint(context);
                if (fingerprint == null) {
                    Log.e(TAG, "指纹计算失败");
                    postResult(callback, false);
                    return;
                }
                if (!checkWithSupabase(context, fingerprint)) {
                    Log.w(TAG, "Supabase 校验失败，尝试本地指纹兜底");
                    checkWithNative(fingerprint, callback);
                    return;
                }
                postResult(callback, true);
            } catch (Exception e) {
                Log.e(TAG, "校验异常，尝试本地指纹兜底", e);
                try {
                    checkWithNative(computeFingerprint(context), callback);
                } catch (Exception ignored) {
                    postResult(callback, false);
                }
            }
        }, "AppIntegrity-check");
        t.setDaemon(true);
        t.start();
    }

    private static boolean checkWithSupabase(Context context, String fingerprint) throws Exception {
        int versionCode = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0).versionCode;

        String url = SUPABASE_URL + "/rest/v1/app_checksums"
                + "?package_name=eq." + context.getPackageName()
                + "&version_code=eq." + versionCode
                + "&select=allowed_fingerprint";

        Request request = new Request.Builder()
                .url(url)
                .header("apikey", ANON_KEY)
                .header("Authorization", "Bearer " + ANON_KEY)
                .get()
                .build();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) return false;

            String body = response.body() != null ? response.body().string() : "[]";
            JSONArray arr = new JSONArray(body);
            if (arr.length() == 0) return false;

            String allowed = arr.getJSONObject(0).getString("allowed_fingerprint");
            boolean match = allowed.equalsIgnoreCase(fingerprint);
            verified = match;
            Log.i(TAG, "Supabase 校验: " + (match ? "通过" : "失败"));
            return match;
        }
    }

    private static void checkWithNative(String fingerprint, IntegrityCallback callback) {
        try {
            String nativeFp = WatermarkNative.getOfficialFingerprint();
            if (nativeFp == null || nativeFp.isEmpty()) {
                Log.e(TAG, "本地指纹为空，不通过");
                postResult(callback, false);
                return;
            }
            boolean match = nativeFp.equalsIgnoreCase(fingerprint);
            verified = match;
            Log.i(TAG, "本地指纹校验: " + (match ? "通过" : "失败"));
            postResult(callback, match);
        } catch (Exception e) {
            Log.e(TAG, "本地指纹校验异常，不通过", e);
            postResult(callback, false);
        }
    }

    private static void postResult(IntegrityCallback callback, boolean result) {
        if (callback != null) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(result));
        }
    }

    private static String computeFingerprint(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(),
                    PackageManager.GET_SIGNING_CERTIFICATES);
            Signature[] sigs = pi.signingInfo.getApkContentsSigners();
            if (sigs == null || sigs.length == 0) return null;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sigs[0].toByteArray());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "computeFingerprint error", e);
            return null;
        }
    }
}
