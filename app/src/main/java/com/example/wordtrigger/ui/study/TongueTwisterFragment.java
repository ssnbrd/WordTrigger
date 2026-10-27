package com.example.wordtrigger.ui.study;

import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.wordtrigger.data.local.ExercisePool;
import com.example.wordtrigger.R;
import com.example.wordtrigger.speech.SpeechScorer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;


public class TongueTwisterFragment extends ExerciseBaseFragment {
    private static final double[] TARGET_WPS = {1.2, 2.0, 3.0};
    private static final double[] MIN_ACCURACY = {0.75, 0.65, 0.55};
    private static final float[] TTS_RATE = {0.7f, 1.0f, 1.3f};
    private static final int[] TEMPO_NAME = {R.string.tempo_slow, R.string.tempo_medium, R.string.tempo_fast};
    private static final long MAX_RECORD_MS = 25_000;

    private int tempo = 1;
    private String twister;
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean ttsSpeaking;

    private View root;
    private TextView tvTwister;
    private TextView tvTempo;
    private TextView tvPlayLabel;
    private TextView tvRecordLabel;
    private ImageView btnPlay;
    private ImageView btnRecord;

    @Override protected int layoutRes() { return R.layout.fragment_tongue_twister; }
    @Override protected int titleRes() { return R.string.study_title; }
    @Override protected long maxRecordMillis() { return MAX_RECORD_MS; }

    @Override
    protected List<String> grammarWords() {
        return new ArrayList<>(new LinkedHashSet<>(SpeechScorer.tokenize(twister)));
    }

    @Override
    protected int autoFinishWords() {
        int n = SpeechScorer.tokenize(twister).size();
        return Math.max(3, (int) Math.round(n * 0.7));
    }

    @Override
    protected void onContentCreated(View view) {
        root = view;
        tvTwister = view.findViewById(R.id.tvTwister);
        tvTempo = view.findViewById(R.id.tvTempo);
        tvPlayLabel = view.findViewById(R.id.tvPlayLabel);
        tvRecordLabel = view.findViewById(R.id.tvRecordLabel);
        btnPlay = view.findViewById(R.id.btnPlayReference);
        btnRecord = view.findViewById(R.id.btnRecord);

        pickTwister();
        updateTempoLabel();
        setStatus(getString(R.string.twister_hint));

        view.findViewById(R.id.tvAnother).setOnClickListener(v -> {
            if (isRecording()) return;
            stopReference();
            pickTwister();
            root.findViewById(R.id.cardResult).setVisibility(View.GONE);
        });
        tvTempo.setOnClickListener(v -> {
            tempo = (tempo + 1) % TARGET_WPS.length;
            updateTempoLabel();
            if (ttsReady) tts.setSpeechRate(TTS_RATE[tempo]);
        });
        btnPlay.setOnClickListener(v -> toggleReference());
        btnRecord.setOnClickListener(v -> {
            stopReference();
            toggleRecording();
        });

        tts = new TextToSpeech(requireContext().getApplicationContext(), status -> {
            if (status != TextToSpeech.SUCCESS || tts == null) return;
            int r = tts.setLanguage(appLocale());
            ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
            if (ttsReady) tts.setSpeechRate(TTS_RATE[tempo]);
        });
    }

    private void pickTwister() {
        twister = ExercisePool.next(requireContext(), ExercisePool.TWISTERS, R.array.exercise_tongue_twisters, twister, false);
        tvTwister.setText(twister);
    }

    private void updateTempoLabel() {
        tvTempo.setText(getString(R.string.exercise_tempo, getString(TEMPO_NAME[tempo])));
    }

    private void toggleReference() {
        if (ttsSpeaking) {
            stopReference();
            return;
        }
        if (!ttsReady) {
            Toast.makeText(requireContext(), R.string.exercise_tts_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        tts.setSpeechRate(TTS_RATE[tempo]);
        ttsSpeaking = true;
        updateReferenceButton();

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }
            @Override public void onDone(String utteranceId) {
                if ("ref_2".equals(utteranceId)) finishReferenceOnUi();
            }
            @Override public void onError(String utteranceId) { finishReferenceOnUi(); }
        });

        tts.speak(twister, TextToSpeech.QUEUE_FLUSH, null, "ref_1");
        tts.speak(twister, TextToSpeech.QUEUE_ADD, null, "ref_2");
    }

    private void finishReferenceOnUi() {
        uiHandler().post(() -> {
            ttsSpeaking = false;
            if (isAdded()) updateReferenceButton();
        });
    }

    private void stopReference() {
        if (tts != null) tts.stop();
        ttsSpeaking = false;
        if (isAdded() && btnPlay != null) updateReferenceButton();
    }

    private void updateReferenceButton() {
        btnPlay.setImageResource(ttsSpeaking ? R.drawable.ic_stop_square : R.drawable.ic_play);
        tvPlayLabel.setText(ttsSpeaking ? R.string.exercise_stop_reference : R.string.exercise_play_reference);
    }


    @Override
    protected void onRecordingStateChanged(boolean active) {
        btnRecord.setImageResource(active ? R.drawable.ic_stop_square : R.drawable.ic_mic);
        tvRecordLabel.setText(active ? R.string.exercise_stop_record : R.string.exercise_record);
        if (active) root.findViewById(R.id.cardResult).setVisibility(View.GONE);
    }

    @Override
    protected void onRecordingFinished(String text, double speechSeconds) {
        if (SpeechScorer.tokenize(text).isEmpty()) {
            showResult(root, getString(R.string.exercise_nothing_heard), "", false);
            return;
        }
        double accuracy = SpeechScorer.looseAccuracy(twister, text);
        double needAccuracy = MIN_ACCURACY[tempo];

        int refWords = SpeechScorer.tokenize(twister).size();
        int repeats = SpeechScorer.repeats(twister, text);
        double wps = speechSeconds > 0 ? refWords * (double) repeats / speechSeconds : 0;
        double needWps = TARGET_WPS[tempo];

        boolean accurate = accuracy >= needAccuracy;

        boolean fastEnough = speechSeconds <= 0 || wps >= needWps;
        boolean passed = accurate && fastEnough;

        String body = getString(R.string.twister_result, (int) Math.round(accuracy * 100), wps, needWps,
                (int) Math.round(needAccuracy * 100))
                + "\n" + getString(R.string.twister_heard, text);
        String verdict;
        if (passed) {
            verdict = getString(R.string.exercise_done);
            markPassed(ExerciseCategory.ID_TONGUE_TWISTERS);
        } else if (!accurate) {
            verdict = getString(R.string.twister_failed_accuracy);
        } else {
            verdict = getString(R.string.twister_failed_pace);
        }
        showResult(root, body, verdict, passed);
    }

    @Override
    public void onDestroyView() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ttsReady = false;
        ttsSpeaking = false;
        super.onDestroyView();
    }
}
