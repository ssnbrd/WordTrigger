package com.example.wordtrigger.ui.study;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.wordtrigger.speech.ExerciseRecorder;
import com.example.wordtrigger.R;
import com.example.wordtrigger.data.remote.StudyProgress;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;


public abstract class ExerciseBaseFragment extends Fragment {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoStop = this::stopRecording;
    private final ActivityResultLauncher<String> micPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) beginRecording();
                else if (isAdded()) Toast.makeText(requireContext(), R.string.exercise_mic_denied, Toast.LENGTH_SHORT).show();
            });

    private ExerciseRecorder recorder;
    private boolean busy;
    private boolean recording;
    private long recordStartMs;

    protected TextView tvStatus;

    @LayoutRes protected abstract int layoutRes();
    @StringRes protected abstract int titleRes();

    protected abstract void onContentCreated(View root);
    protected abstract void onRecordingFinished(String text, double speechSeconds);
    protected abstract long maxRecordMillis();
    protected void onRecordingStateChanged(boolean active) {}
    protected java.util.List<String> grammarWords() { return null; }
    protected int autoFinishWords() { return 0; }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(layoutRes(), container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((TextView) view.findViewById(R.id.tvHeaderTitle)).setText(titleRes());
        view.findViewById(R.id.returnBut).setOnClickListener(v ->
                getParentFragmentManager().popBackStack());
        tvStatus = view.findViewById(R.id.tvStatus);
        loadStreak(view);
        onContentCreated(view);
    }

    private void loadStreak(View view) {
        TextView tvStreak = view.findViewById(R.id.tvStreakDays);
        tvStreak.setText(getString(R.string.study_streak_days, 0));
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (isAdded()) tvStreak.setText(getString(R.string.study_streak_days,
                            StudyProgress.effectiveStreak(doc)));
                });
    }


    protected final boolean isRecording() { return recording; }
    protected final long recordingElapsedMs() {
        return recording ? SystemClock.elapsedRealtime() - recordStartMs : 0;
    }
    protected final Handler uiHandler() { return handler; }

    protected final void toggleRecording() {
        if (recording) {
            stopRecording();
        } else if (!busy) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                beginRecording();
            } else {
                micPermission.launch(Manifest.permission.RECORD_AUDIO);
            }
        }
    }

    private void beginRecording() {
        if (busy || !isAdded()) return;
        busy = true;
        recorder = new ExerciseRecorder(new ExerciseRecorder.Listener() {
            @Override public void onModelLoading() {
                setStatus(getString(R.string.exercise_loading_model));
            }

            @Override public void onStarted() {
                if (!isAdded()) return;
                recording = true;
                recordStartMs = SystemClock.elapsedRealtime();
                setStatus(getString(R.string.exercise_listening));
                handler.postDelayed(autoStop, maxRecordMillis());
                onRecordingStateChanged(true);
            }

            @Override public void onPartial(String textSoFar) {
                if (recording && !textSoFar.isEmpty()) setStatus(textSoFar);
            }

            @Override public void onFinished(String fullText, double speechSeconds) {
                handler.removeCallbacks(autoStop);
                boolean wasActive = recording;
                recording = false;
                busy = false;
                if (!isAdded()) return;
                setStatus("");
                if (wasActive) onRecordingStateChanged(false);
                onRecordingFinished(fullText, speechSeconds);
            }

            @Override public void onError(String message) {
                handler.removeCallbacks(autoStop);
                boolean wasActive = recording;
                recording = false;
                busy = false;
                if (!isAdded()) return;
                setStatus("");
                if (wasActive) onRecordingStateChanged(false);
                Toast.makeText(requireContext(),
                        getString(R.string.exercise_error, String.valueOf(message)), Toast.LENGTH_LONG).show();
            }
        });
        recorder.start(requireContext(), grammarWords(), autoFinishWords());
    }

    protected final void stopRecording() {
        if (recorder != null && recording) recorder.stop();
    }

    protected final void setStatus(String text) {
        if (tvStatus != null) tvStatus.setText(text);
    }

    protected final void showResult(View root, CharSequence body, CharSequence verdict, boolean passed) {
        root.findViewById(R.id.cardResult).setVisibility(View.VISIBLE);
        ((TextView) root.findViewById(R.id.tvResultBody)).setText(body);
        TextView v = root.findViewById(R.id.tvResultVerdict);
        v.setText(verdict);
        v.setTextColor(ContextCompat.getColor(requireContext(), passed ? R.color.blue : R.color.black));
        v.setVisibility(verdict == null || verdict.length() == 0 ? View.GONE : View.VISIBLE);
    }

    protected final void markPassed(String exerciseId) {
        StudyProgress.markDone(requireContext(), exerciseId, null);
    }

    protected final Locale appLocale() {
        return getResources().getConfiguration().getLocales().get(0);
    }

    protected static String formatTime(long millis) {
        long s = millis / 1000;
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        if (recorder != null) {
            recorder.cancel();
            recorder = null;
        }
        recording = false;
        busy = false;
        tvStatus = null;
        super.onDestroyView();
    }
}
