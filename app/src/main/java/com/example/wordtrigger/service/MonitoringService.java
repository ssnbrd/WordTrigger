package com.example.wordtrigger.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.wordtrigger.data.local.DeviceBattery;
import com.example.wordtrigger.data.local.FillerCounter;
import com.example.wordtrigger.R;
import com.example.wordtrigger.speech.SpeechRecognitionHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MonitoringService extends Service {
    private static final String CHANNEL_ID = "SpeechMonitoringChannel";
    public static final String ACTION_STATUS_UPDATE = "com.example.wordtrigger.STATUS_UPDATE";
    public static final String EXTRA_HEARTBEAT = "is_alive";
    public static final String EXTRA_TEXT = "heard_text";
    private String activeContext = "";

    private UdpService udpService;
    private SpeechRecognitionHelper speechHelper;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String uid = FirebaseAuth.getInstance().getUid();
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private long lastPacketTime = 0;
    private Handler statusHandler = new Handler();

    @Override
    public void onCreate() {
        super.onCreate();

        vibrationReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (udpService != null) udpService.sendVibrate();
            }
        };

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WordTrigger:WakeLock");
        wakeLock.acquire();

        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WordTrigger:WifiLock");
        wifiLock.acquire();

        speechHelper = createSpeechHelper();

        udpService = new UdpService((data, len) -> {
            lastPacketTime = System.currentTimeMillis();
            int battery = DeviceBattery.parsePacket(data, len);
            if (battery >= 0) {               // пакет «B:xx» - это заряд, а не звук
                DeviceBattery.save(getApplicationContext(), battery);
                return;
            }
            speechHelper.processAudio(data, len);
        });

        statusHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (speechHelper != null && speechHelper.isModelStale(MonitoringService.this)) {
                    List<String> words = currentTargetWords;
                    speechHelper.release();
                    speechHelper = createSpeechHelper();
                    if (words != null) speechHelper.setTargetWords(words);
                }
                boolean isAlive = (System.currentTimeMillis() - lastPacketTime) < 3000;
                broadcastStatus(isAlive, null);
                statusHandler.postDelayed(this, 2000);
            }
        }, 2000);
        IntentFilter filter = new IntentFilter("com.example.wordtrigger.ACTION_VIBRATE");
        db.collection("users").document(uid).addSnapshotListener((snapshot, e) -> {
            if (snapshot != null && snapshot.exists()) {
                List<String> wordsFromDb = (List<String>) snapshot.get("trigger_words");
                if (wordsFromDb != null && speechHelper != null) {
                    currentTargetWords = wordsFromDb;
                    speechHelper.setTargetWords(wordsFromDb);
                }
            }
        });
        androidx.core.content.ContextCompat.registerReceiver(
                this,
                vibrationReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    private void broadcastStatus(boolean isAlive, String text) {
        Intent intent = new Intent(ACTION_STATUS_UPDATE);
        intent.putExtra(EXTRA_HEARTBEAT, isAlive);
        if (text != null) intent.putExtra(EXTRA_TEXT, text);
        sendBroadcast(intent);
    }
    private void saveEvent(String word) {
        FillerCounter.increment(this);
        String uid = FirebaseAuth.getInstance().getUid();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        db.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
            List<String> currentContexts = (List<String>) userDoc.get("active_contexts");

            db.collection("users").document(uid).collection("history").document(today)
                    .set(new HashMap<String, Object>() {{
                        put("total_count", com.google.firebase.firestore.FieldValue.increment(1));
                        put("active_contexts", currentContexts);
                    }}, com.google.firebase.firestore.SetOptions.merge());

            Map<String, Object> event = new HashMap<>();
            event.put("word", word);
            event.put("contexts_at_moment", currentContexts);
            event.put("timestamp", com.google.firebase.Timestamp.now());

            db.collection("users").document(uid).collection("history").document(today)
                    .collection("events").add(event);
        });
    }

    private List<String> currentTargetWords;
    private BroadcastReceiver vibrationReceiver;

    private SpeechRecognitionHelper createSpeechHelper() {
        return new SpeechRecognitionHelper(this, new SpeechRecognitionHelper.OnWordDetectedListener() {
            @Override
            public void onDetected(String word) {
                if (udpService != null) udpService.sendVibrate();
                saveEvent(word);
            }
            @Override
            public void onPartialResult(String partialText) {
                broadcastStatus(true, partialText);
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "ACTION_STOP_SERVICE".equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        createNotificationChannel();
        Intent stopIntent = new Intent(this, MonitoringService.class);
        stopIntent.setAction("ACTION_STOP_SERVICE");
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(R.drawable.logo_black)
                .setColor(ContextCompat.getColor(this, R.color.black))
                .addAction(R.drawable.ic_stop, getString(R.string.notif_stop), stopPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        startForeground(1, notification);
        udpService.startListening(50005);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Intent intent = new Intent(ACTION_STATUS_UPDATE);
        intent.putExtra(EXTRA_HEARTBEAT, false);
        sendBroadcast(intent);

        if (wakeLock.isHeld()) wakeLock.release();
        if (wifiLock.isHeld()) wifiLock.release();
        if (udpService != null) udpService.stop();
        statusHandler.removeCallbacksAndMessages(null);
        if (vibrationReceiver != null) {
            try { unregisterReceiver(vibrationReceiver); } catch (IllegalArgumentException ignored) { }
            vibrationReceiver = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Monitoring", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    public void triggerVibration() {
        if (udpService != null) udpService.sendVibrate();
    }
}