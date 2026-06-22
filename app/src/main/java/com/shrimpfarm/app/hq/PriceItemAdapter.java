package com.shrimpfarm.app.hq;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.shrimpfarm.app.R;
import com.shrimpfarm.app.model.PriceItem;

import java.util.ArrayList;
import java.util.List;

public class PriceItemAdapter extends RecyclerView.Adapter<PriceItemAdapter.VH> {

    private List<PriceItem> items = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(PriceItem item);
    }

    @android.annotation.SuppressLint("NotifyDataSetChanged")
    public void setData(List<PriceItem> list) {
        this.items = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_price, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        PriceItem item = items.get(position);
        holder.tvName.setText(item.name != null ? item.name : "");
        holder.tvPrice.setText(item.price != null ? "¥" + item.price : "¥--");

        holder.ivChart.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvPrice;
        ImageView ivChart;

        VH(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_item_name);
            tvPrice = itemView.findViewById(R.id.tv_item_price);
            ivChart = itemView.findViewById(R.id.iv_chart);
        }
    }
}