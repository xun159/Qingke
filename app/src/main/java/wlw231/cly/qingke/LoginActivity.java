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

    // UI 组件
    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private MaterialCheckBox cbRemember;
    private MaterialButton btnLogin;

    // 线程池与主线程 Handler
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);   // 布局文件名为 login.xml

        // 首次启动时请求通知权限（Android 13+）
        requestNotificationPermissionIfNeeded();

        initViews();
        loadSavedEmail();
    }

    /**
     * 在应用启动时请求通知权限（Android 13+ 必须）
     */
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

        btnLogin.setOnClickListener(v -> attemptLogin());
        btnForgotPassword.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, ForgetPSWActivity.class)));
        btnSignup.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
    }

    private void attemptLogin() {
        String email = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();

        // 输入校验
        if (TextUtils.isEmpty(email)) {
            etUsername.setError("邮箱不能为空");
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etUsername.setError("邮箱格式不正确");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("密码不能为空");
            return;
        }

        // SHA-256 加密
        String passwordHash = sha256(password);
        if (passwordHash == null) {
            Toast.makeText(this, "加密失败，请重试", Toast.LENGTH_SHORT).show();
            return;
        }

        // UI 防抖
        btnLogin.setEnabled(false);
        btnLogin.setText("登录中...");

        String dataToSend = PROTOCOL_PREFIX + email + "|" + passwordHash;

        // 后台执行网络请求
        executor.execute(() -> {
            String response = sendDataViaSocket(dataToSend);
            mainHandler.post(() -> {
                btnLogin.setEnabled(true);
                btnLogin.setText("登 录");

                if ("LOGIN_SUCCESS".equals(response)) {
                    Toast.makeText(LoginActivity.this, "登录成功！", Toast.LENGTH_SHORT).show();
                    if (cbRemember.isChecked()) {
                        saveEmailToPrefs(email);
                    }
                    // 保存 user_id
                    getSharedPreferences("login_prefs", MODE_PRIVATE)
                            .edit()
                            .putString("user_id", email)
                            .apply();
                    // 跳转主界面
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                } else if ("LOGIN_FAILED".equals(response)) {
                    Toast.makeText(LoginActivity.this, "邮箱或密码错误", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(LoginActivity.this, "连接服务器失败，请检查网络", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    /**
     * 通过 Socket 发送数据并接收响应
     */
    private String sendDataViaSocket(String data) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {
            socket.setSoTimeout(5000);  // 5 秒超时
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

    /**
     * SHA-256 哈希计算
     */
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

    private void saveEmailToPrefs(String email) {
        getSharedPreferences("login_prefs", MODE_PRIVATE)
                .edit()
                .putString("saved_email", email)
                .apply();
    }

    private void loadSavedEmail() {
        SharedPreferences prefs = getSharedPreferences("login_prefs", MODE_PRIVATE);
        String savedEmail = prefs.getString("saved_email", "");
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