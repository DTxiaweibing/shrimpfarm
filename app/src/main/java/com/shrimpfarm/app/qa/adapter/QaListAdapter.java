package com.shrimpfarm.app.qa.adapter;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.shrimpfarm.app.R;
import com.shrimpfarm.app.qa.model.Question;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class QaListAdapter extends RecyclerView.Adapter<QaListAdapter.ViewHolder> {
    private List<Question> questions;
    private OnItemClickListener listener;
    private OnItemDeleteListener deleteListener;
    private String currentUserId;
    private boolean isAdmin;

    public interface OnItemClickListener {
        void onItemClick(Question question);
    }

    public interface OnItemDeleteListener {
        void onDelete(Question question, int position);
    }

    public QaListAdapter(List<Question> questions, OnItemClickListener listener) {
        this.questions = questions;
        this.listener = listener;
    }

    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }

    public void setAdminMode(boolean admin) {
        this.isAdmin = admin;
    }

    public void setOnItemDeleteListener(OnItemDeleteListener listener) {
        this.deleteListener = listener;
    }

    public void setQuestions(List<Question> questions) {
        this.questions = questions;
        notifyDataSetChanged();
    }

    public void addQuestions(List<Question> more) {
        int start = questions.size();
        questions.addAll(more);
        notifyItemRangeInserted(start, more.size());
    }

    public void removeItem(int position) {
        questions.remove(position);
        notifyItemRemoved(position);
    }

    @Override public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_question, parent, false);
        return new ViewHolder(v);
    }

    @Override public void onBindViewHolder(ViewHolder h, int position) {
        Question q = questions.get(position);
        String nick = q.displayName != null && !q.displayName.isEmpty() ? q.displayName
                : "用户" + (q.userId != null && q.userId.length() > 6 ? q.userId.substring(0, 6) : "");
        h.tvNickname.setText(nick);
        h.tvTitle.setText(q.title);
        h.tvContent.setText(q.content);
        h.tvAnswerCount.setText(q.answerCount + "回答");
        String timeToUse = q.latestAnswerAt != null ? q.latestAnswerAt : q.createdAt;
        h.tvTime.setText(formatTime(timeToUse));
        if (q.isResolved) {
            h.tvResolved.setVisibility(View.VISIBLE);
            h.tvResolved.setText("已解决");
        } else {
            h.tvResolved.setVisibility(View.GONE);
        }

        boolean isAuthor = currentUserId != null && currentUserId.equals(q.userId);
        h.btnDelete.setVisibility(isAuthor || isAdmin ? View.VISIBLE : View.GONE);
        h.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onDelete(q, position);
        });

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(q);
        });

        h.attachLongPress(q, position, deleteListener);
    }

    @Override public int getItemCount() { return questions.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNickname, tvTitle, tvContent, tvAnswerCount, tvTime, tvResolved;
        ImageButton btnDelete;
        private Handler longPressHandler = new Handler(Looper.getMainLooper());
        private Runnable longPressRunnable;

        ViewHolder(View v) {
            super(v);
            tvNickname = v.findViewById(R.id.tv_nickname);
            tvTitle = v.findViewById(R.id.tv_title);
            tvContent = v.findViewById(R.id.tv_content);
            tvAnswerCount = v.findViewById(R.id.tv_answer_count);
            tvTime = v.findViewById(R.id.tv_time);
            tvResolved = v.findViewById(R.id.tv_resolved);
            btnDelete = v.findViewById(R.id.btn_delete_question);

            tvNickname.setOnTouchListener((v1, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (longPressRunnable != null) {
                            longPressHandler.postDelayed(longPressRunnable, 8000);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        longPressHandler.removeCallbacksAndMessages(null);
                        break;
                }
                return false;
            });
        }

        void attachLongPress(Question q, int position, OnItemDeleteListener listener) {
            longPressHandler.removeCallbacksAndMessages(null);
            longPressRunnable = () -> {
                if (listener != null) listener.onDelete(q, position);
            };
        }
    }

    private static String formatTime(String isoTime) {
        if (isoTime == null) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = sdf.parse(isoTime.replace("Z", "").substring(0, 19));
            if (date == null) return "";
            long diff = System.currentTimeMillis() - date.getTime();
            long seconds = diff / 1000;
            if (seconds < 60) return "刚刚";
            long minutes = seconds / 60;
            if (minutes < 60) return minutes + "分钟前";
            long hours = minutes / 60;
            if (hours < 24) return hours + "小时前";
            long days = hours / 24;
            if (days < 7) return days + "天前";
            SimpleDateFormat out = new SimpleDateFormat("MM-dd", Locale.ROOT);
            return out.format(date);
        } catch (Exception e) {
            return "";
        }
    }
}
