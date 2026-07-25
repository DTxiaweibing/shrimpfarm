package com.shrimpfarm.app.qa.api;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.shrimpfarm.app.SupabaseAuthManager;
import com.shrimpfarm.app.qa.model.Answer;
import com.shrimpfarm.app.qa.model.Question;
import com.shrimpfarm.app.utils.HttpClientSingleton;

import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class QaApi {
    private static final String SUPABASE_URL = "https://apumkkayconibhkaawdn.supabase.co";
    private static final String ANON_KEY = "sb_publishable_Tn8FsSUL4iDqUsNQGzos6Q_6zMKytC5";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final SupabaseAuthManager auth;
    private final Handler mainHandler;
    private final Gson gson;
    private final Context context;

    private Map<String, String> profileCache;

    public QaApi(Context context) {
        this.context = context.getApplicationContext();
        this.client = HttpClientSingleton.getInstance();
        this.auth = new SupabaseAuthManager(this.context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.gson = new Gson();
        this.profileCache = new HashMap<>();
    }

    public interface QaCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    /**
     * 检测 401/403，区分读/写操作：
     * - 读操作（列表、详情）：静默失败，不清除登录态
     * - 写操作（发布、回答、采纳、删除）：清除登录态，提示重新登录
     */
    private boolean handleAuthError(int httpCode, boolean isWriteOperation) {
        if (httpCode == 401 || httpCode == 403) {
            if (isWriteOperation) {
                auth.logout();
                postMain(() -> {
                    // 写操作才提示用户，但只在主线程
                });
                return true;
            }
            // 读操作：静默失败，不 logout，不弹 Toast
            return true;
        }
        return false;
    }

    public boolean isLoggedIn() {
        return auth.isLoggedIn();
    }

    private String getToken() {
        return auth.getToken();
    }

    private String getAuthToken() {
        return auth.getValidToken();
    }

    public String getCurrentUserId() {
        String token = getToken();
        if (token.isEmpty()) return "";
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return "";
            byte[] decoded = android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE);
            String json = new String(decoded, "UTF-8");
            return new JSONObject(json).optString("sub", "");
        } catch (Exception e) {
            return "";
        }
    }

    public String getCurrentUserEmail() {
        String token = getToken();
        if (token.isEmpty()) return "";
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return "";
            byte[] decoded = android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE);
            String json = new String(decoded, "UTF-8");
            return new JSONObject(json).optString("email", "");
        } catch (Exception e) {
            return "";
        }
    }

    public String getCurrentNickname() {
        return auth.getNickname();
    }

    private Request.Builder authRequest() {
        Request.Builder builder = new Request.Builder()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation");
        try {
            String token = getAuthToken();
            if (!token.isEmpty()) {
                builder.addHeader("Authorization", "Bearer " + token);
            }
        } catch (Exception e) {
            // 任何异常（网络超时、解密失败等）都不影响读操作，降级为匿名请求
        }
        return builder;
    }

    private void postMain(Runnable r) {
        mainHandler.post(r);
    }

    private void fetchProfiles(Set<String> userIds, QaCallback<Map<String, String>> callback) {
        if (userIds.isEmpty()) {
            postMain(() -> callback.onSuccess(new HashMap<>()));
            return;
        }
        List<String> toFetch = new ArrayList<>();
        for (String uid : userIds) {
            if (!profileCache.containsKey(uid)) {
                toFetch.add(uid);
            }
        }
        if (toFetch.isEmpty()) {
            Map<String, String> result = new HashMap<>();
            for (String uid : userIds) {
                result.put(uid, profileCache.get(uid));
            }
            postMain(() -> callback.onSuccess(result));
            return;
        }
        String ids = TextUtils.join(",", toFetch);
        String url = SUPABASE_URL + "/rest/v1/profiles?id=in.(" + ids + ")&select=id,nickname";
        Request request = authRequest().url(url).get().build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                // 网络失败时，用缓存拼一个结果返回，绝不丢失已有昵称
                Map<String, String> result = new HashMap<>();
                for (String uid : userIds) {
                    result.put(uid, profileCache.getOrDefault(uid, ""));
                }
                postMain(() -> callback.onSuccess(result));
            }
            @Override public void onResponse(okhttp3.Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "[]";
                if (response.isSuccessful()) {
                    try {
                        org.json.JSONArray arr = new org.json.JSONArray(body);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            String id = obj.optString("id", "");
                            String nick = obj.optString("nickname", "");
                            if (!id.isEmpty()) {
                                profileCache.put(id, nick);
                            }
                        }
                        Map<String, String> result = new HashMap<>();
                        for (String uid : userIds) {
                            result.put(uid, profileCache.getOrDefault(uid, ""));
                        }
                        postMain(() -> callback.onSuccess(result));
                    } catch (Exception e) {
                        // 解析失败也走缓存兜底
                        Map<String, String> result = new HashMap<>();
                        for (String uid : userIds) {
                            result.put(uid, profileCache.getOrDefault(uid, ""));
                        }
                        postMain(() -> callback.onSuccess(result));
                    }
                } else {
                    // 接口返回 4xx/5xx 时，同样用缓存兜底
                    Map<String, String> result = new HashMap<>();
                    for (String uid : userIds) {
                        result.put(uid, profileCache.getOrDefault(uid, ""));
                    }
                    postMain(() -> callback.onSuccess(result));
                }
            }
        });
    }

    // ========== 问题 CRUD ==========

    public void getQuestions(int page, int pageSize, QaCallback<List<Question>> callback) {
        int offset = (page - 1) * pageSize;
        String url = SUPABASE_URL + "/rest/v1/questions"
                + "?select=*"
                + "&order=created_at.desc"
                + "&limit=" + pageSize
                + "&offset=" + offset;
        Request request = authRequest().url(url).get().build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                postMain(() -> callback.onError("网络错误: " + e.getMessage()));
            }
            @Override public void onResponse(okhttp3.Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "[]";
                if (response.isSuccessful()) {
                    Type type = new TypeToken<List<Question>>(){}.getType();
                    List<Question> list = gson.fromJson(body, type);
                    attachAnswerStats(list, () -> attachProfilesToQuestions(list, () -> postMain(() -> callback.onSuccess(list))));
                } else {
                    if (handleAuthError(response.code(), false)) {
                        // 静默处理，不打扰用户
                    } else {
                        postMain(() -> callback.onError("请求失败: " + response.code()));
                    }
                }
            }
        });
    }

    public void getQuestionDetail(long questionId, QaCallback<Question> callback) {
        String url = SUPABASE_URL + "/rest/v1/questions"
                + "?id=eq." + questionId
                + "&select=*";
        Request request = authRequest().url(url).get().build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                postMain(() -> callback.onError("网络错误: " + e.getMessage()));
            }
            @Override public void onResponse(okhttp3.Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "[]";
                if (response.isSuccessful()) {
                    Type type = new TypeToken<List<Question>>(){}.getType();
                    List<Question> qlist = gson.fromJson(body, type);
                    if (qlist.isEmpty()) {
                        postMain(() -> callback.onError("问题不存在"));
                        return;
                    }
                    Question q = qlist.get(0);
                    fetchAnswers(q, () -> {
                        Set<String> userIds = new HashSet<>();
                        userIds.add(q.userId);
                        if (q.answers != null) {
                            for (Answer a : q.answers) {
                                userIds.add(a.userId);
                            }
                        }
                        fetchProfiles(userIds, new QaCallback<Map<String, String>>() {
                            @Override public void onSuccess(Map<String, String> map) {
                                q.displayName = map.getOrDefault(q.userId, "");
                                if (q.answers != null) {
                                    for (Answer a : q.answers) {
                                        a.displayName = map.getOrDefault(a.userId, "");
                                    }
                                }
                                postMain(() -> callback.onSuccess(q));
                            }
                            @Override public void onError(String error) {
                                postMain(() -> callback.onSuccess(q));
                            }
                        });
                    });
                } else {
                    if (handleAuthError(response.code(), false)) {
                        // 静默处理，不打扰用户
                    } else {
                        postMain(() -> callback.onError("请求失败: " + response.code()));
                    }
                }
            }
        });
    }

    private void fetchAnswers(Question q, Runnable done) {
        String url = SUPABASE_URL + "/rest/v1/answers"
                + "?question_id=eq." + q.id
                + "&order=created_at.asc";
        Request request = authRequest().url(url).get().build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) { done.run(); }
            @Override public void onResponse(okhttp3.Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "[]";
                if (response.isSuccessful()) {
                    Type type = new TypeToken<List<Answer>>(){}.getType();
                    q.answers = gson.fromJson(body, type);
                }
                done.run();
            }
        });
    }

    public void postQuestion(String title, String content, List<String> imageUrls, QaCallback<Question> callback) {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("title", title);
        bodyMap.put("content", content);
        bodyMap.put("user_id", getCurrentUserId());
        if (imageUrls != null && !imageUrls.isEmpty()) {
            bodyMap.put("image_urls", imageUrls);
        }
        String json = gson.toJson(bodyMap);
        Request request = authRequest()
                .url(SUPABASE_URL + "/rest/v1/questions")
                .post(RequestBody.create(json, JSON))
                .build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                postMain(() -> callback.onError("网络错误: " + e.getMessage()));
            }
            @Override public void onResponse(okhttp3.Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    Type type = new TypeToken<List<Question>>(){}.getType();
                    List<Question> list = gson.fromJson(body, type);
                    if (!list.isEmpty()) {
                        postMain(() -> callback.onSuccess(list.get(0)));
                    } else {
                        postMain(() -> callback.onError("发布失败"));
                    }
                } else {
                    if (handleAuthError(response.code(), true)) {
                        // 静默处理，不打扰用户
                    } else {
                        postMain(() -> callback.onError("发布失败: " + response.code()));
                    }
                }
            }
        });
    }

    public void deleteQuestion(long questionId, QaCallback<Void> callback) {
        Request request = authRequest()
                .url(SUPABASE_URL + "/rest/v1/questions?id=eq." + questionId)
                .delete()
                .build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                postMain(() -> callback.onError("网络错误: " + e.getMessage()));
            }
            @Override public void onResponse(okhttp3.Call call, Response response) {
                if (response.isSuccessful()) {
                    postMain(() -> callback.onSuccess(null));
                } else {
                    if (handleAuthError(response.code(), true)) {
                        // 静默处理，不打扰用户
                    } else {
                        postMain(() -> callback.onError("删除失败: " + response.code()));
                    }
                }
            }
        });
    }

    public void deleteAnswer(long answerId, QaCallback<Void> callback) {
        Request request = authRequest()
                .url(SUPABASE_URL + "/rest/v1/answers?id=eq." + answerId)
                .delete()
                .build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                postMain(() -> callback.onError("网络错误: " + e.getMessage()));
            }
            @Override public void onResponse(okhttp3.Call call, Response response) {
                if (response.isSuccessful()) {
                    postMain(() -> callback.onSuccess(null));
                } else {
                    if (handleAuthError(response.code(), true)) {
                    } else {
                        postMain(() -> callback.onError("删除失败: " + response.code()));
                    }
                }
            }
        });
    }

    // ========== 回答 CRUD ==========

    public void postAnswer(long questionId, String content, List<String> imageUrls, QaCallback<Answer> callback) {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("question_id", questionId);
        bodyMap.put("content", content);
        bodyMap.put("user_id", getCurrentUserId());
        if (imageUrls != null && !imageUrls.isEmpty()) {
            bodyMap.put("image_urls", imageUrls);
        }
        String json = gson.toJson(bodyMap);
        Request request = authRequest()
                .url(SUPABASE_URL + "/rest/v1/answers")
                .post(RequestBody.create(json, JSON))
                .build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                postMain(() -> callback.onError("网络错误: " + e.getMessage()));
            }
            @Override public void onResponse(okhttp3.Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    Type type = new TypeToken<List<Answer>>(){}.getType();
                    List<Answer> list = gson.fromJson(body, type);
                    if (!list.isEmpty()) {
                        Answer answer = list.get(0);
                        answer.displayName = getCurrentNickname();
                        postMain(() -> callback.onSuccess(answer));
                    } else {
                        postMain(() -> callback.onError("发布失败"));
                    }
                } else {
                    if (handleAuthError(response.code(), true)) {
                        // 静默处理，不打扰用户
                    } else {
                        postMain(() -> callback.onError("发布失败: " + response.code()));
                    }
                }
            }
        });
    }

    public void acceptAnswer(long questionId, long answerId, QaCallback<Void> callback) {
        String patchJson = "{\"is_accepted\":true}";
        Request request = authRequest()
                .url(SUPABASE_URL + "/rest/v1/answers?id=eq." + answerId)
                .patch(RequestBody.create(patchJson, JSON))
                .build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                postMain(() -> callback.onError("网络错误: " + e.getMessage()));
            }
            @Override public void onResponse(okhttp3.Call call, Response response) {
                if (response.isSuccessful()) {
                    markResolved(questionId, callback);
                } else {
                    if (handleAuthError(response.code(), true)) {
                        // 静默处理，不打扰用户
                    } else {
                        postMain(() -> callback.onError("操作失败: " + response.code()));
                    }
                }
            }
        });
    }

    private void markResolved(long questionId, QaCallback<Void> callback) {
        String patchJson = "{\"is_resolved\":true}";
        Request request = authRequest()
                .url(SUPABASE_URL + "/rest/v1/questions?id=eq." + questionId)
                .patch(RequestBody.create(patchJson, JSON))
                .build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                postMain(() -> callback.onError("网络错误: " + e.getMessage()));
            }
            @Override public void onResponse(okhttp3.Call call, Response response) {
                if (response.isSuccessful()) {
                    postMain(() -> callback.onSuccess(null));
                } else {
                    if (handleAuthError(response.code(), true)) {
                        // 静默处理，不打扰用户
                    } else {
                        postMain(() -> callback.onError("操作失败: " + response.code()));
                    }
                }
            }
        });
    }

    // ========== 投票 ==========

    public void getAnswerVotes(List<Long> answerIds, QaCallback<Map<Long, int[]>> callback) {
        if (answerIds == null || answerIds.isEmpty()) {
            postMain(() -> callback.onSuccess(new HashMap<>()));
            return;
        }
        String ids = TextUtils.join(",", answerIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.toList()));
        String url = SUPABASE_URL + "/rest/v1/answer_votes"
                + "?answer_id=in.(" + ids + ")"
                + "&select=answer_id,user_id,vote_type";
        Request request = authRequest().url(url).get().build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                postMain(() -> callback.onError("网络错误: " + e.getMessage()));
            }
            @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "[]";
                Map<Long, int[]> result = new HashMap<>();
                String currentUserId = getCurrentUserId();
                try {
                    org.json.JSONArray arr = new org.json.JSONArray(body);
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject obj = arr.getJSONObject(i);
                        long aid = obj.optLong("answer_id");
                        String uid = obj.optString("user_id", "");
                        boolean voteType = obj.optBoolean("vote_type");
                        int[] counts = result.get(aid);
                        if (counts == null) {
                            counts = new int[]{0, 0, 0};
                            result.put(aid, counts);
                        }
                        if (voteType) counts[0]++;
                        else counts[1]++;
                        if (uid.equals(currentUserId)) {
                            counts[2] = voteType ? 1 : -1;
                        }
                    }
                } catch (Exception ignored) {}
                Map<Long, int[]> finalResult = new HashMap<>(result);
                for (Long id : answerIds) {
                    if (!finalResult.containsKey(id)) {
                        finalResult.put(id, new int[]{0, 0, 0});
                    }
                }
                postMain(() -> callback.onSuccess(finalResult));
            }
        });
    }

    public void voteAnswer(long answerId, boolean isUpvote, QaCallback<Void> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("answer_id", answerId);
        body.put("user_id", getCurrentUserId());
        body.put("vote_type", isUpvote);
        String json = gson.toJson(body);
        Request request = authRequest()
                .url(SUPABASE_URL + "/rest/v1/answer_votes?on_conflict=answer_id,user_id")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(RequestBody.create(json, JSON))
                .build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                postMain(() -> callback.onError("网络错误: " + e.getMessage()));
            }
            @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) {
                if (response.isSuccessful()) {
                    postMain(() -> callback.onSuccess(null));
                } else {
                    postMain(() -> callback.onError("操作失败: " + response.code()));
                }
            }
        });
    }

    public void cancelVote(long answerId, QaCallback<Void> callback) {
        String userId = getCurrentUserId();
        Request request = authRequest()
                .url(SUPABASE_URL + "/rest/v1/answer_votes?answer_id=eq." + answerId + "&user_id=eq." + userId)
                .delete()
                .build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                postMain(() -> callback.onError("网络错误: " + e.getMessage()));
            }
            @Override public void onResponse(okhttp3.Call call, okhttp3.Response response) {
                if (response.isSuccessful()) {
                    postMain(() -> callback.onSuccess(null));
                } else {
                    postMain(() -> callback.onError("操作失败: " + response.code()));
                }
            }
        });
    }

    // ========== 图片上传 ==========

    public void uploadImage(Uri imageUri, QaCallback<String> callback) {
        try {
            File compressed = compressImage(context, imageUri);
            if (compressed == null) {
                postMain(() -> callback.onError("图片压缩失败"));
                return;
            }
            String fileName = UUID.randomUUID().toString() + ".jpg";
            String uploadUrl = SUPABASE_URL + "/storage/v1/object/qa-images/" + fileName;
            RequestBody fileBody = RequestBody.create(compressed, MediaType.get("image/jpeg"));
            Request request = new Request.Builder()
                    .url(uploadUrl)
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + getAuthToken())
                    .put(fileBody)
                    .build();
            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override public void onFailure(okhttp3.Call call, IOException e) {
                    postMain(() -> callback.onError("上传失败: " + e.getMessage()));
                }
                @Override public void onResponse(okhttp3.Call call, Response response) {
                    if (response.isSuccessful()) {
                        String publicUrl = SUPABASE_URL + "/storage/v1/object/public/qa-images/" + fileName;
                        postMain(() -> callback.onSuccess(publicUrl));
                    } else {
                        if (handleAuthError(response.code(), true)) {
                            // 静默处理，不打扰用户
                        } else {
                            postMain(() -> callback.onError("上传失败: " + response.code()));
                        }
                    }
                }
            });
        } catch (Exception e) {
            postMain(() -> callback.onError("图片处理失败: " + e.getMessage()));
        }
    }

    // ========== 助手方法 ==========

    private void attachProfilesToQuestions(List<Question> questions, Runnable done) {
        if (questions.isEmpty()) { done.run(); return; }
        Set<String> userIds = new HashSet<>();
        for (Question q : questions) userIds.add(q.userId);
        fetchProfiles(userIds, new QaCallback<Map<String, String>>() {
            @Override public void onSuccess(Map<String, String> map) {
                for (Question q : questions) {
                    q.displayName = map.getOrDefault(q.userId, "");
                }
                done.run();
            }
            @Override public void onError(String error) { done.run(); }
        });
    }

    private void attachAnswerStats(List<Question> questions, Runnable done) {
        if (questions.isEmpty()) { done.run(); return; }
        List<String> ids = new ArrayList<>();
        for (Question q : questions) ids.add(String.valueOf(q.id));
        String url = SUPABASE_URL + "/rest/v1/answers"
                + "?select=question_id,created_at"
                + "&question_id=in.(" + TextUtils.join(",", ids) + ")"
                + "&order=created_at.desc";
        Request request = authRequest().url(url).get().build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) { done.run(); }
            @Override public void onResponse(okhttp3.Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "[]";
                if (response.isSuccessful()) {
                    try {
                        org.json.JSONArray arr = new org.json.JSONArray(body);
                        Map<Long, Integer> countMap = new HashMap<>();
                        Map<Long, String> latestMap = new HashMap<>();
                        for (int i = 0; i < arr.length(); i++) {
                            org.json.JSONObject obj = arr.getJSONObject(i);
                            long qid = obj.optLong("question_id");
                            String ca = obj.optString("created_at", "");
                            countMap.put(qid, countMap.getOrDefault(qid, 0) + 1);
                            if (!ca.isEmpty() && !latestMap.containsKey(qid)) {
                                latestMap.put(qid, ca);
                            }
                        }
                        for (Question q : questions) {
                            q.answerCount = countMap.getOrDefault(q.id, 0);
                            q.latestAnswerAt = latestMap.get(q.id);
                        }
                    } catch (Exception ignored) {}
                }
                done.run();
            }
        });
    }

    private static File compressImage(Context ctx, Uri uri) throws IOException {
        android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        try (java.io.InputStream is = ctx.getContentResolver().openInputStream(uri)) {
            android.graphics.BitmapFactory.decodeStream(is, null, opts);
        }
        int maxW = 1920, maxH = 1920;
        int sample = 1;
        while (opts.outWidth / sample > maxW || opts.outHeight / sample > maxH) {
            sample *= 2;
        }
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = sample;
        android.graphics.Bitmap bitmap;
        try (java.io.InputStream is = ctx.getContentResolver().openInputStream(uri)) {
            bitmap = android.graphics.BitmapFactory.decodeStream(is, null, opts);
        }
        if (bitmap == null) return null;
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        if (w > maxW || h > maxH) {
            float ratio = Math.min((float) maxW / w, (float) maxH / h);
            w = (int) (w * ratio);
            h = (int) (h * ratio);
            android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, w, h, true);
            if (scaled != bitmap) bitmap.recycle();
            bitmap = scaled;
        }
        File dir = new File(ctx.getCacheDir(), "qa_uploads");
        if (!dir.exists()) dir.mkdirs();
        File outFile = new File(dir, UUID.randomUUID().toString() + ".jpg");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, fos);
        }
        bitmap.recycle();
        return outFile;
    }

    private static class TextUtils {
        static String join(String delimiter, List<String> list) {
            if (list == null || list.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(delimiter);
                sb.append(list.get(i));
            }
            return sb.toString();
        }
    }
}
