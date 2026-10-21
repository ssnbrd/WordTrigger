package com.example.wordtrigger;

import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Синонимы: показывается слово, пользователь называет вслух как можно больше синонимов за 30 секунд.
 *
 * Что засчитывается:
 *  1. Синонимы из встроенного списка, с учётом окончаний («огромная» = «огромный»).
 *  2. Остальные названные слова проверяет ИИ (если он включён в настройках и доступен), поэтому
 *     верный синоним, которого нет в списке, тоже считается.
 *
 * Чтобы упражнение нельзя было «подсмотреть»: при неудаче правильные ответы не показываются,
 * а новая попытка предлагается с другим словом. Недостающие синонимы выводятся только после успеха.
 */
public class SynonymsFragment extends ExerciseBaseFragment {

    private static final long DURATION_MS = 30_000;
    /** Сколько синонимов нужно назвать, чтобы упражнение засчиталось. */
    private static final int REQUIRED = 3;
    /** Сколько слов вне списка отправляется на проверку ИИ за один раз. */
    private static final int MAX_AI_CANDIDATES = 12;
    private static final Random RANDOM = new Random();

    private View root;
    private TextView tvWord;
    private TextView tvTimer;
    private TextView tvHint;
    private TextView tvRecordLabel;
    private ImageView btnRecord;

    private List<String> items = new ArrayList<>();
    private List<String> stopWords = new ArrayList<>();
    private int currentIndex = -1;
    private String currentWord = "";
    private List<String> known = new ArrayList<>();
    /** Нужен, чтобы запоздавший ответ ИИ не попал в результат следующей попытки. */
    private int checkToken;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!isRecording() || tvTimer == null) return;
            tvTimer.setText(formatTime(recordingElapsedMs()) + " / " + formatTime(DURATION_MS));
            uiHandler().postDelayed(this, 500);
        }
    };

    @Override protected int layoutRes() { return R.layout.fragment_synonyms; }
    @Override protected int titleRes() { return R.string.study_title; }
    @Override protected long maxRecordMillis() { return DURATION_MS; }

    @Override
    protected void onContentCreated(View view) {
        root = view;
        tvWord = view.findViewById(R.id.tvSynWord);
        tvTimer = view.findViewById(R.id.tvTimer);
        tvHint = view.findViewById(R.id.tvSynHint);
        tvRecordLabel = view.findViewById(R.id.tvRecordLabel);
        btnRecord = view.findViewById(R.id.btnRecord);

        items = ExerciseContent.list(requireContext(), R.array.exercise_synonyms);
        stopWords = ExerciseContent.list(requireContext(), R.array.synonym_stop_words);
        pickWord();
        tvHint.setText(R.string.synonyms_hint);

        view.findViewById(R.id.tvAnother).setOnClickListener(v -> {
            if (!isRecording()) {
                checkToken++;
                pickWord();
                root.findViewById(R.id.cardResult).setVisibility(View.GONE);
            }
        });
        btnRecord.setOnClickListener(v -> toggleRecording());
    }

    /** Берёт случайное слово, не совпадающее с текущим. Элемент списка: «слово|синоним, синоним, ...». */
    private void pickWord() {
        String item = ExercisePool.next(requireContext(), ExercisePool.SYNONYMS, R.array.exercise_synonyms, currentWord);
        int bar = item.indexOf('|');
        currentWord = bar > 0 ? item.substring(0, bar).trim() : item;
        known = bar > 0 ? SpeechScorer.splitSynonyms(item.substring(bar + 1)) : new ArrayList<>();
        tvWord.setText(currentWord.toUpperCase(Locale.getDefault()));
    }

    @Override
    protected void onRecordingStateChanged(boolean active) {
        btnRecord.setImageResource(active ? R.drawable.ic_stop_square : R.drawable.ic_mic);
        tvRecordLabel.setText(active ? R.string.exercise_stop_recording : R.string.exercise_start_recording);
        if (active) {
            checkToken++;
            root.findViewById(R.id.cardResult).setVisibility(View.GONE);
            uiHandler().post(tick);
        } else {
            uiHandler().removeCallbacks(tick);
        }
    }

    @Override
    protected void onRecordingFinished(String text, double speechSeconds) {
        tvTimer.setText("00:00 / " + formatTime(DURATION_MS));
        if (SpeechScorer.tokenize(text).isEmpty()) {
            showResult(root, getString(R.string.exercise_nothing_heard), "", false);
            return;
        }

        final Set<String> fromList = SpeechScorer.matchedPhrasesLenient(text, known);
        if (fromList.size() >= REQUIRED) {
            finish(fromList, Collections.<String>emptyList(), false);
            return;
        }

        List<String> candidates = SpeechScorer.unmatchedCandidates(text, known, currentWord, stopWords, MAX_AI_CANDIDATES);
        if (candidates.isEmpty()) {
            finish(fromList, Collections.<String>emptyList(), false);
            return;
        }
        boolean useAi = AppSettings.isAiCheckEnabled(requireContext()) && LlmClient.isConfigured();
        if (!useAi) {
            finish(fromList, Collections.<String>emptyList(), true);
            return;
        }

        final int token = checkToken;
        final List<String> sent = candidates;
        showResult(root, getString(R.string.synonyms_checking), "", false);
        LlmClient.chatStructured(requireContext(), getString(R.string.ai_syn_system),
                getString(R.string.ai_syn_user, currentWord, TextUtils.join(", ", sent)), 150, null,
                new LlmClient.Callback() {
                    @Override public void onResult(String answer) {
                        if (!isAdded() || token != checkToken) return;
                        List<String> accepted = new ArrayList<>();
                        for (String w : LlmClient.extractStringArray(answer)) {
                            for (String c : sent) {
                                if (c.equalsIgnoreCase(w.trim()) && !accepted.contains(c)) accepted.add(c);
                            }
                        }
                        finish(fromList, accepted, false);
                    }

                    @Override public void onError(LlmClient.Kind kind, String detail) {
                        if (!isAdded() || token != checkToken) return;
                        android.widget.Toast.makeText(requireContext(),
                                R.string.ai_exercise_fallback_toast, android.widget.Toast.LENGTH_LONG).show();
                        finish(fromList, Collections.<String>emptyList(), true);
                    }
                });
    }

    private void finish(Set<String> fromList, List<String> fromAi, boolean unchecked) {
        List<String> shown = new ArrayList<>(fromList);
        for (String w : fromAi) shown.add(w + "*");
        int count = shown.size();

        StringBuilder body = new StringBuilder(getString(R.string.synonyms_result, count, TextUtils.join(", ", shown)));
        if (!fromAi.isEmpty()) body.append('\n').append(getString(R.string.synonyms_ai_note));
        if (unchecked) body.append('\n').append(getString(R.string.synonyms_unchecked));

        boolean passed = count >= REQUIRED;
        String verdict;
        if (passed) {
            verdict = getString(R.string.exercise_done);
            markPassed(ExerciseCategory.ID_SYNONYMS);

            List<String> missed = new ArrayList<>(known);
            missed.removeAll(fromList);
            if (!missed.isEmpty()) {
                body.append('\n').append(getString(R.string.synonyms_more,
                        TextUtils.join(", ", missed.subList(0, Math.min(3, missed.size())))));
            }
        } else {
            verdict = getString(R.string.synonyms_not_enough, REQUIRED);

            pickWord();
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
