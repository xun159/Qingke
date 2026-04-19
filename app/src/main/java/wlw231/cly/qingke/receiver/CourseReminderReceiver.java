package wlw231.cly.qingke.receiver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;

import wlw231.cly.qingke.MainActivity;
import wlw231.cly.qingke.R;

public class CourseReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "course_reminder_channel";
    private static final int NOTIFICATION_ID_BASE = 2000;

    @Override
    public void onReceive(Context context, Intent intent) {
        String courseName = intent.getStringExtra("course_name");
        String classroom = intent.getStringExtra("classroom");
        String teacher = intent.getStringExtra("teacher");
        String startTime = intent.getStringExtra("start_time");
        String endTime = intent.getStringExtra("end_time");

        if (TextUtils.isEmpty(courseName)) {
            courseName = "课程";
        }

        String contentText = "即将开始";
        if (!TextUtils.isEmpty(startTime) && !TextUtils.isEmpty(endTime)) {
            contentText = startTime + " - " + endTime;
        }
        if (!TextUtils.isEmpty(classroom)) {
            contentText += " @" + classroom;
        }

        showNotification(context, courseName, contentText);
    }

    private void showNotification(Context context, String title, String content) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel(manager);

        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("⏰ " + title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        int notificationId = NOTIFICATION_ID_BASE + (int) (System.currentTimeMillis() % 1000);
        manager.notify(notificationId, builder.build());
    }

    private void createNotificationChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "课程提醒",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("课程开始前10分钟提醒");
            manager.createNotificationChannel(channel);
        }
    }
}