package wlw231.cly.qingke.ui.course;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Calendar;

import wlw231.cly.qingke.R;
import wlw231.cly.qingke.model.Course;

public class CourseEditDialogFragment extends DialogFragment {

    private static final String ARG_WEEKDAY = "weekday";
    private static final String ARG_SECTION = "section";
    private static final String ARG_COURSE = "course";

    private int weekday;
    private int section;
    private Course existingCourse;

    private EditText etName, etTeacher, etClassroom;
    private NumberPicker npStartWeek, npEndWeek;
    private TextView tvStartTime, tvEndTime;
    private Spinner spinnerSubSections;
    private LinearLayout layoutSubSections;

    public interface OnSaveListener {
        void onSave(Course course);
    }

    private OnSaveListener onSaveListener;

    public static CourseEditDialogFragment newInstance(int weekday, int section, @Nullable Course course) {
        CourseEditDialogFragment fragment = new CourseEditDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_WEEKDAY, weekday);
        args.putInt(ARG_SECTION, section);
        args.putSerializable(ARG_COURSE, course);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnSaveListener(OnSaveListener listener) {
        this.onSaveListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            weekday = getArguments().getInt(ARG_WEEKDAY);
            section = getArguments().getInt(ARG_SECTION);
            existingCourse = (Course) getArguments().getSerializable(ARG_COURSE);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_course_edit_ext, null);
        initViews(view);
        populateExistingData();
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle("编辑课程")
                .setView(view)
                .setPositiveButton("保存", (dialog, which) -> saveCourse())
                .setNegativeButton("取消", null)
                .create();
    }

    private void initViews(View root) {
        etName = root.findViewById(R.id.etCourseName);
        etTeacher = root.findViewById(R.id.etTeacher);
        etClassroom = root.findViewById(R.id.etClassroom);
        npStartWeek = root.findViewById(R.id.npStartWeek);
        npEndWeek = root.findViewById(R.id.npEndWeek);
        tvStartTime = root.findViewById(R.id.tvStartTime);
        tvEndTime = root.findViewById(R.id.tvEndTime);
        Button btnSetStartTime = root.findViewById(R.id.btnSetStartTime);
        Button btnSetEndTime = root.findViewById(R.id.btnSetEndTime);
        spinnerSubSections = root.findViewById(R.id.spinnerSubSections);
        layoutSubSections = root.findViewById(R.id.layoutSubSections);

        npStartWeek.setMinValue(1);
        npStartWeek.setMaxValue(20);
        npEndWeek.setMinValue(1);
        npEndWeek.setMaxValue(20);

        if (section == 5) {
            layoutSubSections.setVisibility(View.VISIBLE);
            Integer[] options = {2, 3};
            ArrayAdapter<Integer> adapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_item, options);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerSubSections.setAdapter(adapter);
        } else {
            layoutSubSections.setVisibility(View.GONE);
        }

        btnSetStartTime.setOnClickListener(v -> showTimePicker(tvStartTime));
        btnSetEndTime.setOnClickListener(v -> showTimePicker(tvEndTime));
    }

    private void populateExistingData() {
        if (existingCourse != null) {
            etName.setText(existingCourse.getName());
            etTeacher.setText(existingCourse.getTeacher());
            etClassroom.setText(existingCourse.getClassroom());
            npStartWeek.setValue(existingCourse.getStartWeek());
            npEndWeek.setValue(existingCourse.getEndWeek());
            if (!TextUtils.isEmpty(existingCourse.getStartTime())) {
                tvStartTime.setText(existingCourse.getStartTime());
            }
            if (!TextUtils.isEmpty(existingCourse.getEndTime())) {
                tvEndTime.setText(existingCourse.getEndTime());
            }
            if (section == 5) {
                spinnerSubSections.setSelection(existingCourse.getSubSections() == 3 ? 1 : 0);
            }
        } else {
            npStartWeek.setValue(1);
            npEndWeek.setValue(20);
        }
    }

    private void showTimePicker(TextView targetView) {
        Calendar cal = Calendar.getInstance();
        new TimePickerDialog(requireContext(),
                (view, hour, minute) -> targetView.setText(String.format("%02d:%02d", hour, minute)),
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
    }

    private void saveCourse() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(getContext(), "课程名称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        Course course = new Course();
        course.setWeekday(weekday);
        course.setSection(section);
        course.setName(name);
        course.setTeacher(etTeacher.getText().toString().trim());
        course.setClassroom(etClassroom.getText().toString().trim());
        course.setStartWeek(npStartWeek.getValue());
        course.setEndWeek(npEndWeek.getValue());
        course.setStartTime(tvStartTime.getText().toString());
        course.setEndTime(tvEndTime.getText().toString());
        if (section == 5) {
            course.setSubSections((int) spinnerSubSections.getSelectedItem());
        }

        if (onSaveListener != null) {
            onSaveListener.onSave(course);
        }
    }
}