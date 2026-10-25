package com.example.wordtrigger;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.util.List;

/**
 * Запись с микрофона телефона и распознавание речи (Vosk) на время одного упражнения.
 * Собирает итоговый текст и длительность самой речи (от первого до последнего слова),
 * по ней считается реальный темп без учёта пауз до и после.
 */
public class ExerciseRecorder {

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
    private volatile boolean running;
    private Thread thread;

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

    /**
     * @param grammarWords если задан, распознаватель ограничивается этими словами (плюс [unk] для всего
     *                     остального). Для коротких известных фраз, например скороговорок, это сильно
     *                     повышает точность на быстрой речи. Работает только с «малыми» моделями Vosk
     *                     (в assets/model есть graph/HCLr.fst); если не поддерживается, запись идёт без ограничения.
     * @param autoFinishWords после скольких распознанных слов запись завершается сама (0, если не нужно)
     */
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
                    startRecording(recognizer);
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
                // модель не поддерживает грамматику: распознаём без ограничения
            }
        }
        return new Recognizer(model, SAMPLE_RATE);
    }

    /**
     * Собственный цикл записи вместо SpeechService из vosk-android: тот при любой ошибке чтения микрофона
     * (запись остановили, микрофон занят другим приложением) бросает исключение в своём потоке и роняет приложение.
     */
    private void startRecording(Recognizer recognizer) {
        final int minBuf = AudioRecord.getMinBufferSize((int) SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        final int bufSize = Math.max(minBuf, (int) SAMPLE_RATE);       // не меньше полсекунды звука
        final AudioRecord record;
        try {
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, (int) SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize);
        } catch (SecurityException e) {
            recognizer.close();
            listener.onError(e.getMessage());
            return;
        }
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            record.release();
            recognizer.close();
            listener.onError("microphone is not available");
            return;
        }
        running = true;
        thread = new Thread(() -> {
            byte[] buffer = new byte[bufSize / 4];
            String error = null;
            try {
                record.startRecording();
                while (running) {
                    int n = record.read(buffer, 0, buffer.length);
                    if (n < 0) {                       // микрофон отобрали или запись сломалась
                        if (running) error = "error reading audio buffer";
                        break;
                    }
                    if (n == 0) continue;
                    if (recognizer.acceptWaveForm(buffer, n)) {
                        final String result = recognizer.getResult();
                        main.post(() -> { if (!cancelled) onResult(result); });
                    } else {
                        final String part = recognizer.getPartialResult();
                        main.post(() -> { if (!cancelled) onPartialResult(part); });
                    }
                }
            } catch (Exception e) {
                error = String.valueOf(e.getMessage());
            } finally {
                try { record.stop(); } catch (Exception ignored) { }
                record.release();
            }
            String last = "";
            try { last = recognizer.getFinalResult(); } catch (Exception ignored) { }
            try { recognizer.close(); } catch (Exception ignored) { }
            final String lastResult = last;
            final String err = error;
            main.post(() -> {
                if (cancelled) return;
                absorb(lastResult);
                if (err != null && currentText().isEmpty()) listener.onError(err);
                else finish();
            });
        }, "exercise-recorder");
        thread.start();
        listener.onStarted();
    }

    /** Остановить запись; итог придёт в onFinished. */
    public void stop() {
        if (thread == null) {
            finish();
            return;
        }
        running = false;      // поток сам закончит чтение, снимет итог и вызовет finish()
        // если финальный результат по какой-то причине не пришёл, не зависаем
        main.postDelayed(this::finish, 3000);
    }

    /** Прервать без результата (выход с экрана). */
    public void cancel() {
        cancelled = true;
        main.removeCallbacksAndMessages(null);
        running = false;      // поток сам освободит микрофон и распознаватель
    }

    private void releaseService() {
        running = false;
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
            // [unk] появляется, когда включена грамматика и звук не похож ни на одно слово из списка
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

    private void onPartialResult(String hypothesis) {
        try {
            partial = new JSONObject(hypothesis).optString("partial", "")
                    .replace("[unk]", " ").replaceAll("\\s+", " ").trim();
        } catch (Exception ignored) { }
        if (!cancelled) listener.onPartial(currentText());
    }

    private void onResult(String hypothesis) {
        absorb(hypothesis);
        if (!cancelled) listener.onPartial(currentText());
    }
}
