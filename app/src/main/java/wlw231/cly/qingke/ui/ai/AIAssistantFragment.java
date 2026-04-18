package wlw231.cly.qingke.ui.ai;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import wlw231.cly.qingke.R;

public class AIAssistantFragment extends Fragment {

    private static final String TAG = "AIAssistantFragment";
    private static final String SERVER_HOST = "192.168.12.124"; // 模拟器用，真机改为实际 IP
    private static final int SERVER_PORT = 6000;

    private RecyclerView rvMessages;
    private EditText etMessage;
    private MaterialButton btnSend;
    private ChatAdapter adapter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String userId;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 从 SharedPreferences 获取用户 ID
        userId = requireActivity().getSharedPreferences("login_prefs", requireActivity().MODE_PRIVATE)
                .getString("user_id", "user_123");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
    }

    private void initViews(View root) {
        rvMessages = root.findViewById(R.id.rvMessages);
        etMessage = root.findViewById(R.id.etMessage);
        btnSend = root.findViewById(R.id.btnSend);

        adapter = new ChatAdapter();
        rvMessages.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMessages.setAdapter(adapter);

        // 欢迎消息
        adapter.addMessage(new ChatAdapter.Message("你好！我是 AI 助手，有什么可以帮你的？", false));

        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String message = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(message)) return;

        adapter.addMessage(new ChatAdapter.Message(message, true));
        etMessage.setText("");
        scrollToBottom();

        adapter.addMessage(new ChatAdapter.Message("...", false));
        int loadingPos = adapter.getItemCount() - 1;

        executor.execute(() -> {
            String response = sendChatViaSocket(userId, message);
            mainHandler.post(() -> {
                adapter.removeMessageAt(loadingPos);
                if (response != null && !response.isEmpty()) {
                    adapter.addMessage(new ChatAdapter.Message(response, false));
                } else {
                    adapter.addMessage(new ChatAdapter.Message("网络错误，请稍后重试", false));
                }
                scrollToBottom();
            });
        });
    }

    private String sendChatViaSocket(String userId, String message) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {
            socket.setSoTimeout(10000);

            OutputStream out = socket.getOutputStream();
            byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
            String header = "CHAT|" + userId + "|" + msgBytes.length + "\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(msgBytes);
            out.flush();

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
                return new String(ansBytes, StandardCharsets.UTF_8);
            } else {
                return respHeader;
            }
        } catch (IOException e) {
            Log.e(TAG, "Chat socket error", e);
            return null;
        }
    }

    private void scrollToBottom() {
        rvMessages.post(() -> rvMessages.smoothScrollToPosition(adapter.getItemCount() - 1));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdown();
    }

    // ---------- 内部适配器类 ----------
    public static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

        public static class Message {
            public String text;
            public boolean isUser;

            public Message(String text, boolean isUser) {
                this.text = text;
                this.isUser = isUser;
            }
        }

        private final List<Message> messages = new ArrayList<>();

        public void addMessage(Message msg) {
            messages.add(msg);
            notifyItemInserted(messages.size() - 1);
        }

        public void removeMessageAt(int position) {
            if (position >= 0 && position < messages.size()) {
                messages.remove(position);
                notifyItemRemoved(position);
            }
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Message msg = messages.get(position);
            if (msg.isUser) {
                holder.cardUser.setVisibility(View.VISIBLE);
                holder.cardAssistant.setVisibility(View.GONE);
                holder.tvUserMessage.setText(msg.text);
            } else {
                holder.cardUser.setVisibility(View.GONE);
                holder.cardAssistant.setVisibility(View.VISIBLE);
                holder.tvAssistantMessage.setText(msg.text);
            }
        }

        private static class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView cardUser, cardAssistant;
            TextView tvUserMessage, tvAssistantMessage;

            ViewHolder(View itemView) {
                super(itemView);
                cardUser = itemView.findViewById(R.id.cardUser);
                cardAssistant = itemView.findViewById(R.id.cardAssistant);
                tvUserMessage = itemView.findViewById(R.id.tvUserMessage);
                tvAssistantMessage = itemView.findViewById(R.id.tvAssistantMessage);
            }
        }
    }
}