package wlw231.cly.qingke.ui.course;

import static android.app.Activity.RESULT_OK;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import wlw231.cly.qingke.R;
import wlw231.cly.qingke.data.CourseDatabaseHelper;
import wlw231.cly.qingke.model.Course;
import wlw231.cly.qingke.utils.CourseImportHelper;
import wlw231.cly.qingke.utils.CourseOcrHelper;
import wlw231.cly.qingke.utils.CourseReminderHelper;

public class CourseTableFragment extends Fragment {

    private static final String TAG = "CourseTableFragment";
    private static final int TOTAL_WEEKDAYS = 7;
    private static final int TOTAL_SECTIONS = 5;
    private static final int REQUEST_CODE_PICK_IMAGE = 1001;
    private static final int REQUEST_CODE_IMPORT_FILE = 1002;

    private static final String[] COLOR_PALETTE = {
            "#FFAB91", "#80CBC4", "#FFF59D", "#CE93D8",
            "#90CAF9", "#A5D6A7", "#F48FB1", "#FFCC80"
    };

    private ConstraintLayout headerRow;
    private ConstraintLayout gridContainer;
    private Spinner spinnerWeek;
    private CourseDatabaseHelper dbHelper;

    private int currentWeek = 1;
    private final Map<String, MaterialCardView> cellMap = new HashMap<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_course_table, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dbHelper = new CourseDatabaseHelper(requireContext());

        headerRow = view.findViewById(R.id.headerRow);
        gridContainer = view.findViewById(R.id.gridContainer);
        spinnerWeek = view.findViewById(R.id.spinnerWeek);

        initWeekSpinner();
        buildTableHeader();
        buildGridCells();
        loadCoursesForWeek(currentWeek);

        MaterialButton btnImport = view.findViewById(R.id.btnImport);
        MaterialButton btnOcr = view.findViewById(R.id.btnOcr);
        btnImport.setOnClickListener(v -> showImportOptions());
        btnOcr.setOnClickListener(v -> startOcrRecognition());
    }

    private void initWeekSpinner() {
        Integer[] weeks = new Integer[20];
        for (int i = 0; i < 20; i++) weeks[i] = i + 1;
        ArrayAdapter<Integer> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, weeks);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerWeek.setAdapter(adapter);
        spinnerWeek.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentWeek = position + 1;
                loadCoursesForWeek(currentWeek);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void buildTableHeader() {
        headerRow.removeAllViews();
        String[] weekdays = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        int cellWidth = dpToPx(52);
        int cellHeight = dpToPx(36);

        for (int i = 0; i < TOTAL_WEEKDAYS; i++) {
            TextView tv = new TextView(requireContext());
            tv.setId(View.generateViewId());
            tv.setText(weekdays[i]);
            tv.setTextSize(14);
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary));
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundResource(R.drawable.cell_header_bg);

            ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(cellWidth, cellHeight);
            if (i == 0) {
                params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            } else {
                params.startToEnd = headerRow.getChildAt(i - 1).getId();
            }
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
            tv.setLayoutParams(params);
            headerRow.addView(tv);
        }
    }

    private void buildGridCells() {
        gridContainer.removeAllViews();
        cellMap.clear();

        int cellWidth = dpToPx(52);
        int cellHeight = dpToPx(96);
        MaterialCardView[][] cells = new MaterialCardView[TOTAL_SECTIONS][TOTAL_WEEKDAYS];

        for (int row = 0; row < TOTAL_SECTIONS; row++) {
            for (int col = 0; col < TOTAL_WEEKDAYS; col++) {
                MaterialCardView card = createEmptyCell(row + 1, col + 1);
                cells[row][col] = card;
                gridContainer.addView(card);
                String key = (col + 1) + "_" + (row + 1);
                cellMap.put(key, card);
            }
        }

        ConstraintSet set = new ConstraintSet();
        set.clone(gridContainer);
        for (int row = 0; row < TOTAL_SECTIONS; row++) {
            for (int col = 0; col < TOTAL_WEEKDAYS; col++) {
                MaterialCardView card = cells[row][col];
                int cardId = card.getId();
                set.constrainWidth(cardId, cellWidth);
                set.constrainHeight(cardId, cellHeight);
                if (row == 0) {
                    set.connect(cardId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                } else {
                    set.connect(cardId, ConstraintSet.TOP, cells[row - 1][col].getId(), ConstraintSet.BOTTOM);
                }
                if (col == 0) {
                    set.connect(cardId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                } else {
                    set.connect(cardId, ConstraintSet.START, cells[row][col - 1].getId(), ConstraintSet.END);
                }
            }
        }
        set.applyTo(gridContainer);
    }

    private MaterialCardView createEmptyCell(int section, int weekday) {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setId(View.generateViewId());
        card.setRadius(dpToPx(8));
        card.setCardElevation(dpToPx(2));
        card.setCardBackgroundColor(Color.WHITE);
        card.setUseCompatPadding(true);

        TextView tv = new TextView(requireContext());
        tv.setId(View.generateViewId());
        tv.setTextSize(11);
        tv.setTextColor(Color.BLACK);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(4, 4, 4, 4);
        tv.setMaxLines(5);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(tv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        card.setOnClickListener(v -> showEditDialog(section, weekday, card));
        card.setOnLongClickListener(v -> {
            deleteCourse(section, weekday, card);
            return true;
        });
        return card;
    }

    private void loadCoursesForWeek(int week) {
        for (MaterialCardView card : cellMap.values()) {
            card.setCardBackgroundColor(Color.WHITE);
            TextView tv = (TextView) card.getChildAt(0);
            tv.setText("");
            tv.setTextColor(Color.BLACK);
            card.setTag(null);
        }

        Cursor cursor = dbHelper.queryCoursesByWeek(week);
        while (cursor.moveToNext()) {
            Course course = CourseDatabaseHelper.cursorToCourse(cursor);
            String key = course.getWeekday() + "_" + course.getSection();
            MaterialCardView card = cellMap.get(key);
            if (card != null) {
                updateCellDisplay(card, course);
            }
        }
        cursor.close();
    }

    private void updateCellDisplay(MaterialCardView card, Course course) {
        TextView tv = (TextView) card.getChildAt(0);
        String display = course.getName();
        if (!TextUtils.isEmpty(course.getClassroom())) {
            display += "\n@" + course.getClassroom();
        }
        if (!TextUtils.isEmpty(course.getTeacher())) {
            display += "\n" + course.getTeacher();
        }
        tv.setText(display);

        String colorStr = course.getColor();
        if (TextUtils.isEmpty(colorStr)) {
            colorStr = getColorForCourse(course.getName());
        }
        try {
            int bgColor = Color.parseColor(colorStr);
            card.setCardBackgroundColor(bgColor);
            tv.setTextColor(isColorDark(bgColor) ? Color.WHITE : Color.BLACK);
        } catch (IllegalArgumentException e) {
            card.setCardBackgroundColor(Color.parseColor(COLOR_PALETTE[0]));
        }
        card.setTag(course);
    }

    private String getColorForCourse(String courseName) {
        if (TextUtils.isEmpty(courseName)) {
            return COLOR_PALETTE[0];
        }
        int index = Math.abs(courseName.hashCode()) % COLOR_PALETTE.length;
        return COLOR_PALETTE[index];
    }

    private Course getCourseFromCard(MaterialCardView card) {
        Object tag = card.getTag();
        return (tag instanceof Course) ? (Course) tag : null;
    }

    private void showEditDialog(int section, int weekday, MaterialCardView card) {
        Course existing = getCourseFromCard(card);
        CourseEditDialogFragment dialog = CourseEditDialogFragment.newInstance(weekday, section, existing);
        dialog.setOnSaveListener(course -> {
            course.setColor(getColorForCourse(course.getName()));
            dbHelper.saveCourse(course);
            CourseReminderHelper.setReminder(requireContext(), course);
            if (currentWeek >= course.getStartWeek() && currentWeek <= course.getEndWeek()) {
                updateCellDisplay(card, course);
            } else {
                card.setCardBackgroundColor(Color.WHITE);
                ((TextView) card.getChildAt(0)).setText("");
                card.setTag(null);
            }
            Toast.makeText(getContext(), "课程已保存", Toast.LENGTH_SHORT).show();
        });
        dialog.show(getChildFragmentManager(), "CourseEditDialog");
    }

    private void deleteCourse(int section, int weekday, MaterialCardView card) {
        Course course = getCourseFromCard(card);
        if (course == null) {
            Toast.makeText(getContext(), "该时段无课程", Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除课程")
                .setMessage("确定删除课程「" + course.getName() + "」吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    dbHelper.deleteCourse(weekday, section, course.getStartWeek(), course.getEndWeek());
                    CourseReminderHelper.cancelReminder(requireContext(), course);
                    card.setCardBackgroundColor(Color.WHITE);
                    ((TextView) card.getChildAt(0)).setText("");
                    card.setTag(null);
                    Toast.makeText(getContext(), "已删除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showImportOptions() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "选择课表文件"), REQUEST_CODE_IMPORT_FILE);
    }

    private void importCoursesFromFile(Uri fileUri) {
        executor.execute(() -> {
            try {
                List<Course> courses = CourseImportHelper.importFromCsv(requireContext(), fileUri);
                int savedCount = 0;
                for (Course course : courses) {
                    course.setColor(getColorForCourse(course.getName()));
                    dbHelper.saveCourse(course);
                    CourseReminderHelper.setReminder(requireContext(), course);
                    savedCount++;
                }
                int finalSavedCount = savedCount;
                mainHandler.post(() -> {
                    Toast.makeText(getContext(),
                            "成功导入 " + finalSavedCount + " 门课程", Toast.LENGTH_SHORT).show();
                    loadCoursesForWeek(currentWeek);
                });
            } catch (Exception e) {
                Log.e(TAG, "导入失败", e);
                mainHandler.post(() -> Toast.makeText(getContext(),
                        "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void startOcrRecognition() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                Toast.makeText(getContext(), "正在识别图片，请稍候...", Toast.LENGTH_SHORT).show();

                CourseOcrHelper.recognizeAndParse(requireContext(), imageUri, new CourseOcrHelper.OcrCallback() {
                    @Override
                    public void onResult(List<Course> fullCourses, List<Course> partialCourses) {
                        // 完整课程直接保存
                        if (!fullCourses.isEmpty()) {
                            executor.execute(() -> {
                                for (Course course : fullCourses) {
                                    course.setColor(getColorForCourse(course.getName()));
                                    dbHelper.saveCourse(course);
                                    CourseReminderHelper.setReminder(requireContext(), course);
                                }
                                mainHandler.post(() -> {
                                    Toast.makeText(getContext(),
                                            "自动识别并添加了 " + fullCourses.size() + " 门课程",
                                            Toast.LENGTH_SHORT).show();
                                    loadCoursesForWeek(currentWeek);
                                });
                            });
                        }

                        // 部分识别的课程需要用户手动补全信息
                        if (!partialCourses.isEmpty()) {
                            showCourseAssignmentDialog(partialCourses);
                        } else if (fullCourses.isEmpty()) {
                            Toast.makeText(getContext(),
                                    "未能从图片中识别出任何课程信息", Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(getContext(),
                                "识别失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        } else if (requestCode == REQUEST_CODE_IMPORT_FILE && resultCode == RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            if (fileUri != null) {
                importCoursesFromFile(fileUri);
            }
        }
    }

    private void showCourseAssignmentDialog(List<Course> courses) {
        processNextCourse(courses, 0);
    }

    private void processNextCourse(List<Course> courses, int index) {
        if (index >= courses.size()) {
            Toast.makeText(getContext(), "所有课程已处理完成", Toast.LENGTH_SHORT).show();
            loadCoursesForWeek(currentWeek);
            return;
        }

        Course course = courses.get(index);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle("补充课程信息：" + course.getName());

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_assign_course, null);
        Spinner spinnerWeekday = dialogView.findViewById(R.id.spinnerWeekday);
        Spinner spinnerSection = dialogView.findViewById(R.id.spinnerSection);
        TextView tvCourseInfo = dialogView.findViewById(R.id.tvCourseInfo);

        String info = "教师：" + (TextUtils.isEmpty(course.getTeacher()) ? "未识别" : course.getTeacher())
                + "\n教室：" + (TextUtils.isEmpty(course.getClassroom()) ? "未识别" : course.getClassroom());
        tvCourseInfo.setText(info);

        String[] weekdays = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        ArrayAdapter<String> weekdayAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, weekdays);
        weekdayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerWeekday.setAdapter(weekdayAdapter);

        String[] sections = {"第1大节 (1-2节)", "第2大节 (3-4节)", "第3大节 (5-6节)",
                "第4大节 (7-8节)", "第5大节 (9-10节)"};
        ArrayAdapter<String> sectionAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, sections);
        sectionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSection.setAdapter(sectionAdapter);

        builder.setView(dialogView);
        builder.setPositiveButton("保存", (dialog, which) -> {
            int weekday = spinnerWeekday.getSelectedItemPosition() + 1;
            int section = spinnerSection.getSelectedItemPosition() + 1;
            course.setWeekday(weekday);
            course.setSection(section);
            course.setStartWeek(1);
            course.setEndWeek(20);
            course.setColor(getColorForCourse(course.getName()));

            executor.execute(() -> {
                dbHelper.saveCourse(course);
                CourseReminderHelper.setReminder(requireContext(), course);
                mainHandler.post(() -> {
                    Toast.makeText(getContext(), "已添加：" + course.getName(), Toast.LENGTH_SHORT).show();
                    processNextCourse(courses, index + 1);
                });
            });
        });
        builder.setNegativeButton("跳过", (dialog, which) -> processNextCourse(courses, index + 1));
        builder.setNeutralButton("全部取消", (dialog, which) -> {
            Toast.makeText(getContext(), "已取消导入剩余课程", Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdown();
    }
}