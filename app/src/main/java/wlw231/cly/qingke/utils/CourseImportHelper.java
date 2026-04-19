package wlw231.cly.qingke.utils;

import android.content.Context;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import wlw231.cly.qingke.model.Course;

public class CourseImportHelper {
    public static List<Course> importFromCsv(Context context, Uri uri) throws Exception {
        List<Course> courses = new ArrayList<>();
        try (InputStream is = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Course course = parseCsvLine(line);
                if (course != null) courses.add(course);
            }
        }
        return courses;
    }

    private static Course parseCsvLine(String line) {
        // 示例格式：周一,1,高等数学,张三,教101,1,16,08:00,09:40
        // 实际需根据具体格式解析
        return null;
    }
}