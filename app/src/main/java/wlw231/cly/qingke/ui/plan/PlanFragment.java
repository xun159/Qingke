package wlw231.cly.qingke.ui.plan;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
import wlw231.cly.qingke.R;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlanFragment extends Fragment {

    private static final String PREFS_NAME = "QuadrantPlanPrefs";
    private static final String KEY_PLANS = "plans";

    private LinearLayout q1List, q2List, q3List, q4List;
    private FloatingActionButton fabAdd;

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

        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();

        q1List = view.findViewById(R.id.q1_list);
        q2List = view.findViewById(R.id.q2_list);
        q3List = view.findViewById(R.id.q3_list);
        q4List = view.findViewById(R.id.q4_list);
        fabAdd = view.findViewById(R.id.fab_add);

        fabAdd.setOnClickListener(v -> showAddDialog(null, 2));
        loadPlans();
    }

    /** 渲染列表 - 极简风格 */
    private void renderPlans() {
        q1List.removeAllViews();
        q2List.removeAllViews();
        q3List.removeAllViews();
        q4List.removeAllViews();

        for (PlanItem plan : planList) {
            LinearLayout itemRow = new LinearLayout(requireContext());
            itemRow.setOrientation(LinearLayout.HORIZONTAL);
            itemRow.setGravity(Gravity.CENTER_VERTICAL);
            itemRow.setPadding(0, dp(8), 0, dp(8)); // 紧凑间距

            // 状态圆点
            TextView statusDot = new TextView(requireContext());
            statusDot.setLayoutParams(new LinearLayout.LayoutParams(dp(10), dp(10)));
            statusDot.setBackground(createDotDrawable(plan.quadrant, plan.isCompleted));
            statusDot.setPadding(0, 0, dp(10), 0);
            itemRow.addView(statusDot);

            // 计划名称
            TextView tvName = new TextView(requireContext());
            tvName.setText(plan.name);
            tvName.setTextSize(14);
            tvName.setTextColor(plan.isCompleted ? 0xFFB0B0B0 : 0xFF2C3E50);
            if (plan.isCompleted) {
                tvName.setPaintFlags(Paint.STRIKE_THRU_TEXT_FLAG);
            }
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            itemRow.addView(tvName);

            // 长按菜单
            itemRow.setOnLongClickListener(v -> {
                showPopupMenu(v, plan);
                return true;
            });

            // 点击切换状态 (替代 CheckBox)
            itemRow.setOnClickListener(v -> {
                plan.isCompleted = !plan.isCompleted;
                savePlans();
                renderPlans();
            });

            switch (plan.quadrant) {
                case 1: q1List.addView(itemRow); break;
                case 2: q2List.addView(itemRow); break;
                case 3: q3List.addView(itemRow); break;
                case 4: q4List.addView(itemRow); break;
            }
        }
    }

    /** 创建状态圆点 (空心/实心) */
    private GradientDrawable createDotDrawable(int quadrant, boolean completed) {
        int color = quadrant == 1 ? 0xFFE74C3C :
                quadrant == 2 ? 0xFF27AE60 :
                        quadrant == 3 ? 0xFFF39C12 : 0xFF3498DB;

        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(completed ? color : Color.TRANSPARENT);
        d.setStroke(dp(2), color);
        d.setSize(dp(10), dp(10));
        return d;
    }

    /** 长按菜单 (已修复变量作用域) */
    private void showPopupMenu(View anchorView, PlanItem plan) {
        LinearLayout menuRoot = new LinearLayout(requireContext());
        menuRoot.setOrientation(LinearLayout.VERTICAL);
        menuRoot.setPadding(0, dp(4), 0, dp(4));
        menuRoot.setBackground(createRoundBg(Color.WHITE, 8, 1, 0xFFE0E0E0));

        final PopupWindow[] popupRef = new PopupWindow[1];
        String[] menuItems = {"📋 详情", "✏️ 修改", "🗑️ 删除"};

        for (int i = 0; i < menuItems.length; i++) {
            final int idx = i;
            TextView item = new TextView(requireContext());
            item.setText(menuItems[i]);
            item.setTextSize(13);
            item.setTextColor(0xFF2C3E50);
            item.setPadding(dp(16), dp(10), dp(16), dp(10));
            item.setOnClickListener(v -> {
                if (popupRef[0] != null) popupRef[0].dismiss();
                if (idx == 0) showDetailDialog(plan);
                else if (idx == 1) showAddDialog(plan, plan.quadrant);
                else if (idx == 2) confirmDelete(plan);
            });
            menuRoot.addView(item);
        }
        menuRoot.setLayoutParams(new LinearLayout.LayoutParams(dp(100), LinearLayout.LayoutParams.WRAP_CONTENT));

        popupRef[0] = new PopupWindow(menuRoot, dp(100), LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popupRef[0].setBackgroundDrawable(createRoundBg(Color.TRANSPARENT, 0));
        popupRef[0].setElevation(dp(10));
        popupRef[0].setOutsideTouchable(true);

        int[] loc = new int[2];
        anchorView.getLocationOnScreen(loc);
        popupRef[0].showAtLocation(requireActivity().getWindow().getDecorView(),
                Gravity.NO_GRAVITY, loc[0], loc[1] + anchorView.getHeight());
    }

    private void showDetailDialog(PlanItem plan) {
        new AlertDialog.Builder(requireContext())
                .setTitle("详情")
                .setMessage(String.format("%s\n%s\n开始：%02d月%02d日 %02d:00\n结束：%02d月%02d日 %02d:00",
                        plan.name, getQuadrantName(plan.quadrant),
                        plan.startMonth, plan.startDay, plan.startHour,
                        plan.endMonth, plan.endDay, plan.endHour))
                .show();
    }

    private void confirmDelete(PlanItem plan) {
        new AlertDialog.Builder(requireContext())
                .setTitle("删除")
                .setMessage("确定删除？")
                .setPositiveButton("删除", (d, w) -> {
                    planList.remove(plan);
                    savePlans();
                    renderPlans();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAddDialog(PlanItem editPlan, int defaultQuadrant) {
        boolean isEdit = editPlan != null;
        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setBackground(createRoundBg(Color.WHITE, 16));
        root.setLayoutParams(new LinearLayout.LayoutParams(dp(280), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(requireContext());
        title.setText(isEdit ? "修改" : "新建");
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(0xFF2C3E50);
        root.addView(title);

        root.addView(createLabel("象限"));
        Spinner spinner = new Spinner(requireContext());
        spinner.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                new String[]{"🔴 重要且紧急", "🟢 重要不紧急", "🟡 紧急不重要", "🔵 不重要不紧急"}));
        spinner.setSelection(defaultQuadrant - 1);
        spinner.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
        root.addView(spinner);

        root.addView(createLabel("名称"));
        EditText etName = createEditText(isEdit ? editPlan.name : "");
        root.addView(etName);

        root.addView(createLabel("开始 (月/日 时)"));
        LinearLayout startRow = createTimeRow(isEdit ? editPlan.startMonth : 1, isEdit ? editPlan.startDay : 1, isEdit ? editPlan.startHour : 9);
        root.addView(startRow);

        root.addView(createLabel("结束 (月/日 时)"));
        LinearLayout endRow = createTimeRow(isEdit ? editPlan.endMonth : 1, isEdit ? editPlan.endDay : 1, isEdit ? editPlan.endHour : 18);
        root.addView(endRow);

        LinearLayout btnRow = new LinearLayout(requireContext());
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnCancel = createButton("取消", 0xFF999999, 0xFFF5F5F5);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnRow.addView(btnCancel);

        Button btnOk = createButton(isEdit ? "保存" : "添加", 0xFFFFFFFF, 0xFF2C3E50);
        btnOk.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) return;
            int sM = parseInt((EditText)startRow.getChildAt(0), 1), sD = parseInt((EditText)startRow.getChildAt(1), 1), sH = parseInt((EditText)startRow.getChildAt(2), 0);
            int eM = parseInt((EditText)endRow.getChildAt(0), 1), eD = parseInt((EditText)endRow.getChildAt(1), 1), eH = parseInt((EditText)endRow.getChildAt(2), 0);
            int q = spinner.getSelectedItemPosition() + 1;

            if (isEdit) {
                editPlan.name = name; editPlan.quadrant = q;
                editPlan.startMonth=sM; editPlan.startDay=sD; editPlan.startHour=sH;
                editPlan.endMonth=eM; editPlan.endDay=eD; editPlan.endHour=eH;
            } else {
                planList.add(new PlanItem(name, sM, sD, sH, eM, eD, eH, q));
            }
            savePlans(); renderPlans(); dialog.dismiss();
        });
        btnRow.addView(btnOk);
        root.addView(btnRow);

        dialog.setContentView(root);
        dialog.getWindow().setLayout(dp(280), LinearLayout.LayoutParams.WRAP_CONTENT);
        dialog.show();
        etName.requestFocus();
    }

    /** 创建时间输入行（同步修复） */
    private LinearLayout createTimeRow(int m, int d, int h) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        int[] vals = {m, d, h};

        for (int i = 0; i < 3; i++) {
            EditText e = new EditText(requireContext());
            e.setText(String.valueOf(vals[i]));
            e.setTextSize(12); // ✅ 字体调小
            e.setGravity(Gravity.CENTER);
            e.setIncludeFontPadding(false); // ✅ 关闭字体留白
            e.setPadding(0, 0, 0, 0); // ✅ 清除默认内边距
            e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            e.setTextColor(0xFF2C3E50);
            e.setBackground(createRoundBg(0xFFF8F8F8, 6, 1, 0xFFDDDDDD));
            e.setLayoutParams(new LinearLayout.LayoutParams(0, dp(34), 1)); // 高度微调
            row.addView(e);
        }
        return row;
    }

    private String getQuadrantName(int q) {
        return q==1?"重要且紧急":q==2?"重要不紧急":q==3?"紧急不重要":"不重要不紧急";
    }
    private TextView createLabel(String t) {
        TextView tv = new TextView(requireContext());
        tv.setText(t); tv.setTextSize(12); tv.setTextColor(0xFF999999);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(12), 0, dp(4)); tv.setLayoutParams(lp); return tv;
    }
    /** 创建单行输入框（修复字体裁切问题） */
    private EditText createEditText(String hint) {
        EditText et = new EditText(requireContext());
        et.setHint(hint);
        et.setTextSize(12); // ✅ 字体调小
        et.setSingleLine(true);
        et.setIncludeFontPadding(false); // ✅ 关闭默认字体留白，防止上下被切
        et.setPadding(dp(10), 0, dp(10), 0); // ✅ 仅保留左右内边距
        et.setGravity(Gravity.CENTER_VERTICAL); // ✅ 垂直居中
        et.setTextColor(0xFF2C3E50);
        et.setHintTextColor(0xFFAAAAAA);
        et.setBackground(createRoundBg(0xFFF8F8F8, 6, 1, 0xFFDDDDDD));
        et.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36))); // 高度适配小字体
        return et;
    }
    private Button createButton(String t, int tc, int bc) {
        Button b = new Button(requireContext());
        b.setText(t); b.setTextColor(tc); b.setTextSize(13); b.setAllCaps(false);
        b.setBackground(createRoundBg(bc, 6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1);
        b.setLayoutParams(lp); return b;
    }
    private int dp(float dp) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()); }
    private int parseInt(EditText e, int d) { try { return Integer.parseInt(e.getText().toString()); } catch (Exception ex) { return d; } }
    private GradientDrawable createRoundBg(int c, int r) { GradientDrawable d = new GradientDrawable(); d.setColor(c); d.setCornerRadius(dp(r)); return d; }
    private GradientDrawable createRoundBg(int c, int r, int s, int sc) { GradientDrawable d = createRoundBg(c, r); d.setStroke(dp(s), sc); return d; }

    private void savePlans() { prefs.edit().putString(KEY_PLANS, gson.toJson(planList)).apply(); }
    private void loadPlans() {
        String json = prefs.getString(KEY_PLANS, null);
        planList = json != null ? gson.fromJson(json, new TypeToken<List<PlanItem>>(){}.getType()) : new ArrayList<>();
        renderPlans();
    }

    public static class PlanItem {
        String id, name;
        int startMonth, startDay, startHour, endMonth, endDay, endHour, quadrant;
        boolean isCompleted;
        public PlanItem(String n, int sM, int sD, int sH, int eM, int eD, int eH, int q) {
            id = UUID.randomUUID().toString(); name = n;
            startMonth=sM; startDay=sD; startHour=sH; endMonth=eM; endDay=eD; endHour=eH; quadrant=q;
        }
    }
}