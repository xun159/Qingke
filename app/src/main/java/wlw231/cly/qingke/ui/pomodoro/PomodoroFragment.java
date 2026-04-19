package wlw231.cly.qingke.ui.pomodoro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import wlw231.cly.qingke.R;
import wlw231.cly.qingke.service.PomodoroService;

public class PomodoroFragment extends Fragment {
    private TextView tvTimer;
    private TextView tvStatus;
    private ProgressBar progressBar;
    private MaterialButton btnStartPause;
    private MaterialButton btnReset;
    private MaterialButton btnCustomDuration;
    private MaterialButton btnDuration15, btnDuration25, btnDuration45;
    private long totalDuration = 25 * 60 * 1000L;
    private long currentLeft = totalDuration;
    private String currentStatus = PomodoroService.STATUS_PAUSED;
    private boolean isFinished = false;

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PomodoroService.ACTION_UPDATE_UI.equals(intent.getAction())) {
                currentStatus = intent.getStringExtra(PomodoroService.EXTRA_STATUS);
                currentLeft = intent.getLongExtra(PomodoroService.EXTRA_TIME_LEFT, 0);
                totalDuration = intent.getLongExtra("TOTAL_TIME", totalDuration);
                isFinished = intent.getBooleanExtra(PomodoroService.EXTRA_FINISHED, false);
                updateUI();
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pomodoro, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvTimer = view.findViewById(R.id.tvTimer);
        tvStatus = view.findViewById(R.id.tvStatus);
        progressBar = view.findViewById(R.id.progressBar);
        btnStartPause = view.findViewById(R.id.btnStartPause);
        btnReset = view.findViewById(R.id.btnReset);
        btnCustomDuration = view.findViewById(R.id.btnCustomDuration);
        btnDuration15 = view.findViewById(R.id.btnDuration15);
        btnDuration25 = view.findViewById(R.id.btnDuration25);
        btnDuration45 = view.findViewById(R.id.btnDuration45);

        LocalBroadcastManager.getInstance(requireContext())
                .registerReceiver(updateReceiver, new IntentFilter(PomodoroService.ACTION_UPDATE_UI));

        btnDuration15.setOnClickListener(v -> setDuration(15));
        btnDuration25.setOnClickListener(v -> setDuration(25));
        btnDuration45.setOnClickListener(v -> setDuration(45));

        btnStartPause.setOnClickListener(v -> {
            if (PomodoroService.STATUS_RUNNING.equals(currentStatus)) {
                pauseService();
            } else {
                long durationToStart = (PomodoroService.STATUS_PAUSED.equals(currentStatus) && currentLeft > 0) ? currentLeft : totalDuration;
                startServiceWithDuration(durationToStart);
            }
        });

        // 重置逻辑：直接发指令
        btnReset.setOnClickListener(v -> {
            // 1. 发送 RESET 指令给 Service
            Intent intent = new Intent(requireContext(), PomodoroService.class);
            intent.setAction("RESET");
            requireContext().startService(intent);

            // 2. 立刻更新UI（UI数据会在广播里被Service刷新）
            // 这里可以简单同步一下状态，防止UI闪烁
            currentStatus = PomodoroService.STATUS_PAUSED;
            isFinished = false;
            // 注意：这里不手动改 currentLeft，等 Service 发广播回来是最准的
            updateUI();
        });

        btnCustomDuration.setOnClickListener(v -> showCustomDurationDialog());

        syncTotalDurationToService();
        updateUI();
    }

    private void syncTotalDurationToService() {
        Intent intent = new Intent(requireContext(), PomodoroService.class);
        intent.setAction("SET_TOTAL");
        intent.putExtra("TOTAL_DURATION", totalDuration);
        requireContext().startService(intent);
    }

    private void setDuration(int minutes) {
        long newDuration = minutes * 60 * 1000L;
        totalDuration = newDuration;
        currentLeft = newDuration;
        isFinished = false;
        syncTotalDurationToService();
        if (PomodoroService.STATUS_RUNNING.equals(currentStatus)) {
            startServiceWithDuration(newDuration);
            Toast.makeText(getContext(), "已切换至 " + minutes + " 分钟并重新计时", Toast.LENGTH_SHORT).show();
        } else {
            updateUI();
            Toast.makeText(getContext(), "已设置 " + minutes + " 分钟", Toast.LENGTH_SHORT).show();
        }
    }

    private void startServiceWithDuration(long duration) {
        Intent intent = new Intent(requireContext(), PomodoroService.class);
        intent.setAction("START");
        intent.putExtra("DURATION", duration);
        requireContext().startService(intent);
    }

    private void pauseService() {
        Intent intent = new Intent(requireContext(), PomodoroService.class);
        intent.setAction("PAUSE");
        requireContext().startService(intent);
    }

    private void showCustomDurationDialog() {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View dialogView = inflater.inflate(R.layout.dialog_custom_duration, null);
        TextInputLayout inputLayout = dialogView.findViewById(R.id.customDurationInputLayout);
        TextInputEditText etMinutes = dialogView.findViewById(R.id.etCustomMinutes);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("自定义专注时长")
                .setMessage("请输入分钟数（1 ~ 120）")
                .setView(dialogView)
                .setPositiveButton("确定", (dialog, which) -> {
                    String text = etMinutes.getText().toString().trim();
                    if (text.isEmpty()) {
                        inputLayout.setError("请输入分钟数");
                        return;
                    }
                    int minutes;
                    try {
                        minutes = Integer.parseInt(text);
                    } catch (NumberFormatException e) {
                        inputLayout.setError("请输入有效数字");
                        return;
                    }
                    if (minutes < 1 || minutes > 120) {
                        inputLayout.setError("时长范围 1~120 分钟");
                        return;
                    }
                    setDuration(minutes);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateUI() {
        if (tvTimer == null) return;
        int minutes = (int) (currentLeft / 1000) / 60;
        int seconds = (int) (currentLeft / 1000) % 60;
        tvTimer.setText(String.format("%02d:%02d", minutes, seconds));
        int progress = (totalDuration > 0) ? (int) (currentLeft * 100 / totalDuration) : 0;
        progressBar.setProgress(progress);
        if (isFinished) {
            tvStatus.setText("已完成！休息一下吧 🎉");
            btnStartPause.setText("开始");
        } else if (PomodoroService.STATUS_RUNNING.equals(currentStatus)) {
            tvStatus.setText("专注中...");
            btnStartPause.setText("暂停");
        } else {
            tvStatus.setText("已暂停");
            btnStartPause.setText("开始");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Intent intent = new Intent(requireContext(), PomodoroService.class);
        intent.setAction("REQUEST_STATUS");
        requireContext().startService(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(updateReceiver);
    }
}