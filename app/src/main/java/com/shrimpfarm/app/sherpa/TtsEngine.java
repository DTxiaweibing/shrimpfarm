package com.shrimpfarm.app.sherpa;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class TtsEngine {

    public interface SpeakCallback {
        void onStarted();

        void onFinished();

        void onError(String message);
    }

    private static final class SpeechTask {
        final String text;
        final int sid;
        final float speed;
        final SpeakCallback callback;
        final long gen;
        SpeechTask(String text, int sid, float speed, SpeakCallback callback, long gen) {
            this.text = text;
            this.sid = sid;
            this.speed = speed;
            this.callback = callback;
            this.gen = gen;
        }
    }

    private static final class SpeechAudio {
        final float[] samples;
        final int sampleRate;
        final SpeakCallback callback;
        final long gen;
        SpeechAudio(float[] samples, int sampleRate, SpeakCallback callback, long gen) {
            this.samples = samples;
            this.sampleRate = sampleRate;
            this.callback = callback;
            this.gen = gen;
        }
    }

    private static final String TAG = "TtsEngine";

    private final File modelDir;
    private final Object lock = new Object();
    private OfflineTts tts;
    private AudioTrack activeTrack;
    private volatile long generation = 0;
    private volatile boolean running = false;

    private final LinkedBlockingQueue<SpeechTask> synthQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<SpeechAudio> audioQueue = new LinkedBlockingQueue<>(8);
    private final ExecutorService synthExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService playExecutor = Executors.newSingleThreadExecutor();

    public TtsEngine(File modelDir) {
        this.modelDir = modelDir;
    }

    public boolean init() {
        synchronized (lock) {
            if (tts != null) return true;
            try {
                System.loadLibrary("onnxruntime");
                Log.i(TAG, "onnxruntime loaded");
                OfflineTtsVitsModelConfig vits = new OfflineTtsVitsModelConfig();
                vits.setModel(new File(modelDir, "model.onnx").getAbsolutePath());
                vits.setLexicon(new File(modelDir, "lexicon.txt").getAbsolutePath());
                vits.setTokens(new File(modelDir, "tokens.txt").getAbsolutePath());
                OfflineTtsModelConfig modelConfig = new OfflineTtsModelConfig();
                modelConfig.setVits(vits);
                modelConfig.setNumThreads(2);
                OfflineTtsConfig config = new OfflineTtsConfig();
                config.setModel(modelConfig);
                config.setRuleFsts(ruleFstsPath());
                config.setMaxNumSentences(1);
                config.setSilenceScale(0.2f);
                tts = new OfflineTts(null, config);
                Log.i(TAG, "TTS ready, speakers=" + tts.numSpeakers());
                if (!running) {
                    running = true;
                    synthExecutor.execute(this::synthesizeLoop);
                    playExecutor.execute(this::playLoop);
                }
                return true;
            } catch (Throwable t) {
                tts = null;
                Log.e(TAG, "TTS init failed: " + t.getMessage());
                return false;
            }
        }
    }

    private String ruleFstsPath() {
        StringBuilder sb = new StringBuilder();
        String[] names = {"phone.fst", "date.fst", "number.fst"};
        for (int i = 0; i < names.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(new File(modelDir, names[i]).getAbsolutePath());
        }
        return sb.toString();
    }

    public boolean isReady() {
        synchronized (lock) {
            return tts != null;
        }
    }

    public int numSpeakers() {
        synchronized (lock) {
            return tts == null ? 5 : tts.numSpeakers();
        }
    }

    public void speak(final String text, final int sid, final float speed,
                      final SpeakCallback callback) {
        if (text == null || text.isEmpty()) return;
        final long gen;
        final int voSid;
        synchronized (lock) {
            if (tts == null) {
                if (callback != null) callback.onError("TTS引擎未就绪");
                return;
            }
            gen = generation;
            voSid = Math.max(0, Math.min(sid, tts.numSpeakers() - 1));
        }
        for (String sentence : splitSentences(text)) {
            if (sentence.isEmpty()) continue;
            synthQueue.offer(new SpeechTask(sentence, voSid, speed, callback, gen));
        }
    }

    private static boolean isBoundary(String text, int i, int segLen, char c) {
        if (c == '\n') return true;
        if (c == '。' || c == '！' || c == '？' || c == '…') return true;
        if (c == '；' || c == '，' || c == '：' || c == ';' || c == ':') return segLen >= 10;
        if (c == '.' || c == ',') {
            boolean prevDigit = i > 0 && text.charAt(i - 1) >= '0' && text.charAt(i - 1) <= '9';
            boolean nextDigit = i + 1 < text.length()
                    && text.charAt(i + 1) >= '0' && text.charAt(i + 1) <= '9';
            if (prevDigit && nextDigit) return false;
            return c == '.' || segLen >= 10;
        }
        if (c == '?' || c == '!') return true;
        return false;
    }

    private static java.util.List<String> splitSentences(String text) {
        java.util.List<String> result = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length() && result.size() < 256; i++) {
            char c = text.charAt(i);
            cur.append(c);
            if (isBoundary(text, i, cur.length(), c)) {
                result.add(cur.toString());
                cur.setLength(0);
            } else if (cur.length() >= 40) {
                int sp = cur.lastIndexOf(" ");
                if (sp > 0) {
                    result.add(cur.substring(0, sp));
                    String rest = cur.substring(sp + 1);
                    cur.setLength(0);
                    cur.append(rest);
                } else {
                    result.add(cur.toString());
                    cur.setLength(0);
                }
            }
        }
        if (cur.toString().trim().length() > 0) result.add(cur.toString());
        return result;
    }

    private void synthesizeLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                SpeechTask task = synthQueue.take();
                if (task.gen != generation) continue;
                OfflineTts ttsRef;
                synchronized (lock) {
                    ttsRef = tts;
                }
                if (ttsRef == null || task.gen != generation) continue;
                long genStart = System.currentTimeMillis();
                GeneratedAudio audio;
                try {
                    audio = ttsRef.generate(task.text, task.sid, task.speed);
                } catch (Throwable t) {
                    Log.e(TAG, "generate error: " + t.getMessage());
                    if (task.callback != null) task.callback.onError(t.getMessage());
                    continue;
                }
                int len = (audio == null || audio.getSamples() == null) ? -1 : audio.getSamples().length;
                Log.i(TAG, "generate(" + (System.currentTimeMillis() - genStart) + "ms): len="
                        + len + " rate=" + (audio == null ? -1 : audio.getSampleRate()));
                if (len <= 0) {
                    if (task.callback != null) task.callback.onError("合成失败");
                    continue;
                }
                if (task.gen != generation) continue;
                audioQueue.put(new SpeechAudio(audio.getSamples(), audio.getSampleRate(),
                        task.callback, task.gen));
            } catch (InterruptedException e) {
                break;
            } catch (Throwable t) {
                Log.e(TAG, "synth loop error: " + t.getMessage());
            }
        }
    }

    private void playLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                SpeechAudio audio = audioQueue.take();
                if (audio.gen != generation) continue;
                if (audio.callback != null && audio.gen == generation) audio.callback.onStarted();
                playAudio(audio.samples, audio.sampleRate, audio.gen);
                if (audio.callback != null && audio.gen == generation) audio.callback.onFinished();
            } catch (InterruptedException e) {
                break;
            } catch (Throwable t) {
                Log.e(TAG, "play loop error: " + t.getMessage());
            }
        }
    }

    private static float[] normalizePeak(float[] samples) {
        float peak = 0f;
        for (float s : samples) {
            float a = Math.abs(s);
            if (a > peak) peak = a;
        }
        if (peak < 1e-4f) return samples;
        float gain = 0.9f / peak;
        if (gain > 3f) gain = 3f;
        if (gain <= 1f) return samples;
        float[] out = new float[samples.length];
        for (int i = 0; i < samples.length; i++) {
            float v = samples[i] * gain;
            out[i] = v > 1f ? 1f : (v < -1f ? -1f : v);
        }
        return out;
    }

    private void playAudio(float[] samples, int sampleRate, long gen) {
        samples = normalizePeak(samples);
        AudioTrack track;
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            AudioFormat fmt = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            int minBuf = AudioTrack.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT);
            if (minBuf <= 0) minBuf = sampleRate * 2;
            int bufSize = Math.max(minBuf, sampleRate);
            track = new AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(fmt)
                    .setBufferSizeInBytes(bufSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
        } catch (Throwable t) {
            Log.e(TAG, "AudioTrack create failed: " + t.getMessage());
            return;
        }
        try {
            synchronized (lock) {
                if (gen != generation) return;
                activeTrack = track;
            }
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack not initialized");
                track.release();
                return;
            }
            track.play();
            int total = 0;
            long deadline = System.currentTimeMillis()
                    + (long) (samples.length / (double) sampleRate * 1000) + 2000;
            while (gen == generation && System.currentTimeMillis() < deadline) {
                if (total < samples.length) {
                    int chunk = Math.min(sampleRate / 2, samples.length - total);
                    int w = track.write(samples, total, chunk, AudioTrack.WRITE_NON_BLOCKING);
                    if (w < 0) {
                        Log.e(TAG, "AudioTrack write failed at " + total + " (" + w + ")");
                        break;
                    }
                    if (w == 0) {
                        sleep(10);
                        continue;
                    }
                    total += w;
                } else {
                    int head;
                    try {
                        head = track.getPlaybackHeadPosition();
                    } catch (Throwable ignored) {
                        head = 0;
                    }
                    if (total > 0 && head >= total) break;
                    sleep(30);
                }
            }
            Log.i(TAG, "played " + total + "/" + samples.length + " @ " + sampleRate);
        } catch (Throwable t) {
            Log.e(TAG, "play error: " + t.getMessage());
        } finally {
            try {
                track.stop();
            } catch (Throwable ignored) { /* ignored */ }
            track.release();
            synchronized (lock) {
                if (activeTrack == track) activeTrack = null;
            }
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isPlaying() {
        synchronized (lock) {
            return activeTrack != null && activeTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;
        }
    }

    public void stop() {
        generation++;
        synthQueue.clear();
        audioQueue.clear();
        synchronized (lock) {
            if (activeTrack != null) {
                try {
                    activeTrack.stop();
                } catch (Throwable ignored) { /* ignored */ }
            }
        }
    }

    public void release() {
        generation++;
        synthQueue.clear();
        audioQueue.clear();
        synthExecutor.shutdownNow();
        playExecutor.shutdownNow();
        running = false;
        synchronized (lock) {
            if (tts != null) {
                tts.release();
                tts = null;
            }
            activeTrack = null;
        }
    }
}