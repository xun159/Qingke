package wlw231.cly.qingke;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    // 消息数据模型
    public static class Message {
        public String text;
        public boolean isUser; // true: 用户消息（右侧）, false: 助手消息（左侧）

        public Message(String text, boolean isUser) {
            this.text = text;
            this.isUser = isUser;
        }
    }

    private final List<Message> messages = new ArrayList<>();

    // 添加一条消息
    public void addMessage(Message msg) {
        messages.add(msg);
        notifyItemInserted(messages.size() - 1);
    }

    // 删除指定位置的消息
    public void removeMessageAt(int position) {
        if (position >= 0 && position < messages.size()) {
            messages.remove(position);
            notifyItemRemoved(position);
        }
    }

    // 获取消息数量
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
            // 用户消息：显示右侧气泡，隐藏左侧气泡
            holder.cardUser.setVisibility(View.VISIBLE);
            holder.cardAssistant.setVisibility(View.GONE);
            holder.tvUserMessage.setText(msg.text);
        } else {
            // 助手消息：显示左侧气泡，隐藏右侧气泡
            holder.cardUser.setVisibility(View.GONE);
            holder.cardAssistant.setVisibility(View.VISIBLE);
            holder.tvAssistantMessage.setText(msg.text);
        }
    }

    // ViewHolder 持有视图引用
    static class ViewHolder extends RecyclerView.ViewHolder {
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