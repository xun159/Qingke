package wlw231.cly.qingke.ui.plan;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public class PlanReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "plan_reminder_channel";
    private static final String CHANNEL_NAME = "计划开始提醒";
    private static final int REMINDER_NOTIFICATION_ID = 2001;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        String planName = intent.getStringExtra("plan_name");
        if (planName == null || planName.isEmpty()) planName = "未命名计划";

        int quadrant = intent.getIntExtra("plan_quadrant", 1);
        String quadrantName = getQuadrantName(quadrant);

        // Android 13+ 检查通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w("PlanReminder", "没有通知权限，无法发送计划提醒");
                return;
            }
        }

        sendPlanReminderNotification(context, planName, quadrantName);
        Log.d("PlanReminder", "计划提醒已发送: " + planName);
    }

    private void sendPlanReminderNotification(Context context, String planName, String quadrantName) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        // 创建通知渠道（Android 8.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("当计划到达开始时间时发送提醒");
            channel.enableVibration(true);
            channel.enableLights(true);
            channel.setShowBadge(true);
            manager.createNotificationChannel(channel);
        }

        // 构建通知
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("⏰ 计划开始提醒")
                .setContentText("「" + planName + "」\n" + quadrantName)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("计划「" + planName + "」已到开始时间。\n" + quadrantName));

        // 发送通知
        NotificationManagerCompat.from(context).notify(REMINDER_NOTIFICATION_ID, builder.build());
    }

    private String getQuadrantName(int quadrant) {
        switch (quadrant) {
            case 1: return "🔴 重要且紧急";
            case 2: return "🟢 重要不紧急";
            case 3: return "🟡 紧急不重要";
            case 4: return "🔵 不重要不紧急";
            default: return "📌 未知象限";
        }
    }
}