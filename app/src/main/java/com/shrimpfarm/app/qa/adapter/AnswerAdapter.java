package com.shrimpfarm.app.qa.adapter;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
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
    private Context context;
    private List<Answer> answers;
    private boolean isQuestionAuthor;
    private boolean isAdmin;
    private AcceptListener acceptListener;
    private DeleteListener deleteListener;
    private VoteListener voteListener;
    private String currentUserId;

    public interface AcceptListener {
        void onAccept(Answer answer);
    }

    public interface DeleteListener {
        void onDelete(Answer answer, int position);
    }

    public interface VoteListener {
        void onVote(Answer answer, int voteType);
    }

    public AnswerAdapter(Context context, List<Answer> answers, boolean isQuestionAuthor, AcceptListener listener) {
        this.context = context;
        this.answers = answers;
        this.isQuestionAuthor = isQuestionAuthor;
        this.acceptListener = listener;
    }

    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }

    public void setAdminMode(boolean admin) {
        this.isAdmin = admin;
    }

    public void setDeleteListener(DeleteListener listener) {
        this.deleteListener = listener;
    }

    public void setVoteListener(VoteListener listener) {
        this.voteListener = listener;
    }

    public void addAnswer(Answer answer) {
        answers.add(answer);
        notifyItemInserted(answers.size() - 1);
    }

    public void setQuestionAuthor(boolean isAuthor) {
        this.isQuestionAuthor = isAuthor;
    }

    public void removeItem(int position) {
        if (position < 0 || position >= answers.size()) return;
        answers.remove(position);
        notifyItemRemoved(position);
    }

    @Override public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_answer, parent, false);
        return new ViewHolder(v);
    }

    @Override public void onBindViewHolder(ViewHolder h, int position) {
        Answer a = answers.get(position);
        String nick = a.displayName != null && !a.displayName.isEmpty() ? a.displayName
                : context.getString(R.string.qa_user_prefix) + (a.userId != null && a.userId.length() > 6 ? a.userId.substring(0, 6) : "");
        h.tvNickname.setText(nick);
        h.tvTime.setText(formatTime(context, a.createdAt));
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

        boolean isAuthor = currentUserId != null && currentUserId.equals(a.userId);
        h.btnDelete.setVisibility(isAuthor || isAdmin ? View.VISIBLE : View.GONE);
        h.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onDelete(a, position);
        });

        boolean loggedIn = currentUserId != null && !currentUserId.isEmpty();
        h.btnUpvote.setText("👍 " + a.upvotes);
        h.btnDownvote.setText("👎 " + a.downvotes);
        h.btnUpvote.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        h.btnDownvote.setVisibility(loggedIn ? View.VISIBLE : View.GONE);

        if (a.userVote == 1) {
            h.btnUpvote.setTextColor(0xFF2D8C42);
            h.btnDownvote.setTextColor(0xFF333333);
        } else if (a.userVote == -1) {
            h.btnUpvote.setTextColor(0xFF333333);
            h.btnDownvote.setTextColor(0xFFE53935);
        } else {
            h.btnUpvote.setTextColor(0xFF333333);
            h.btnDownvote.setTextColor(0xFF333333);
        }

        h.btnUpvote.setOnClickListener(v -> {
            if (voteListener != null) voteListener.onVote(a, a.userVote == 1 ? 0 : 1);
        });
        h.btnDownvote.setOnClickListener(v -> {
            if (voteListener != null) voteListener.onVote(a, a.userVote == -1 ? 0 : -1);
        });

        h.attachLongPress(a, position, deleteListener);
    }

    @Override public int getItemCount() { return answers.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNickname, tvTime, tvContent, tvAccepted;
        Button btnAccept;
        ImageButton btnDelete;
        TextView btnUpvote, btnDownvote;
        private Handler longPressHandler = new Handler(Looper.getMainLooper());
        private Runnable longPressRunnable;

        ViewHolder(View v) {
            super(v);
            tvNickname = v.findViewById(R.id.tv_nickname);
            tvTime = v.findViewById(R.id.tv_time);
            tvContent = v.findViewById(R.id.tv_content);
            tvAccepted = v.findViewById(R.id.tv_accepted);
            btnAccept = v.findViewById(R.id.btn_accept);
            btnDelete = v.findViewById(R.id.btn_delete_answer);
            btnUpvote = v.findViewById(R.id.btn_upvote);
            btnDownvote = v.findViewById(R.id.btn_downvote);

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

        void attachLongPress(Answer a, int position, DeleteListener listener) {
            longPressHandler.removeCallbacksAndMessages(null);
            longPressRunnable = () -> {
                if (listener != null) listener.onDelete(a, position);
            };
        }
    }

    private static String formatTime(Context ctx, String isoTime) {
        if (isoTime == null) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = sdf.parse(isoTime.replace("Z", "").substring(0, 19));
            if (date == null) return "";
            long diff = System.currentTimeMillis() - date.getTime();
            long seconds = diff / 1000;
            if (seconds < 60) return ctx.getString(R.string.qa_time_just_now);
            long minutes = seconds / 60;
            if (minutes < 60) return String.format(ctx.getString(R.string.qa_time_minutes_ago), minutes);
            long hours = minutes / 60;
            if (hours < 24) return String.format(ctx.getString(R.string.qa_time_hours_ago), hours);
            long days = hours / 24;
            if (days < 7) return String.format(ctx.getString(R.string.qa_time_days_ago), days);
            SimpleDateFormat out = new SimpleDateFormat("MM-dd HH:mm", Locale.ROOT);
            return out.format(date);
        } catch (Exception e) {
            return "";
        }
    }
}
