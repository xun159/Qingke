package wlw231.cly.qingke.utils;

import android.content.Context;
import android.net.Uri;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import wlw231.cly.qingke.model.Course;
import java.io.IOException;

public class CourseOcrHelper {

    public interface OcrCallback {
        void onResult(String rawText);
        void onError(Exception e);
    }

    /**
     * 从图片 URI 识别文字
     */
    public static void recognizeText(Context context, Uri imageUri, OcrCallback callback) {
        try {
            InputImage image = InputImage.fromFilePath(context, imageUri);
            TextRecognizer recognizer = TextRecognition.getClient(
                    new ChineseTextRecognizerOptions.Builder().build());

            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        String result = visionText.getText();
                        callback.onResult(result);
                    })
                    .addOnFailureListener(e -> callback.onError(e));
        } catch (IOException e) {
            callback.onError(e);
        }
    }

    /**
     * 从识别出的文本中解析课程信息（示例正则解析）
     * 实际可根据具体课表格式调整
     */
    public static Course parseCourseFromText(String rawText) {
        // 示例：从文本中提取 "周一 1-2节 高等数学 张三 教101"
        // 此处需根据实际课表格式编写解析逻辑
        return null;
    }
}