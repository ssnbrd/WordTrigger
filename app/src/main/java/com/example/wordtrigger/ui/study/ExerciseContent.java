package com.example.wordtrigger.ui.study;

import android.content.Context;

import androidx.annotation.ArrayRes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class ExerciseContent {
    private static final Random RANDOM = new Random();

    private ExerciseContent() {}

    public static String random(Context context, @ArrayRes int arrayRes) {
        String[] items = context.getResources().getStringArray(arrayRes);
        return items[RANDOM.nextInt(items.length)];
    }

    public static List<String> list(Context context, @ArrayRes int arrayRes) {
        return new ArrayList<>(Arrays.asList(context.getResources().getStringArray(arrayRes)));
    }
}
