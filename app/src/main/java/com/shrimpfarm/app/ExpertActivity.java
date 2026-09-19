package com.shrimpfarm.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import com.shrimpfarm.app.BaseActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shrimpfarm.app.model.KnowledgeBase;
import com.shrimpfarm.app.model.KnowledgeBaseUpdater;
import com.shrimpfarm.app.model.RagPipeline;
import com.shrimpfarm.app.model.Reranker;
import com.shrimpfarm.app.model.TokenEmbedder;
import com.shrimpfarm.app.sherpa.AsrEngine;
import com.shrimpfarm.app.sherpa.TtsEngine;
import com.shrimpfarm.app.sherpa.VoiceModelManager;

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

public class ExpertActivity extends BaseActivity {

    @Override
    protected int getCurrentNavId() {
        return 0;
    }

    private static final String CLOUD_API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    private static final String CLOUD_MODEL = "glm-4-flash";
    private static final String KEY_REMOTE_URL = "https://dtxiaweibing.github.io/TIMU/ai_key.txt";
    private static final String TAG = "ExpertActivity";
    private static final String SYSTEM_PROMPT =
        "你是一位南美白对虾小棚养殖专家。你的任务是：\n" +
        "1. 严格基于提供的参考知识回答，绝不使用你预训练中学到的其他知识。\n" +
        "2. 参考知识中【首要执行规则】优先于【理论背景】。如果规则中有具体操作数值（如100斤/亩、0.3mg/L），必须原样引用。\n" +
        "3. 绝对禁止展开讲解化学反应机理、水化学基础理论，除非用户明确问\"为什么\"。\n" +
        "4. 回答必须口语化、简洁、直接，像老师傅在塘口说话，不要像教科书。\n" +
        "5. 注意用户说\"X度\"时可能指水温（℃）也可能指盐度（格/‰），两者含义完全不同。如果不确定用户指的是水温还是盐度，必须追问澄清。";

    private RecyclerView chatList;
    private EditText inputMessage;
    private ImageButton btnSend;
    private ImageButton btnToggleInput;
    private TextView btnUnleash;
    private LinearLayout btnHoldSpeak;
    private ImageView imgMicHold;
    private ImageButton btnSpeaker;
    private ChatAdapter adapter;
    private boolean isKeyboardMode = true;
    private boolean speakingEnabled = true;
    private int speakSid = 2;

    private static final String PREFS_NAME = "expert_voice";
    private static final String KEY_SPEAKING = "speaking";
    private static final String KEY_SID = "speak_sid";

    private static final boolean ENABLE_ROUTING = false;

    private TokenEmbedder embedder;
    private KnowledgeBase knowledgeBase;
    private Reranker reranker;
    private String cloudApiKey;
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;
    private Vibrator vibrator;
    private AsrEngine asrEngine;
    private TtsEngine ttsEngine;

    private boolean unleashed = false;

    private String getSystemPrompt() {
        if (unleashed) return null;
        return SYSTEM_PROMPT;
    }

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
        final String versionText;
        ChatMessage(int type, String text) {
            this.type = type;
            this.text = text;
            this.time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            this.versionText = null;
        }
        ChatMessage(int type, String text, String versionText) {
            this.type = type;
            this.text = text;
            this.time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            this.versionText = versionText;
        }
    }

    private static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
        private final List<ChatMessage> messages;
        private Runnable replayListener;
        private String replayText;
        ChatAdapter(List<ChatMessage> messages) { this.messages = messages; }
        void setReplayListener(Runnable replayListener) { this.replayListener = replayListener; }
        void setReplayText(String replayText) { this.replayText = replayText; }
        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView textMsg; final TextView textTime; final TextView textKbVersion; final View bubble; final ImageButton btnReplay;
            ViewHolder(View itemView, int viewType) {
                super(itemView);
                if (viewType == TYPE_DEBUG) {
                    textMsg = itemView.findViewById(com.shrimpfarm.app.R.id.text_debug); textTime = null; textKbVersion = null; bubble = null; btnReplay = null;
                } else if (viewType == TYPE_ANIMATION) {
                    textMsg = itemView.findViewById(com.shrimpfarm.app.R.id.text_animation); textTime = null; textKbVersion = null; bubble = null; btnReplay = null;
                } else {
                    textMsg = itemView.findViewById(com.shrimpfarm.app.R.id.text_message);
                    textTime = itemView.findViewById(com.shrimpfarm.app.R.id.text_time);
                    textKbVersion = itemView.findViewById(com.shrimpfarm.app.R.id.text_kb_version);
                    bubble = itemView.findViewById(com.shrimpfarm.app.R.id.bubble);
                    btnReplay = itemView.findViewById(com.shrimpfarm.app.R.id.btn_replay);
                }
            }
        }
        @Override public int getItemViewType(int position) { return messages.get(position).type; }
        @Override @NonNull
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
            if (holder.textKbVersion != null) {
                if (msg.versionText != null) {
                    holder.textKbVersion.setVisibility(View.VISIBLE);
                    holder.textKbVersion.setText(msg.versionText);
                } else {
                    holder.textKbVersion.setVisibility(View.GONE);
                }
            }
            if (holder.btnReplay != null) {
                holder.btnReplay.setVisibility(msg.text == null || msg.text.trim().isEmpty() ? View.GONE : View.VISIBLE);
                holder.btnReplay.setOnClickListener(v -> {
                    setReplayText(msg.text);
                    if (replayListener != null) replayListener.run();
                });
            }
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
        imgMicHold = findViewById(com.shrimpfarm.app.R.id.img_mic_hold);
        btnUnleash = findViewById(com.shrimpfarm.app.R.id.btn_unleash);
        btnSpeaker = findViewById(com.shrimpfarm.app.R.id.btn_speaker);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        speakingEnabled = prefs.getBoolean(KEY_SPEAKING, true);
        speakSid = prefs.getInt(KEY_SID, 2);
        updateSpeakerIcon();

        chatList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatAdapter(messages);
        chatList.setAdapter(adapter);
        adapter.setReplayListener(() -> {
            String text = adapter.replayText;
            if (text == null || text.trim().isEmpty()) return;
            stopCurrentSpeech();
            synchronized (ttsBuffer) { ttsBuffer.setLength(0); }
            if (ttsEngine != null && ttsEngine.isReady()) {
                ttsEngine.speak(text.replace("*", "").replace("#", "").replace("`", "").trim(), speakSid, 1.0f, null);
            } else if (ttsReady) {
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });

        int kbVer = com.shrimpfarm.app.model.KnowledgeBaseUpdater.getLocalVersion(this);
        String welcomeText = getString(R.string.expert_welcome);
        messages.add(new ChatMessage(TYPE_BOT, welcomeText, getString(R.string.expert_kb_version) + kbVer));
        adapter.notifyItemInserted(messages.size() - 1);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        btnSend.setOnClickListener(v -> sendMessage());
        btnUnleash.setOnClickListener(v -> {
            unleashed = !unleashed;
            btnUnleash.setText(unleashed ? getString(R.string.expert_btn_unleash) : getString(R.string.expert_btn_strict));
            btnUnleash.setTextColor(unleashed ? 0xfff59e0b : 0xff10b981);
        });
        inputMessage.setOnEditorActionListener((v, actionId, event) -> { sendMessage(); return true; });

        btnToggleInput.setOnClickListener(v -> toggleInputMode());

        btnHoldSpeak.setOnTouchListener(this::onSpeakTouch);

        btnSpeaker.setOnClickListener(v -> {
            speakingEnabled = !speakingEnabled;
            saveSpeakingPref();
            updateSpeakerIcon();
            if (!speakingEnabled) {
                stopCurrentSpeech();
                ttsBuffer.setLength(0);
            }
        });
        btnSpeaker.setOnLongClickListener(v -> {
            showSidPicker();
            return true;
        });

        startAvd(btnToggleInput.getDrawable());

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
            try {
                reranker = new Reranker(this);
                Log.i(TAG, "Reranker loaded");
            } catch (Exception e) {
                reranker = null;
                Log.w(TAG, "Reranker unavailable: " + e.getMessage());
            }
            initVoiceEngines();
            KnowledgeBaseUpdater.checkUpdate(this);
            initialized = true;
            Log.i(TAG, "Init OK, KB=" + knowledgeBase.size());
        } catch (Throwable t) {
            String err = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            Log.e(TAG, "Init failed: " + err);
        }
    }

    private void initVoiceEngines() {
        VoiceModelManager mgr = VoiceModelManager.getInstance(this);
        mgr.installFromAssets();
        if (mgr.isAsrReady()) {
            asrEngine = new AsrEngine(mgr.asrDir());
            if (!asrEngine.init()) asrEngine = null;
        }
        if (mgr.isTtsReady()) {
            ttsEngine = new TtsEngine(mgr.ttsDir());
            if (!ttsEngine.init()) ttsEngine = null;
        }
    }

    private void speakAnswer(String text) {
        if (!speakingEnabled || text == null || text.trim().isEmpty()) return;
        if (ttsEngine != null && ttsEngine.isReady()) {
            ttsEngine.speak(text, speakSid, 1.0f, null);
        } else if (ttsReady) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private final StringBuilder ttsBuffer = new StringBuilder();

    private void speechChunk(String delta) {
        synchronized (ttsBuffer) {
            if (!speakingEnabled) {
                ttsBuffer.setLength(0);
                return;
            }
            ttsBuffer.append(delta);
            String buf = ttsBuffer.toString();
            int last = -1;
            for (int i = 0; i < buf.length(); i++) {
                char c = buf.charAt(i);
                if (c == '。' || c == '！' || c == '？' || c == '；' || c == '\n') last = i + 1;
            }
            if (last <= 0) return;
            String ready = buf.substring(0, last);
            ttsBuffer.delete(0, last);
            String clean = ready.replace("。", "").replace("！", "").replace("？", "").replace("；", "").replace("\n", "")
                    .replace("*", "").replace("#", "").replace("`", "").trim();
            if (!clean.isEmpty()) speakAnswer(clean);
        }
    }

    private void speechFlush() {
        synchronized (ttsBuffer) {
            if (ttsBuffer.length() == 0) return;
            String left = ttsBuffer.toString().trim();
            ttsBuffer.setLength(0);
            if (!left.isEmpty() && speakingEnabled) speakAnswer(left);
        }
    }

    private void stopCurrentSpeech() {
        if (ttsEngine != null) ttsEngine.stop();
        if (textToSpeech != null) textToSpeech.stop();
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
        } catch (Exception ignored) { /* ignored */ }
        cloudApiKey = WatermarkNative.getAiFallbackKey();
        Log.i(TAG, "API: fallback key");
    }

    private void toggleInputMode() {
        isKeyboardMode = !isKeyboardMode;
        if (isKeyboardMode) {
            inputMessage.setVisibility(View.VISIBLE);
            btnHoldSpeak.setVisibility(View.GONE);
            btnToggleInput.setImageResource(R.drawable.avd_mic);
            startAvd(btnToggleInput.getDrawable());
        } else {
            inputMessage.setVisibility(View.GONE);
            btnHoldSpeak.setVisibility(View.VISIBLE);
            btnToggleInput.setImageResource(R.drawable.ic_keyboard);
            startAvd(imgMicHold.getDrawable());
        }
    }

    private void startAvd(Drawable d) {
        if (d instanceof AnimatedVectorDrawable) {
            ((AnimatedVectorDrawable) d).start();
        }
    }

    private boolean onSpeakTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                beginHoldSpeak();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                endHoldSpeak();
                return true;
            default:
                return false;
        }
    }

    private void beginHoldSpeak() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 100);
            return;
        }
        VoiceModelManager mgr = VoiceModelManager.getInstance(this);
        if (!mgr.isAsrReady()) {
            confirmDownloadModel();
            return;
        }
        if (asrEngine == null || !asrEngine.isReady()) {
            Toast.makeText(this, getString(R.string.expert_toast_asr_loading), Toast.LENGTH_SHORT).show();
            return;
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
        }
        imgMicHold.setImageResource(R.drawable.ic_mic_red);
        asrEngine.startRecording();
    }

    private void endHoldSpeak() {
        imgMicHold.setImageResource(R.drawable.avd_mic);
        if (asrEngine == null || !asrEngine.isRecording()) return;
        asrEngine.stop(new AsrEngine.ResultCallback() {
            @Override
            public void onResult(String text) {
                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
                }
                mainHandler.post(() -> {
                    inputMessage.setText(text);
                    sendMessage();
                });
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> Toast.makeText(ExpertActivity.this,
                        message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void confirmDownloadModel() {
        showStyledConfirmDialog(
                getString(R.string.expert_voice_need_model),
                getString(R.string.expert_voice_need_model_msg),
                new String[]{getString(R.string.expert_voice_download), getString(R.string.expert_voice_cancel)},
                new int[]{0xFF4CAF50, 0xFF757575},
                new android.content.DialogInterface.OnClickListener[]{
                        (d, w) -> startActivity(new Intent(this, VoiceModelDownloadActivity.class)),
                        (d, w) -> { /* cancel */ }
                });
    }

    private void updateSpeakerIcon() {
        if (btnSpeaker == null) return;
        btnSpeaker.setImageResource(speakingEnabled
                ? R.drawable.ic_speaker_on : R.drawable.ic_speaker_off);
    }

    private void saveSpeakingPref() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_SPEAKING, speakingEnabled).apply();
    }

    private void saveSidPref() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putInt(KEY_SID, speakSid).apply();
    }

    private void showSidPicker() {
        int count = (ttsEngine != null && ttsEngine.isReady()) ? ttsEngine.numSpeakers() : 5;
        String[] items = new String[count];
        for (int i = 0; i < count; i++) {
            items[i] = String.format(Locale.getDefault(), "%s %d", getString(R.string.expert_voice_sid), i + 1);
        }
        Dialog dialog = new Dialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_sid_picker, null);
        dialog.setContentView(dialogView);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setCancelable(true);

        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        tvTitle.setText(R.string.expert_voice_sid_title);

        final int current = Math.max(0, Math.min(speakSid, count - 1));
        ListView lv = dialogView.findViewById(R.id.lv_sids);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.item_sid, items);
        lv.setAdapter(adapter);
        lv.setItemChecked(current, true);
        lv.setOnItemClickListener((parent, view, position, id) -> {
            speakSid = position;
            saveSidPref();
            stopCurrentSpeech();
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());

        dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, getString(R.string.expert_toast_perm_granted), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.expert_toast_perm_audio), Toast.LENGTH_SHORT).show();
            }
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
        stopCurrentSpeech();
        synchronized (ttsBuffer) { ttsBuffer.setLength(0); }
        inputMessage.setText("");
        addUserMessage(text);
        startAnimation(unleashed ? getString(R.string.expert_anim_direct) : getString(R.string.expert_anim_refine));
        if (!initialized || cloudApiKey == null) {
            stopAnimation();
            btnSend.setEnabled(true);
            Toast.makeText(this, R.string.expert_toast_ai_loading, Toast.LENGTH_SHORT).show();
            return;
        }
        btnSend.setEnabled(false);
        executor.execute(() -> processQuery(text));
    }

    private void processQuery(String query) {
        try {
            if (unleashed) {
                mainHandler.post(() -> transitionAnimation(getString(R.string.expert_anim_contacting)));
                startStreamingResponse(query);
                return;
            }
            RagPipeline pipeline = new RagPipeline();
            RagPipeline.Result result = pipeline.process(query, embedder, knowledgeBase,
                    reranker, ENABLE_ROUTING, SYSTEM_PROMPT);

            if (result.isLocal) {
                mainHandler.post(() -> {
                    stopAnimation();
                    messages.add(new ChatMessage(TYPE_BOT, result.text));
                    adapter.notifyItemInserted(messages.size() - 1);
                    chatList.scrollToPosition(messages.size() - 1);
                    btnSend.setEnabled(true);
                    speakAnswer(result.text);
                });
            } else {
                String context = buildConversationContext();
                String promptWithContext = context.isEmpty()
                        ? result.promptForApi
                        : context + "\n" + result.promptForApi;
                mainHandler.post(() -> transitionAnimation(getString(R.string.expert_anim_contacting)));
                startStreamingResponse(promptWithContext);
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

    private void startStreamingResponse(String userPrompt) {
        mainHandler.post(() -> transitionAnimation(getString(R.string.expert_anim_thinking)));

        callCloudAPIStreaming(getSystemPrompt(), userPrompt, new StreamCallback() {
            private final StringBuilder accumulated = new StringBuilder();
            private boolean firstChunk = true;
            private int botMsgIdx = -1;

            @Override
            public void onChunk(String delta) {
                accumulated.append(delta);
                speechChunk(delta);
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
                speechFlush();
                mainHandler.post(() -> {
                    btnSend.setEnabled(true);
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    stopAnimation();
                    String display;
                    if (error.startsWith("API返回")) {
                        display = getString(R.string.expert_error_retry);
                    } else {
                        display = getString(R.string.expert_error_request_fail, error);
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

    private void callCloudAPIStreaming(String systemPrompt, String userPrompt, StreamCallback callback) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build();
        JSONObject body = new JSONObject();
        try {
            body.put("model", CLOUD_MODEL);
            body.put("stream", true);
            JSONArray messages = new JSONArray();
            if (systemPrompt != null) {
                JSONObject sys = new JSONObject(); sys.put("role", "system"); sys.put("content", systemPrompt);
                messages.put(sys);
            }
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
                        } catch (Exception ignored) { /* ignored */ }
                        callback.onError("API返回" + response.code() + detail);
                        return;
                    }
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
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
                    }
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
    private void addBotMessage(String text) { messages.add(new ChatMessage(TYPE_BOT, text)); adapter.notifyItemInserted(messages.size() - 1); chatList.scrollToPosition(messages.size() - 1); }
    @Override
    protected void onResume() {
        super.onResume();
        if (asrEngine == null || ttsEngine == null) {
            executor.execute(this::ensureVoiceEngines);
        }
    }

    private void ensureVoiceEngines() {
        VoiceModelManager mgr = VoiceModelManager.getInstance(this);
        mgr.installFromAssets();
        if (asrEngine == null && mgr.isAsrReady()) {
            asrEngine = new AsrEngine(mgr.asrDir());
            if (!asrEngine.init()) asrEngine = null;
        }
        if (ttsEngine == null && mgr.isTtsReady()) {
            ttsEngine = new TtsEngine(mgr.ttsDir());
            if (!ttsEngine.init()) ttsEngine = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAnimationTimer();
        executor.shutdown();
        if (embedder != null) embedder.close();
        if (reranker != null) reranker.close();
        if (asrEngine != null) asrEngine.release();
        if (ttsEngine != null) ttsEngine.release();
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); }
    }
}
