package wlw231.cly.qingke.ui.profile;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import wlw231.cly.qingke.LoginActivity;
import wlw231.cly.qingke.R;

public class ProfileFragment extends Fragment {

    private static final String SERVER_HOST = "192.168.12.124"; // 与登录注册保持一致
    private static final int SERVER_PORT = 5050;

    private ImageView ivAvatar;
    private TextView tvUsername;
    private TextView tvEmail;
    private MaterialButton btnLogout;
    private MaterialButton btnDeleteAccount;

    private SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = requireActivity().getSharedPreferences("login_prefs", requireActivity().MODE_PRIVATE);

        initViews(view);
        loadUserInfo();
    }

    private void initViews(View root) {
        ivAvatar = root.findViewById(R.id.ivAvatar);
        tvUsername = root.findViewById(R.id.tvUsername);
        tvEmail = root.findViewById(R.id.tvEmail);
        btnLogout = root.findViewById(R.id.btnLogout);
        btnDeleteAccount = root.findViewById(R.id.btnDeleteAccount);

        btnLogout.setOnClickListener(v -> logout());
        btnDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());
    }

    private void loadUserInfo() {
        String username = prefs.getString("username", "");
        String email = prefs.getString("user_id", "");
        String savedEmail = prefs.getString("saved_email", "");

        String displayEmail = email.isEmpty() ? savedEmail : email;

        if (!username.isEmpty()) {
            tvUsername.setText(username);
        } else if (!displayEmail.isEmpty()) {
            tvUsername.setText(displayEmail.split("@")[0]);
        } else {
            tvUsername.setText("未登录");
        }

        tvEmail.setText(displayEmail);
    }

    private void logout() {
        prefs.edit()
                .remove("user_id")
                .remove("saved_email")
                .remove("saved_password_hash") // 新增
                .apply();

        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    private void showDeleteAccountDialog() {
        // 弹出对话框，要求输入密码确认
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle("注销账户");
        builder.setMessage("此操作不可逆，所有数据将被永久删除。\n请输入密码确认：");

        final EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton("确认注销", (dialog, which) -> {
            String password = input.getText().toString();
            if (TextUtils.isEmpty(password)) {
                Toast.makeText(requireContext(), "密码不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            // 执行注销请求
            deleteAccount(password);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void deleteAccount(String password) {
        String email = prefs.getString("user_id", "");
        if (email.isEmpty()) {
            Toast.makeText(requireContext(), "无法获取账户信息", Toast.LENGTH_SHORT).show();
            return;
        }

        // SHA-256 哈希密码
        String passwordHash = sha256(password);
        if (passwordHash == null) {
            Toast.makeText(requireContext(), "加密失败", Toast.LENGTH_SHORT).show();
            return;
        }

        String data = "DELETE_ACCOUNT|" + email + "|" + passwordHash;

        executor.execute(() -> {
            String response = sendDataViaSocket(data);
            mainHandler.post(() -> {
                if ("DELETE_SUCCESS".equals(response)) {
                    Toast.makeText(requireContext(), "账户已注销", Toast.LENGTH_LONG).show();
                    // 清除本地数据并跳转登录页
                    logout();
                } else if ("DELETE_FAILED".equals(response)) {
                    Toast.makeText(requireContext(), "密码错误，注销失败", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(requireContext(), "网络错误，请稍后重试", Toast.LENGTH_LONG).show();
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
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdown();
    }
}