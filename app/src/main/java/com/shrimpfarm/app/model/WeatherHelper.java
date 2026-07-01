package com.shrimpfarm.app.model;

import android.util.Log;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WeatherHelper {

    private static final String TAG = "WeatherHelper";

    private static final String API_HOST = "ku33jr9ct3.re.qweatherapi.com";
    private static final String QWEATHER_KEY_REMOTE_URL = "https://dtxiaweibing.github.io/TIMU/qweather_key.txt";
    private static final String QWEATHER_KEY_FALLBACK = ""; // 用户申请到 API Key 后填入（+1 编码）

    private static String qweatherApiKey;

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    private static String deobfuscate(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) sb.append((char)(s.charAt(i) - 1));
        return sb.toString();
    }

    public static void initApiKey() {
        if (qweatherApiKey != null) return;
        try {
            OkHttpClient c = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build();
            Request req = new Request.Builder().url(QWEATHER_KEY_REMOTE_URL).build();
            try (Response resp = c.newCall(req).execute()) {
                if (resp.isSuccessful()) {
                    String remote = resp.body().string().trim();
                    String decrypted = deobfuscate(remote);
                    if (decrypted.length() > 5) {
                        qweatherApiKey = decrypted;
                        Log.i(TAG, "QWeather API Key: remote OK");
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}
        if (!QWEATHER_KEY_FALLBACK.isEmpty()) {
            qweatherApiKey = deobfuscate(QWEATHER_KEY_FALLBACK);
            Log.i(TAG, "QWeather API Key: fallback");
        } else {
            Log.w(TAG, "QWeather API Key not configured");
        }
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }

    private static Request.Builder buildAuthRequest(String url) {
        Request.Builder rb = new Request.Builder().url(url);
        if (qweatherApiKey != null && !qweatherApiKey.isEmpty()) {
            rb.header("X-QW-Api-Key", qweatherApiKey);
            return rb;
        }
        Log.e(TAG, "no QWeather API Key set");
        return rb;
    }

    public static JSONObject getWeatherRaw(String location) {
        if (qweatherApiKey == null || qweatherApiKey.isEmpty()) {
            Log.e(TAG, "getWeatherRaw skipped: no API Key");
            return null;
        }
        try {
            String url = "https://" + API_HOST + "/v7/weather/now?location=" + urlEncode(location) + "&lang=zh";
            Log.d(TAG, "requesting: " + url);

            Request req = buildAuthRequest(url).get().build();

            try (Response resp = client.newCall(req).execute()) {
                String body = resp.body() != null ? resp.body().string() : "";
                Log.d(TAG, "HTTP " + resp.code() + ": " + body.substring(0, Math.min(200, body.length())));
                if (!resp.isSuccessful()) {
                    Log.w(TAG, "API error " + resp.code() + ": " + body);
                    return null;
                }
                JSONObject json = new JSONObject(body);
                String code = json.optString("code");
                if (!"200".equals(code)) {
                    Log.w(TAG, "QWeather error code=" + code + " body=" + body);
                    return null;
                }
                return json;
            }
        } catch (Exception e) {
            Log.e(TAG, "getWeatherRaw failed for location=" + location, e);
            return null;
        }
    }

    public static String getWeatherNow(String location) {
        JSONObject json = getWeatherRaw(location);
        if (json == null) return null;
        try {
            JSONObject now = json.getJSONObject("now");
            String temp = now.getString("temp");
            String text = now.getString("text");
            String windDir = now.optString("windDir", "");
            String windScale = now.optString("windScale", "");
            String humidity = now.optString("humidity", "");
            String feelsLike = now.optString("feelsLike", temp);
            return String.format(Locale.CHINA,
                    "当前天气：%s，%s°C（体感%s°C），%s风%s级，湿度%s%%",
                    text, temp, feelsLike, windDir, windScale, humidity);
        } catch (Exception e) {
            Log.e(TAG, "format weather failed", e);
            return null;
        }
    }

    public static String getCityByIP() {
        // Try ip-api.com with HTTPS first
        String city = geoDirect("https://ip-api.com/json?lang=zh-CN&fields=status,city", "ip-api");
        if (city != null) return city;
        // Fallback: pconline (HTTP only, needs network_security_config)
        city = geoDirect("http://whois.pconline.com.cn/ipJson.jsp?json=true", "pconline");
        if (city != null) return city;
        Log.w(TAG, "all IP geolocation services failed");
        return null;
    }

    private static String geoDirect(String url, String source) {
        try {
            Request req = new Request.Builder().url(url).get().build();
            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    Log.w(TAG, source + " failed: " + resp.code());
                    return null;
                }
                String body = resp.body().string();
                JSONObject geo = new JSONObject(body);
                String city;
                if ("pconline".equals(source)) {
                    city = geo.optString("city", "");
                    if (city.endsWith("\u5e02")) city = city.substring(0, city.length() - 1);
                } else {
                    if (!"success".equals(geo.optString("status"))) {
                        Log.w(TAG, source + " status not success: " + geo);
                        return null;
                    }
                    city = geo.optString("city", "");
                }
                if (city.isEmpty()) return null;
                Log.i(TAG, source + " city=" + city);
                return city;
            }
        } catch (Exception e) {
            Log.e(TAG, source + " exception", e);
            return null;
        }
    }

    public static String getWeatherForCurrentLocation() {
        String city = getCityByIP();
        if (city == null) return null;
        return city + "，" + getWeatherNow(city);
    }

    public static String getWeatherJsonForAI(String query, String location) {
        JSONObject raw = getWeatherRaw(location);
        if (raw == null) return null;
        try {
            JSONObject now = raw.getJSONObject("now");
            now.put("query", query);
            now.put("city", location);
            return now.toString();
        } catch (Exception e) {
            Log.e(TAG, "getWeatherJsonForAI failed", e);
            return null;
        }
    }
}
