package wlw231.cly.qingke.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Log;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import wlw231.cly.qingke.model.Course;

public class CourseOcrHelper {

    private static final String TAG = "CourseOcrHelper";
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

    public interface OcrCallback {
        void onResult(List<Course> fullCourses, List<Course> partialCourses);
        void onError(Exception e);
    }

    /**
     * 识别图片并解析课程
     */
    public static void recognizeAndParse(Context context, Uri imageUri, OcrCallback callback) {
        try {
            // 压缩图片防止内存溢出
            Bitmap bitmap = getCompressedBitmap(context, imageUri, 1024);
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            TextRecognizer recognizer = TextRecognition.getClient(
                    new ChineseTextRecognizerOptions.Builder().build());

            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        Log.d(TAG, "OCR 成功，文本长度：" + visionText.getText().length());
                        List<Course> full = new ArrayList<>();
                        List<Course> partial = new ArrayList<>();
                        parseVisionText(visionText, bitmap.getHeight(), full, partial);
                        callback.onResult(full, partial);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "OCR 失败", e);
                        callback.onError(e);
                    });
        } catch (IOException e) {
            Log.e(TAG, "图片加载失败", e);
            callback.onError(e);
        }
    }

    /**
     * 压缩图片，最大边长 maxSize
     */
    private static Bitmap getCompressedBitmap(Context context, Uri uri, int maxSize) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        InputStream is = context.getContentResolver().openInputStream(uri);
        BitmapFactory.decodeStream(is, null, options);
        if (is != null) is.close();

        int scale = 1;
        while (options.outWidth / scale > maxSize || options.outHeight / scale > maxSize) {
            scale *= 2;
        }

        options = new BitmapFactory.Options();
        options.inSampleSize = scale;
        is = context.getContentResolver().openInputStream(uri);
        Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
        if (is != null) is.close();
        return bitmap;
    }

    private static void parseVisionText(Text visionText, int imageHeight,
                                        List<Course> fullCourses, List<Course> partialCourses) {
        List<Text.TextBlock> blocks = visionText.getTextBlocks();
        if (blocks.isEmpty()) return;

        // 1. 建立星期列坐标映射
        Map<Integer, Integer> colMap = new HashMap<>();
        for (Text.TextBlock block : blocks) {
            String text = block.getText().trim();
            Rect rect = block.getBoundingBox();
            if (rect == null) continue;
            if (WEEKDAY_MAP.containsKey(text)) {
                colMap.put(rect.centerX(), WEEKDAY_MAP.get(text));
            }
        }

        // 2. 合并分散的文本块
        List<TextBlockGroup> groups = mergeBlocksByLine(blocks);

        // 3. 解析每个合并块
        for (TextBlockGroup group : groups) {
            String combinedText = group.getText();
            Rect rect = group.getRect();
            if (rect == null) continue;
            if (isIrrelevantText(combinedText)) continue;

            int weekday = getClosestWeekday(rect.centerX(), colMap);
            int section = calculateSectionByHeight(rect.centerY(), imageHeight);

            Course course = parseCourse(combinedText);
            if (course == null) continue;

            // 根据是否已有星期和节次决定分类
            if (weekday > 0 && section > 0) {
                course.setWeekday(weekday);
                course.setSection(section);
                course.setStartWeek(1);
                course.setEndWeek(20);
                fullCourses.add(course);
            } else {
                partialCourses.add(course);
            }
        }
    }

    // ---------- 文本合并 ----------
    private static List<TextBlockGroup> mergeBlocksByLine(List<Text.TextBlock> blocks) {
        List<TextBlockGroup> groups = new ArrayList<>();
        // 过滤无边框的块
        List<Text.TextBlock> validBlocks = new ArrayList<>();
        for (Text.TextBlock block : blocks) {
            if (block.getBoundingBox() != null) validBlocks.add(block);
        }
        if (validBlocks.isEmpty()) return groups;

        validBlocks.sort((b1, b2) -> {
            Rect r1 = b1.getBoundingBox();
            Rect r2 = b2.getBoundingBox();
            return Integer.compare(r1.top, r2.top);
        });

        TextBlockGroup currentGroup = null;
        for (Text.TextBlock block : validBlocks) {
            Rect r = block.getBoundingBox();
            if (currentGroup == null) {
                currentGroup = new TextBlockGroup(block);
            } else {
                Rect lastRect = currentGroup.getRect();
                boolean sameColumn = Math.abs(r.centerX() - lastRect.centerX()) < 200;
                boolean nextLine = r.top > lastRect.bottom && (r.top - lastRect.bottom) < r.height() * 1.5;
                if (sameColumn || nextLine) {
                    currentGroup.addBlock(block);
                } else {
                    groups.add(currentGroup);
                    currentGroup = new TextBlockGroup(block);
                }
            }
        }
        if (currentGroup != null) groups.add(currentGroup);
        return groups;
    }

    // ---------- 位置计算 ----------
    private static int calculateSectionByHeight(int centerY, int totalHeight) {
        float ratio = (float) centerY / totalHeight;
        if (ratio < 0.15f) return 1;
        if (ratio < 0.35f) return 2;
        if (ratio < 0.55f) return 3;
        if (ratio < 0.75f) return 4;
        return 5;
    }

    private static int getClosestWeekday(int x, Map<Integer, Integer> colMap) {
        if (colMap.isEmpty()) return 0;
        int best = 1;
        int minDiff = Integer.MAX_VALUE;
        for (Map.Entry<Integer, Integer> e : colMap.entrySet()) {
            int diff = Math.abs(e.getKey() - x);
            if (diff < minDiff) {
                minDiff = diff;
                best = e.getValue();
            }
        }
        return best;
    }

    private static boolean isIrrelevantText(String text) {
        String t = text.trim();
        return t.matches("[0-9:]+") || t.contains("日程") || t.contains("课程表") ||
                t.contains("2025") || t.contains("学年") || (t.contains("周") && t.length() < 5);
    }

    // ---------- 课程解析 ----------
    private static Course parseCourse(String text) {
        String[] lines = text.split("\\n");
        if (lines.length == 0) return null;
        Course c = new Course();
        c.setName(lines[0].trim());

        Pattern roomPat = Pattern.compile("([0-9]+[-—][A-Za-z0-9]+|[A-Za-z0-9]+[-—][0-9]+)");
        Pattern teacherPat = Pattern.compile("^[\\u4e00-\\u9fa5]{2,5}$");
        String room = "", teacher = "";
        for (int i = 1; i < lines.length; i++) {
            String l = lines[i].trim();
            Matcher rm = roomPat.matcher(l);
            Matcher tm = teacherPat.matcher(l);
            if (rm.find()) room = rm.group(0);
            if (tm.matches()) teacher = tm.group(0);
        }
        c.setClassroom(room);
        c.setTeacher(teacher);
        return c;
    }

    // ---------- 辅助类 ----------
    private static class TextBlockGroup {
        private final StringBuilder textBuilder = new StringBuilder();
        private Rect boundingBox;

        TextBlockGroup(Text.TextBlock block) {
            addBlock(block);
        }

        void addBlock(Text.TextBlock block) {
            if (textBuilder.length() > 0) textBuilder.append("\n");
            textBuilder.append(block.getText());
            Rect r = block.getBoundingBox();
            if (r != null) {
                if (boundingBox == null) boundingBox = new Rect(r);
                else boundingBox.union(r);
            }
        }

        String getText() { return textBuilder.toString(); }
        Rect getRect() { return boundingBox; }
    }
}