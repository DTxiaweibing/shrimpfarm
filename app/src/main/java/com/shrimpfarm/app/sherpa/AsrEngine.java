package com.shrimpfarm.app.sherpa;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineStream;

import java.io.File;

public class AsrEngine {

    public interface ResultCallback {
        void onResult(String text);

        void onError(String message);
    }

    private static final String TAG = "AsrEngine";
    private static final int SAMPLE_RATE = 16000;

    private final File modelDir;
    private final Object lock = new Object();
    private OfflineRecognizer recognizer;
    private AudioRecord audioRecord;
    private volatile boolean recording;
    private Thread recordThread;
    private volatile float[] lastSamples;

    public AsrEngine(File modelDir) {
        this.modelDir = modelDir;
    }

    public boolean init() {
        synchronized (lock) {
            if (recognizer != null) return true;
            try {
                System.loadLibrary("onnxruntime");
                Log.i(TAG, "onnxruntime loaded");
                OfflineParaformerModelConfig para = new OfflineParaformerModelConfig();
                para.setModel(new File(modelDir, "model.int8.onnx").getAbsolutePath());
                OfflineModelConfig modelConfig = new OfflineModelConfig();
                modelConfig.setParaformer(para);
                modelConfig.setNumThreads(2);
                modelConfig.setModelType("paraformer");
                modelConfig.setTokens(new File(modelDir, "tokens.txt").getAbsolutePath());
                OfflineRecognizerConfig config = new OfflineRecognizerConfig();
                config.setFeatConfig(new FeatureConfig());
                config.setModelConfig(modelConfig);
                recognizer = new OfflineRecognizer(null, config);
                Log.i(TAG, "ASR recognizer ready");
                return true;
            } catch (Throwable t) {
                recognizer = null;
                Log.e(TAG, "Init failed: " + t.getMessage());
                return false;
            }
        }
    }

    public boolean isReady() {
        synchronized (lock) {
            return recognizer != null;
        }
    }

    public boolean isRecording() {
        return recording;
    }

    public void startRecording() {
        synchronized (lock) {
            if (recording) return;
        }
        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuf <= 0) minBuf = SAMPLE_RATE * 2;
        AudioRecord rec = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                Math.max(minBuf, SAMPLE_RATE));
        if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
            rec.release();
            return;
        }
        synchronized (lock) {
            audioRecord = rec;
            recording = true;
            lastSamples = null;
        }
        recordThread = new Thread(this::recordLoop, "asr-record");
        recordThread.start();
    }

    private void recordLoop() {
        AudioRecord rec;
        synchronized (lock) {
            rec = audioRecord;
        }
        if (rec == null) return;
        try {
            rec.startRecording();
            byte[] buf = new byte[SAMPLE_RATE * 2];
            float[] samples = new float[SAMPLE_RATE * 10];
            int pos = 0;
            while (recording) {
                int n = rec.read(buf, 0, buf.length);
                if (n <= 0) continue;
                int limit = pos + n / 2;
                if (limit > samples.length) limit = samples.length;
                int read = 0;
                while (pos < limit) {
                    samples[pos++] = (short) ((buf[read] & 0xff) | (buf[read + 1] << 8)) / 32768f;
                    read += 2;
                }
            }
            int len = Math.min(pos, samples.length);
            if (len > 0) {
                float[] data = new float[len];
                System.arraycopy(samples, 0, data, 0, len);
                lastSamples = data;
            }
        } catch (Throwable t) {
            Log.e(TAG, "record error: " + t.getMessage());
        } finally {
            try {
                rec.stop();
            } catch (Throwable ignored) { /* ignored */ }
            rec.release();
        }
    }

    public void stop(final ResultCallback callback) {
        synchronized (lock) {
            if (!recording) return;
            recording = false;
        }
        new Thread(() -> {
            Thread recorder = recordThread;
            if (recorder != null && recorder.isAlive()) {
                try {
                    recorder.join(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            final float[] data = lastSamples;
            if (data == null || data.length < SAMPLE_RATE / 2) {
                if (callback != null) callback.onError("语音太短");
                return;
            }
            OfflineRecognizer rec;
            synchronized (lock) {
                rec = recognizer;
            }
            if (rec == null) {
                if (callback != null) callback.onError("识别引擎未就绪");
                return;
            }
            long start = System.currentTimeMillis();
            String text = recognizeAndGet(data, rec);
            Log.i(TAG, "decoded(" + (System.currentTimeMillis() - start) + "ms): " + text);
            if (callback != null) {
                if (text == null || text.isEmpty()) {
                    callback.onError("听不清，请再说一次");
                } else {
                    callback.onResult(text);
                }
            }
        }, "asr-stop").start();
    }

    private String recognizeAndGet(float[] samples, OfflineRecognizer rec) {
        OfflineStream stream = rec.createStream();
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE);
            rec.decode(stream);
            OfflineRecognizerResult result = rec.getResult(stream);
            return result.getText() == null ? "" : result.getText().trim();
        } catch (Throwable t) {
            Log.e(TAG, "decode error: " + t.getMessage());
            return "";
        } finally {
            stream.release();
        }
    }

    public void release() {
        synchronized (lock) {
            recording = false;
            if (recognizer != null) {
                recognizer.release();
                recognizer = null;
            }
        }
        lastSamples = null;
    }
}