package wlw231.cly.qingke.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.text.TextUtils;

import java.util.Calendar;

import wlw231.cly.qingke.data.CourseDatabaseHelper;
import wlw231.cly.qingke.model.Course;
import wlw231.cly.qingke.receiver.CourseReminderReceiver;

public class CourseReminderHelper {

    private static final int REQUEST_CODE_BASE = 10000;
    private static final int ADVANCE_MINUTES = 10;   // 提前10分钟提醒

    /**
     * 为课程设置提醒闹钟
     * @param context 上下文
     * @param course  课程对象
     */
    public static void setReminder(Context context, Course course) {
        if (TextUtils.isEmpty(course.getStartTime())) {
            return;  // 未设置上课时间，不提醒
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, CourseReminderReceiver.class);
        intent.putExtra("course_name", course.getName());
        intent.putExtra("classroom", course.getClassroom());
        intent.putExtra("teacher", course.getTeacher());
        intent.putExtra("start_time", course.getStartTime());
        intent.putExtra("end_time", course.getEndTime());

        int requestCode = generateRequestCode(course);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long triggerTime = calculateTriggerTime(context, course);
        if (triggerTime <= System.currentTimeMillis()) {
            return; // 已过提醒时间，不设置
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }
    }

    /**
     * 取消课程的提醒闹钟
     */
    public static void cancelReminder(Context context, Course course) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, CourseReminderReceiver.class);
        int requestCode = generateRequestCode(course);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pendingIntent);
    }

    /**
     * 重新注册所有未过期的课程提醒（设备重启后调用）
     */
    public static void rescheduleAllReminders(Context context) {
        new Thread(() -> {
            CourseDatabaseHelper dbHelper = new CourseDatabaseHelper(context);
            // 查询所有课程（这里需要自定义查询全部课程的方法）
            Cursor cursor = dbHelper.queryAllCourses();
            while (cursor.moveToNext()) {
                Course course = CourseDatabaseHelper.cursorToCourse(cursor);
                setReminder(context, course);
            }
            cursor.close();
        }).start();
    }

    /**
     * 计算提醒触发时间（上课当天上课时间 - 10分钟）
     * 注意：需要结合学期起始日期准确计算，此处简化处理，以本周为基准查找最近的上课日。
     */
    private static long calculateTriggerTime(Context context, Course course) {
        Calendar calendar = Calendar.getInstance();
        int todayWeekday = calendar.get(Calendar.DAY_OF_WEEK); // 1=周日 ... 7=周六

        int targetWeekday = convertWeekdayToCalendar(course.getWeekday());
        int daysDiff = targetWeekday - todayWeekday;
        if (daysDiff < 0) {
            daysDiff += 7; // 本周已过，移到下周
        }

        calendar.add(Calendar.DAY_OF_YEAR, daysDiff);

        String[] timeParts = course.getStartTime().split(":");
        if (timeParts.length != 2) return 0;

        int hour = Integer.parseInt(timeParts[0]);
        int minute = Integer.parseInt(timeParts[1]);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // 减去提前量
        calendar.add(Calendar.MINUTE, -ADVANCE_MINUTES);
        return calendar.getTimeInMillis();
    }

    /**
     * 将自定义星期（1=周一 ... 7=周日）转换为 Calendar 的星期常量
     */
    private static int convertWeekdayToCalendar(int weekday) {
        // Calendar: 1=周日, 2=周一, ..., 7=周六
        if (weekday == 7) return Calendar.SUNDAY;
        return weekday + 1;
    }

    /**
     * 生成唯一的请求码，用于区分不同课程的 PendingIntent
     */
    private static int generateRequestCode(Course course) {
        return REQUEST_CODE_BASE + course.getWeekday() * 100 + course.getSection() * 10 + course.getStartWeek();
    }
}