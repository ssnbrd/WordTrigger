package com.example.wordtrigger;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.util.ArrayList;
import java.util.List;

public class SpeechRecognitionHelper {
    private Recognizer recognizer;
    private boolean isReady = false;
    private List<String> targetWords = new ArrayList<>();

    public interface OnWordDetectedListener {
        void onDetected(String word);
        void onPartialResult(String partialText);
    }

    private final OnWordDetectedListener listener;
    private String modelDir = "";

    public SpeechRecognitionHelper(Context context, OnWordDetectedListener listener) {
        this.listener = listener;
        initModel(context);
    }

    private void initModel(Context context) {
        // Модель выбирается по языку приложения: assets/model (ru) или assets/model-en (en)
        modelDir = VoskModelHolder.modelDirFor(context);
        VoskModelHolder.get(context, new VoskModelHolder.Callback() {
            @Override public void onReady(Model model) {
                try {
                    recognizer = new Recognizer(model, 16000.0f);
                    isReady = true;
                    Log.d("VOSK", "READY, model dir: " + VoskModelHolder.modelDirFor(context));
                } catch (Exception e) { Log.e("VOSK", "Error: " + e.getMessage()); }
            }
            @Override public void onError(Exception e) {
                Log.e("VOSK", "Unpack failed: " + e.getMessage());
            }
        });
    }

//    public void processAudio(byte[] buffer, int len) {
//        if (!isReady || recognizer == null) return;
//
//        short[] samples = new short[len / 2];
//        for (int i = 0; i < samples.length; i++) {
//            samples[i] = (short) ((buffer[i * 2 + 1] << 8) | (buffer[i * 2] & 0xff));
//        }
//
//        if (recognizer.acceptWaveForm(samples, samples.length)) {
//            parseAndCheck(recognizer.getResult(), true);
//        } else {
//            parseAndCheck(recognizer.getPartialResult(), false);
//        }
//    }
public void processAudio(byte[] buffer, int len) {
    if (!isReady || recognizer == null) return;
    if (recognizer.acceptWaveForm(buffer, len)) {
        parseAndCheck(recognizer.getResult(), true);
    } else {
        parseAndCheck(recognizer.getPartialResult(), false);
    }
}
    /** true, если язык приложения сменили и распознаватель собран на модели другого языка. */
    public boolean isModelStale(Context context) {
        return !modelDir.equals(VoskModelHolder.modelDirFor(context));
    }

    public void release() {
        isReady = false;
        if (recognizer != null) {
            try { recognizer.close(); } catch (Exception ignored) { }
            recognizer = null;
        }
    }

    public void setTargetWords(List<String> words) {
        this.targetWords = words;
    }
    private void parseAndCheck(String json, boolean isFinal) {
        try {
            JSONObject obj = new JSONObject(json);
            String text = obj.optString(isFinal ? "text" : "partial", "");

            if (!text.isEmpty()) {
                Log.d("VOSK_HEARD", "HEARD: " + text);
                listener.onPartialResult(text);

                // слово должно совпасть целиком: «like» не срабатывает на «unlike», «ну» на «рисунок»
                String padded = " " + text.toLowerCase().replaceAll("\\s+", " ") + " ";
                for (String target : targetWords) {
                    String t = target == null ? "" : target.trim().toLowerCase();
                    if (!t.isEmpty() && padded.contains(" " + t + " ")) {
                        listener.onDetected(target);
                        recognizer.reset();
                        break;
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}