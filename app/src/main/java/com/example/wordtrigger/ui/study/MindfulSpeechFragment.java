package com.example.wordtrigger.ui.study;

import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.wordtrigger.data.local.AppSettings;
import com.example.wordtrigger.data.local.ExercisePool;
import com.example.wordtrigger.data.remote.LlmClient;
import com.example.wordtrigger.R;
import com.example.wordtrigger.speech.SpeechScorer;
import com.example.wordtrigger.speech.TopicScorer;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class MindfulSpeechFragment extends ExerciseBaseFragment {

    private static final long DURATION_MS = 3 * 60 * 1000;

    private static final int MIN_SPEECH_SECONDS = 60;
    private static final int MIN_WORDS = 40;
    private static final int MAX_FILLERS = 3;
    private static final int MIN_AI_SCORE = 50;
    private static final int MIN_KEYWORDS = 2;
    private static final int MAX_TRANSCRIPT_CHARS = 3500;

    private View root;
    private TextView tvTopic;
    private TextView tvTimer;
    private TextView tvFillers;
    private TextView tvRecordLabel;
    private ImageView btnRecord;
    private List<String> fillerWords = new ArrayList<>();

    private String topicTitle = "";
    private List<String> topicKeywords = new ArrayList<>();
    private int checkToken;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!isRecording() || tvTimer == null) return;
            tvTimer.setText(formatTime(recordingElapsedMs()) + " / " + formatTime(DURATION_MS));
            uiHandler().postDelayed(this, 500);
        }
    };

    @Override protected int layoutRes() { return R.layout.fragment_mindful_speech; }
    @Override protected int titleRes() { return R.string.study_title; }
    @Override protected long maxRecordMillis() { return DURATION_MS; }

    @Override
    protected void onContentCreated(View view) {
        root = view;
        tvTopic = view.findViewById(R.id.tvTopic);
        tvTimer = view.findViewById(R.id.tvTimer);
        tvFillers = view.findViewById(R.id.tvFillers);
        tvRecordLabel = view.findViewById(R.id.tvRecordLabel);
        btnRecord = view.findViewById(R.id.btnRecord);

        pickTopic();
        fillerWords = ExerciseContent.list(requireContext(), R.array.default_filler_words);
        showFillers();
        loadUserFillers();

        ((TextView) view.findViewById(R.id.tvResultBody)).setText(R.string.mindful_hint);
        view.findViewById(R.id.tvResultVerdict).setVisibility(View.GONE);

        view.findViewById(R.id.tvAnother).setOnClickListener(v -> {
            if (!isRecording()) pickTopic();
        });
        btnRecord.setOnClickListener(v -> toggleRecording());
    }

    private void pickTopic() {
        String item = ExercisePool.next(requireContext(), ExercisePool.TOPICS, R.array.exercise_topics, topicTitle);
        int bar = item.indexOf('|');
        topicTitle = bar > 0 ? item.substring(0, bar).trim() : item;
        topicKeywords = new ArrayList<>();
        if (bar > 0) {
            for (String k : item.substring(bar + 1).split(",")) {
                if (!k.trim().isEmpty()) topicKeywords.add(k.trim());
            }
        }
        for (String stem : TopicScorer.stemsFromTitle(topicTitle)) {
            if (!topicKeywords.contains(stem)) topicKeywords.add(stem);
        }
        tvTopic.setText(getString(R.string.mindful_topic, topicTitle));
    }

    private void showFillers() {
        tvFillers.setText(getString(R.string.mindful_my_fillers, TextUtils.join(", ", fillerWords)));
    }

    private void loadUserFillers() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;
                    List<String> words = (List<String>) doc.get("trigger_words");
                    if (words != null && !words.isEmpty()) {
                        fillerWords = new ArrayList<>(words);
                        showFillers();
                    }
                });
    }

    @Override
    protected void onRecordingStateChanged(boolean active) {
        btnRecord.setImageResource(active ? R.drawable.ic_stop_square : R.drawable.ic_mic);
        tvRecordLabel.setText(active ? R.string.exercise_stop_recording : R.string.exercise_start_recording);
        if (active) {
            checkToken++;
            uiHandler().post(tick);
        } else {
            uiHandler().removeCallbacks(tick);
        }
    }

    @Override
    protected void onRecordingFinished(String text, double speechSeconds) {
        tvTimer.setText("00:00 / " + formatTime(DURATION_MS));
        int words = SpeechScorer.tokenize(text).size();
        if (words == 0) {
            showResult(root, getString(R.string.exercise_nothing_heard), "", false);
            return;
        }

        Map<String, Integer> perWord = new LinkedHashMap<>();
        int fillers = 0;
        for (String w : fillerWords) {
            int n = SpeechScorer.countOccurrences(text, Collections.singletonList(w));
            if (n > 0) perWord.put(w, n);
            fillers += n;
        }

        StringBuilder body = new StringBuilder(getString(R.string.mindful_result,
                formatTime((long) (speechSeconds * 1000)), words, fillers));
        if (!perWord.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Integer> e : perWord.entrySet()) {
                parts.add(e.getKey() + " ×" + e.getValue());
            }
            body.append('\n').append(getString(R.string.mindful_fillers_breakdown, TextUtils.join(", ", parts)));
        }

        if (speechSeconds < MIN_SPEECH_SECONDS) {
            showResult(root, body, getString(R.string.mindful_too_short, MIN_SPEECH_SECONDS), false);
            return;
        }
        if (words < MIN_WORDS) {
            showResult(root, body, getString(R.string.mindful_too_few_words, MIN_WORDS), false);
            return;
        }
        if (fillers > MAX_FILLERS) {
            showResult(root, body, getString(R.string.mindful_too_many, MAX_FILLERS), false);
            return;
        }
        if (!TopicScorer.isVaried(text)) {
            showResult(root, body, getString(R.string.mindful_variety_low), false);
            return;
        }

        checkTopic(text, body);
    }

    private void checkTopic(String text, StringBuilder body) {
        final int token = checkToken;
        final int keywordHits = TopicScorer.keywordsHit(text, topicKeywords);
        final boolean useAi = AppSettings.isAiCheckEnabled(requireContext()) && LlmClient.isConfigured();
        if (!useAi) {
            finishLocal(body, keywordHits);
            return;
        }

        showResult(root, body, getString(R.string.mindful_checking), false);
        String transcript = text.length() > MAX_TRANSCRIPT_CHARS ? text.substring(0, MAX_TRANSCRIPT_CHARS) : text;
        LlmClient.chatStructured(requireContext(), getString(R.string.ai_topic_system),
                getString(R.string.ai_topic_user, topicTitle, transcript), 200, null,
                new LlmClient.Callback() {
                    @Override public void onResult(String answer) {
                        if (!isAdded() || token != checkToken) return;
                        JSONObject json = LlmClient.extractJson(answer);
                        if (json == null) {
                            finishLocal(body, keywordHits);
                            return;
                        }
                        boolean onTopic = json.optBoolean("on_topic", false);
                        int score = (int) Math.max(0, Math.min(100, Math.round(json.optDouble("score", onTopic ? 60 : 20))));
                        finishAi(body, onTopic && score >= MIN_AI_SCORE, score, json.optString("comment", "").trim());
                    }

                    @Override public void onError(LlmClient.Kind kind, String detail) {
                        if (!isAdded() || token != checkToken) return;
                        android.widget.Toast.makeText(requireContext(),
                                R.string.ai_exercise_fallback_toast, android.widget.Toast.LENGTH_LONG).show();
                        finishLocal(body, keywordHits);
                    }
                });
    }

    private void finishAi(StringBuilder body, boolean passed, int score, String comment) {
        body.append('\n').append(getString(R.string.mindful_topic_ai, score));
        if (!comment.isEmpty()) body.append('\n').append(comment);
        String verdict;
        if (passed) {
            verdict = getString(R.string.exercise_done);
            markPassed(ExerciseCategory.ID_MINDFUL_SPEECH);
        } else {
            verdict = getString(R.string.mindful_topic_off_topic, topicTitle);
        }
        showResult(root, body, verdict, passed);
    }

    private void finishLocal(StringBuilder body, int keywordHits) {
        boolean passed = keywordHits >= MIN_KEYWORDS;
        body.append('\n').append(getString(R.string.mindful_simple_check_note));
        String verdict;
        if (passed) {
            body.append('\n').append(getString(R.string.mindful_topic_local_ok, keywordHits));
            verdict = getString(R.string.exercise_done);
            markPassed(ExerciseCategory.ID_MINDFUL_SPEECH);
        } else {
            verdict = getString(R.string.mindful_topic_local_fail);
        }
        showResult(root, body, verdict, passed);
    }

    @Override
    public void onDestroyView() {
        checkToken++;
        uiHandler().removeCallbacks(tick);
        super.onDestroyView();
    }
}
