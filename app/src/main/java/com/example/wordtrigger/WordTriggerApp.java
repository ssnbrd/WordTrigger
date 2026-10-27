package com.example.wordtrigger;

import android.app.Application;

import com.example.wordtrigger.data.local.AppSettings;

public class WordTriggerApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppSettings.applyTheme(this);
    }
}
