package wlw231.cly.qingke.ui.pomodoro;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import wlw231.cly.qingke.R;

public class PomodoroFragment extends Fragment {

    private TextView tvTimer;
    private TextView tvStatus;
    private ProgressBar progressBar;
    private MaterialButton btnStartPause;
    private MaterialButton btnReset;
    private ChipGroup chipGroupDuration;
    private Chip chip15, chip25, chip45;
    private MaterialButton btnCustomDuration;

    private CountDownTimer countDownTimer;
    private boolean isRunning = false;
    private long timeLeftInMillis;
    private long totalTimeInMillis;

    private static final long DEFAULT_TIME = 25 * 60 * 1000;
    private static final String CHANNEL_ID = "pomodoro_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;

    private Vibrator vibrator;
    private NotificationManager notificationManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pomodoro, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 绑定视图
        tvTimer = view.findViewById(R.id.tvTimer);
        tvStatus = view.findViewById(R.id.tvStatus);
        progressBar = view.findViewById(R.id.progressBar);
        btnStartPause = view.findViewById(R.id.btnStartPause);
        btnReset = view.findViewById(R.id.btnReset);
        chipGroupDuration = view.findViewById(R.id.chipGroupDuration);
        chip15 = view.findViewById(R.id.chip15);
        chip25 = view.findViewById(R.id.chip25);
        chip45 = view.findViewById(R.id.chip45);
        btnCustomDuration = view.findViewById(R.id.btnCustomDuration);

        // 系统服务
        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        notificationManager = (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();

        // 默认选中25分钟
        chip25.setChecked(true);
        setDurationFromChip(chip25);
        updateTimerText();

        // Chip 选择监听
        chipGroupDuration.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                Chip selectedChip = group.findViewById(checkedIds.get(0));
                setDurationFromChip(selectedChip);
                if (!isRunning) {
                    updateTimerText();
                    progressBar.setProgress(100);
                }
            }
        });

        // 开始/暂停
        btnStartPause.setOnClickListener(v -> {
            if (isRunning) {
                pauseTimer();
            } else {
                startTimer();
            }
        });

        // 重置
        btnReset.setOnClickListener(v -> resetTimer());

        // 自定义时长对话框
        btnCustomDuration.setOnClickListener(v -> showCustomDurationDialog());
    }

    private void setDurationFromChip(Chip chip) {
        if (chip == chip15) {
            totalTimeInMillis = 15 * 60 * 1000;
        } else if (chip == chip25) {
            totalTimeInMillis = 25 * 60 * 1000;
        } else if (chip == chip45) {
            totalTimeInMillis = 45 * 60 * 1000;
        }
        timeLeftInMillis = totalTimeInMillis;
    }

    private void updateTimerText() {
        int minutes = (int) (timeLeftInMillis / 1000) / 60;
        int seconds = (int) (timeLeftInMillis / 1000) % 60;
        tvTimer.setText(String.format("%02d:%02d", minutes, seconds));
    }

    private void updateProgressBar() {
        int progress = (int) (timeLeftInMillis * 100 / totalTimeInMillis);
        progressBar.setProgress(progress);
    }

    private void startTimer() {
        if (timeLeftInMillis <= 0) {
            timeLeftInMillis = totalTimeInMillis;
        }

        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateTimerText();
                updateProgressBar();
            }

            @Override
            public void onFinish() {
                isRunning = false;
                btnStartPause.setText("开始");
                tvStatus.setText("已完成！休息一下吧 🎉");
                progressBar.setProgress(0);
                tvTimer.setText("00:00");

                sendNotificationAndVibrate();
            }
        }.start();

        isRunning = true;
        btnStartPause.setText("暂停");
        tvStatus.setText("专注中...");
    }

    private void pauseTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        isRunning = false;
        btnStartPause.setText("开始");
        tvStatus.setText("已暂停");
    }

    private void resetTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        isRunning = false;
        btnStartPause.setText("开始");

        int checkedId = chipGroupDuration.getCheckedChipId();
        if (checkedId != View.NO_ID) {
            Chip selectedChip = chipGroupDuration.findViewById(checkedId);
            setDurationFromChip(selectedChip);
        } else {
            timeLeftInMillis = totalTimeInMillis;
        }

        updateTimerText();
        progressBar.setProgress(100);
        tvStatus.setText("准备就绪");
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
                    if (TextUtils.isEmpty(text)) {
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

                    chipGroupDuration.clearCheck();

                    totalTimeInMillis = minutes * 60 * 1000L;
                    timeLeftInMillis = totalTimeInMillis;

                    if (!isRunning) {
                        updateTimerText();
                        progressBar.setProgress(100);
                        tvStatus.setText("自定义 " + minutes + " 分钟");
                    } else {
                        pauseTimer();
                        updateTimerText();
                        progressBar.setProgress(100);
                        tvStatus.setText("自定义 " + minutes + " 分钟");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "番茄钟提醒",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("番茄钟计时结束通知");
            channel.enableVibration(true);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void sendNotificationAndVibrate() {
        // 震动 1 秒
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(1000);
            }
        }

        // 检查通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION);
                return; // 本次不发送通知
            }
        }

        // 发送通知
        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🍅 番茄钟完成")
                .setContentText("专注时间结束！休息一下吧。")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getContext(), "通知权限已授予，下次计时结束将正常提醒", Toast.LENGTH_SHORT).show();
            } else {
                // 用户拒绝，提示并可引导到设置
                showPermissionDeniedDialog();
            }
        }
    }

    private void showPermissionDeniedDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("需要通知权限")
                .setMessage("番茄钟完成时需要发送通知提醒您，请在设置中开启通知权限。")
                .setPositiveButton("去设置", (dialog, which) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}