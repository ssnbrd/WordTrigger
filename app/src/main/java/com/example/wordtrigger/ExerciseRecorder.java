package com.example.wordtrigger;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.util.List;

public class ExerciseRecorder implements RecognitionListener {

    public interface Listener {
        void onModelLoading();
        void onStarted();
        void onPartial(String textSoFar);
        void onFinished(String fullText, double speechSeconds);
        void onError(String message);
    }

    private static final float SAMPLE_RATE = 16000f;

    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private SpeechService service;

    private final StringBuilder finalized = new StringBuilder();
    private String partial = "";
    private double firstStart = -1;
    private double lastEnd = -1;
    private boolean finished;
    private boolean cancelled;
    private boolean stopRequested;
    private int autoFinishWords;

    public ExerciseRecorder(Listener listener) {
        this.listener = listener;
    }

    public void start(Context context) {
        start(context, null, 0);
    }

    public void start(Context context, List<String> grammarWords, int autoFinishWords) {
        this.autoFinishWords = autoFinishWords;
        listener.onModelLoading();
        VoskModelHolder.get(context, new VoskModelHolder.Callback() {
            @Override
            public void onReady(Model model) {
                if (cancelled) return;
                try {
                    Recognizer recognizer = createRecognizer(model, grammarWords);
                    recognizer.setWords(true);
                    service = new SpeechService(recognizer, SAMPLE_RATE);
                    service.startListening(ExerciseRecorder.this);
                    listener.onStarted();
                } catch (Exception e) {
                    listener.onError(e.getMessage());
                }
            }

            @Override
            public void onError(Exception e) {
                if (!cancelled) listener.onError(e.getMessage());
            }
        });
    }

    private static Recognizer createRecognizer(Model model, List<String> grammarWords) throws Exception {
        if (grammarWords != null && !grammarWords.isEmpty()) {
            try {
                JSONArray grammar = new JSONArray();
                for (String w : grammarWords) grammar.put(w);
                grammar.put("[unk]");
                return new Recognizer(model, SAMPLE_RATE, grammar.toString());
            } catch (Throwable ignored) {
                // модель не поддерживает грамматику распознаём без ограничения
            }
        }
        return new Recognizer(model, SAMPLE_RATE);
    }

    public void stop() {
        if (service == null) {
            finish();
            return;
        }
        service.stop();
        main.postDelayed(this::finish, 2000);
    }

    public void cancel() {
        cancelled = true;
        main.removeCallbacksAndMessages(null);
        releaseService();
    }

    private void releaseService() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
    }

    private void finish() {
        if (finished || cancelled) return;
        finished = true;
        main.removeCallbacksAndMessages(null);
        releaseService();
        double seconds = (firstStart >= 0 && lastEnd > firstStart) ? lastEnd - firstStart : 0;
        listener.onFinished(currentText(), seconds);
    }

    private String currentText() {
        return (finalized.toString() + " " + partial).trim();
    }

    private void absorb(String json) {
        try {
            JSONObject obj = new JSONObject(json);

            String text = obj.optString("text", "").replace("[unk]", " ").replaceAll("\\s+", " ").trim();
            if (!text.isEmpty()) {
                if (finalized.length() > 0) finalized.append(' ');
                finalized.append(text);
            }
            partial = "";
            if (autoFinishWords > 0 && !stopRequested && !cancelled
                    && SpeechScorer.tokenize(finalized.toString()).size() >= autoFinishWords) {
                stopRequested = true;
                main.post(this::stop);
            }
            JSONArray words = obj.optJSONArray("result");
            if (words != null) {
                for (int i = 0; i < words.length(); i++) {
                    JSONObject w = words.getJSONObject(i);
                    double s = w.optDouble("start", -1);
                    double e = w.optDouble("end", -1);
                    if (s >= 0 && (firstStart < 0 || s < firstStart)) firstStart = s;
                    if (e > lastEnd) lastEnd = e;
                }
            }
        } catch (Exception ignored) { }
    }

    @Override
    public void onPartialResult(String hypothesis) {
        try {
            partial = new JSONObject(hypothesis).optString("partial", "")
                    .replace("[unk]", " ").replaceAll("\\s+", " ").trim();
        } catch (Exception ignored) { }
        if (!cancelled) listener.onPartial(currentText());
    }

    @Override
    public void onResult(String hypothesis) {
        absorb(hypothesis);
        if (!cancelled) listener.onPartial(currentText());
    }

    @Override
    public void onFinalResult(String hypothesis) {
        absorb(hypothesis);
        finish();
    }

    @Override
    public void onError(Exception exception) {
        if (!cancelled) listener.onError(exception.getMessage());
    }

    @Override
    public void onTimeout() {
        finish();
    }
}
