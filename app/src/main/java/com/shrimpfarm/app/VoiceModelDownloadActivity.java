package com.shrimpfarm.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.shrimpfarm.app.sherpa.VoiceModelManager;

public class VoiceModelDownloadActivity extends BaseActivity {

    private static final String TAG = "VoiceDownload";

    @Override
    protected int getCurrentNavId() {
        return 0;
    }

    private TextView tvHint;
    private TextView tvAsrTitle;
    private TextView tvAsrStatus;
    private TextView tvTtsTitle;
    private TextView tvTtsStatus;
    private ProgressBar progressAsr;
    private ProgressBar progressTts;
    private Button btnStart;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean downloading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_download);
        tvHint = findViewById(R.id.tv_download_hint);
        tvAsrTitle = findViewById(R.id.tv_asr_title);
        tvAsrStatus = findViewById(R.id.tv_asr_status);
        tvTtsTitle = findViewById(R.id.tv_tts_title);
        tvTtsStatus = findViewById(R.id.tv_tts_status);
        progressAsr = findViewById(R.id.progress_asr);
        progressTts = findViewById(R.id.progress_tts);
        btnStart = findViewById(R.id.btn_start_download);

        tvAsrTitle.setText(getString(R.string.voice_asr_title));
        tvTtsTitle.setText(getString(R.string.voice_tts_title));
        tvHint.setText(getString(R.string.voice_download_hint));

        VoiceModelManager mgr = VoiceModelManager.getInstance(this);
        if (mgr.isAsrReady() && mgr.isTtsReady()) {
            Log.i(TAG, "models already ready");
            setupFinishButton();
            showReady();
            return;
        }
        btnStart.setText(getString(R.string.voice_btn_start));
        btnStart.setOnClickListener(v -> {
            if (downloading) return;
            Log.i(TAG, "start clicked, enqueue download");
            downloading = true;
            btnStart.setText(getString(R.string.voice_btn_downloading));
            btnStart.setEnabled(false);
            mgr.downloadAll(new VoiceModelManager.ProgressListener() {
                @Override
                public void onProgress(float asrPercent, float ttsPercent) {
                    mainHandler.post(() -> updateProgress(asrPercent, ttsPercent));
                }

                @Override
                public void onFinished(String message) {
                    mainHandler.post(() -> onDownloadFinished(message));
                }
            });
        });
        btnStart.performClick();
    }

    private void showReady() {
        progressAsr.setProgress(100);
        progressTts.setProgress(100);
        tvAsrStatus.setText(getString(R.string.voice_status_ready));
        tvTtsStatus.setText(getString(R.string.voice_status_ready));
    }

    private void setupFinishButton() {
        btnStart.setText(getString(R.string.voice_btn_finish));
        Log.i(TAG, "setupFinishButton enabled=" + btnStart.isEnabled()
                + " clickable=" + btnStart.isClickable());
        btnStart.setEnabled(true);
        btnStart.setOnClickListener(v -> {
            Log.i(TAG, "finish clicked, finishing activity");
            finish();
        });
    }

    private void updateProgress(float asr, float tts) {
        if (!Float.isNaN(asr)) {
            int pct = (int) asr;
            progressAsr.setProgress(pct);
            tvAsrStatus.setText(getString(R.string.voice_status_downloading, pct));
        }
        if (!Float.isNaN(tts)) {
            int pct = (int) tts;
            progressTts.setProgress(pct);
            tvTtsStatus.setText(getString(R.string.voice_status_downloading, pct));
        }
    }

    private void onDownloadFinished(String message) {
        downloading = false;
        if (message != null && message.contains("失败")) {
            btnStart.setText(getString(R.string.voice_btn_retry));
            btnStart.setEnabled(true);
            btnStart.setOnClickListener(v -> {
                if (downloading) return;
                downloading = true;
                btnStart.setText(getString(R.string.voice_btn_downloading));
                btnStart.setEnabled(false);
                VoiceModelManager.getInstance(this)
                        .downloadAll(listener());
            });
            tvAsrStatus.setText(getString(R.string.voice_status_failed));
            tvTtsStatus.setText(getString(R.string.voice_status_failed));
            return;
        }
        btnStart.setEnabled(true);
        setupFinishButton();
        VoiceModelManager mgr = VoiceModelManager.getInstance(this);
        if (mgr.isAsrReady()) {
            progressAsr.setProgress(100);
            tvAsrStatus.setText(getString(R.string.voice_status_ready));
        }
        if (mgr.isTtsReady()) {
            progressTts.setProgress(100);
            tvTtsStatus.setText(getString(R.string.voice_status_ready));
        }
    }

    private VoiceModelManager.ProgressListener listener() {
        return new VoiceModelManager.ProgressListener() {
            @Override
            public void onProgress(float asrPercent, float ttsPercent) {
                mainHandler.post(() -> updateProgress(asrPercent, ttsPercent));
            }

            @Override
            public void onFinished(String message) {
                mainHandler.post(() -> onDownloadFinished(message));
            }
        };
    }
}