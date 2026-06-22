package com.shrimpfarm.app.hq;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shrimpfarm.app.R;
import com.shrimpfarm.app.model.PriceCategory;
import com.shrimpfarm.app.model.PriceItem;

import java.util.ArrayList;
import java.util.List;

public class PriceCategoryAdapter extends RecyclerView.Adapter<PriceCategoryAdapter.VH> {

    private List<PriceCategory> categories = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(PriceItem item);
    }

    @android.annotation.SuppressLint("NotifyDataSetChanged")
    public void setData(List<PriceCategory> list) {
        this.categories = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_price_category, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        PriceCategory cat = categories.get(position);
        holder.tvCategory.setText(cat.title != null ? cat.title : "未分类");
        holder.adapter.setData(cat.items);
        holder.adapter.setOnItemClickListener(item -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvCategory;
        RecyclerView recyclerItems;
        PriceItemAdapter adapter;

        VH(View itemView) {
            super(itemView);
            tvCategory = itemView.findViewById(R.id.tv_category);
            recyclerItems = itemView.findViewById(R.id.recycler_items);
            recyclerItems.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
            recyclerItems.setNestedScrollingEnabled(false);
            adapter = new PriceItemAdapter();
            recyclerItems.setAdapter(adapter);
        }
    }
}