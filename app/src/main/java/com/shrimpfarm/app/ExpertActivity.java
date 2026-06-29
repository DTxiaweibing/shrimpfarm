package com.shrimpfarm.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shrimpfarm.app.model.ChlorineHelper;
import com.shrimpfarm.app.model.DOHelper;
import com.shrimpfarm.app.model.H2SHelper;
import com.shrimpfarm.app.model.KnowledgeBase;
import com.shrimpfarm.app.model.KnowledgeBaseUpdater;
import com.shrimpfarm.app.model.NitriteHelper;
import com.shrimpfarm.app.model.ORPHelper;
import com.shrimpfarm.app.model.Reranker;
import com.shrimpfarm.app.model.ShrimpAdviceHelper;
import com.shrimpfarm.app.model.SynonymExpander;
import com.shrimpfarm.app.model.TokenEmbedder;
import com.shrimpfarm.app.model.VibrioHelper;
import com.shrimpfarm.app.utils.EncryptUtils;

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
    private Reranker reranker;
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
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            int layout;
            if (viewType == TYPE_DEBUG) layout = com.shrimpfarm.app.R.layout.item_chat_debug;
            else if (viewType == TYPE_ANIMATION) layout = com.shrimpfarm.app.R.layout.item_chat_animation;
            else layout = viewType == TYPE_USER ? com.shrimpfarm.app.R.layout.item_chat_user : com.shrimpfarm.app.R.layout.item_chat_bot;
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false), viewType);
        }
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
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

        addBotMessage("您好！我是你的小棚养虾智慧助手，有什么问题你问我吧！");

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
            try {
                reranker = new Reranker(this);
                Log.i(TAG, "Reranker loaded");
            } catch (Exception e) {
                reranker = null;
                Log.w(TAG, "Reranker unavailable: " + e.getMessage());
            }
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
                if (resp.isSuccessful() && resp.body() != null) {
                    String remote = resp.body().string().trim();
                    String decrypted = deobfuscate(remote);
                    if (decrypted != null && decrypted.length() > 10) {
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
            vibrator.vibrate(50);
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
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
            query = SynonymExpander.expand(query);
            String localAdvice = checkLocalRules(query);
            String userPrompt;

            if (!localAdvice.isEmpty()) {
                userPrompt = "用户问题：" + query + "\n\n【首要执行规则】\n" + localAdvice + "\n\n请根据上述规则用口语化方式给出具体操作步骤。";
                mainHandler.post(() -> transitionAnimation("正在联系专家"));
            } else {
                float[] queryEmb = embedder.embed(query);
                String route = ENABLE_ROUTING ? routeQuery(query) : null;
                List<KnowledgeBase.ScoredIdx> candidates = knowledgeBase.searchRaw(queryEmb, 20, route);

                List<String> docContents = new ArrayList<>();
                for (KnowledgeBase.ScoredIdx si : candidates) {
                    docContents.add(knowledgeBase.getChunk(si.idx).content);
                }

                List<Reranker.ScoredDoc> reranked;
                if (reranker != null && !docContents.isEmpty()) {
                    reranked = reranker.rerank(query, docContents, 3);
                } else {
                    reranked = new ArrayList<>();
                    for (String c : docContents) reranked.add(new Reranker.ScoredDoc(c, 0f));
                }

                StringBuilder sb = new StringBuilder();
                sb.append("用户问题：").append(query).append("\n\n");
                for (Reranker.ScoredDoc sd : reranked) {
                    sb.append("【参考知识】\n").append(sd.content).append("\n\n");
                }
                sb.append("请严格基于以上参考知识回答。如果参考知识有具体操作数值，必须原样引用。");
                userPrompt = sb.toString();
                mainHandler.post(() -> transitionAnimation("正在联系专家"));
            }

            startStreamingResponse(userPrompt, wasVoice);
        } catch (Throwable t) {
            String err = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            Log.e(TAG, "Query failed: " + err);
            mainHandler.post(() -> { stopAnimation(); btnSend.setEnabled(true); });
        }
    }

    private void startStreamingResponse(String userPrompt, boolean wasVoice) {
        mainHandler.post(() -> transitionAnimation("专家思考中"));

        final boolean speak = wasVoice;
        callCloudAPIStreaming(SYSTEM_PROMPT, userPrompt, new StreamCallback() {
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
                    if (botMsgIdx >= 0) {
                        messages.get(botMsgIdx).text = "（请求失败：" + error + "）";
                        adapter.notifyItemChanged(botMsgIdx);
                    } else {
                        messages.add(new ChatMessage(TYPE_BOT, "（请求失败：" + error + "）"));
                        adapter.notifyItemInserted(messages.size() - 1);
                    }
                    btnSend.setEnabled(true);
                });
            }
        });
    }

    private String routeQuery(String query) {
        if (query.contains("怎么") || query.contains("如何") || query.contains("步骤")
                || query.contains("处理") || query.contains("操作") || query.contains("调")
                || query.contains("多少") || query.contains("多久") || query.contains("用什么"))
            return "manual";
        if (query.contains("为什么") || query.contains("原理") || query.contains("原因")
                || query.contains("什么道理") || query.contains("机制") || query.contains("影响"))
            return "theory";
        if (query.contains("规则") || query.contains("规定") || query.contains("必须")
                || query.contains("禁止") || query.contains("允许") || query.contains("不能"))
            return "rules";
        return null;
    }

    private String checkLocalRules(String query) {
        StringBuilder sb = new StringBuilder();
        double temp = 25, ph = 8.2, tan = 0.5, sal = 15;
        int day = 30;

        java.util.regex.Matcher tempMatcher = java.util.regex.Pattern.compile("(\\d+(\\.\\d+)?)\\s*度").matcher(query);
        java.util.regex.Matcher phMatcher = java.util.regex.Pattern.compile("pH[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
        java.util.regex.Matcher nh3Matcher = java.util.regex.Pattern.compile("(氨氮|nh3|NH3)[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
        java.util.regex.Matcher salMatcher = java.util.regex.Pattern.compile("(盐度|盐)[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
        java.util.regex.Matcher geMatcher = java.util.regex.Pattern.compile("(\\d+)\\s*格").matcher(query);

        if (tempMatcher.find()) temp = Double.parseDouble(tempMatcher.group(1));
        if (phMatcher.find()) ph = Double.parseDouble(phMatcher.group(1));
        if (nh3Matcher.find()) tan = Double.parseDouble(nh3Matcher.group(2));
        if (salMatcher.find()) sal = Double.parseDouble(salMatcher.group(2));
        if (geMatcher.find() && (query.contains("盐") || query.contains("咸"))) {
            double ge = Double.parseDouble(geMatcher.group(1));
            sal = ge;
            sb.append("盐度").append((int)ge).append("格 = ").append((int)ge).append("PSU；");
        }
        java.util.regex.Matcher dayMatcher = java.util.regex.Pattern.compile("(第|养殖)?(\\d+)\\s*天").matcher(query);
        if (dayMatcher.find()) day = Integer.parseInt(dayMatcher.group(2));

        if (query.contains("温度") || query.contains("水温")) {
            String a = ShrimpAdviceHelper.getTempAdvice(temp);
            if (!a.isEmpty()) sb.append(a).append("；");
        }
        if (query.contains("pH") || query.contains("酸碱")) {
            String a = ShrimpAdviceHelper.getPhAdvice(ph);
            if (!a.isEmpty()) sb.append(a).append("；");
        }
        if (query.contains("氨氮") || query.contains("nh3")) {
            String a = ShrimpAdviceHelper.getNh3Advice(ph, temp, tan, sal, day);
            if (!a.isEmpty()) sb.append(a).append("；");
        }
        if (query.contains("亚盐") || query.contains("亚硝酸盐") || query.contains("NO2")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(亚盐|亚硝酸盐|NO2)[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
            if (m.find()) {
                double nitrite = Double.parseDouble(m.group(2));
                String a = NitriteHelper.getAdvice(nitrite, day, sal);
                if (!a.isEmpty()) sb.append(a).append("；");
            }
        }
        if (query.contains("溶氧") || query.contains("DO") || query.contains("溶解氧")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(溶氧|DO|溶解氧)[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
            if (m.find()) {
                double doVal = Double.parseDouble(m.group(2));
                String a = DOHelper.getAdvice(doVal);
                if (!a.isEmpty()) sb.append(a).append("；");
            }
        }
        if (query.contains("ORP") || query.contains("氧化还原")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("ORP[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
            if (m.find()) {
                double orp = Double.parseDouble(m.group(1));
                String a = ORPHelper.getAdvice(orp);
                if (!a.isEmpty()) sb.append(a).append("；");
            }
        }
        if (query.contains("硫化氢") || query.contains("H2S") || query.contains("h2s")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(硫化氢|H2S|h2s)[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
            if (m.find()) {
                double h2s = Double.parseDouble(m.group(2));
                String a = H2SHelper.getAdvice(h2s);
                if (!a.isEmpty()) sb.append(a).append("；");
            }
        }
        if (query.contains("余氯") || query.contains("氯")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(余氯|氯)[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
            if (m.find()) {
                double chlorine = Double.parseDouble(m.group(2));
                String a = ChlorineHelper.getAdvice(chlorine, day);
                if (!a.isEmpty()) sb.append(a).append("；");
            }
        }
        if (query.contains("弧菌")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("弧菌[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
            if (m.find()) {
                double vibrio = Double.parseDouble(m.group(1));
                String a = VibrioHelper.getAdvice(vibrio, day);
                if (!a.isEmpty()) sb.append(a).append("；");
            }
        }
        if (query.contains("密度") || query.contains("比重")) {
            double d = com.shrimpfarm.app.model.SeawaterHelper.calcDensity(temp, sal);
            sb.append("当前海水密度为").append(String.format(Locale.ROOT, "%.2f", d)).append(" kg/m³；");
        }
        if (query.contains("偷死")) {
            sb.append("偷死通常由底质恶化、弧菌感染或虾苗体质弱引起。建议：1.停料观察2-3天，同时使用底改产品（如过硫酸氢钾）；2.检测水体弧菌；3.拌料投喂免疫增强剂（多糖、维生素C）；4.适当换水，控制投喂量在正常水平的70%；");
        }
        return sb.toString();
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
            JSONObject sys = new JSONObject(); sys.put("role", "system"); sys.put("content", systemPrompt);
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
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onError("API error: " + response.code());
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
            public void onFailure(Call call, IOException e) {
                callback.onError(e.getMessage());
            }
        });
    }

    private void addUserMessage(String text) { messages.add(new ChatMessage(TYPE_USER, text)); adapter.notifyItemInserted(messages.size() - 1); chatList.scrollToPosition(messages.size() - 1); }
    private void addBotMessage(String text) { messages.add(new ChatMessage(TYPE_BOT, text)); adapter.notifyItemInserted(messages.size() - 1); chatList.scrollToPosition(messages.size() - 1); }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAnimationTimer();
        executor.shutdown();
        if (embedder != null) embedder.close();
        if (reranker != null) reranker.close();
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); }
    }
}
