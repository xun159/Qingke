package wlw231.cly.qingke.ui.plan;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class PlanReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "plan_reminder_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        String planName = intent.getStringExtra("plan_name");
        if (planName == null) planName = "未知计划";

        int quadrant = intent.getIntExtra("plan_quadrant", 1);
        String qName;
        if (quadrant == 2) qName = "🟢 重要不紧急";
        else if (quadrant == 3) qName = "🟡 紧急不重要";
        else if (quadrant == 4) qName = "🔵 不重要不紧急";
        else qName = "🔴 重要且紧急";

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "计划开始提醒", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("计划到达开始时间时自动提醒");
        channel.enableVibration(true);
        channel.enableLights(true);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("⏰ 计划即将开始")
                    .setContentText("「" + planName + "」\n" + qName)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setDefaults(NotificationCompat.DEFAULT_ALL);

            NotificationManagerCompat.from(context).notify(
                    (int) System.currentTimeMillis(), builder.build());

            Log.d("PlanReminder", "Notification sent for: " + planName);
        } else {
            Log.w("PlanReminder", "Notifications are disabled for this app");
        }
    }
}