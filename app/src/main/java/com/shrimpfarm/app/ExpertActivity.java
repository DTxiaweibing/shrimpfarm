package com.shrimpfarm.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.util.Log;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shrimpfarm.app.model.KnowledgeBase;
import com.shrimpfarm.app.model.KnowledgeBaseUpdater;
import com.shrimpfarm.app.model.RagPipeline;

import com.shrimpfarm.app.model.TokenEmbedder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ExpertActivity extends AppCompatActivity {

    private static final String CLOUD_API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    private static final String CLOUD_MODEL = "glm-4-flash";
    private static final String KEY_REMOTE_URL = "https://dtxiaweibing.github.io/TIMU/ai_key.txt";
    private static final String KEY_FALLBACK = "f2g1b791bg4452:5b:e59gcbeeegf7c8/qzV[oylghtg6HKx:";
    private static final String TAG = "ExpertActivity";
    private static final String SYSTEM_PROMPT =
        "你是一位南美白对虾小棚养殖专家。你的任务是：\n" +
        "1. 严格基于提供的参考知识回答，绝不使用你预训练中学到的其他知识。\n" +
        "2. 参考知识中【首要执行规则】优先于【理论背景】。如果规则中有具体操作数值（如100斤/亩、0.3mg/L），必须原样引用。\n" +
        "3. 绝对禁止展开讲解化学反应机理、水化学基础理论，除非用户明确问\"为什么\"。\n" +
        "4. 回答必须口语化、简洁、直接，像老师傅在塘口说话，不要像教科书。";

    private RecyclerView chatList;
    private EditText inputMessage;
    private Button btnSend;
    private Button btnToggleInput;
    private Button btnHoldSpeak;
    private ChatAdapter adapter;
    private boolean isKeyboardMode = true;

    private static final int VOICE_REQUEST_CODE = 200;
    private static final boolean ENABLE_ROUTING = false;

    private TokenEmbedder embedder;
    private KnowledgeBase knowledgeBase;
    private String cloudApiKey;
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;
    private boolean isVoiceInput = false;
    private Vibrator vibrator;

    private final List<ChatMessage> messages = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean initialized = false;

    private static final int TYPE_BOT = 0;
    private static final int TYPE_USER = 1;
    private static final int TYPE_DEBUG = 2;
    private static final int TYPE_ANIMATION = 3;

    private int animationMsgIndex = -1;
    private int animationDotCount = 1;
    private String animationPrefix = "";
    private Runnable animationRunnable;

    private static class ChatMessage {
        final int type;
        String text;
        final String time;
        ChatMessage(int type, String text) {
            this.type = type;
            this.text = text;
            this.time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        }
    }

    private static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
        private final List<ChatMessage> messages;
        ChatAdapter(List<ChatMessage> messages) { this.messages = messages; }
        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView textMsg; final TextView textTime; final View bubble;
            ViewHolder(View itemView, int viewType) {
                super(itemView);
                if (viewType == TYPE_DEBUG) {
                    textMsg = itemView.findViewById(com.shrimpfarm.app.R.id.text_debug); textTime = null; bubble = null;
                } else if (viewType == TYPE_ANIMATION) {
                    textMsg = itemView.findViewById(com.shrimpfarm.app.R.id.text_animation); textTime = null; bubble = null;
                } else {
                    textMsg = itemView.findViewById(com.shrimpfarm.app.R.id.text_message); textTime = itemView.findViewById(com.shrimpfarm.app.R.id.text_time); bubble = itemView.findViewById(com.shrimpfarm.app.R.id.bubble);
                }
            }
        }
        @Override public int getItemViewType(int position) { return messages.get(position).type; }
        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout;
            if (viewType == TYPE_DEBUG) layout = com.shrimpfarm.app.R.layout.item_chat_debug;
            else if (viewType == TYPE_ANIMATION) layout = com.shrimpfarm.app.R.layout.item_chat_animation;
            else layout = viewType == TYPE_USER ? com.shrimpfarm.app.R.layout.item_chat_user : com.shrimpfarm.app.R.layout.item_chat_bot;
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false), viewType);
        }
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChatMessage msg = messages.get(position);
            holder.textMsg.setText(msg.text);
            if (holder.textTime != null) holder.textTime.setText(msg.time);
        }
        @Override public int getItemCount() { return messages.size(); }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.shrimpfarm.app.R.layout.activity_expert);

        chatList = findViewById(com.shrimpfarm.app.R.id.chat_list);
        inputMessage = findViewById(com.shrimpfarm.app.R.id.input_message);
        btnSend = findViewById(com.shrimpfarm.app.R.id.btn_send);
        btnToggleInput = findViewById(com.shrimpfarm.app.R.id.btn_toggle_input);
        btnHoldSpeak = findViewById(com.shrimpfarm.app.R.id.btn_hold_speak);

        chatList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatAdapter(messages);
        chatList.setAdapter(adapter);

        addBotWelcome();

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        btnSend.setOnClickListener(v -> sendMessage());
        inputMessage.setOnEditorActionListener((v, actionId, event) -> { sendMessage(); return true; });

        btnToggleInput.setOnClickListener(v -> toggleInputMode());

        btnHoldSpeak.setOnClickListener(v -> startVoiceInput());

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) { textToSpeech.setLanguage(Locale.CHINESE); ttsReady = true; }
        });

        executor.execute(this::initModels);
    }

    private void initModels() {
        try {
            loadApiKey();
            embedder = new TokenEmbedder(this);
            knowledgeBase = new KnowledgeBase(this);
            KnowledgeBaseUpdater.checkUpdate(this);
            initialized = true;
            Log.i(TAG, "Init OK, KB=" + knowledgeBase.size());
        } catch (Throwable t) {
            String err = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            Log.e(TAG, "Init failed: " + err);
        }
    }

    private static String deobfuscate(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) sb.append((char)(s.charAt(i) - 1));
        return sb.toString();
    }

    private void loadApiKey() {
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build();
            Request req = new Request.Builder().url(KEY_REMOTE_URL).build();
            try (Response resp = client.newCall(req).execute()) {
                if (resp.isSuccessful()) {
                    String remote = resp.body().string().trim();
                    String decrypted = deobfuscate(remote);
                    if (decrypted.length() > 10) {
                        cloudApiKey = decrypted;
                        Log.i(TAG, "API: remote OK");
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}
        cloudApiKey = deobfuscate(KEY_FALLBACK);
        Log.i(TAG, "API: fallback key");
    }

    private void toggleInputMode() {
        isKeyboardMode = !isKeyboardMode;
        if (isKeyboardMode) {
            inputMessage.setVisibility(View.VISIBLE);
            btnHoldSpeak.setVisibility(View.GONE);
            btnToggleInput.setText("麦");
        } else {
            inputMessage.setVisibility(View.GONE);
            btnHoldSpeak.setVisibility(View.VISIBLE);
            btnToggleInput.setText("键");
        }
    }

    @SuppressWarnings("deprecation")
    private void startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 100);
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出您的问题");
        if (intent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, "设备不支持语音识别", Toast.LENGTH_SHORT).show();
            return;
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
        }
        startActivityForResult(intent, VOICE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null) am.playSoundEffect(AudioManager.FX_KEY_CLICK);
                isVoiceInput = true;
                inputMessage.setText(matches.get(0));
                sendMessage();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceInput();
        } else {
            Toast.makeText(this, "需要录音权限", Toast.LENGTH_SHORT).show();
        }
    }

    // ========== 等待动画控制 ==========

    private void startAnimation(String prefix) {
        animationPrefix = prefix;
        animationDotCount = 1;
        if (animationMsgIndex < 0) {
            messages.add(new ChatMessage(TYPE_ANIMATION, buildAnimationText()));
            animationMsgIndex = messages.size() - 1;
            adapter.notifyItemInserted(animationMsgIndex);
            chatList.scrollToPosition(animationMsgIndex);
        }
        stopAnimationTimer();
        animationRunnable = new Runnable() {
            @Override
            public void run() {
                animationDotCount = (animationDotCount % 4) + 1;
                messages.get(animationMsgIndex).text = buildAnimationText();
                adapter.notifyItemChanged(animationMsgIndex);
                mainHandler.postDelayed(this, 500);
            }
        };
        mainHandler.postDelayed(animationRunnable, 500);
    }

    private void transitionAnimation(String newPrefix) {
        if (animationMsgIndex < 0) { startAnimation(newPrefix); return; }
        animationPrefix = newPrefix;
        messages.get(animationMsgIndex).text = buildAnimationText();
        adapter.notifyItemChanged(animationMsgIndex);
    }

    private void stopAnimation() {
        stopAnimationTimer();
        if (animationMsgIndex >= 0) {
            messages.remove(animationMsgIndex);
            adapter.notifyItemRemoved(animationMsgIndex);
            animationMsgIndex = -1;
        }
    }

    private void stopAnimationTimer() {
        if (animationRunnable != null) {
            mainHandler.removeCallbacks(animationRunnable);
            animationRunnable = null;
        }
    }

    private String buildAnimationText() {
        StringBuilder sb = new StringBuilder(animationPrefix);
        for (int i = 0; i < animationDotCount; i++) sb.append(".");
        return sb.toString();
    }

    // ========== 消息发送 ==========

    private void sendMessage() {
        String text = inputMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        inputMessage.setText("");
        addUserMessage(text);
        startAnimation("正在提炼问题");
        if (!initialized) { Log.w(TAG, "Not initialized"); stopAnimation(); btnSend.setEnabled(true); return; }
        if (cloudApiKey == null) { Log.w(TAG, "No API key"); stopAnimation(); btnSend.setEnabled(true); return; }
        btnSend.setEnabled(false);
        final boolean wasVoice = isVoiceInput;
        isVoiceInput = false;
        executor.execute(() -> processQuery(text, wasVoice));
    }

    private void processQuery(String query, boolean wasVoice) {
        try {
            Log.i(TAG, "Processing query: " + query);
            RagPipeline pipeline = new RagPipeline();
            RagPipeline.Result result = pipeline.process(query, embedder, knowledgeBase,
                    ENABLE_ROUTING, SYSTEM_PROMPT);

            if (result.isLocal) {
                mainHandler.post(() -> {
                    stopAnimation();
                    messages.add(new ChatMessage(TYPE_BOT, result.text));
                    adapter.notifyItemInserted(messages.size() - 1);
                    chatList.scrollToPosition(messages.size() - 1);
                    btnSend.setEnabled(true);
                    if (wasVoice && ttsReady)
                        textToSpeech.speak(result.text, TextToSpeech.QUEUE_FLUSH, null, null);
                });
            } else {
                String context = buildConversationContext();
                String promptWithContext = context.isEmpty()
                        ? result.promptForApi
                        : context + "\n" + result.promptForApi;
                mainHandler.post(() -> transitionAnimation("正在联系专家"));
                startStreamingResponse(promptWithContext, wasVoice);
            }
        } catch (Throwable t) {
            String err = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            Log.e(TAG, "Query failed: " + err);
            mainHandler.post(() -> { stopAnimation(); btnSend.setEnabled(true); });
        }
    }

    private String buildConversationContext() {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = messages.size() - 1; i >= 0 && count < 3; i--) {
            ChatMessage msg = messages.get(i);
            if (msg.type == TYPE_BOT) {
                sb.insert(0, "\n专家回答：" + msg.text + "\n");
            } else if (msg.type == TYPE_USER) {
                sb.insert(0, "用户问题：" + msg.text);
                count++;
                if (count < 3) sb.insert(0, "\n---\n");
            }
        }
        if (sb.length() == 0) return "";
        sb.insert(0, "以下是最近的对话历史：\n");
        sb.append("\n---\n请结合历史对话回答当前问题。");
        return sb.toString();
    }

    private void startStreamingResponse(String userPrompt, boolean wasVoice) {
        mainHandler.post(() -> transitionAnimation("专家思考中"));

        final boolean speak = wasVoice;
        callCloudAPIStreaming(userPrompt, new StreamCallback() {
            private final StringBuilder accumulated = new StringBuilder();
            private boolean firstChunk = true;
            private int botMsgIdx = -1;

            @Override
            public void onChunk(String delta) {
                accumulated.append(delta);
                final String text = accumulated.toString();
                mainHandler.post(() -> {
                    if (firstChunk) {
                        firstChunk = false;
                        stopAnimation();
                        messages.add(new ChatMessage(TYPE_BOT, text));
                        botMsgIdx = messages.size() - 1;
                        adapter.notifyItemInserted(botMsgIdx);
                        chatList.scrollToPosition(botMsgIdx);
                    } else if (botMsgIdx >= 0) {
                        messages.get(botMsgIdx).text = text;
                        adapter.notifyItemChanged(botMsgIdx);
                        chatList.scrollToPosition(botMsgIdx);
                    }
                });
            }

            @Override
            public void onComplete() {
                mainHandler.post(() -> {
                    btnSend.setEnabled(true);
                    if (speak && ttsReady)
                        textToSpeech.speak(accumulated.toString(), TextToSpeech.QUEUE_FLUSH, null, null);
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    stopAnimation();
                    String display;
                    if (error.startsWith("API返回")) {
                        display = "专家没看懂问题，请换个问法试试";
                    } else {
                        display = "（请求失败：" + error + "）";
                    }
                    if (botMsgIdx >= 0) {
                        messages.get(botMsgIdx).text = display;
                        adapter.notifyItemChanged(botMsgIdx);
                    } else {
                        messages.add(new ChatMessage(TYPE_BOT, display));
                        adapter.notifyItemInserted(messages.size() - 1);
                    }
                    btnSend.setEnabled(true);
                });
            }
        });
    }

    private interface StreamCallback {
        void onChunk(String delta);
        void onComplete();
        void onError(String error);
    }

    private void callCloudAPIStreaming(String userPrompt, StreamCallback callback) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build();
        JSONObject body = new JSONObject();
        try {
            body.put("model", CLOUD_MODEL);
            body.put("stream", true);
            JSONArray messages = new JSONArray();
            JSONObject sys = new JSONObject(); sys.put("role", "system"); sys.put("content", SYSTEM_PROMPT);
            messages.put(sys);
            JSONObject usr = new JSONObject(); usr.put("role", "user"); usr.put("content", userPrompt);
            messages.put(usr);
            body.put("messages", messages); body.put("temperature", 0.2); body.put("max_tokens", 4096);
        } catch (Exception e) {
            callback.onError("JSON build error");
            return;
        }
        Request request = new Request.Builder()
                .url(CLOUD_API_URL)
                .addHeader("Authorization", "Bearer " + cloudApiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json; charset=utf-8")))
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (!response.isSuccessful()) {
                        String detail = "";
                        try {
                            String bodyStr = response.body().string();
                            if (!bodyStr.isEmpty()) {
                                int len = Math.min(120, bodyStr.length());
                                detail = " (" + bodyStr.replaceAll("[\\r\\n]", " ").substring(0, len) + ")";
                            }
                        } catch (Exception ignored) {}
                        callback.onError("API返回" + response.code() + detail);
                        return;
                    }
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if ("[DONE]".equals(data)) {
                                callback.onComplete();
                                return;
                            }
                            JSONObject chunk = new JSONObject(data);
                            JSONArray choices = chunk.getJSONArray("choices");
                            if (choices.length() > 0) {
                                String delta = choices.getJSONObject(0)
                                        .getJSONObject("delta")
                                        .optString("content", "");
                                if (!delta.isEmpty()) {
                                    callback.onChunk(delta);
                                }
                            }
                        }
                    }
                    callback.onComplete();
                } catch (Exception e) {
                    callback.onError(e.getMessage());
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onError(e.getMessage());
            }
        });
    }

    private void addUserMessage(String text) { messages.add(new ChatMessage(TYPE_USER, text)); adapter.notifyItemInserted(messages.size() - 1); chatList.scrollToPosition(messages.size() - 1); }
    private void addBotWelcome() { messages.add(new ChatMessage(TYPE_BOT, "您好！我是你的小棚养虾智慧助手，有什么问题你问我吧！")); adapter.notifyItemInserted(messages.size() - 1); chatList.scrollToPosition(messages.size() - 1); }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAnimationTimer();
        executor.shutdown();
        if (embedder != null) embedder.close();
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); }
    }
}
