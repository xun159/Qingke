package wlw231.cly.qingke;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
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

public class RegisterActivity extends AppCompatActivity {

    private static final String SERVER_HOST = "10.111.254.85"; // 模拟器使用，真机改为实际 IP
    private static final int SERVER_PORT = 5050;

    private TextInputEditText etUsername;
    private TextInputEditText etEmail;
    private TextInputEditText etCode;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirmPassword;
    private MaterialCheckBox cbAgreement;
    private MaterialButton btnGetCode;
    private MaterialButton btnRegister;
    private MaterialButton btnGoToLogin;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 倒计时相关
    private int countdownSeconds = 60;
    private boolean isCountdownActive = false;
    private final Handler countdownHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register); // 确保布局文件名为 register.xml

        initViews();
        setListeners();
    }

    private void initViews() {
        etUsername = findViewById(R.id.etUsername);
        etEmail = findViewById(R.id.etEmail);
        etCode = findViewById(R.id.etCode);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        cbAgreement = findViewById(R.id.cbAgreement);
        btnGetCode = findViewById(R.id.btnGetCode);
        btnRegister = findViewById(R.id.btnRegister);
        btnGoToLogin = findViewById(R.id.btnGoToLogin);
    }

    private void setListeners() {
        btnGetCode.setOnClickListener(v -> requestVerificationCode());
        btnRegister.setOnClickListener(v -> attemptRegister());
        btnGoToLogin.setOnClickListener(v -> {
            // 返回登录页
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
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

        // 开始倒计时，禁用按钮
        btnGetCode.setEnabled(false);
        startCountdown();

        String data = "REQ_CODE_REG|" + email;   // 注册专用验证码请求

        executor.execute(() -> {
            String response = sendDataViaSocket(data);
            mainHandler.post(() -> {
                if ("CODE_SENT".equals(response)) {
                    Toast.makeText(RegisterActivity.this, "验证码已发送，请查收", Toast.LENGTH_SHORT).show();
                } else if ("EMAIL_EXISTS".equals(response)) {
                    Toast.makeText(RegisterActivity.this, "该邮箱已被注册", Toast.LENGTH_LONG).show();
                    btnGetCode.setEnabled(true);
                    stopCountdown();
                } else {
                    Toast.makeText(RegisterActivity.this, "发送失败，请稍后重试", Toast.LENGTH_LONG).show();
                    btnGetCode.setEnabled(true);
                    stopCountdown();
                }
            });
        });
    }

    /**
     * 尝试注册
     */
    private void attemptRegister() {
        String username = etUsername.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String code = etCode.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        // 输入校验
        if (TextUtils.isEmpty(username)) {
            etUsername.setError("用户名不能为空");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("邮箱不能为空");
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("邮箱格式不正确");
            return;
        }
        if (TextUtils.isEmpty(code)) {
            etCode.setError("验证码不能为空");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("密码不能为空");
            return;
        }
        if (password.length() < 6) {
            etPassword.setError("密码长度至少6位");
            return;
        }
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("两次密码输入不一致");
            return;
        }
        if (!cbAgreement.isChecked()) {
            Toast.makeText(this, "请阅读并同意用户协议和隐私政策", Toast.LENGTH_SHORT).show();
            return;
        }

        // SHA-256 加密密码
        String passwordHash = sha256(password);
        if (passwordHash == null) {
            Toast.makeText(this, "加密失败，请重试", Toast.LENGTH_SHORT).show();
            return;
        }

        btnRegister.setEnabled(false);
        btnRegister.setText("注册中...");

        String data = "REGISTER|" + username + "|" + email + "|" + code + "|" + passwordHash;

        executor.execute(() -> {
            String response = sendDataViaSocket(data);
            mainHandler.post(() -> {
                btnRegister.setEnabled(true);
                btnRegister.setText("注 册");

                if ("REG_SUCCESS".equals(response)) {
                    Toast.makeText(RegisterActivity.this, "注册成功，请登录", Toast.LENGTH_LONG).show();
                    // 返回登录页
                    startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                    finish();
                } else if ("EMAIL_EXISTS".equals(response)) {
                    Toast.makeText(RegisterActivity.this, "该邮箱已被注册", Toast.LENGTH_LONG).show();
                } else if ("USERNAME_EXISTS".equals(response)) {
                    Toast.makeText(RegisterActivity.this, "用户名已被占用", Toast.LENGTH_LONG).show();
                } else if ("CODE_ERROR".equals(response)) {
                    Toast.makeText(RegisterActivity.this, "验证码错误或已过期", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(RegisterActivity.this, "注册失败，请稍后重试", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    /**
     * Socket 通信
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        stopCountdown();
    }
}