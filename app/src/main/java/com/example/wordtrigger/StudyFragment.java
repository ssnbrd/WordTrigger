package com.example.wordtrigger;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StudyFragment extends Fragment {

    private static final int DAILY_GOAL = StudyProgress.DAILY_GOAL;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final List<ExerciseCategory> categories = new ArrayList<>();
    private ExerciseCategoryAdapter adapter;

    private TextView tvStreakDays;
    private TextView tvDailyGoal;
    private TextView tvDailyGoalHint;
    private ProgressBar pbDailyGoal;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_study, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvStreakDays = view.findViewById(R.id.tvStreakDays);
        tvDailyGoal = view.findViewById(R.id.tvDailyGoal);
        tvDailyGoalHint = view.findViewById(R.id.tvDailyGoalHint);
        pbDailyGoal = view.findViewById(R.id.pbDailyGoal);
        pbDailyGoal.setMax(DAILY_GOAL);

        view.findViewById(R.id.returnBut).setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new HomeFragment())
                        .commit());

        categories.clear();
        categories.add(new ExerciseCategory(ExerciseCategory.ID_TONGUE_TWISTERS,
                R.string.study_tongue_twisters, R.string.study_tongue_twisters_desc));
        categories.add(new ExerciseCategory(ExerciseCategory.ID_MINDFUL_SPEECH,
                R.string.study_mindful_speech, R.string.study_mindful_speech_desc));
        categories.add(new ExerciseCategory(ExerciseCategory.ID_SYNONYMS,
                R.string.study_synonyms, R.string.study_synonyms_desc));

        RecyclerView rv = view.findViewById(R.id.rvExerciseCategories);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ExerciseCategoryAdapter(categories, this::openExercise);
        rv.setAdapter(adapter);

        updateProgress(0);
        updateStreak(0);
        loadProgress();
    }

    private void openExercise(ExerciseCategory category) {
        Fragment target;
        switch (category.id) {
            case ExerciseCategory.ID_TONGUE_TWISTERS: target = new TongueTwisterFragment(); break;
            case ExerciseCategory.ID_MINDFUL_SPEECH: target = new MindfulSpeechFragment(); break;
            case ExerciseCategory.ID_SYNONYMS: target = new SynonymsFragment(); break;
            default: return;
        }
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, target)
                .addToBackStack(null)
                .commit();
    }

    private void loadProgress() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        String today = StudyProgress.dateString(0);

        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (!isAdded()) return;
            StudyProgress.cacheStreak(requireContext(), doc);
            updateStreak(StudyProgress.effectiveStreak(doc));
        });

        db.collection("users").document(uid).collection("history").document(today).get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;
                    List<String> done = doc.exists() ? (List<String>) doc.get("exercises_done") : null;
                    StudyCache.saveDone(requireContext(), done == null ? 0 : done.size());
                    if (done == null) {
                        updateProgress(0);
                        return;
                    }

                    int count = 0;
                    for (ExerciseCategory c : categories) {
                        c.completedToday = done.contains(c.id);
                        if (c.completedToday) count++;
                    }
                    adapter.notifyDataSetChanged();
                    updateProgress(count);
                });
    }

    private void updateProgress(int done) {
        int shown = Math.min(done, DAILY_GOAL);
        tvDailyGoal.setText(getString(R.string.study_daily_goal, shown, DAILY_GOAL));
        pbDailyGoal.setProgress(shown);
        tvDailyGoalHint.setText(shown >= DAILY_GOAL
                ? getString(R.string.study_daily_goal_done)
                : getString(R.string.study_daily_goal_hint, DAILY_GOAL));
    }

    private void updateStreak(int days) {
        tvStreakDays.setText(getString(R.string.study_streak_days, days));
    }
}
