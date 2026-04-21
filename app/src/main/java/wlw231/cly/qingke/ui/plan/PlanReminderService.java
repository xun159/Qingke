package wlw231.cly.qingke.ui.plan;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.List;

import wlw231.cly.qingke.MainActivity;
import wlw231.cly.qingke.R;

public class PlanReminderService extends Service {
    private static final String TAG = "PlanReminderService";
    private static final String CHANNEL_ID = "plan_reminder_foreground";
    private static final int FOREGROUND_NOTIFICATION_ID = 4000;
    private static final long CHECK_INTERVAL = 60 * 1000; // 1分钟

    private HandlerThread handlerThread;
    private Handler handler;
    private volatile boolean isRunning = false;
    private PlanDatabaseHelper dbHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        dbHelper = PlanDatabaseHelper.getInstance(this);
        handlerThread = new HandlerThread("PlanReminderThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        startForeground(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRunning) {
            isRunning = true;
            handler.post(reminderRunnable);
        }
        return START_STICKY;
    }

    private final Runnable reminderRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                checkAndNotify();
            } catch (Exception e) {
                Log.e(TAG, "检查提醒时出错", e);
            } finally {
                if (isRunning) {
                    handler.postDelayed(this, CHECK_INTERVAL);
                }
            }
        }
    };

    private void checkAndNotify() {
        long now = System.currentTimeMillis();
        long windowStart = now - 30 * 1000;
        long windowEnd = now + 30 * 1000;

        List<PlanEntity> allPlans = dbHelper.getAllPlansSync();
        if (allPlans == null) return;

        for (PlanEntity plan : allPlans) {
            if (plan.isCompleted || plan.notified) continue;
            long start = plan.startTimeMillis;
            if (start >= windowStart && start <= windowEnd) {
                sendPlanNotification(plan);
                dbHelper.markNotified(plan.id);
                Log.d(TAG, "发送通知: " + plan.name);
            }
        }
    }

    private void sendPlanNotification(PlanEntity plan) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;

        String channelId = "plan_reminder";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "计划提醒", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        String quadrantName = getQuadrantName(plan.quadrant);
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("⏰ " + plan.name)
                .setContentText(quadrantName + " · 现在开始")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        manager.notify((int) plan.id, builder.build());
    }

    private String getQuadrantName(int quadrant) {
        switch (quadrant) {
            case 1: return "🔥 重要紧急";
            case 2: return "📌 重要不紧急";
            case 3: return "⏰ 紧急不重要";
            case 4: return "🗑️ 不重要不紧急";
            default: return "";
        }
    }

    private android.app.Notification buildForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "计划提醒后台", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("计划提醒运行中")
                .setContentText("正在监控计划开始时间")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        handler.removeCallbacks(reminderRunnable);
        handlerThread.quitSafely();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}