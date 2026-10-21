package com.example.wordtrigger;

import android.content.Context;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StudyProgress {
    public static final int DAILY_GOAL = 3;

    private StudyProgress() {}

    public static String dateString(int daysFromToday) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, daysFromToday);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    public static int effectiveStreak(DocumentSnapshot userDoc) {
        if (userDoc == null || !userDoc.exists()) return 0;
        Long streak = userDoc.getLong("streak");
        String last = userDoc.getString("last_streak_date");
        if (streak == null || last == null) return 0;
        boolean alive = last.equals(dateString(0)) || last.equals(dateString(-1));
        return alive ? streak.intValue() : 0;
    }

    public static void cacheStreak(Context context, DocumentSnapshot userDoc) {
        if (userDoc == null || !userDoc.exists()) return;
        Long streak = userDoc.getLong("streak");
        String last = userDoc.getString("last_streak_date");
        StudyCache.saveStreak(context, streak == null ? 0 : streak.intValue(), last);
        Long best = userDoc.getLong("best_streak");
        if (best != null) StudyCache.saveBest(context, best.intValue());
    }

    public static void markDone(Context context, String exerciseId, Runnable onDone) {
        final Context app = context.getApplicationContext();
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String today = dateString(0);
        DocumentReference history = db.collection("users").document(uid)
                .collection("history").document(today);

        Map<String, Object> data = new HashMap<>();
        data.put("exercises_done", FieldValue.arrayUnion(exerciseId));
        history.set(data, SetOptions.merge()).addOnSuccessListener(v ->
                history.get().addOnSuccessListener(doc -> {
                    List<String> done = (List<String>) doc.get("exercises_done");
                    StudyCache.saveDone(app, done == null ? 0 : done.size());
                    if (done != null && done.size() >= DAILY_GOAL) bumpStreak(app, db, uid, today);
                    if (onDone != null) onDone.run();
                }));
    }

    private static void bumpStreak(Context app, FirebaseFirestore db, String uid, String today) {
        DocumentReference userRef = db.collection("users").document(uid);
        String yesterday = dateString(-1);
        db.runTransaction(tx -> {
            DocumentSnapshot user = tx.get(userRef);
            String last = user.getString("last_streak_date");
            Long current = user.getLong("streak");
            if (today.equals(last)) return current == null ? 1L : current; // серия за сегодня уже засчитана
            long next = yesterday.equals(last) && current != null ? current + 1 : 1;
            Long best = user.getLong("best_streak");
            tx.update(userRef, "streak", next, "last_streak_date", today,
                    "best_streak", Math.max(best == null ? 0 : best, next));
            return next;
        }).addOnSuccessListener(streak -> StudyCache.saveStreak(app, streak.intValue(), today));
    }
}
