package com.example.wordtrigger;

import android.app.Application;

/** Применяет выбранную тему до создания первого экрана, чтобы не было вспышки не той темы. */
public class WordTriggerApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppSettings.applyTheme(this);
    }
}
