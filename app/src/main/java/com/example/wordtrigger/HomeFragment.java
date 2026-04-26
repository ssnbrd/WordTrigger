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

public class HomeFragment extends Fragment {
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
            Toast.makeText(getContext(), "Подготовка отчета...", Toast.LENGTH_SHORT).show();
            getAIWeeklyReport();
        });

        return root;
    }

    private void setupSimpleChart(ArrayList<Entry> entries) {

        if (entries == null || entries.isEmpty()) {
            lineChart.clear();
            return;
        }

        LineDataSet dataSet = new LineDataSet(entries, "Слова-паразиты");

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

        String[] days = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
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

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String todayStr = sdf.format(new Date());

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
        String yesterdayStr = sdf.format(cal.getTime());

        db.collection("users").document(uid).collection("history")
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots.isEmpty()) {
                        updateUI(new int[7], 0, 0, 0, 0);
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

                        if (count > globalMax) globalMax = (int) count;
                        if (count > 0 && count < globalMin) globalMin = (int) count;

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

                    if (globalMin == Integer.MAX_VALUE) globalMin = 0;

                    updateUI(weekData, totalToday, totalYesterday, globalMax, globalMin);
                });
    }

    private void updateUI(int[] dailyCounts, int today, int yesterday, int max, int min) {
        countToday.setText(String.valueOf(today));
        tvMaxDaily.setText(String.valueOf(max));
        tvMinDaily.setText(String.valueOf(min == Integer.MAX_VALUE ? 0 : min));

        int diff = yesterday - today;
        tvComparisonYesterday.setText(String.valueOf(Math.abs(diff)));

        if (diff > 0) {
            tvDiffLabel.setText("На " + diff + " меньше, чем вчера");
        } else if (diff < 0) {
            tvDiffLabel.setText("На " + Math.abs(diff) + " больше, чем вчера");
        }
        else {
            tvDiffLabel.setText("Столько же, сколько вчера");
        }

        ArrayList<Entry> entries = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            entries.add(new Entry(i, dailyCounts[i]));
        }
        setupSimpleChart(entries);
    }
    private void getAIWeeklyReport() {
        String uid = FirebaseAuth.getInstance().getUid();
        db.collection("users").document(uid).collection("history")
                .get()
                .addOnSuccessListener(snapshots -> {
                    StringBuilder statsSummary = new StringBuilder();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        statsSummary.append("День: ").append(doc.getId())
                                .append(". Контексты: ").append(doc.get("active_contexts"))
                                .append(". Всего паразитов: ").append(doc.get("total_count")).append("; ");
                    }
                    sendRequestToGroq(statsSummary.toString());
                });
    }
    private void sendRequestToGroq(String fullStats) {
        new Thread(() -> {
            try {
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", "llama-3.1-8b-instant");

                org.json.JSONArray messages = new org.json.JSONArray();

                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", "Ты лингвист-психолог. Проанализируй данные речи (количество слов-паразитов за период) и контексты речи, отмеченные пользователем в различные дни. Отвечай на русском и ответ не должен иметь продолжение, ответ должен иметь логическое завершение и содержать анализ речи и упражнения для индивидуального плана улучшения речи (для дикции, речевого дыхания и отсутствия слов-паразитов).");

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", "Мои данные за неделю: " + fullStats);

                messages.put(systemMsg);
                messages.put(userMsg);
                jsonBody.put("messages", messages);

                URL url = new URL("https://api.groq.com/openai/v1/chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + BuildConfig.GROQ_API_KEY);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.toString().getBytes("UTF-8"));
                }

                if (conn.getResponseCode() == 200) {
                    Scanner s = new Scanner(conn.getInputStream(), "UTF-8").useDelimiter("\\A");
                    String response = s.hasNext() ? s.next() : "";
                    JSONObject respJson = new JSONObject(response);
                    String aiText = respJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");

                    getActivity().runOnUiThread(() -> showAiReportDialog(aiText));
                } else {
                    Scanner s = new Scanner(conn.getErrorStream(), "UTF-8").useDelimiter("\\A");
                    Log.e("GROQ_ERROR", "Server returned: " + conn.getResponseCode() + " " + (s.hasNext() ? s.next() : ""));
                }
            } catch (Exception e) {
                Log.e("GROQ_ERROR", "Crash", e);
            }
        }).start();
    }

    private void showAiReportDialog(String text) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Аналитический отчет ИИ")
                .setMessage(text)
                .setPositiveButton("Понятно", null)
                .show();
    }
    private boolean isSameDay(Date date1, Date date2) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        return fmt.format(date1).equals(fmt.format(date2));
    }
    private void showAboutUsDialog(){
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Об авторе проекта")
                .setMessage("Система контроля культуры речи\n Автор: Софья Бурда\n" +
                        "Контакты: ssnbrd3@gmail.com\n Ссылка проекта на Github: https://github.com/ssnbrd/WordTrigger\n" +
                        "Научный руководитель: Андрейчук Александр Олегович")
                .setPositiveButton("Понятно", null)
                .show();
    }
    private void showHelpDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Как пользоваться системой?")
                .setMessage("1. Включите устройство.\n2. Проверьте интернет соединение и в приложении нажмите Запустить систему.\n" +
                        "3. Выберите контекст в профиле и загрузите его.\n4. Если вы скажете слово-паразит, устройство завибрирует.\n" +
                        "5. Вечером отключите систему и получите ИИ-отчет!")
                .setPositiveButton("Понятно", null)
                .show();
    }

    private void shareProgress() {
        String message = "Мой прогресс в WordTrigger: сегодня я сказал " + countToday.getText() + " слов-паразитов.";
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, message);
        startActivity(Intent.createChooser(intent, "Поделиться"));
    }
}