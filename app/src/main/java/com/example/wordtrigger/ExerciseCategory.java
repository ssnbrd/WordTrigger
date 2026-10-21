package com.example.wordtrigger;

import androidx.annotation.StringRes;

public class ExerciseCategory {
    public static final String ID_TONGUE_TWISTERS = "tongue_twisters";
    public static final String ID_MINDFUL_SPEECH = "mindful_speech";
    public static final String ID_SYNONYMS = "synonyms";

    public final String id;
    @StringRes public final int titleRes;
    @StringRes public final int descRes;
    public boolean completedToday;

    public ExerciseCategory(String id, @StringRes int titleRes, @StringRes int descRes) {
        this.id = id;
        this.titleRes = titleRes;
        this.descRes = descRes;
    }
}
