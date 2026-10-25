package com.example.wordtrigger;

import com.example.wordtrigger.BuildConfig;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class HomeFragment extends Fragment implements TabAware {
    // последние загруженные данные: по ним экран пересчитывается, когда приходит новое слово-паразит
    private int[] weekData = new int[7];
    private int yesterdayCount, maxOther, minOther = Integer.MAX_VALUE;
    private boolean statsLoaded;
    private android.content.SharedPreferences.OnSharedPreferenceChangeListener counterListener;

    private LineChart lineChart;
    private FirebaseFirestore db;
    private TextView countToday;
    private TextView tvMinDaily;
    private TextView tvMaxDaily;
    private TextView tvComparisonYesterday;
    private TextView tvDiffLabel;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        ImageView moreBtn = root.findViewById(R.id.ivMore);

        moreBtn.setOnClickListener(view -> {
            PopupMenu popup = new PopupMenu(getContext(), moreBtn);
            popup.getMenuInflater().inflate(R.menu.dashboard_menu, popup.getMenu());

            popup.show();
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.menu_help) {
                    showHelpDialog();
                    return true;
                } else if (item.getItemId() == R.id.menu_share) {
                    shareProgress();
                    return true;
                } else if (item.getItemId() == R.id.menu_about) {
                    showAboutUsDialog();
                    return true;
                } return false;
            });
        });

        lineChart = root.findViewById(R.id.chartSpeechStats);
        countToday = root.findViewById(R.id.tvTodayCount);
        tvMinDaily = root.findViewById(R.id.tvMinDaily);
        tvMaxDaily = root.findViewById(R.id.tvMaxDaily);
        tvComparisonYesterday = root.findViewById(R.id.tvComparisonYesterday);
        tvDiffLabel = root.findViewById(R.id.tvDiffLabel);
        db = FirebaseFirestore.getInstance();

        listenToUpdates();
        loadStatistics();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = getContext().getPackageName();
            PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }
        root.findViewById(R.id.btnGetReport).setOnClickListener(v -> {
            getAIWeeklyReport();
        });

        return root;
    }

    private void setupSimpleChart(ArrayList<Entry> entries) {

        if (entries == null || entries.isEmpty()) {
            lineChart.clear();
            return;
        }

        LineDataSet dataSet = new LineDataSet(entries, getString(R.string.home_chart_label));

        dataSet.setColor(Color.parseColor("#0000FF"));
        dataSet.setLineWidth(3f);
        dataSet.setCircleColor(Color.WHITE);
        dataSet.setCircleRadius(5f);
        dataSet.setDrawCircleHole(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#0000FF"));
        dataSet.setFillAlpha(50);
        dataSet.setDrawValues(false);

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);

        lineChart.setBackgroundColor(Color.parseColor("#121314"));
        lineChart.setDrawGridBackground(false);
        lineChart.getDescription().setEnabled(false);
        lineChart.getLegend().setEnabled(false);

        String[] days = getResources().getStringArray(R.array.weekdays_short);
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(days));
        xAxis.setGranularity(1f);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setDrawGridLines(false);
        xAxis.setAxisLineColor(Color.WHITE);

        lineChart.getAxisRight().setEnabled(false);
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setGridColor(Color.parseColor("#333333"));
        leftAxis.setAxisMinimum(0f);
        leftAxis.setGranularity(1f);

        lineChart.invalidate();
    }

    private void listenToUpdates() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        db.collection("users").document(uid)
                .addSnapshotListener((value, error) -> {
                    if (value != null && value.exists()) {
                        Long count = value.getLong("words_today");
                        if (count != null) countToday.setText(String.valueOf(count));
                    }
                });
    }

    private void loadStatistics() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String todayStr = StudyProgress.dateString(0);

        String yesterdayStr = StudyProgress.dateString(-1);

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setFirstDayOfWeek(java.util.Calendar.MONDAY);

        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        Date todayStart = cal.getTime();

        int dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK);
        int delta = (dayOfWeek == java.util.Calendar.SUNDAY) ? -6 : (java.util.Calendar.MONDAY - dayOfWeek);
        cal.add(java.util.Calendar.DAY_OF_YEAR, delta);
        Date mondayStart = cal.getTime();

        db.collection("users").document(uid).collection("history")
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots.isEmpty()) {
                        updateUI(new int[7], 0, 0, 0, Integer.MAX_VALUE);
                        return;
                    }

                    int[] weekData = new int[7];
                    int totalToday = 0;
                    int totalYesterday = 0;
                    int globalMax = 0;
                    int globalMin = Integer.MAX_VALUE;

                    for (QueryDocumentSnapshot doc : snapshots) {
                        long count = doc.getLong("total_count") != null ? doc.getLong("total_count") : 0;
                        String dateId = doc.getId();

                        // максимум и минимум считаем по остальным дням: сегодняшнее значение добавится при отрисовке
                        if (!dateId.equals(todayStr)) {
                            if (count > globalMax) globalMax = (int) count;
                            if (count > 0 && count < globalMin) globalMin = (int) count;
                        }

                        if (dateId.equals(todayStr)) totalToday = (int) count;
                        if (dateId.equals(yesterdayStr)) totalYesterday = (int) count;

                        try {
                            Date date = sdf.parse(dateId);
                            if (date != null && date.after(mondayStart) || dateId.equals(sdf.format(mondayStart))) {
                                java.util.Calendar tempCal = java.util.Calendar.getInstance();
                                tempCal.setTime(date);
                                int dayIndex = tempCal.get(java.util.Calendar.DAY_OF_WEEK);
                                int myIndex = (dayIndex == 1) ? 6 : dayIndex - 2; // Пн=0..Вс=6
                                if (myIndex >= 0 && myIndex < 7) weekData[myIndex] = (int) count;
                            }
                        } catch (Exception e) { e.printStackTrace(); }
                    }

                    updateUI(weekData, totalToday, totalYesterday, globalMax, globalMin);
                });
    }

    private void updateUI(int[] dailyCounts, int today, int yesterday, int max, int min) {
        if (getContext() != null) {
            today = Math.max(today, FillerCounter.getToday(getContext()));   // локальный счётчик мог уйти вперёд облака
            FillerCounter.set(getContext(), today);
            FillerCounter.setYesterday(getContext(), yesterday);
        }
        weekData = dailyCounts;
        yesterdayCount = yesterday;
        maxOther = max;
        minOther = min;
        statsLoaded = true;
        render(today);
    }

    /** Мгновенное обновление: сегодняшнее число берём из локального счётчика, который ведёт служба. */
    private void refreshFromLocal() {
        if (!isAdded() || getContext() == null || !statsLoaded) return;
        render(FillerCounter.getToday(requireContext()));
    }

    @Override
    public void onTabShown() {
        loadStatistics();
    }

    @Override
    public void onStart() {
        super.onStart();
        counterListener = FillerCounter.addListener(requireContext(), this::refreshFromLocal);
        refreshFromLocal();
    }

    @Override
    public void onStop() {
        FillerCounter.removeListener(requireContext(), counterListener);
        counterListener = null;
        super.onStop();
    }

    private void render(int today) {
        int[] dailyCounts = weekData.clone();
        int dow = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK);
        dailyCounts[dow == java.util.Calendar.SUNDAY ? 6 : dow - 2] = today;     // Пн=0 ... Вс=6
        int yesterday = yesterdayCount;
        int max = Math.max(maxOther, today);
        int min = today > 0 ? Math.min(minOther, today) : minOther;
        countToday.setText(String.valueOf(today));
        tvMaxDaily.setText(String.valueOf(max));
        tvMinDaily.setText(String.valueOf(min == Integer.MAX_VALUE ? 0 : min));

        int diff = yesterday - today;
        tvComparisonYesterday.setText(String.valueOf(Math.abs(diff)));

        if (diff > 0) {
            tvDiffLabel.setText(getString(R.string.home_diff_less, diff));
        } else if (diff < 0) {
            tvDiffLabel.setText(getString(R.string.home_diff_more, Math.abs(diff)));
        }
        else {
            tvDiffLabel.setText(getString(R.string.home_diff_same));
        }

        ArrayList<Entry> entries = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            entries.add(new Entry(i, dailyCounts[i]));
        }
        setupSimpleChart(entries);
    }
    private void getAIWeeklyReport() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        android.widget.Toast.makeText(requireContext(), R.string.ai_report_loading, android.widget.Toast.LENGTH_SHORT).show();

        db.collection("users").document(uid).collection("history")
                .whereGreaterThanOrEqualTo(com.google.firebase.firestore.FieldPath.documentId(), StudyProgress.dateString(-6))
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!isAdded()) return;
                    java.util.List<LocalReport.Day> days = new ArrayList<>();
                    StringBuilder stats = new StringBuilder();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Long total = doc.getLong("total_count");
                        java.util.List<String> ctx = (java.util.List<String>) doc.get("active_contexts");
                        int count = total == null ? 0 : total.intValue();
                        days.add(new LocalReport.Day(doc.getId(), count, ctx));
                        stats.append(getString(R.string.ai_report_day_line, doc.getId(),
                                ctx == null || ctx.isEmpty() ? "-" : android.text.TextUtils.join(", ", ctx), count));
                    }
                    if (days.isEmpty()) {
                        showAiReportDialog(getString(R.string.ai_report_local_title), getString(R.string.ai_report_empty));
                        return;
                    }

                    LlmClient.chat(requireContext(), getString(R.string.ai_report_system),
                            getString(R.string.ai_report_user, stats.toString()), 700, "weekly_report",
                            new LlmClient.Callback() {
                                @Override public void onResult(String text) {
                                    if (isAdded()) showAiReportDialog(getString(R.string.ai_report_title), text);
                                }
                                @Override public void onError(LlmClient.Kind kind, String detail) {
                                    if (!isAdded()) return;
                                    showAiReportDialog(getString(R.string.ai_report_local_title),
                                            LocalReport.build(requireContext(), days) + "\n\n"
                                                    + getString(R.string.ai_report_local_note) + "\n"
                                                    + getString(R.string.ai_report_reason,
                                                    LlmClient.explain(requireContext(), kind, detail)));
                                }
                            });
                });
    }

    private void showAiReportDialog(String title, String text) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(text)
                .setPositiveButton(R.string.ai_report_ok, null)
                .show();
    }
    private boolean isSameDay(Date date1, Date date2) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        return fmt.format(date1).equals(fmt.format(date2));
    }
    private void showAboutUsDialog(){
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.home_about_title)
                .setMessage(R.string.home_about_msg)
                .setPositiveButton(R.string.ai_report_ok, null)
                .show();
    }
    private void showHelpDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.home_help_title)
                .setMessage(R.string.home_help_msg)
                .setPositiveButton(R.string.ai_report_ok, null)
                .show();
    }

    private void shareProgress() {
        String message = getString(R.string.home_share_msg, String.valueOf(countToday.getText()));
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, message);
        startActivity(Intent.createChooser(intent, getString(R.string.home_share_title)));
    }
}