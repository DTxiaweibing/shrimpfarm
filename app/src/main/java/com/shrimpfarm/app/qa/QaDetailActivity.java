package com.shrimpfarm.app.qa;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.qa.adapter.AnswerAdapter;
import com.shrimpfarm.app.qa.api.QaApi;
import com.shrimpfarm.app.qa.model.Answer;
import com.shrimpfarm.app.qa.model.Question;
import com.shrimpfarm.app.utils.SensitiveWordFilter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class QaDetailActivity extends AppCompatActivity {
    private QaApi api;
    private Question question;
    private AnswerAdapter answerAdapter;
    private List<Answer> answers;
    private long questionId;
    private String currentUserId;
    private boolean isAdmin;

    private TextView tvNickname, tvTime, tvTitle, tvContent;
    private LinearLayout layoutImages;
    private EditText etAnswer;
    private ImageButton btnSend, btnDeleteQuestion;
    private Handler longPressHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qa_detail);

        ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0).setOnApplyWindowInsetsListener((v, insets) -> {
            View bottomBar = findViewById(R.id.bottom_bar);
            if (bottomBar != null) {
                int bottom = insets.getSystemWindowInsetBottom();
                if (bottom > 0) {
                    bottomBar.setPadding(bottomBar.getPaddingLeft(), bottomBar.getPaddingTop(),
                            bottomBar.getPaddingRight(), bottom + bottomBar.getPaddingBottom());
                }
            }
            return insets;
        });

        questionId = getIntent().getLongExtra("question_id", 0);
        if (questionId == 0) {
            Toast.makeText(this, "参数错误", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        api = new QaApi(this);
        currentUserId = api.getCurrentUserId();
        isAdmin = "1032699170@qq.com".equals(api.getCurrentUserEmail());

        tvNickname = findViewById(R.id.tv_nickname);
        tvTime = findViewById(R.id.tv_time);
        tvTitle = findViewById(R.id.tv_title);
        tvContent = findViewById(R.id.tv_content);
        layoutImages = findViewById(R.id.layout_images);
        etAnswer = findViewById(R.id.et_answer);
        btnSend = findViewById(R.id.btn_send);
        btnDeleteQuestion = findViewById(R.id.btn_delete_question);

        setupNicknameLongPress();

        answers = new ArrayList<>();
        RecyclerView rv = findViewById(R.id.answer_recycler_view);
        rv.setLayoutManager(new LinearLayoutManager(this));
        answerAdapter = new AnswerAdapter(answers, false, answer -> {
            api.acceptAnswer(questionId, answer.id, new QaApi.QaCallback<Void>() {
                @Override public void onSuccess(Void result) {
                    answer.isAccepted = true;
                    answerAdapter.notifyDataSetChanged();
                    Toast.makeText(QaDetailActivity.this, "已采纳", Toast.LENGTH_SHORT).show();
                }
                @Override public void onError(String error) {
                    Toast.makeText(QaDetailActivity.this, error, Toast.LENGTH_SHORT).show();
                }
            });
        });
        answerAdapter.setCurrentUserId(currentUserId);
        answerAdapter.setAdminMode(isAdmin);
        answerAdapter.setDeleteListener((answer, position) -> confirmDeleteAnswer(answer, position));
        rv.setAdapter(answerAdapter);

        loadDetail();

        btnDeleteQuestion.setOnClickListener(v -> confirmDeleteQuestion());

        btnSend.setOnClickListener(v -> {
            if (!api.isLoggedIn()) {
                Toast.makeText(this, "请先登录后再回答", Toast.LENGTH_SHORT).show();
                return;
            }
            String content = etAnswer.getText().toString().trim();
            if (content.isEmpty()) {
                Toast.makeText(this, "请输入回答内容", Toast.LENGTH_SHORT).show();
                return;
            }
            if (SensitiveWordFilter.contains(content)) {
                Toast.makeText(this, "内容包含敏感词，请修改后重试", Toast.LENGTH_SHORT).show();
                return;
            }
            btnSend.setEnabled(false);
            api.postAnswer(questionId, content, null, new QaApi.QaCallback<Answer>() {
                @Override public void onSuccess(Answer answer) {
                    answer.displayName = api.getCurrentNickname();
                    answerAdapter.addAnswer(answer);
                    etAnswer.setText("");
                    btnSend.setEnabled(true);
                }
                @Override public void onError(String error) {
                    btnSend.setEnabled(true);
                    Toast.makeText(QaDetailActivity.this, error, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void setupNicknameLongPress() {
        Runnable longPressRunnable = () -> confirmDeleteQuestion();
        tvNickname.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    longPressHandler.postDelayed(longPressRunnable, 8000);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    longPressHandler.removeCallbacksAndMessages(null);
                    break;
            }
            return false;
        });
    }

    private void confirmDeleteQuestion() {
        if (!api.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除此问题吗？")
                .setPositiveButton("删除", (d, w) -> {
                    api.deleteQuestion(questionId, new QaApi.QaCallback<Void>() {
                        @Override public void onSuccess(Void result) {
                            Toast.makeText(QaDetailActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                        @Override public void onError(String error) {
                            Toast.makeText(QaDetailActivity.this, error, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDeleteAnswer(Answer answer, int position) {
        if (!api.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除此回答吗？")
                .setPositiveButton("删除", (d, w) -> {
                    api.deleteAnswer(answer.id, new QaApi.QaCallback<Void>() {
                        @Override public void onSuccess(Void result) {
                            answerAdapter.removeItem(position);
                            Toast.makeText(QaDetailActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onError(String error) {
                            Toast.makeText(QaDetailActivity.this, error, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void loadDetail() {
        api.getQuestionDetail(questionId, new QaApi.QaCallback<Question>() {
            @Override public void onSuccess(Question q) {
                if (isFinishing() || isDestroyed()) return;
                question = q;
                displayQuestion(q);
            }
            @Override public void onError(String error) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(QaDetailActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayQuestion(Question q) {
        if (isFinishing() || isDestroyed()) return;
        String nick = q.displayName != null && !q.displayName.isEmpty() ? q.displayName
                : "用户" + (q.userId != null && q.userId.length() > 6 ? q.userId.substring(0, 6) : "");
        tvNickname.setText(nick);
        tvTime.setText(formatTime(q.createdAt));
        tvTitle.setText(q.title);
        tvContent.setText(q.content);

        if (q.answers != null) {
            answers.clear();
            answers.addAll(q.answers);
            answerAdapter.notifyDataSetChanged();
        }

        boolean isAuthor = currentUserId != null && currentUserId.equals(q.userId);
        answerAdapter.setQuestionAuthor(isAuthor);
        btnDeleteQuestion.setVisibility(isAuthor || isAdmin ? View.VISIBLE : View.GONE);

        if (q.imageUrls != null && !q.imageUrls.isEmpty()) {
            layoutImages.setVisibility(View.VISIBLE);
            layoutImages.removeAllViews();
            for (String url : q.imageUrls) {
                android.widget.ImageView iv = new android.widget.ImageView(this);
                int size = getResources().getDisplayMetrics().widthPixels / 3 - 16;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.setMargins(0, 0, 8, 0);
                iv.setLayoutParams(lp);
                iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                iv.setOnClickListener(v -> showImagePreview(url));
                Glide.with(this).load(url).into(iv);
                layoutImages.addView(iv);
            }
        }
    }

    private void showImagePreview(String url) {
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setView(new android.widget.ProgressBar(this))
                .show();
        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        iv.setAdjustViewBounds(true);
        Glide.with(this).load(url).into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
            @Override public void onResourceReady(android.graphics.drawable.Drawable resource, com.bumptech.glide.request.transition.Transition<? super android.graphics.drawable.Drawable> transition) {
                dialog.dismiss();
                iv.setImageDrawable(resource);
                new android.app.AlertDialog.Builder(QaDetailActivity.this)
                        .setView(iv)
                        .setPositiveButton("关闭", null)
                        .show();
            }
            @Override public void onLoadCleared(android.graphics.drawable.Drawable placeholder) {}
            @Override public void onLoadFailed(android.graphics.drawable.Drawable errorDrawable) {
                dialog.dismiss();
                Toast.makeText(QaDetailActivity.this, "图片加载失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    static String formatTime(String isoTime) {
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
