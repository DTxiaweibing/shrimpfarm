package com.shrimpfarm.app.model;

import android.util.Log;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WeatherHelper {

    private static final String TAG = "WeatherHelper";

    private static final String API_HOST = "ku33jr9ct3.re.qweatherapi.com";
    private static final String KEY_ID = "KKB33C6QW6";
    private static final String PROJECT_ID = "4C88VKET5W";
    private static final String PRIVATE_KEY_B64 = "MC4CAQAwBQYDK2VwBCIEIDUg5O7nq8Wkoked7BrxooGkfZwwv5kbdigvhcX/6dzd";

    private static String qweatherApiKey;

    private static PrivateKey privateKey;
    private static String cachedToken;
    private static long tokenExpiresAt;

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    public static void setApiKey(String key) {
        qweatherApiKey = key;
    }

    private static PrivateKey loadPrivateKey() throws Exception {
        if (privateKey == null) {
            byte[] keyBytes = Base64.getDecoder().decode(PRIVATE_KEY_B64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            try {
                KeyFactory kf = KeyFactory.getInstance("Ed25519");
                privateKey = kf.generatePrivate(spec);
            } catch (Exception e) {
                Log.w(TAG, "Ed25519 KeyFactory failed, trying EdDSA", e);
                KeyFactory kf = KeyFactory.getInstance("EdDSA");
                privateKey = kf.generatePrivate(spec);
            }
        }
        return privateKey;
    }

    private static synchronized String getToken() {
        long now = System.currentTimeMillis() / 1000;
        if (cachedToken != null && now < tokenExpiresAt) {
            return cachedToken;
        }
        try {
            long iat = now - 30;
            long exp = now + 1800;

            String headerStr = "{\"alg\":\"EdDSA\",\"typ\":\"JWT\",\"kid\":\"" + KEY_ID + "\"}";
            String payloadStr = "{\"iss\":\"" + PROJECT_ID + "\",\"iat\":" + iat + ",\"exp\":" + exp + ",\"sub\":\"" + PROJECT_ID + "\"}";

            String b64Header = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(headerStr.getBytes(StandardCharsets.UTF_8));
            String b64Payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payloadStr.getBytes(StandardCharsets.UTF_8));

            String toSign = b64Header + "." + b64Payload;

            Signature sig;
            try {
                sig = Signature.getInstance("Ed25519");
            } catch (Exception e) {
                Log.w(TAG, "Ed25519 Signature not available, trying EdDSA", e);
                sig = Signature.getInstance("EdDSA");
            }
            sig.initSign(loadPrivateKey());
            sig.update(toSign.getBytes(StandardCharsets.UTF_8));
            String b64Sig = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(sig.sign());

            cachedToken = toSign + "." + b64Sig;
            tokenExpiresAt = exp;
            Log.i(TAG, "JWT generated, expires in 30min");
            return cachedToken;
        } catch (Exception e) {
            Log.e(TAG, "JWT generation failed", e);
            return null;
        }
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }

    private static Request.Builder buildAuthRequest(String url) {
        Request.Builder rb = new Request.Builder().url(url);
        String token = getToken();
        if (token != null) {
            rb.header("Authorization", "Bearer " + token);
            Log.d(TAG, "using JWT auth");
            return rb;
        }
        if (qweatherApiKey != null && !qweatherApiKey.isEmpty()) {
            rb.header("X-QW-Api-Key", qweatherApiKey);
            Log.w(TAG, "JWT failed, falling back to API Key");
            return rb;
        }
        Log.e(TAG, "no auth method available (JWT failed, no API Key set)");
        return rb;
    }

    public static JSONObject getWeatherRaw(String location) {
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
        // Try pconline first (reliable in China, auto-detects caller IP)
        String city = geoDirect("http://whois.pconline.com.cn/ipJson.jsp?json=true",
                "pconline");
        if (city != null) return city;
        // Fallback: ip-api.com (also auto-detects)
        city = geoDirect("http://ip-api.com/json?lang=zh-CN&fields=status,city",
                "ip-api");
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
