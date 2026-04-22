package wlw231.cly.qingke;

import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.util.Calendar;
import android.os.Build;          // 必须导入
import android.os.Bundle;
import android.text.TextUtils;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.concurrent.TimeUnit;

import wlw231.cly.qingke.ui.plan.MidnightCleanupWorker;
import wlw231.cly.qingke.ui.plan.PlanReminderService;
import wlw231.cly.qingke.utils.CourseReminderHelper;

public class MainActivity extends AppCompatActivity {

    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 检查登录状态
        if (!isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        // 导航设置
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
            NavigationUI.setupWithNavController(bottomNav, navController);
        }

        // 返回键处理
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (navController.getCurrentDestination() != null &&
                        navController.getCurrentDestination().getId() == R.id.nav_plan) {
                    moveTaskToBack(true);
                } else {
                    if (!navController.popBackStack()) {
                        moveTaskToBack(true);
                    }
                }
            }
        });

        // 课程提醒（如果存在）
//        CourseReminderHelper.rescheduleAllReminders(this);

        // 启动计划提醒服务
        Intent serviceIntent = new Intent(this, PlanReminderService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        scheduleMidnightCleanup();
    }

    private boolean isLoggedIn() {
        SharedPreferences prefs = getSharedPreferences("login_prefs", MODE_PRIVATE);
        String userId = prefs.getString("user_id", "");
        return !TextUtils.isEmpty(userId);
    }
    // 例如在 MainActivity.onCreate() 末尾
    private void scheduleMidnightCleanup() {
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        midnight.add(Calendar.DAY_OF_YEAR, 1); // 明天 00:00

        long initialDelay = midnight.getTimeInMillis() - System.currentTimeMillis();

        PeriodicWorkRequest cleanupRequest = new PeriodicWorkRequest.Builder(
                MidnightCleanupWorker.class,
                24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "midnight_cleanup",
                ExistingPeriodicWorkPolicy.KEEP,
                cleanupRequest
        );
    }
}