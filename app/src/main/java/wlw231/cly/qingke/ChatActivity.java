package wlw231.cly.qingke;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {

    private static final String SERVER_HOST = "10.0.2.2"; // 模拟器用，真机改为实际 IP
    private static final int SERVER_PORT = 6000;

    private RecyclerView rvMessages;
    private EditText etMessage;
    private MaterialButton btnSend;
    private ChatAdapter adapter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String userId; // 当前登录用户 ID，可从 Intent 或 SharedPreferences 获取

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // 获取用户 ID（示例从 SharedPreferences 读取，也可从 Intent 传递）
        userId = getSharedPreferences("login_prefs", MODE_PRIVATE)
                .getString("user_id", "user_123");

        initViews();
    }

    private void initViews() {
        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

        adapter = new ChatAdapter();
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);

        // 添加欢迎消息
        adapter.addMessage(new ChatAdapter.Message("你好！我是 AI 助手，有什么可以帮你的？", false));

        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String message = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(message)) return;

        // 显示用户消息
        adapter.addMessage(new ChatAdapter.Message(message, true));
        etMessage.setText("");
        scrollToBottom();

        // 显示“正在输入”占位
        adapter.addMessage(new ChatAdapter.Message("...", false));
        int loadingPos = adapter.getItemCount() - 1;  // 记录占位位置

        executor.execute(() -> {
            String response = sendChatViaSocket(userId, message);
            mainHandler.post(() -> {
                // 移除“正在输入”占位
                adapter.removeMessageAt(loadingPos);

                if (response != null && !response.isEmpty()) {
                    // ✅ 修正：直接传参，不要 isUser: 前缀
                    adapter.addMessage(new ChatAdapter.Message(response, false));
                } else {
                    // ✅ 修正：直接传参
                    adapter.addMessage(new ChatAdapter.Message("网络错误，请稍后重试", false));
                }
                scrollToBottom();
            });
        });
    }

    private String sendChatViaSocket(String userId, String message) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {
            socket.setSoTimeout(10000); // 10 秒超时

            OutputStream out = socket.getOutputStream();
            byte[] msgBytes = message.getBytes("UTF-8");
            String header = "CHAT|" + userId + "|" + msgBytes.length + "\n";
            out.write(header.getBytes("UTF-8"));
            out.write(msgBytes);
            out.flush();

            // 读取响应头
            InputStream in = socket.getInputStream();
            StringBuilder headerBuilder = new StringBuilder();
            int ch;
            while ((ch = in.read()) != -1 && ch != '\n') {
                headerBuilder.append((char) ch);
            }
            String respHeader = headerBuilder.toString();
            if (respHeader.startsWith("ANSWER|")) {
                int ansLen = Integer.parseInt(respHeader.split("\\|")[1]);
                byte[] ansBytes = new byte[ansLen];
                int offset = 0;
                while (offset < ansLen) {
                    int read = in.read(ansBytes, offset, ansLen - offset);
                    if (read == -1) break;
                    offset += read;
                }
                return new String(ansBytes, "UTF-8");
            } else {
                return respHeader; // 错误信息
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void scrollToBottom() {
        rvMessages.post(() -> rvMessages.smoothScrollToPosition(adapter.getItemCount() - 1));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}