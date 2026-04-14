package com.example.wordtrigger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConnectFragment extends Fragment {
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String uid = FirebaseAuth.getInstance().getUid();
    private TextView statusText, tvConnectionStatus;
    private Button btnStart;
    private CardView searchBut;
    private boolean isServiceRunning = false;
    private Handler searchHandler = new Handler();
    private Runnable searchRunnable;
    private ChipGroup parasiteChipGroup;
    private  Button btnApplyWords;
    private  Button btnAddWord;
    private EditText etNewParasite;
    private SpeechRecognitionHelper speechHelper;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(MonitoringService.ACTION_STATUS_UPDATE)) {
                boolean isAlive = intent.getBooleanExtra(MonitoringService.EXTRA_HEARTBEAT, false);
                String heardText = intent.getStringExtra(MonitoringService.EXTRA_TEXT);

                if (isAlive) {
                    tvConnectionStatus.setText("Устройство подключено");
                    tvConnectionStatus.setTextColor(Color.parseColor("#0000FF"));
                } else {
                    tvConnectionStatus.setText("Поиск устройства...");
                    tvConnectionStatus.setTextColor(Color.parseColor("#121314"));
                }
                if (heardText != null) statusText.setText(heardText);
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_connect, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        statusText = view.findViewById(R.id.statusText);
        tvConnectionStatus = view.findViewById(R.id.tvDeviceStatus);
        btnStart = view.findViewById(R.id.btnStart);
        searchBut = view.findViewById(R.id.searchBut);
        btnApplyWords = view.findViewById(R.id.btnApplyWords);
        parasiteChipGroup = view.findViewById(R.id.parasiteChipGroup);
        btnAddWord = view.findViewById(R.id.btnAddWord);
        etNewParasite = view.findViewById(R.id.etNewParasite);

        btnStart.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(getContext(), MonitoringService.class);
            if (!isServiceRunning) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    getContext().startForegroundService(serviceIntent);
                } else {
                    getContext().startService(serviceIntent);
                }
                btnStart.setText("Остановить систему");
                isServiceRunning = true;
            } else {
                getContext().stopService(serviceIntent);
                btnStart.setText("Запустить систему");
                isServiceRunning = false;
                tvConnectionStatus.setText("Система выключена");
                tvConnectionStatus.setTextColor(Color.GRAY);
            }
        });

        searchBut.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                searchBut.setCardBackgroundColor(Color.LTGRAY);
                searchRunnable = new Runnable() {
                    @Override
                    public void run() {
                        Intent intent = new Intent("com.example.wordtrigger.ACTION_VIBRATE");
                        getContext().sendBroadcast(intent);
                        searchHandler.postDelayed(this, 600);
                    }
                };
                searchHandler.post(searchRunnable);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                searchBut.setCardBackgroundColor(Color.parseColor("#F6F6F6"));
                searchHandler.removeCallbacks(searchRunnable);
                return true;
            }
            return false;
        });
        btnApplyWords.setOnClickListener(v -> {
            List<String> selectedWords = new ArrayList<>();
            for (int id : parasiteChipGroup.getCheckedChipIds()) {
                selectedWords.add(((Chip)parasiteChipGroup.findViewById(id)).getText().toString().toLowerCase());
            }

            db.collection("users").document(uid).update("trigger_words", selectedWords)
                    .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Список обновлен!", Toast.LENGTH_SHORT).show());
        });

        btnAddWord.setOnClickListener(v -> {
            String newWord = etNewParasite.getText().toString().trim().toLowerCase();
            if (!newWord.isEmpty()) {
                Context themeContext = new android.view.ContextThemeWrapper(getContext(), R.style.CustomChipStyle);
                Chip chip = new Chip(themeContext);
                chip.setText(newWord);
                chip.setCheckable(true);
                chip.setChecked(true);

                chip.setCheckedIconVisible(false);

                int[][] states = new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                };
                int[] colors = new int[]{
                        Color.parseColor("#0000FF"),
                        Color.parseColor("#2C2C2E")
                };
                chip.setChipBackgroundColor(new android.content.res.ColorStateList(states, colors));

                chip.setTextColor(Color.WHITE);

                chip.setChipStrokeWidth(0f);

                if (!newWord.matches("^[a-zA-Zа-яА-ЯёЁ\\s]+$")) {
                    Toast.makeText(getContext(), "Цифры и символы запрещены", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (newWord.length() > 20) {
                    Toast.makeText(getContext(), "Слишком длинная фраза", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (newWord.contains("\n") || newWord.contains("\r")) {
                    Toast.makeText(getContext(), "Переносы строк запрещены", Toast.LENGTH_SHORT).show();
                    return;
                }

                parasiteChipGroup.addView(chip);
                etNewParasite.setText("");
            }
        });
        loadTriggerWords();
    }

    private final String[] defaultWords = {"короче", "типа", "ну ", "блин"};

    private void loadTriggerWords() {
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                List<String> savedWords = (List<String>) doc.get("trigger_words");
                if (savedWords == null) savedWords = new ArrayList<>();

                parasiteChipGroup.removeAllViews();

                for (String word : savedWords) {
                    addChipToGroup(word, true);
                }

                for (String dw : defaultWords) {
                    if (!savedWords.contains(dw)) {
                        addChipToGroup(dw, false);
                    }
                }
            }
        });
    }
    private void addChipToGroup(String word, boolean isChecked) {
        Context themeContext = new android.view.ContextThemeWrapper(getContext(), R.style.CustomChipStyle);
        Chip chip = new Chip(themeContext);

        chip.setText(word);
        chip.setCheckable(true);
        chip.setChecked(isChecked);
        chip.setCheckedIconVisible(false);

        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        int[] colors = new int[]{
                Color.parseColor("#0000FF"),
                Color.parseColor("#2C2C2E")
        };
        chip.setChipBackgroundColor(new android.content.res.ColorStateList(states, colors));

        chip.setTextColor(Color.WHITE);

        chip.setChipStrokeWidth(0f);

        if (!isDefaultWord(word)) {
            chip.setCloseIconVisible(true);
            chip.setCloseIconTint(android.content.res.ColorStateList.valueOf(Color.WHITE));
            chip.setOnCloseIconClickListener(v -> {
                parasiteChipGroup.removeView(chip);
            });
        }

        parasiteChipGroup.addView(chip);
    }


    private boolean isDefaultWord(String word) {
        for (String s : defaultWords) if (s.equalsIgnoreCase(word)) return true;
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(MonitoringService.ACTION_STATUS_UPDATE);
        ContextCompat.registerReceiver(getContext(), statusReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onPause() {
        super.onPause();
        getContext().unregisterReceiver(statusReceiver);
    }
}