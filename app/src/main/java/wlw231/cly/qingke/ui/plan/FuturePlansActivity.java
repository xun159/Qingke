package wlw231.cly.qingke.ui.plan;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import wlw231.cly.qingke.R;

public class FuturePlansActivity extends AppCompatActivity {

    private RecyclerView rvFuturePlans;
    private FuturePlansAdapter adapter;
    private PlanDatabaseHelper dbHelper;

    private Calendar selectedStartCalendar = Calendar.getInstance();
    private Calendar selectedEndCalendar = Calendar.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_future_plans);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitleTextColor(getResources().getColor(R.color.primary));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);  // 显式禁用返回箭头
            getSupportActionBar().setTitle("未来计划");
        }

        dbHelper = PlanDatabaseHelper.getInstance(this);

        rvFuturePlans = findViewById(R.id.rvFuturePlans);
        rvFuturePlans.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FuturePlansAdapter();
        adapter.setOnPlanLongClickListener(this::showPlanOptionsMenu);
        rvFuturePlans.setAdapter(adapter);

        loadFuturePlans();
    }

    private void loadFuturePlans() {
        List<PlanEntity> allPlans = dbHelper.getAllPlansSync();

        Calendar todayStart = Calendar.getInstance();
        todayStart.set(Calendar.HOUR_OF_DAY, 0);
        todayStart.set(Calendar.MINUTE, 0);
        todayStart.set(Calendar.SECOND, 0);
        todayStart.set(Calendar.MILLISECOND, 0);
        long minStart = todayStart.getTimeInMillis();

        List<PlanEntity> futurePlans = new ArrayList<>();
        for (PlanEntity plan : allPlans) {
            if (plan.startTimeMillis >= minStart && !plan.isCompleted) {
                futurePlans.add(plan);
            }
        }

        Collections.sort(futurePlans, (p1, p2) -> Long.compare(p1.startTimeMillis, p2.startTimeMillis));

        List<Object> items = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String currentDate = "";
        for (PlanEntity plan : futurePlans) {
            String dateKey = dateFormat.format(new Date(plan.startTimeMillis));
            if (!dateKey.equals(currentDate)) {
                currentDate = dateKey;
                items.add("------ " + currentDate + " ------");
            }
            items.add(plan);
        }

        adapter.submitList(items);
    }

    // ---------- 长按菜单（与 PlanFragment 逻辑一致）----------
    private void showPlanOptionsMenu(PlanEntity plan) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(plan.name)
                .setItems(new String[]{"查看详情", "编辑", "删除"}, (dialog, which) -> {
                    switch (which) {
                        case 0: showPlanDetail(plan); break;
                        case 1: showEditDialog(plan); break;
                        case 2: confirmDeletePlan(plan); break;
                    }
                })
                .show();
    }

    private void showPlanDetail(PlanEntity plan) {
        String quadrantName = getQuadrantName(plan.quadrant);
        String start = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date(plan.startTimeMillis));
        String end = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date(plan.endTimeMillis));
        String message = "名称：" + plan.name +
                "\n象限：" + quadrantName +
                "\n开始：" + start +
                "\n结束：" + end;
        new MaterialAlertDialogBuilder(this)
                .setTitle("计划详情")
                .setMessage(message)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void showEditDialog(PlanEntity plan) {
        showAddEditDialog(plan, plan.quadrant);
    }

    private void confirmDeletePlan(PlanEntity plan) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除计划")
                .setMessage("确定要删除「" + plan.name + "」吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    dbHelper.deletePlan(plan.id, () -> {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                            loadFuturePlans();
                        });
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String getQuadrantName(int q) {
        switch (q) {
            case 1: return "重要且紧急";
            case 2: return "重要不紧急";
            case 3: return "紧急不重要";
            default: return "不重要不紧急";
        }
    }

    // ---------- 编辑对话框（复用添加布局）----------
    private void showAddEditDialog(PlanEntity editPlan, int defaultQuadrant) {
        boolean isEdit = editPlan != null;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_plan, null);
        builder.setView(dialogView);
        final AlertDialog dialog = builder.create();

        TextView tvStartDate = dialogView.findViewById(R.id.tvStartDate);
        TextView tvStartTime = dialogView.findViewById(R.id.tvStartTime);
        TextView tvEndDate = dialogView.findViewById(R.id.tvEndDate);
        TextView tvEndTime = dialogView.findViewById(R.id.tvEndTime);
        com.google.android.material.textfield.TextInputEditText etName = dialogView.findViewById(R.id.etPlanName);
        android.widget.Spinner spinnerQuadrant = dialogView.findViewById(R.id.spinnerQuadrant);

        String[] quadrants = {"重要且紧急", "重要不紧急", "紧急不重要", "不重要不紧急"};
        android.widget.ArrayAdapter<String> spinnerAdapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, quadrants);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerQuadrant.setAdapter(spinnerAdapter);

        if (isEdit) {
            etName.setText(editPlan.name);
            spinnerQuadrant.setSelection(editPlan.quadrant - 1);
            selectedStartCalendar.setTimeInMillis(editPlan.startTimeMillis);
            selectedEndCalendar.setTimeInMillis(editPlan.endTimeMillis);
        } else {
            spinnerQuadrant.setSelection(defaultQuadrant - 1);
            selectedStartCalendar = Calendar.getInstance();
            selectedEndCalendar = Calendar.getInstance();
            selectedEndCalendar.add(Calendar.HOUR_OF_DAY, 1);
        }

        updateDateTimeTexts(tvStartDate, tvStartTime, selectedStartCalendar);
        updateDateTimeTexts(tvEndDate, tvEndTime, selectedEndCalendar);

        tvStartDate.setOnClickListener(v -> showDatePicker(selectedStartCalendar,
                () -> updateDateTimeTexts(tvStartDate, tvStartTime, selectedStartCalendar)));
        tvStartTime.setOnClickListener(v -> showTimePicker(selectedStartCalendar,
                () -> updateDateTimeTexts(tvStartDate, tvStartTime, selectedStartCalendar)));
        tvEndDate.setOnClickListener(v -> showDatePicker(selectedEndCalendar,
                () -> updateDateTimeTexts(tvEndDate, tvEndTime, selectedEndCalendar)));
        tvEndTime.setOnClickListener(v -> showTimePicker(selectedEndCalendar,
                () -> updateDateTimeTexts(tvEndDate, tvEndTime, selectedEndCalendar)));

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnSave).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "请输入计划名称", Toast.LENGTH_SHORT).show();
                return;
            }
            int quadrant = spinnerQuadrant.getSelectedItemPosition() + 1;

            if (isEdit) {
                editPlan.name = name;
                editPlan.quadrant = quadrant;
                editPlan.startTimeMillis = selectedStartCalendar.getTimeInMillis();
                editPlan.endTimeMillis = selectedEndCalendar.getTimeInMillis();
                dbHelper.updatePlan(editPlan, () -> {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "计划已更新", Toast.LENGTH_SHORT).show();
                        loadFuturePlans();
                    });
                });
            } else {
                PlanEntity plan = new PlanEntity();
                plan.name = name;
                plan.quadrant = quadrant;
                plan.startTimeMillis = selectedStartCalendar.getTimeInMillis();
                plan.endTimeMillis = selectedEndCalendar.getTimeInMillis();
                plan.isCompleted = false;
                plan.notified = false;
                dbHelper.insertPlan(plan, () -> {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "计划已添加", Toast.LENGTH_SHORT).show();
                        loadFuturePlans();
                    });
                });
            }

            dialog.dismiss();
            selectedStartCalendar = Calendar.getInstance();
            selectedEndCalendar = Calendar.getInstance();
        });

        dialog.show();
    }

    private void updateDateTimeTexts(TextView dateView, TextView timeView, Calendar cal) {
        dateView.setText(dateFormat.format(cal.getTime()));
        timeView.setText(timeFormat.format(cal.getTime()));
    }

    private void showDatePicker(Calendar calendar, Runnable onDateSet) {
        new DatePickerDialog(this,
                (view, year, month, day) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, day);
                    onDateSet.run();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker(Calendar calendar, Runnable onTimeSet) {
        new TimePickerDialog(this,
                (view, hour, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hour);
                    calendar.set(Calendar.MINUTE, minute);
                    onTimeSet.run();
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true).show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}