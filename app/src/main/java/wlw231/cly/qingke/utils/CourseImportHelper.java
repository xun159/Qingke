package wlw231.cly.qingke.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import wlw231.cly.qingke.model.Course;

public class CourseImportHelper {

    private static final String TAG = "CourseImportHelper";

    private static final Map<String, Integer> WEEKDAY_MAP = new HashMap<>();
    static {
        WEEKDAY_MAP.put("星期一", 1); WEEKDAY_MAP.put("周一", 1);
        WEEKDAY_MAP.put("星期二", 2); WEEKDAY_MAP.put("周二", 2);
        WEEKDAY_MAP.put("星期三", 3); WEEKDAY_MAP.put("周三", 3);
        WEEKDAY_MAP.put("星期四", 4); WEEKDAY_MAP.put("周四", 4);
        WEEKDAY_MAP.put("星期五", 5); WEEKDAY_MAP.put("周五", 5);
        WEEKDAY_MAP.put("星期六", 6); WEEKDAY_MAP.put("周六", 6);
        WEEKDAY_MAP.put("星期日", 7); WEEKDAY_MAP.put("周日", 7);
    }

    public static List<Course> importFromCsv(Context context, Uri uri) throws Exception {
        List<Course> courses = new ArrayList<>();
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine(); // 跳过标题行
            if (headerLine == null) return courses;

            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty()) continue;

                Course course = parseCsvLine(line, lineNum);
                if (course != null) {
                    courses.add(course);
                }
            }
        }
        return courses;
    }

    private static Course parseCsvLine(String line, int lineNum) {
        try {
            String[] parts = line.split(",");
            if (parts.length < 6) {
                Log.w(TAG, "第" + lineNum + "行字段不足，跳过: " + line);
                return null;
            }

            String weekdayStr = parts[0].trim();
            String sectionStr = parts[1].trim();
            String name = parts[2].trim();
            String teacher = parts[3].trim();
            String classroom = parts[4].trim();
            String weeksStr = parts[5].trim();

            Integer weekday = WEEKDAY_MAP.get(weekdayStr);
            if (weekday == null) {
                Log.w(TAG, "无法解析星期: " + weekdayStr);
                return null;
            }

            int section = parseSection(sectionStr);
            if (section == -1) {
                Log.w(TAG, "无法解析节次: " + sectionStr);
                return null;
            }

            int[] weeks = parseWeeks(weeksStr);
            if (weeks == null) {
                Log.w(TAG, "无法解析周次: " + weeksStr);
                return null;
            }

            Course course = new Course();
            course.setWeekday(weekday);
            course.setSection(section);
            course.setName(name);
            course.setTeacher(teacher);
            course.setClassroom(classroom);
            course.setStartWeek(weeks[0]);
            course.setEndWeek(weeks[1]);

            if (section == 5) {
                course.setSubSections(2);
            }
            return course;
        } catch (Exception e) {
            Log.e(TAG, "解析第" + lineNum + "行出错: " + line, e);
            return null;
        }
    }

    // 修改：支持空格，如 "第 1-2 节"
    private static int parseSection(String sectionStr) {
        Pattern pattern = Pattern.compile("第?\\s*(\\d+)\\s*-\\s*(\\d+)\\s*节");
        Matcher matcher = pattern.matcher(sectionStr);
        if (matcher.find()) {
            int start = Integer.parseInt(matcher.group(1));
            int end = Integer.parseInt(matcher.group(2));
            if (start >= 1 && start <= 2) return 1;
            if (start >= 3 && start <= 4) return 2;
            if (start >= 5 && start <= 6) return 3;
            if (start >= 7 && start <= 8) return 4;
            if (start >= 9) return 5;
        }
        return -1;
    }

    // 修改：支持空格，如 "1-16 周" 或 "1-16周"
    private static int[] parseWeeks(String weeksStr) {
        Pattern pattern = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)\\s*周?");
        Matcher matcher = pattern.matcher(weeksStr);
        if (matcher.find()) {
            int start = Integer.parseInt(matcher.group(1));
            int end = Integer.parseInt(matcher.group(2));
            return new int[]{start, end};
        }
        pattern = Pattern.compile("(\\d+)\\s*周");
        matcher = pattern.matcher(weeksStr);
        if (matcher.find()) {
            int week = Integer.parseInt(matcher.group(1));
            return new int[]{week, week};
        }
        return null;
    }
}