package wlw231.cly.qingke.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import wlw231.cly.qingke.utils.CourseReminderHelper;

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            CourseReminderHelper.rescheduleAllReminders(context);
        }
    }
}