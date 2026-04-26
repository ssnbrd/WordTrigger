package com.example.wordtrigger;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.StorageService;

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

    public SpeechRecognitionHelper(Context context, OnWordDetectedListener listener) {
        this.listener = listener;
        initModel(context);
    }

    private void initModel(Context context) {
        StorageService.unpack(context, "model", "model",
                (model) -> {
                    try {
                        recognizer = new Recognizer(model, 16000.0f);
                        isReady = true;
                        Log.d("VOSK", "СИСТЕМА ГОТОВА");
                    } catch (Exception e) { Log.e("VOSK", "Error: " + e.getMessage()); }
                },
                (e) -> Log.e("VOSK", "Unpack failed: " + e.getMessage())
        );
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
    public void setTargetWords(List<String> words) {
        this.targetWords = words;
    }
    private void parseAndCheck(String json, boolean isFinal) {
        try {
            JSONObject obj = new JSONObject(json);
            String text = obj.optString(isFinal ? "text" : "partial", "");

            if (!text.isEmpty()) {
                Log.d("VOSK_HEARD", "СЛЫШУ: " + text);
                listener.onPartialResult(text);

                for (String target : targetWords) {
                    if (text.toLowerCase().contains(target.toLowerCase())) {
                        listener.onDetected(target);
                        recognizer.reset();
                        break;
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}