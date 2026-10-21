package com.example.wordtrigger;

import android.content.Context;
import android.util.Log;

import org.vosk.Model;
import org.vosk.android.StorageService;

import java.util.ArrayList;
import java.util.List;

public final class VoskModelHolder {
    public interface Callback {
        void onReady(Model model);
        void onError(Exception e);
    }

    private static Model model;
    private static String loadedDir;
    private static boolean loading;
    private static String loadingDir;
    private static final List<Callback> waiting = new ArrayList<>();

    private VoskModelHolder() {}

    public static String modelDirFor(Context context) {
        if ("en".equals(AppSettings.currentLanguage(context))) {
            try {
                String[] files = context.getAssets().list("model-en");
                if (files != null && files.length > 0) return "model-en";
            } catch (Exception ignored) { }
        }
        return "model";
    }

    public static synchronized void get(Context context, Callback callback) {
        final String dir = modelDirFor(context);
        if (model != null && dir.equals(loadedDir)) {
            callback.onReady(model);
            return;
        }
        waiting.add(callback);
        if (loading) return;
        loading = true;
        loadingDir = dir;
        StorageService.unpack(context.getApplicationContext(), dir, dir,
                (m) -> {
                    List<Callback> toNotify;
                    synchronized (VoskModelHolder.class) {
                        model = m;
                        loadedDir = loadingDir;
                        loading = false;
                        toNotify = new ArrayList<>(waiting);
                        waiting.clear();
                    }
                    for (Callback c : toNotify) c.onReady(m);
                },
                (e) -> {
                    Log.e("VOSK", "Unpack failed: " + e.getMessage());
                    List<Callback> toNotify;
                    synchronized (VoskModelHolder.class) {
                        loading = false;
                        toNotify = new ArrayList<>(waiting);
                        waiting.clear();
                    }
                    for (Callback c : toNotify) c.onError(e);
                });
    }
}
