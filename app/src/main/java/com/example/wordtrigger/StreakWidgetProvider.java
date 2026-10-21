package com.example.wordtrigger;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;

public class StreakWidgetProvider extends AppWidgetProvider {

    public static final String EXTRA_OPEN_STUDY = "open_study";
    private static final int[] DOT_IDS = {R.id.widgetDot0, R.id.widgetDot1, R.id.widgetDot2, R.id.widgetDot3,
            R.id.widgetDot4, R.id.widgetDot5, R.id.widgetDot6};

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            manager.updateAppWidget(id, buildViews(context));
        }
    }

    public static void updateAll(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, StreakWidgetProvider.class));
        if (ids == null || ids.length == 0) return;
        RemoteViews views = buildViews(app);
        for (int id : ids) manager.updateAppWidget(id, views);
    }

    private static RemoteViews buildViews(Context context) {
        int streak = StudyCache.effectiveStreak(context);
        int done = StudyCache.doneToday(context);
        int goal = StudyProgress.DAILY_GOAL;
        boolean goalReached = done >= goal;
        int best = Math.max(StudyCache.best(context), streak);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_streak);
        views.setTextViewText(R.id.widgetStreakDays, String.valueOf(streak));
        views.setImageViewResource(R.id.widgetBolt, goalReached ? R.drawable.ic_bolt : R.drawable.ic_bolt_off);
        views.setInt(R.id.widgetStreakRoot, "setBackgroundResource",
                goalReached ? R.drawable.widget_dark_bg_done : R.drawable.widget_dark_bg);

        int filled = WidgetMath.filledDots(streak);
        int todayIndex = goalReached ? filled - 1 : filled % 7;
        for (int i = 0; i < DOT_IDS.length; i++) {
            int res = i == todayIndex ? R.drawable.widget_dot_today
                    : i < filled ? R.drawable.widget_dot_on : R.drawable.widget_dot_off;
            views.setImageViewResource(DOT_IDS[i], res);
        }

        if (goalReached) {
            views.setTextViewText(R.id.widgetStreakSub,
                    context.getResources().getQuantityString(R.plurals.widget_record_days, best, best));
            views.setTextColor(R.id.widgetStreakSub, Color.parseColor("#C4CAE8"));
        } else {
            views.setTextViewText(R.id.widgetStreakSub, context.getString(R.string.widget_lesson_left, done, goal));
            views.setTextColor(R.id.widgetStreakSub, Color.parseColor("#8FA6FF"));
        }

        Intent open = new Intent(context, WelcomeActivity.class);
        open.putExtra(EXTRA_OPEN_STUDY, true);
        PendingIntent pi = PendingIntent.getActivity(context, 2, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.widgetStreakRoot, pi);
        return views;
    }
}
