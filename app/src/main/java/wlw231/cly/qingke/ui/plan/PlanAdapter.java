package wlw231.cly.qingke.ui.plan;

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

    // 长按回调接口
    public interface OnPlanLongClickListener {
        void onPlanLongClick(PlanEntity plan);
    }

    private List<PlanEntity> plans = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private OnPlanLongClickListener longClickListener;

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

        // 设置长按监听
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