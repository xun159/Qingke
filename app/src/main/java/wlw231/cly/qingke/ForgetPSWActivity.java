package wlw231.cly.qingke;


import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ForgetPSWActivity extends AppCompatActivity {

    private static final String SERVER_HOST = "10.0.2.2"; // 模拟器用，真机改为实际IP
    private static final int SERVER_PORT = 5050;

    private TextInputEditText etEmail;
    private TextInputEditText etCode;
    private TextInputEditText etNewPassword;
    private TextInputEditText etConfirmPassword;
    private MaterialButton btnGetCode;
    private MaterialButton btnSubmitReset;
    private MaterialButton btnBackToLogin;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 倒计时相关
    private int countdownSeconds = 60;
    private boolean isCountdownActive = false;
    private final Handler countdownHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.forget_password);

        initViews();
        setListeners();
    }

    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etCode = findViewById(R.id.etCode);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmNewPassword);
        btnGetCode = findViewById(R.id.btnGetCode);
        btnSubmitReset = findViewById(R.id.btnSubmitReset);
        btnBackToLogin = findViewById(R.id.btnBackToLogin);
    }

    private void setListeners() {
        btnGetCode.setOnClickListener(v -> requestVerificationCode());
        btnSubmitReset.setOnClickListener(v -> attemptResetPassword());
        btnBackToLogin.setOnClickListener(v -> finish()); // 返回登录页
    }

    /**
     * 请求发送验证码
     */
    private void requestVerificationCode() {
        String email = etEmail.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("邮箱不能为空");
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("邮箱格式不正确");
            return;
        }

        // 禁用按钮并开始倒计时
        btnGetCode.setEnabled(false);
        startCountdown();

        String data = "REQ_CODE|" + email;

        executor.execute(() -> {
            String response = sendDataViaSocket(data);
            mainHandler.post(() -> {
                if ("CODE_SENT".equals(response)) {
                    Toast.makeText(ForgetPSWActivity.this, "验证码已发送，请查收", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ForgetPSWActivity.this, "发送失败，请检查邮箱或网络", Toast.LENGTH_LONG).show();
                    btnGetCode.setEnabled(true);
                    stopCountdown();
                }
            });
        });
    }

    /**
     * 提交重置密码请求
     */
    private void attemptResetPassword() {
        String email = etEmail.getText().toString().trim();
        String code = etCode.getText().toString().trim();
        String newPassword = etNewPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        // 校验
        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("请输入有效的邮箱");
            return;
        }
        if (TextUtils.isEmpty(code)) {
            etCode.setError("验证码不能为空");
            return;
        }
        if (TextUtils.isEmpty(newPassword)) {
            etNewPassword.setError("密码不能为空");
            return;
        }
        if (newPassword.length() < 6) {
            etNewPassword.setError("密码长度至少6位");
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            etConfirmPassword.setError("两次密码输入不一致");
            return;
        }

        // SHA-256 加密新密码
        String newPasswordHash = sha256(newPassword);
        if (newPasswordHash == null) {
            Toast.makeText(this, "加密失败，请重试", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmitReset.setEnabled(false);
        btnSubmitReset.setText("提交中...");

        String data = "RESET_PWD|" + email + "|" + code + "|" + newPasswordHash;

        executor.execute(() -> {
            String response = sendDataViaSocket(data);
            mainHandler.post(() -> {
                btnSubmitReset.setEnabled(true);
                btnSubmitReset.setText("确认重置密码");

                if ("RESET_SUCCESS".equals(response)) {
                    Toast.makeText(ForgetPSWActivity.this, "密码重置成功，请登录", Toast.LENGTH_LONG).show();
                    // 返回登录页
                    startActivity(new Intent(ForgetPSWActivity.this, LoginActivity.class));
                    finish();
                } else if ("RESET_FAILED".equals(response)) {
                    Toast.makeText(ForgetPSWActivity.this, "验证码错误或已过期", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(ForgetPSWActivity.this, "连接服务器失败，请稍后重试", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    /**
     * 倒计时逻辑
     */
    private void startCountdown() {
        isCountdownActive = true;
        countdownSeconds = 60;
        countdownHandler.post(new Runnable() {
            @Override
            public void run() {
                if (countdownSeconds > 0) {
                    btnGetCode.setText(countdownSeconds + "秒后重试");
                    countdownSeconds--;
                    countdownHandler.postDelayed(this, 1000);
                } else {
                    btnGetCode.setEnabled(true);
                    btnGetCode.setText("获取验证码");
                    isCountdownActive = false;
                }
            }
        });
    }

    private void stopCountdown() {
        isCountdownActive = false;
        countdownHandler.removeCallbacksAndMessages(null);
        btnGetCode.setEnabled(true);
        btnGetCode.setText("获取验证码");
    }

    /**
     * Socket 通信（与登录类似，协议前缀不同）
     */
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

    /**
     * SHA-256 哈希
     */
    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        stopCountdown();
    }
}