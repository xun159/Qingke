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

public class FuturePlansAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_PLAN = 1;

    public interface OnPlanClickListener {
        void onPlanClick(PlanEntity plan);
    }

    public interface OnPlanLongClickListener {
        void onPlanLongClick(PlanEntity plan);
    }

    private List<Object> items = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private OnPlanClickListener clickListener;
    private OnPlanLongClickListener longClickListener;

    public void setOnPlanClickListener(OnPlanClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnPlanLongClickListener(OnPlanLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void submitList(List<Object> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_HEADER : TYPE_PLAN;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_future_plan_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_plan_entry, parent, false);
            return new PlanViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object obj = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).tvHeader.setText((String) obj);
        } else if (holder instanceof PlanViewHolder) {
            PlanEntity plan = (PlanEntity) obj;
            PlanViewHolder vh = (PlanViewHolder) holder;
            vh.tvName.setText(plan.name);
            vh.tvTime.setText(timeFormat.format(new Date(plan.startTimeMillis)));

            // 删除线效果
            if (plan.isCompleted) {
                vh.tvName.setPaintFlags(vh.tvName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                vh.tvName.setTextColor(0xFFB0B0B0);
            } else {
                vh.tvName.setPaintFlags(vh.tvName.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                vh.tvName.setTextColor(0xFF2C3E50);
            }

            vh.itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onPlanClick(plan);
            });
            vh.itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onPlanLongClick(plan);
                    return true;
                }
                return false;
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvHeader;
        HeaderViewHolder(View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tvHeader);
        }
    }

    static class PlanViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvTime;
        PlanViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvPlanName);
            tvTime = itemView.findViewById(R.id.tvStartTime);
        }
    }
}