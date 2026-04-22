package wlw231.cly.qingke.ui.plan;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import wlw231.cly.qingke.R;

public class PlanAdapter extends RecyclerView.Adapter<PlanAdapter.ViewHolder> {

    // 单击回调接口
    public interface OnPlanClickListener {
        void onPlanClick(PlanEntity plan);
    }

    // 长按回调接口
    public interface OnPlanLongClickListener {
        void onPlanLongClick(PlanEntity plan);
    }

    private List<PlanEntity> plans = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private OnPlanClickListener clickListener;
    private OnPlanLongClickListener longClickListener;

    public void setOnPlanClickListener(OnPlanClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnPlanLongClickListener(OnPlanLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setPlans(List<PlanEntity> newPlans) {
        this.plans = newPlans;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_plan_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PlanEntity plan = plans.get(position);
        holder.tvName.setText(plan.name);
        holder.tvTime.setText(timeFormat.format(new Date(plan.startTimeMillis)));

        // 根据完成状态设置删除线和颜色
        if (plan.isCompleted) {
            holder.tvName.setPaintFlags(holder.tvName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.tvName.setTextColor(0xFFB0B0B0); // 灰色
        } else {
            holder.tvName.setPaintFlags(holder.tvName.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.tvName.setTextColor(0xFF2C3E50); // 正常深色
        }

        // 单击事件：切换完成状态
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onPlanClick(plan);
            }
        });

        // 长按事件：弹出操作菜单
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onPlanLongClick(plan);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return plans.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvTime;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvPlanName);
            tvTime = itemView.findViewById(R.id.tvStartTime);
        }
    }
}