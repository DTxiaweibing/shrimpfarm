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

import com.shrimpfarm.app.model.KnowledgeBase;
import com.shrimpfarm.app.model.KnowledgeBaseUpdater;
import com.shrimpfarm.app.model.Reranker;
import com.shrimpfarm.app.model.ShrimpAdviceHelper;
import com.shrimpfarm.app.model.TokenEmbedder;
import com.shrimpfarm.app.utils.EncryptUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    private static class ChatMessage {
        final int type;
        final String text;
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
            ViewHolder(View itemView, boolean isDebug) {
                super(itemView);
                if (isDebug) { textMsg = itemView.findViewById(com.shrimpfarm.app.R.id.text_debug); textTime = null; bubble = null; }
                else { textMsg = itemView.findViewById(com.shrimpfarm.app.R.id.text_message); textTime = itemView.findViewById(com.shrimpfarm.app.R.id.text_time); bubble = itemView.findViewById(com.shrimpfarm.app.R.id.bubble); }
            }
        }
        @Override public int getItemViewType(int position) { return messages.get(position).type; }
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == TYPE_DEBUG) {
                return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(com.shrimpfarm.app.R.layout.item_chat_debug, parent, false), true);
            }
            int layout = viewType == TYPE_USER ? com.shrimpfarm.app.R.layout.item_chat_user : com.shrimpfarm.app.R.layout.item_chat_bot;
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false), false);
        }
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ChatMessage msg = messages.get(position);
            if (msg.type == TYPE_DEBUG) { holder.textMsg.setText(msg.text); }
            else { holder.textMsg.setText(msg.text); if (holder.textTime != null) holder.textTime.setText(msg.time); }
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

        addBotMessage("您好！我是小棚养虾智能助手。");

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
                addDebugMessage("重排序加载成功");
            } catch (Exception e) {
                reranker = null;
                addDebugMessage("重排序不可用: " + e.getMessage());
            }
            KnowledgeBaseUpdater.checkUpdate(this);
            initialized = true;
            mainHandler.post(() -> addDebugMessage("初始化完成，知识库 " + knowledgeBase.size() + " 条"));
        } catch (Throwable t) {
            String err = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            addDebugMessage("初始化失败: " + err);
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
                        addDebugMessage("API密钥: 远程获取成功");
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}
        cloudApiKey = deobfuscate(KEY_FALLBACK);
        addDebugMessage("API密钥: 本地密钥");
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

    private void sendMessage() {
        String text = inputMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        inputMessage.setText("");
        addUserMessage(text);
        if (!initialized) { addDebugMessage("初始化未完成，请稍候"); return; }
        if (cloudApiKey == null) { addDebugMessage("API密钥未就绪"); return; }
        btnSend.setEnabled(false);
        final boolean wasVoice = isVoiceInput;
        isVoiceInput = false;
        executor.execute(() -> processQuery(text, wasVoice));
    }

    private void processQuery(String query, boolean wasVoice) {
        try {
            addDebugMessage("正在检索知识库…");
            String localAdvice = checkLocalRules(query);
            String userPrompt;

            if (!localAdvice.isEmpty()) {
                userPrompt = "用户问题：" + query + "\n\n【首要执行规则】\n" + localAdvice + "\n\n请根据上述规则用口语化方式给出具体操作步骤。";
                String firstRule = localAdvice.length() > 80 ? localAdvice.substring(0, 80) + "…" : localAdvice;
                addDebugMessage("命中本地规则: " + firstRule);
            } else {
                float[] queryEmb = embedder.embed(query);
                addDebugMessage("查询向量前5值: " + queryEmb[0] + "," + queryEmb[1] + "," + queryEmb[2] + "," + queryEmb[3] + "," + queryEmb[4]);
                String route = ENABLE_ROUTING ? routeQuery(query) : null;
                if (route != null) addDebugMessage("路由 -> " + route);
                List<KnowledgeBase.ScoredIdx> candidates = knowledgeBase.searchRaw(queryEmb, 20, route);
                addDebugMessage("向量检索候选 " + candidates.size() + " 条");

                List<String> docContents = new ArrayList<>();
                for (KnowledgeBase.ScoredIdx si : candidates) {
                    docContents.add(knowledgeBase.getChunk(si.idx).content);
                }

                List<Reranker.ScoredDoc> reranked;
                if (reranker != null && !docContents.isEmpty()) {
                    addDebugMessage("重排序中…");
                    reranked = reranker.rerank(query, docContents, 3);
                    addDebugMessage("重排完成，top1=" + String.format(java.util.Locale.ROOT, "%.2f", reranked.get(0).score));
                } else {
                    reranked = new ArrayList<>();
                    for (String c : docContents) reranked.add(new Reranker.ScoredDoc(c, 0f));
                }

                StringBuilder sb = new StringBuilder();
                sb.append("用户问题：").append(query).append("\n\n");
                for (Reranker.ScoredDoc sd : reranked) {
                    sb.append("【参考知识】\n").append(sd.content).append("\n\n");
                    String snippet = sd.content.length() > 60 ? sd.content.substring(0, 60).replace("\n", " ") + "…" : sd.content.replace("\n", " ");
                    addDebugMessage("  重排 " + String.format(java.util.Locale.ROOT, "%.2f", sd.score) + ": " + snippet);
                }
                sb.append("请严格基于以上参考知识回答。如果参考知识有具体操作数值，必须原样引用。");
                userPrompt = sb.toString();
                addDebugMessage("最终取 " + reranked.size() + " 条，请求大模型…");
            }

            String answer = callCloudAPI(SYSTEM_PROMPT, userPrompt);

            final String finalAnswer = answer;
            final boolean speak = wasVoice;
            mainHandler.post(() -> {
                addBotMessage(finalAnswer);
                if (speak && ttsReady) textToSpeech.speak(finalAnswer, TextToSpeech.QUEUE_FLUSH, null, null);
                btnSend.setEnabled(true);
            });
        } catch (Throwable t) {
            String err = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            mainHandler.post(() -> { addDebugMessage("处理失败: " + err); btnSend.setEnabled(true); });
        }
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
        java.util.regex.Matcher tempMatcher = java.util.regex.Pattern.compile("(\\d+(\\.\\d+)?)\\s*度").matcher(query);
        java.util.regex.Matcher phMatcher = java.util.regex.Pattern.compile("pH[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
        java.util.regex.Matcher nh3Matcher = java.util.regex.Pattern.compile("(氨氮|nh3|NH3)[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
        java.util.regex.Matcher salMatcher = java.util.regex.Pattern.compile("(盐度|盐)[\\s:]*(\\d+(\\.\\d+)?)").matcher(query);
        double temp = 25, ph = 8.2, tan = 0.5, sal = 15;
        int day = 30;
        if (tempMatcher.find()) temp = Double.parseDouble(tempMatcher.group(1));
        if (phMatcher.find()) ph = Double.parseDouble(phMatcher.group(1));
        if (nh3Matcher.find()) tan = Double.parseDouble(nh3Matcher.group(2));
        if (salMatcher.find()) sal = Double.parseDouble(salMatcher.group(2));
        java.util.regex.Matcher dayMatcher = java.util.regex.Pattern.compile("(第|养殖)?(\\d+)\\s*天").matcher(query);
        if (dayMatcher.find()) day = Integer.parseInt(dayMatcher.group(2));
        if (query.contains("温度") || query.contains("水温")) { String a = ShrimpAdviceHelper.getTempAdvice(temp); if (!a.isEmpty()) sb.append(a).append("；"); }
        if (query.contains("pH") || query.contains("酸碱")) { String a = ShrimpAdviceHelper.getPhAdvice(ph); if (!a.isEmpty()) sb.append(a).append("；"); }
        if (query.contains("氨氮") || query.contains("nh3")) { String a = ShrimpAdviceHelper.getNh3Advice(ph, temp, tan, sal, day); if (!a.isEmpty()) sb.append(a).append("；"); }
        if (query.contains("密度") || query.contains("比重")) { double d = com.shrimpfarm.app.model.SeawaterHelper.calcDensity(temp, sal); sb.append("当前海水密度为").append(String.format(Locale.ROOT, "%.2f", d)).append(" kg/m³；"); }
        return sb.toString();
    }

    private String callCloudAPI(String systemPrompt, String userPrompt) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build();
        JSONObject body = new JSONObject();
        try {
            body.put("model", CLOUD_MODEL);
            JSONArray messages = new JSONArray();
            JSONObject sys = new JSONObject(); sys.put("role", "system"); sys.put("content", systemPrompt);
            messages.put(sys);
            JSONObject usr = new JSONObject(); usr.put("role", "user"); usr.put("content", userPrompt);
            messages.put(usr);
            body.put("messages", messages); body.put("temperature", 0.2); body.put("max_tokens", 4096);
        } catch (Exception e) { throw new IOException("JSON build error", e); }
        Request request = new Request.Builder()
                .url(CLOUD_API_URL)
                .addHeader("Authorization", "Bearer " + cloudApiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json; charset=utf-8")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("API error: " + response.code());
            String respBody = response.body().string();
            JSONObject json = new JSONObject(respBody);
            JSONArray choices = json.getJSONArray("choices");
            if (choices.length() > 0) return choices.getJSONObject(0).getJSONObject("message").getString("content");
            return "（无回答）";
        } catch (Exception e) { throw new IOException("API call failed: " + e.getMessage()); }
    }

    private void addUserMessage(String text) { messages.add(new ChatMessage(TYPE_USER, text)); adapter.notifyItemInserted(messages.size() - 1); chatList.scrollToPosition(messages.size() - 1); }
    private void addBotMessage(String text) { messages.add(new ChatMessage(TYPE_BOT, text)); adapter.notifyItemInserted(messages.size() - 1); chatList.scrollToPosition(messages.size() - 1); }
    private void addDebugMessage(String text) { mainHandler.post(() -> { messages.add(new ChatMessage(TYPE_DEBUG, text)); adapter.notifyItemInserted(messages.size() - 1); chatList.scrollToPosition(messages.size() - 1); }); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        if (embedder != null) embedder.close();
        if (reranker != null) reranker.close();
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); }
    }
}
