package wlw231.cly.qingke.ui.ai;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
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

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import wlw231.cly.qingke.R;

public class AIAssistantFragment extends Fragment {

    private static final String TAG = "AIAssistantFragment";
    private static final String SERVER_HOST = "192.168.12.124"; // 真机需替换为实际 IP
    private static final int SERVER_PORT = 6000;
    private static final int REQUEST_CODE_FILE = 100;

    private RecyclerView rvMessages;
    private EditText etMessage;
    private MaterialButton btnSend;
    private MaterialButton btnUploadFile;
    private ChatAdapter adapter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String userId;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        userId = requireActivity().getSharedPreferences("login_prefs", requireActivity().MODE_PRIVATE)
                .getString("user_id", "user_123");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ai_assistant, container, false);
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
        btnUploadFile = root.findViewById(R.id.btnUploadFile);
        MaterialButton btnClearChat = root.findViewById(R.id.btnClearChat);

        adapter = new ChatAdapter();
        rvMessages.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMessages.setAdapter(adapter);

        // 欢迎消息
        adapter.addMessage(new ChatAdapter.Message("你好！我是 AI 助手，有什么可以帮你的？", false));

        btnSend.setOnClickListener(v -> sendMessage());
        btnUploadFile.setOnClickListener(v -> openFilePicker());
        btnClearChat.setOnClickListener(v -> {
            adapter.clearMessages();
            adapter.addMessage(new ChatAdapter.Message("聊天已清空，有什么新问题？", false));
        });
    }

    private void sendMessage() {
        String message = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(message)) return;

        adapter.addMessage(new ChatAdapter.Message(message, true));
        etMessage.setText("");
        scrollToBottom();

        // 占位消息
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
            socket.setSoTimeout(0);  // 无限等待

            OutputStream out = socket.getOutputStream();
            byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
            String header = "CHAT|" + userId + "|" + msgBytes.length + "\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(msgBytes);
            out.flush();

            // 读取响应头
            InputStream in = socket.getInputStream();
            String respHeader = readLine(in);
            Log.d(TAG, "响应头: " + respHeader);

            if (respHeader == null || respHeader.isEmpty()) {
                Log.e(TAG, "响应头为空，可能服务端提前关闭连接");
                return null;
            }

            if (respHeader.startsWith("ANSWER|")) {
                int ansLen = Integer.parseInt(respHeader.split("\\|")[1]);
                Log.d(TAG, "期望回答长度: " + ansLen);
                byte[] ansBytes = readExact(in, ansLen);
                String answer = new String(ansBytes, StandardCharsets.UTF_8);
                Log.d(TAG, "实际回答长度: " + answer.length());
                return answer;
            } else {
                Log.e(TAG, "未知响应头: " + respHeader);
                return null;
            }
        } catch (SocketTimeoutException e) {
            Log.e(TAG, "Socket 超时", e);
            return "服务响应超时，请稍后重试";
        } catch (IOException e) {
            Log.e(TAG, "IO 异常", e);
            return null;
        }
    }

    // 添加 readLine 和 readExact 方法（若之前未定义）
    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int ch;
        while ((ch = in.read()) != -1 && ch != '\n') {
            baos.write(ch);
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    private byte[] readExact(InputStream in, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(data, offset, length - offset);
            if (read == -1) break;
            offset += read;
        }
        return data;
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "选择文件"), REQUEST_CODE_FILE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_FILE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                String fileName = getFileName(uri);
                adapter.addMessage(new ChatAdapter.Message("📎 上传文件: " + fileName, true));
                uploadFile(uri, fileName);
            }
        }
    }

    private String getFileName(Uri uri) {
        String result = "unknown";
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) result = cursor.getString(index);
                }
            }
        }
        if ("unknown".equals(result)) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result;
    }

    private void uploadFile(Uri uri, String fileName) {
        executor.execute(() -> {
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                 Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {

                if (in == null) {
                    mainHandler.post(() -> adapter.addMessage(new ChatAdapter.Message("无法读取文件", false)));
                    return;
                }

                byte[] fileBytes = IOUtils.toByteArray(in);
                OutputStream out = socket.getOutputStream();
                String header = "FILE|" + userId + "|" + fileName + "|" + fileBytes.length + "\n";
                out.write(header.getBytes(StandardCharsets.UTF_8));
                out.write(fileBytes);
                out.flush();

                InputStream socketIn = socket.getInputStream();
                StringBuilder respBuilder = new StringBuilder();
                int ch;
                while ((ch = socketIn.read()) != -1 && ch != '\n') {
                    respBuilder.append((char) ch);
                }
                String response = respBuilder.toString();

                mainHandler.post(() -> {
                    if (response.startsWith("FILE_SUCCESS")) {
                        String[] parts = response.split("\\|");
                        String msg = "✅ 文件上传成功，知识已存入图谱";
                        if (parts.length >= 3) {
                            msg += "（实体: " + parts[1] + "，关系: " + parts[2] + "）";
                        }
                        adapter.addMessage(new ChatAdapter.Message(msg, false));
                    } else {
                        adapter.addMessage(new ChatAdapter.Message("❌ 文件处理失败: " + response, false));
                    }
                });
            } catch (IOException e) {
                Log.e(TAG, "文件上传失败", e);
                mainHandler.post(() -> adapter.addMessage(new ChatAdapter.Message("网络错误，文件上传失败", false)));
            }
        });
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

        public void clearMessages() {
            messages.clear();
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_simple, parent, false);
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