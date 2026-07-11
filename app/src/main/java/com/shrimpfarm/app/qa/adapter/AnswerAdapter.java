package com.shrimpfarm.app.qa.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.shrimpfarm.app.R;
import com.shrimpfarm.app.qa.model.Answer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class AnswerAdapter extends RecyclerView.Adapter<AnswerAdapter.ViewHolder> {
    private List<Answer> answers;
    private boolean isQuestionAuthor;
    private AcceptListener acceptListener;

    public interface AcceptListener {
        void onAccept(Answer answer);
    }

    public AnswerAdapter(List<Answer> answers, boolean isQuestionAuthor, AcceptListener listener) {
        this.answers = answers;
        this.isQuestionAuthor = isQuestionAuthor;
        this.acceptListener = listener;
    }

    public void addAnswer(Answer answer) {
        answers.add(answer);
        notifyItemInserted(answers.size() - 1);
    }

    public void setQuestionAuthor(boolean isAuthor) {
        this.isQuestionAuthor = isAuthor;
    }

    @Override public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_answer, parent, false);
        return new ViewHolder(v);
    }

    @Override public void onBindViewHolder(ViewHolder h, int position) {
        Answer a = answers.get(position);
        String nick = a.displayName != null && !a.displayName.isEmpty() ? a.displayName
                : "用户" + (a.userId != null && a.userId.length() > 6 ? a.userId.substring(0, 6) : "");
        h.tvNickname.setText(nick);
        h.tvTime.setText(formatTime(a.createdAt));
        h.tvContent.setText(a.content);
        if (a.isAccepted) {
            h.tvAccepted.setVisibility(View.VISIBLE);
        } else {
            h.tvAccepted.setVisibility(View.GONE);
        }
        if (isQuestionAuthor && !a.isAccepted) {
            h.btnAccept.setVisibility(View.VISIBLE);
        } else {
            h.btnAccept.setVisibility(View.GONE);
        }
        h.btnAccept.setOnClickListener(v -> {
            if (acceptListener != null) acceptListener.onAccept(a);
        });
    }

    @Override public int getItemCount() { return answers.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNickname, tvTime, tvContent, tvAccepted;
        Button btnAccept;
        ViewHolder(View v) {
            super(v);
            tvNickname = v.findViewById(R.id.tv_nickname);
            tvTime = v.findViewById(R.id.tv_time);
            tvContent = v.findViewById(R.id.tv_content);
            tvAccepted = v.findViewById(R.id.tv_accepted);
            btnAccept = v.findViewById(R.id.btn_accept);
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
            SimpleDateFormat out = new SimpleDateFormat("MM-dd HH:mm", Locale.ROOT);
            return out.format(date);
        } catch (Exception e) {
            return "";
        }
    }
}
