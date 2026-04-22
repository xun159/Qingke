package wlw231.cly.qingke.ui.plan;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import wlw231.cly.qingke.R;

public class PlanFragment extends Fragment {

    private RecyclerView rvQ1, rvQ2, rvQ3, rvQ4;
    private PlanAdapter adapterQ1, adapterQ2, adapterQ3, adapterQ4;
    private PlanDatabaseHelper dbHelper;

    private Calendar selectedStartCalendar = Calendar.getInstance();
    private Calendar selectedEndCalendar = Calendar.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_plan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dbHelper = PlanDatabaseHelper.getInstance(requireContext());

        initRecyclerViews(view);
        setupAddButtons(view);
        observeTodayPlans();

        // 右上角按钮：查看未来计划
        view.findViewById(R.id.btnFuturePlans).setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), FuturePlansActivity.class));
        });

        // 首次加载数据
        dbHelper.refreshTodayPlans();
    }

    private void initRecyclerViews(View view) {
        rvQ1 = view.findViewById(R.id.rvQ1);
        rvQ2 = view.findViewById(R.id.rvQ2);
        rvQ3 = view.findViewById(R.id.rvQ3);
        rvQ4 = view.findViewById(R.id.rvQ4);

        adapterQ1 = new PlanAdapter();
        adapterQ2 = new PlanAdapter();
        adapterQ3 = new PlanAdapter();
        adapterQ4 = new PlanAdapter();

        // 设置单击监听（切换完成状态）
        PlanAdapter.OnPlanClickListener clickListener = plan -> {
            plan.isCompleted = !plan.isCompleted;
            dbHelper.updatePlan(plan, null);
        };

        // 设置长按监听（弹出操作菜单）
        PlanAdapter.OnPlanLongClickListener longClickListener = this::showPlanOptionsMenu;

        for (PlanAdapter adapter : new PlanAdapter[]{adapterQ1, adapterQ2, adapterQ3, adapterQ4}) {
            adapter.setOnPlanClickListener(clickListener);
            adapter.setOnPlanLongClickListener(longClickListener);
        }

        LinearLayoutManager layoutManager1 = new LinearLayoutManager(requireContext());
        LinearLayoutManager layoutManager2 = new LinearLayoutManager(requireContext());
        LinearLayoutManager layoutManager3 = new LinearLayoutManager(requireContext());
        LinearLayoutManager layoutManager4 = new LinearLayoutManager(requireContext());

        rvQ1.setLayoutManager(layoutManager1);
        rvQ2.setLayoutManager(layoutManager2);
        rvQ3.setLayoutManager(layoutManager3);
        rvQ4.setLayoutManager(layoutManager4);

        rvQ1.setAdapter(adapterQ1);
        rvQ2.setAdapter(adapterQ2);
        rvQ3.setAdapter(adapterQ3);
        rvQ4.setAdapter(adapterQ4);
    }

    private void setupAddButtons(View view) {
        view.findViewById(R.id.tvQ1Add).setOnClickListener(v -> showAddDialog(null, 1));
        view.findViewById(R.id.tvQ2Add).setOnClickListener(v -> showAddDialog(null, 2));
        view.findViewById(R.id.tvQ3Add).setOnClickListener(v -> showAddDialog(null, 3));
        view.findViewById(R.id.tvQ4Add).setOnClickListener(v -> showAddDialog(null, 4));
    }

    private void observeTodayPlans() {
        dbHelper.getTodayPlansLiveData().observe(getViewLifecycleOwner(), plans -> {
            adapterQ1.setPlans(filterByQuadrant(plans, 1));
            adapterQ2.setPlans(filterByQuadrant(plans, 2));
            adapterQ3.setPlans(filterByQuadrant(plans, 3));
            adapterQ4.setPlans(filterByQuadrant(plans, 4));
        });
    }

    private List<PlanEntity> filterByQuadrant(List<PlanEntity> plans, int quadrant) {
        List<PlanEntity> filtered = new ArrayList<>();
        for (PlanEntity p : plans) {
            if (p.quadrant == quadrant) {
                filtered.add(p);
            }
        }
        return filtered;
    }

    // ---------- 长按菜单 ----------
    private void showPlanOptionsMenu(PlanEntity plan) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(plan.name)
                .setItems(new String[]{"查看详情", "编辑", "删除"}, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showPlanDetail(plan);
                            break;
                        case 1:
                            showAddDialog(plan, plan.quadrant);
                            break;
                        case 2:
                            confirmDeletePlan(plan);
                            break;
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
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("计划详情")
                .setMessage(message)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void confirmDeletePlan(PlanEntity plan) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除计划")
                .setMessage("确定要删除「" + plan.name + "」吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    dbHelper.deletePlan(plan.id, () -> {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show();
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

    // ---------- 添加/编辑对话框 ----------
    private void showAddDialog(PlanEntity editPlan, int defaultQuadrant) {
        boolean isEdit = editPlan != null;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_plan, null);
        builder.setView(dialogView);
        final androidx.appcompat.app.AlertDialog dialog = builder.create();

        TextView tvStartDate = dialogView.findViewById(R.id.tvStartDate);
        TextView tvStartTime = dialogView.findViewById(R.id.tvStartTime);
        TextView tvEndDate = dialogView.findViewById(R.id.tvEndDate);
        TextView tvEndTime = dialogView.findViewById(R.id.tvEndTime);
        com.google.android.material.textfield.TextInputEditText etName = dialogView.findViewById(R.id.etPlanName);
        android.widget.Spinner spinnerQuadrant = dialogView.findViewById(R.id.spinnerQuadrant);

        String[] quadrants = {"重要且紧急", "重要不紧急", "紧急不重要", "不重要不紧急"};
        android.widget.ArrayAdapter<String> spinnerAdapter = new android.widget.ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, quadrants);
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
                Toast.makeText(requireContext(), "请输入计划名称", Toast.LENGTH_SHORT).show();
                return;
            }
            int quadrant = spinnerQuadrant.getSelectedItemPosition() + 1;

            if (isEdit) {
                editPlan.name = name;
                editPlan.quadrant = quadrant;
                editPlan.startTimeMillis = selectedStartCalendar.getTimeInMillis();
                editPlan.endTimeMillis = selectedEndCalendar.getTimeInMillis();
                dbHelper.updatePlan(editPlan, () -> {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "计划已更新", Toast.LENGTH_SHORT).show();
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
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "计划已添加", Toast.LENGTH_SHORT).show();
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
        new DatePickerDialog(requireContext(),
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
        new TimePickerDialog(requireContext(),
                (view, hour, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hour);
                    calendar.set(Calendar.MINUTE, minute);
                    onTimeSet.run();
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true).show();
    }
}