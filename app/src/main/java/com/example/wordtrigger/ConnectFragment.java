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
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

public class ConnectFragment extends Fragment {
    private TextView statusText, tvConnectionStatus;
    private Button btnStart;
    private CardView searchBut;
    private boolean isServiceRunning = false;
    private Handler searchHandler = new Handler();
    private Runnable searchRunnable;

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