package wlw231.cly.qingke.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import wlw231.cly.qingke.MainActivity;
import wlw231.cly.qingke.R;

public class PomodoroService extends Service {
    private static final String TAG = "PomodoroService";
    private static final String CHANNEL_ID = "pomodoro_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_UPDATE_UI = "ACTION_UPDATE_UI";
    public static final String EXTRA_TIME_LEFT = "EXTRA_TIME_LEFT";
    public static final String EXTRA_STATUS = "EXTRA_STATUS";
    public static final String EXTRA_FINISHED = "EXTRA_FINISHED";

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_FINISHED = "FINISHED";

    private CountDownTimer countDownTimer;

    // 1. 剩余时间：会随着倒计时不断变化
    private long timeLeftInMillis;

    // 2. 总时间：会随着用户点击按钮（如15/25/45）而改变
    private long totalTimeInMillis;

    // 3. 基准总时间（关键）：记录用户最后一次设置的“总时长”是多少
    // 这个值只有在用户点击按钮设置时才变，重置时用来恢复
    private long baseTotalDuration = 25 * 60 * 1000L;

    private boolean isRunning = false;
    private boolean isFinished = false;
    private Vibrator vibrator;
    private NotificationManager notificationManager;
    private LocalBroadcastManager localBroadcastManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        createNotificationChannel();
        // 初始化时，基准时间就是默认25分钟
        baseTotalDuration = 25 * 60 * 1000L;
        timeLeftInMillis = baseTotalDuration;
        totalTimeInMillis = baseTotalDuration;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: " + (intent != null ? intent.getAction() : "null"));
        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }

        String action = intent.getAction();
        switch (action) {
            case "START":
                long duration = intent.getLongExtra("DURATION", baseTotalDuration);
                // 重点：开始时，totalTimeInMillis 设为传入的值
                // 但 baseTotalDuration 不变，除非用户手动改按钮
                totalTimeInMillis = duration;
                timeLeftInMillis = duration;
                startTimer(duration);
                break;
            case "PAUSE":
                pauseTimer();
                break;
            case "RESET":
                // 重点：重置时，直接把剩余时间设为“基准时间”
                // 这样无论用户自定义了1分钟还是100分钟，重置都回得去
                resetTimer();
                break;
            case "STOP":
                stopTimer();
                stopSelf();
                break;
            case "REQUEST_STATUS":
                broadcastCurrentStatus();
                break;
            case "SET_TOTAL":
                // 重点：只有用户点击按钮时，才更新“基准时间”
                // 这样重置才知道该回哪去
                long newTotal = intent.getLongExtra("TOTAL_DURATION", baseTotalDuration);
                baseTotalDuration = newTotal;
                totalTimeInMillis = newTotal;
                timeLeftInMillis = newTotal; // 同步更新当前状态
                broadcastUpdate(STATUS_PAUSED, timeLeftInMillis, totalTimeInMillis, false);
                break;
        }
        return START_STICKY;
    }

    // 重置逻辑：直接把时间拉回“基准时间”
    private void resetTimer() {
        Log.d(TAG, "resetTimer: 强制重置回基准时间");
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        // 关键代码：把剩余时间设为 基准总时间 (baseTotalDuration)
        // 这样如果是25分钟模式，就回25:00；如果是自定义1分钟，就回1:00
        timeLeftInMillis = baseTotalDuration;
        totalTimeInMillis = baseTotalDuration; // 确保总时长也对齐

        isRunning = false;
        isFinished = false;
        stopForeground(false);
        updateNotification(timeLeftInMillis);
        broadcastUpdate(STATUS_PAUSED, timeLeftInMillis, totalTimeInMillis, false);
    }

    private void startTimer(long duration) {
        Log.d(TAG, "startTimer: " + duration);
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        // 注意这里只改当前会话的 totalTimeInMillis，不改 baseTotalDuration
        totalTimeInMillis = duration;
        timeLeftInMillis = duration;
        isFinished = false;
        isRunning = true;
        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateNotification(timeLeftInMillis);
                broadcastUpdate(STATUS_RUNNING, timeLeftInMillis, totalTimeInMillis, false);
            }

            @Override
            public void onFinish() {
                Log.d(TAG, "onFinish");
                isRunning = false;
                isFinished = true;
                timeLeftInMillis = 0;
                stopForeground(true);
                notificationManager.cancel(NOTIFICATION_ID);
                broadcastUpdate(STATUS_FINISHED, 0, totalTimeInMillis, true);
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(1000);
                    }
                }
                sendCompletionNotification();
            }
        }.start();
        startForeground(NOTIFICATION_ID, buildNotification(timeLeftInMillis));
        broadcastUpdate(STATUS_RUNNING, timeLeftInMillis, totalTimeInMillis, false);
    }

    private void pauseTimer() {
        Log.d(TAG, "pauseTimer");
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        isRunning = false;
        isFinished = false;
        stopForeground(false);
        updateNotification(timeLeftInMillis);
        broadcastUpdate(STATUS_PAUSED, timeLeftInMillis, totalTimeInMillis, false);
    }

    private void stopTimer() {
        Log.d(TAG, "stopTimer");
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        isRunning = false;
        stopForeground(true);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private void broadcastCurrentStatus() {
        String status;
        if (isFinished) {
            status = STATUS_FINISHED;
        } else if (isRunning) {
            status = STATUS_RUNNING;
        } else {
            status = STATUS_PAUSED;
        }
        Log.d(TAG, "broadcastCurrentStatus: " + status + " left=" + timeLeftInMillis);
        broadcastUpdate(status, timeLeftInMillis, totalTimeInMillis, isFinished);
    }

    private void broadcastUpdate(String status, long left, long total, boolean finished) {
        Intent intent = new Intent(ACTION_UPDATE_UI);
        intent.putExtra(EXTRA_STATUS, status);
        intent.putExtra(EXTRA_TIME_LEFT, left);
        intent.putExtra("TOTAL_TIME", total);
        intent.putExtra(EXTRA_FINISHED, finished);
        localBroadcastManager.sendBroadcast(intent);
    }

    private void updateNotification(long millisLeft) {
        Notification notification = buildNotification(millisLeft);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private Notification buildNotification(long millisLeft) {
        String timeText = formatTime(millisLeft);
        String contentText = isRunning ? "专注中 - " + timeText : "已暂停 - " + timeText;
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🍅 番茄钟")
                .setContentText(contentText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        return builder.build();
    }

    private void sendCompletionNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🍅 番茄钟完成")
                .setContentText("专注时间结束！休息一下吧。")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private String formatTime(long millis) {
        int minutes = (int) (millis / 1000) / 60;
        int seconds = (int) (millis / 1000) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "番茄钟计时",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("番茄钟前台服务通知");
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}