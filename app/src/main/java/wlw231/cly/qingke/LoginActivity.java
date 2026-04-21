package wlw231.cly.qingke;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private static final String SERVER_HOST = "192.168.12.124";
    private static final int SERVER_PORT = 5050;
    private static final String PROTOCOL_PREFIX = "LOGIN|";

    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;

    // SharedPreferences 键名
    private static final String PREF_NAME = "login_prefs";
    private static final String KEY_SAVED_EMAIL = "saved_email";
    private static final String KEY_SAVED_PASSWORD_HASH = "saved_password_hash";

    // UI 组件
    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private MaterialCheckBox cbRemember;
    private MaterialButton btnLogin;

    // 线程池与主线程 Handler（仅用于手动登录的网络请求）
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);

        requestNotificationPermissionIfNeeded();
        initViews();

        // 检查本地凭证：如果已保存邮箱和密码哈希，直接进入主页（无需网络验证）
        if (tryAutoLoginOffline()) {
            return; // 自动登录成功，已跳转并关闭当前 Activity
        }

        // 未触发自动登录，则加载已保存的邮箱到输入框
        loadSavedEmail();
    }

    /**
     * 离线自动登录：仅检查本地是否保存了邮箱和密码哈希。
     * 若存在，直接视为已登录，跳转 MainActivity。
     * @return true 表示已触发自动跳转，false 表示无有效凭证
     */
    private boolean tryAutoLoginOffline() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String savedEmail = prefs.getString(KEY_SAVED_EMAIL, "");
        String savedPasswordHash = prefs.getString(KEY_SAVED_PASSWORD_HASH, "");

        if (!TextUtils.isEmpty(savedEmail) && !TextUtils.isEmpty(savedPasswordHash)) {
            // 凭证完整，直接跳转主页
            Toast.makeText(this, "自动登录成功", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return true;
        }
        return false;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "通知权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "通知权限被拒绝", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void initViews() {
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        cbRemember = findViewById(R.id.cbRemember);
        btnLogin = findViewById(R.id.btnLogin);
        MaterialButton btnForgotPassword = findViewById(R.id.btnForgotPassword);
        MaterialButton btnSignup = findViewById(R.id.btnSignup);

        btnLogin.setOnClickListener(v -> {
            String email = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString();
            if (validateInput(email, password)) {
                String passwordHash = sha256(password);
                if (passwordHash != null) {
                    performLogin(email, passwordHash, cbRemember.isChecked());
                } else {
                    Toast.makeText(this, "加密失败，请重试", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnForgotPassword.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, ForgetPSWActivity.class)));
        btnSignup.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
    }

    private boolean validateInput(String email, String password) {
        if (TextUtils.isEmpty(email)) {
            etUsername.setError("邮箱不能为空");
            return false;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etUsername.setError("邮箱格式不正确");
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("密码不能为空");
            return false;
        }
        return true;
    }

    /**
     * 执行登录操作（需要网络验证）
     * @param email 邮箱
     * @param passwordHash 密码哈希
     * @param remember 是否记住凭证
     */
    private void performLogin(String email, String passwordHash, boolean remember) {
        btnLogin.setEnabled(false);
        btnLogin.setText("登录中...");

        String dataToSend = PROTOCOL_PREFIX + email + "|" + passwordHash;

        executor.execute(() -> {
            String response = sendDataViaSocket(dataToSend);
            mainHandler.post(() -> {
                btnLogin.setEnabled(true);
                btnLogin.setText("登 录");

                if ("LOGIN_SUCCESS".equals(response)) {
                    Toast.makeText(LoginActivity.this, "登录成功！", Toast.LENGTH_SHORT).show();

                    // 保存登录状态
                    SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("user_id", email);
                    if (remember) {
                        editor.putString(KEY_SAVED_EMAIL, email);
                        editor.putString(KEY_SAVED_PASSWORD_HASH, passwordHash);
                    } else {
                        // 如果不记住，清除之前可能保存的密码
                        editor.remove(KEY_SAVED_PASSWORD_HASH);
                    }
                    editor.apply();

                    // 跳转主界面
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                } else if ("LOGIN_FAILED".equals(response)) {
                    Toast.makeText(LoginActivity.this, "邮箱或密码错误", Toast.LENGTH_LONG).show();
                    // 登录失败时，如果之前有保存的凭证，应清除密码（因为可能密码已修改）
                    if (remember) {
                        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                                .edit()
                                .remove(KEY_SAVED_PASSWORD_HASH)
                                .apply();
                    }
                } else {
                    Toast.makeText(LoginActivity.this, "连接服务器失败，请检查网络", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private String sendDataViaSocket(String data) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write((data + "\n").getBytes("UTF-8"));
            out.flush();

            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[1024];
            int len = in.read(buffer);
            if (len > 0) {
                return new String(buffer, 0, len, "UTF-8").trim();
            }
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void loadSavedEmail() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String savedEmail = prefs.getString(KEY_SAVED_EMAIL, "");
        if (!TextUtils.isEmpty(savedEmail)) {
            etUsername.setText(savedEmail);
            cbRemember.setChecked(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}