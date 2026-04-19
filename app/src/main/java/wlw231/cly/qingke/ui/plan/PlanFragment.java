package wlw231.cly.qingke.ui.plan;

import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import wlw231.cly.qingke.R;

public class PlanFragment extends Fragment {

    private static final String PREFS_NAME = "QuadrantPlanPrefs";
    private static final String KEY_PLANS = "plans";
    private static final String KEY_SCHEDULED_IDS = "scheduled_alarm_ids_str";

    private LinearLayout q1List, q2List, q3List, q4List;
    private FloatingActionButton fabAdd;
    private AlarmManager alarmManager;
    private List<PlanItem> planList;
    private SharedPreferences prefs;
    private Gson gson;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_four_quadrant, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getContext() == null) return;

        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);

        q1List = view.findViewById(R.id.q1_list);
        q2List = view.findViewById(R.id.q2_list);
        q3List = view.findViewById(R.id.q3_list);
        q4List = view.findViewById(R.id.q4_list);

        fabAdd = view.findViewById(R.id.fab_add);
        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> showAddDialog(null, 2));
        }

        loadPlans();
        refreshAllReminders();
    }

    // ------------------------------------------------------------
    // 渲染所有计划到对应象限列表
    // ------------------------------------------------------------
    private void renderPlans() {
        if (q1List == null || q2List == null || q3List == null || q4List == null || planList == null) {
            return;
        }

        q1List.removeAllViews();
        q2List.removeAllViews();
        q3List.removeAllViews();
        q4List.removeAllViews();

        for (PlanItem plan : planList) {
            if (plan == null) continue;

            LinearLayout itemRow = new LinearLayout(requireContext());
            itemRow.setOrientation(LinearLayout.HORIZONTAL);
            itemRow.setGravity(Gravity.CENTER_VERTICAL);
            itemRow.setPadding(0, dp(6), 0, dp(6));

            TextView statusDot = new TextView(requireContext());
            statusDot.setLayoutParams(new LinearLayout.LayoutParams(dp(10), dp(10)));
            statusDot.setBackground(createDotDrawable(plan.quadrant, plan.isCompleted));
            statusDot.setPadding(0, 0, dp(8), 0);
            itemRow.addView(statusDot);

            TextView tvName = new TextView(requireContext());
            tvName.setText(plan.name);
            tvName.setTextSize(14);
            tvName.setTextColor(plan.isCompleted ? 0xFFB0B0B0 : 0xFF2C3E50);
            if (plan.isCompleted) tvName.setPaintFlags(Paint.STRIKE_THRU_TEXT_FLAG);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            itemRow.addView(tvName);

            itemRow.setTag(plan);
            itemRow.setOnLongClickListener(v -> {
                showPopupMenu(v, (PlanItem) v.getTag());
                return true;
            });
            itemRow.setOnClickListener(v -> {
                PlanItem p = (PlanItem) v.getTag();
                if (p != null) {
                    p.isCompleted = !p.isCompleted;
                    savePlans();
                }
            });

            switch (plan.quadrant) {
                case 1: q1List.addView(itemRow); break;
                case 2: q2List.addView(itemRow); break;
                case 3: q3List.addView(itemRow); break;
                case 4: q4List.addView(itemRow); break;
            }
        }
    }

    private GradientDrawable createDotDrawable(int quadrant, boolean completed) {
        int color = quadrant == 1 ? 0xFFE74C3C : quadrant == 2 ? 0xFF27AE60 :
                quadrant == 3 ? 0xFFF39C12 : 0xFF3498DB;
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(completed ? color : Color.TRANSPARENT);
        d.setStroke(dp(2), color);
        d.setSize(dp(10), dp(10));
        return d;
    }

    // ------------------------------------------------------------
    // 弹出菜单（详情/修改/删除）
    // ------------------------------------------------------------
    private void showPopupMenu(View anchorView, PlanItem plan) {
        if (plan == null) return;

        LinearLayout menuRoot = new LinearLayout(requireContext());
        menuRoot.setOrientation(LinearLayout.VERTICAL);
        menuRoot.setPadding(0, dp(4), 0, dp(4));
        menuRoot.setBackground(createRoundBg(Color.WHITE, 8, 0xFFE0E0E0));

        final PopupWindow[] popupRef = new PopupWindow[1];
        String[] menuItems = {"📋 详情", "✏️ 修改", "🗑️ 删除"};

        for (int i = 0; i < menuItems.length; i++) {
            TextView item = new TextView(requireContext());
            item.setText(menuItems[i]);
            item.setTextSize(13);
            item.setTextColor(0xFF2C3E50);
            item.setPadding(dp(16), dp(10), dp(16), dp(10));
            item.setTag(i);
            item.setOnClickListener(v -> {
                if (popupRef[0] != null) popupRef[0].dismiss();
                int idx = (int) v.getTag();
                if (idx == 0) showDetailDialog(plan);
                else if (idx == 1) showAddDialog(plan, plan.quadrant);
                else if (idx == 2) confirmDelete(plan);
            });
            menuRoot.addView(item);
        }
        menuRoot.setLayoutParams(new LinearLayout.LayoutParams(dp(100), LinearLayout.LayoutParams.WRAP_CONTENT));

        popupRef[0] = new PopupWindow(menuRoot, dp(100), LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popupRef[0].setBackgroundDrawable(createRoundBg(Color.TRANSPARENT, 8));
        popupRef[0].setElevation(dp(10));
        popupRef[0].setOutsideTouchable(true);

        int[] loc = new int[2];
        anchorView.getLocationOnScreen(loc);
        popupRef[0].showAtLocation(requireActivity().getWindow().getDecorView(),
                Gravity.NO_GRAVITY, loc[0], loc[1] + anchorView.getHeight());
    }

    private void showDetailDialog(PlanItem plan) {
        if (plan == null) return;
        String msg = String.format(Locale.ROOT, "%s\n%s\n开始：%02d月%02d日 %02d:%02d\n结束：%02d月%02d日 %02d:%02d",
                plan.name, getQuadrantName(plan.quadrant),
                plan.startMonth, plan.startDay, plan.startHour, plan.startMinute,
                plan.endMonth, plan.endDay, plan.endHour, plan.endMinute);
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("详情").setMessage(msg).show();
    }

    private void confirmDelete(PlanItem plan) {
        if (plan == null) return;
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("删除")
                .setMessage("确定删除「" + plan.name + "」吗？")
                .setPositiveButton("删除", (d, w) -> {
                    cancelReminder(plan);
                    if (planList != null) {
                        planList.remove(plan);
                        savePlans();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ------------------------------------------------------------
    // 紧凑型添加/修改对话框（核心修改区域）
    // ------------------------------------------------------------
    private void showAddDialog(PlanItem editPlan, int defaultQuadrant) {
        boolean isEdit = editPlan != null;
        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.setBackground(createRoundBg(Color.WHITE, 12));

        TextView title = new TextView(requireContext());
        title.setText(isEdit ? "修改计划" : "新建计划");
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(0xFF2C3E50);
        title.setPadding(0, 0, 0, dp(4));
        root.addView(title);

        root.addView(createCompactLabel("象限"));
        Spinner spinner = new Spinner(requireContext());
        spinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                new String[]{"重要且紧急", "重要不紧急", "紧急不重要", "不重要不紧急"}));
        spinner.setSelection(defaultQuadrant - 1);
        spinner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));
        root.addView(spinner);

        root.addView(createCompactLabel("名称"));
        EditText etName = new EditText(requireContext());
        etName.setText(isEdit ? editPlan.name : "");
        etName.setHint("输入计划名称");
        etName.setSingleLine(true);
        etName.setTextSize(13);
        etName.setPadding(dp(8), dp(4), dp(8), dp(4));
        etName.setBackground(createRoundBg(0xFFF8F8F8, 6, 0xFFDDDDDD));
        etName.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));
        root.addView(etName);

        root.addView(createCompactLabel("开始时间 (月/日 时:分)"));
        LinearLayout startRow = createCompactTimeRow(
                isEdit ? editPlan.startMonth : 1,
                isEdit ? editPlan.startDay : 1,
                isEdit ? editPlan.startHour : 9,
                isEdit ? editPlan.startMinute : 0);
        root.addView(startRow);

        root.addView(createCompactLabel("结束时间 (月/日 时:分)"));
        LinearLayout endRow = createCompactTimeRow(
                isEdit ? editPlan.endMonth : 1,
                isEdit ? editPlan.endDay : 1,
                isEdit ? editPlan.endHour : 18,
                isEdit ? editPlan.endMinute : 0);
        root.addView(endRow);

        LinearLayout btnRow = new LinearLayout(requireContext());
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(8), 0, 0);

        Button btnCancel = createCompactButton("取消", 0xFF999999, 0xFFF5F5F5);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnRow.addView(btnCancel);

        Button btnOk = createCompactButton(isEdit ? "保存" : "添加", 0xFFFFFFFF, 0xFF2C3E50);
        btnOk.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) return;

            int sM = parseInt((EditText) startRow.getChildAt(0), 1);
            int sD = parseInt((EditText) startRow.getChildAt(1), 1);
            int sH = parseInt((EditText) startRow.getChildAt(2), 0);
            int sMin = parseInt((EditText) startRow.getChildAt(3), 0);
            int eM = parseInt((EditText) endRow.getChildAt(0), 1);
            int eD = parseInt((EditText) endRow.getChildAt(1), 1);
            int eH = parseInt((EditText) endRow.getChildAt(2), 0);
            int eMin = parseInt((EditText) endRow.getChildAt(3), 0);
            int q = spinner.getSelectedItemPosition() + 1;

            if (isEdit) {
                editPlan.name = name;
                editPlan.quadrant = q;
                editPlan.startMonth = sM;
                editPlan.startDay = sD;
                editPlan.startHour = sH;
                editPlan.startMinute = sMin;
                editPlan.endMonth = eM;
                editPlan.endDay = eD;
                editPlan.endHour = eH;
                editPlan.endMinute = eMin;
            } else {
                if (planList != null) {
                    planList.add(new PlanItem(name, sM, sD, sH, sMin, eM, eD, eH, eMin, q));
                }
            }
            savePlans();
            dialog.dismiss();
        });
        btnRow.addView(btnOk);
        root.addView(btnRow);

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.width = dp(280);
            dialog.getWindow().setAttributes(lp);
        }
        dialog.show();
        etName.requestFocus();
    }

    private TextView createCompactLabel(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(0xFF666666);
        tv.setPadding(0, dp(4), 0, dp(2));
        return tv;
    }

    private LinearLayout createCompactTimeRow(int month, int day, int hour, int minute) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, 0);

        row.addView(createCompactTimeEdit(String.valueOf(month)));
        row.addView(createCompactTimeEdit(String.valueOf(day)));
        row.addView(createCompactTimeEdit(String.valueOf(hour)));
        row.addView(createCompactTimeEdit(String.valueOf(minute)));
        return row;
    }

    private EditText createCompactTimeEdit(String value) {
        EditText et = new EditText(requireContext());
        et.setText(value);
        et.setTextSize(12);
        et.setGravity(Gravity.CENTER);
        et.setPadding(0, dp(2), 0, dp(2));
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setTextColor(0xFF2C3E50);
        et.setBackground(createRoundBg(0xFFF8F8F8, 4, 0xFFDDDDDD));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(24), 1);
        lp.setMargins(dp(2), 0, dp(2), 0);
        et.setLayoutParams(lp);
        return et;
    }

    private Button createCompactButton(String text, int textColor, int bgColor) {
        Button btn = new Button(requireContext());
        btn.setText(text);
        btn.setTextColor(textColor);
        btn.setTextSize(13);
        btn.setAllCaps(false);
        btn.setBackground(createRoundBg(bgColor, 6, 0x00000000));
        btn.setPadding(dp(4), dp(6), dp(4), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
        btn.setLayoutParams(lp);
        return btn;
    }

    // ------------------------------------------------------------
    // 定时提醒相关
    // ------------------------------------------------------------
    private void scheduleReminder(PlanItem plan) {
        if (plan == null || plan.isCompleted || alarmManager == null || plan.id == null) return;

        Calendar targetCal = Calendar.getInstance();
        targetCal.set(Calendar.MONTH, plan.startMonth - 1);
        targetCal.set(Calendar.DAY_OF_MONTH, plan.startDay);
        targetCal.set(Calendar.HOUR_OF_DAY, plan.startHour);
        targetCal.set(Calendar.MINUTE, plan.startMinute);
        targetCal.set(Calendar.SECOND, 0);
        targetCal.set(Calendar.MILLISECOND, 0);

        long targetTime = targetCal.getTimeInMillis();
        long currentTime = System.currentTimeMillis();

        if (targetTime <= currentTime) {
            targetCal.add(Calendar.DAY_OF_YEAR, 1);
            targetTime = targetCal.getTimeInMillis();
        }

        Intent intent = new Intent(requireContext(), PlanReminderReceiver.class);
        intent.putExtra("plan_name", plan.name);
        intent.putExtra("plan_quadrant", plan.quadrant);

        int requestCode = plan.id.hashCode();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                requireContext(),
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetTime, pendingIntent);
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, targetTime, pendingIntent);
                    Toast.makeText(requireContext(),
                            "建议在设置中允许“闹钟和提醒”权限，以确保准时通知",
                            Toast.LENGTH_LONG).show();
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, targetTime, pendingIntent);
            }
            saveScheduledId(plan.id);
            Log.d("PlanFragment", "已设置提醒: " + plan.name + " 时间: " + targetCal.getTime());
        } catch (SecurityException e) {
            Log.e("PlanFragment", "设置提醒失败: " + e.getMessage());
            Toast.makeText(requireContext(), "无法设置提醒，请检查权限", Toast.LENGTH_SHORT).show();
        }
    }

    private void cancelReminder(PlanItem plan) {
        if (plan == null || alarmManager == null || plan.id == null) return;

        int requestCode = plan.id.hashCode();
        Intent intent = new Intent(requireContext(), PlanReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                requireContext(),
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            removeScheduledId(plan.id);
            Log.d("PlanFragment", "已取消提醒: " + plan.name);
        }
    }

    private void refreshAllReminders() {
        clearAllScheduledReminders();
        if (planList != null) {
            for (PlanItem plan : planList) {
                if (plan != null && !plan.isCompleted) {
                    scheduleReminder(plan);
                }
            }
        }
    }

    private void clearAllScheduledReminders() {
        Set<String> ids = getScheduledIds();
        for (String idStr : ids) {
            int requestCode = idStr.hashCode();
            Intent intent = new Intent(requireContext(), PlanReminderReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(
                    requireContext(),
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
            );
            if (pi != null) {
                alarmManager.cancel(pi);
                pi.cancel();
            }
        }
        prefs.edit().remove(KEY_SCHEDULED_IDS).apply();
    }

    private void saveScheduledId(String id) {
        if (id == null) return;
        Set<String> ids = getScheduledIds();
        ids.add(id);
        prefs.edit().putStringSet(KEY_SCHEDULED_IDS, ids).apply();
    }

    private void removeScheduledId(String id) {
        if (id == null) return;
        Set<String> ids = getScheduledIds();
        ids.remove(id);
        prefs.edit().putStringSet(KEY_SCHEDULED_IDS, ids).apply();
    }

    private Set<String> getScheduledIds() {
        return prefs.getStringSet(KEY_SCHEDULED_IDS, new HashSet<>());
    }

    // ------------------------------------------------------------
    // 数据持久化
    // ------------------------------------------------------------
    private void savePlans() {
        if (planList != null && prefs != null && gson != null) {
            prefs.edit().putString(KEY_PLANS, gson.toJson(planList)).apply();
            refreshAllReminders();
            renderPlans();
        }
    }

    private void loadPlans() {
        if (prefs == null || gson == null) return;
        String json = prefs.getString(KEY_PLANS, null);
        planList = json != null ? gson.fromJson(json, new TypeToken<List<PlanItem>>(){}.getType()) : new ArrayList<>();
        if (planList != null) {
            for (PlanItem plan : planList) {
                if (plan.id == null) plan.id = UUID.randomUUID().toString();
            }
        }
        renderPlans();
    }

    // ------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------
    private String getQuadrantName(int q) {
        if (q == 1) return "重要且紧急";
        if (q == 2) return "重要不紧急";
        if (q == 3) return "紧急不重要";
        return "不重要不紧急";
    }

    private int dp(float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private int parseInt(EditText e, int d) {
        try {
            if (e != null && e.getText() != null) {
                return Integer.parseInt(e.getText().toString());
            }
        } catch (Exception ignored) {}
        return d;
    }

    private GradientDrawable createRoundBg(int color, int radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), strokeColor);
        return d;
    }

    private GradientDrawable createRoundBg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    // ------------------------------------------------------------
    // 数据模型
    // ------------------------------------------------------------
    public static class PlanItem {
        String id, name;
        int startMonth, startDay, startHour, startMinute;
        int endMonth, endDay, endHour, endMinute;
        int quadrant;
        boolean isCompleted;

        public PlanItem(String n, int sM, int sD, int sH, int sMin, int eM, int eD, int eH, int eMin, int q) {
            id = UUID.randomUUID().toString();
            name = n;
            startMonth = sM; startDay = sD; startHour = sH; startMinute = sMin;
            endMonth = eM; endDay = eD; endHour = eH; endMinute = eMin;
            quadrant = q;
        }

        public PlanItem(String n, int sM, int sD, int sH, int eM, int eD, int eH, int q) {
            this(n, sM, sD, sH, 0, eM, eD, eH, 0, q);
        }
    }
}