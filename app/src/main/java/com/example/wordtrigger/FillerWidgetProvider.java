package com.example.wordtrigger;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;

public class FillerWidgetProvider extends AppWidgetProvider {
    private static final int COLOR_UP = Color.parseColor("#FF8A8A");
    private static final int COLOR_DOWN = Color.parseColor("#6BE59A");
    private static final int COLOR_NEUTRAL = Color.parseColor("#C4CAE8");

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            manager.updateAppWidget(id, buildViews(context));
        }
    }

    public static void updateAll(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, FillerWidgetProvider.class));
        if (ids == null || ids.length == 0) return;
        RemoteViews views = buildViews(app);
        for (int id : ids) manager.updateAppWidget(id, views);
    }

    private static RemoteViews buildViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_filler_count);
        int today = FillerCounter.getToday(context);
        views.setTextViewText(R.id.widgetCount, String.valueOf(today));

        Integer percent = FillerCounter.changePercent(today, FillerCounter.getYesterday(context));
        if (percent == null) {
            views.setTextViewText(R.id.widgetDelta, "—");
            views.setTextColor(R.id.widgetDelta, COLOR_NEUTRAL);
        } else {
            views.setTextViewText(R.id.widgetDelta, (percent > 0 ? "+" : "") + percent + "%");
            views.setTextColor(R.id.widgetDelta, percent > 0 ? COLOR_UP : percent < 0 ? COLOR_DOWN : COLOR_NEUTRAL);
        }

        Intent open = new Intent(context, WelcomeActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.widgetRoot, pi);
        return views;
    }
}
