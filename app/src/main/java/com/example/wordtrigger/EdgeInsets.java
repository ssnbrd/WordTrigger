package com.example.wordtrigger;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public final class EdgeInsets {
    private EdgeInsets() {}

    public static void apply(ComponentActivity activity) {
        apply(activity, true);
    }

    public static void apply(ComponentActivity activity, boolean darkBackground) {
        SystemBarStyle style = darkBackground
                ? SystemBarStyle.dark(Color.TRANSPARENT)
                : SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT);

        EdgeToEdge.enable(activity, style, style);

        ViewGroup content = activity.findViewById(android.R.id.content);
        View root = content.getChildAt(0);
        if (root == null) return;

        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(left + bars.left, top + bars.top, right + bars.right,
                    bottom + Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
