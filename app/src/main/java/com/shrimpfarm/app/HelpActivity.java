package com.shrimpfarm.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import com.shrimpfarm.app.BaseActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.shrimpfarm.app.qa.QaDetailActivity;
import com.shrimpfarm.app.qa.QaPostActivity;
import com.shrimpfarm.app.qa.adapter.QaListAdapter;
import com.shrimpfarm.app.qa.api.QaApi;
import com.shrimpfarm.app.qa.model.Question;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class HelpActivity extends BaseActivity {

    @Override
    protected int getCurrentNavId() {
        return 0;
    }
    private static final String ONLINE_URL = "https://dtxiaweibing.github.io/TIMU/promo/help.html";
    private static final String CACHE_NAME = "help.html";

    private WebView webView;
    private RecyclerView qaRecyclerView;
    private FloatingActionButton fabPost;
    private SwipeRefreshLayout swipeRefresh;
    private QaListAdapter adapter;
    private List<Question> questions;
    private QaApi api;
    private int currentPage = 1;
    private boolean isLoading = false;
    private boolean hasMore = true;
    private boolean needRefresh = false;
    private ActivityResultLauncher<Intent> postQuestionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        postQuestionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        loadQuestions();
                    }
                }
        );

        api = new QaApi(this);

        // 打开帮助页时后台静默刷新登录状态，确保切换到问答社区时 token 有效
        new SupabaseAuthManager(this).backgroundRefresh();

        webView = findViewById(R.id.web_view);
        qaRecyclerView = findViewById(R.id.qa_recycler_view);
        fabPost = findViewById(R.id.fab_post_question);
        swipeRefresh = findViewById(R.id.swipe_refresh);

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        setupWebView();
        loadPage();

        questions = new ArrayList<>();
        adapter = new QaListAdapter(this, questions, question -> {
            needRefresh = true;
            Intent intent = new Intent(HelpActivity.this, QaDetailActivity.class);
            intent.putExtra("question_id", question.id);
            startActivity(intent);
        });
        adapter.setCurrentUserId(api.getCurrentUserId());
        adapter.setAdminMode("1032699170@qq.com".equals(api.getCurrentUserEmail()));
        adapter.setOnItemDeleteListener((question, position) -> {
            if (!api.isLoggedIn()) {
                Toast.makeText(this, getString(R.string.qa_toast_login_first), Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.qa_title_confirm_delete)
                    .setMessage(R.string.qa_msg_delete_question)
                    .setPositiveButton(R.string.qa_btn_delete, (d, w) -> {
                        api.deleteQuestion(question.id, new QaApi.QaCallback<Void>() {
                            @Override public void onSuccess(Void result) {
                                adapter.removeItem(position);
                                Toast.makeText(HelpActivity.this, R.string.qa_toast_deleted, Toast.LENGTH_SHORT).show();
                            }
                            @Override public void onError(String error) {
                                Toast.makeText(HelpActivity.this, error, Toast.LENGTH_SHORT).show();
                            }
                        });
                    })
                    .setNegativeButton(R.string.qa_cancel, null)
                    .show();
        });
        qaRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        qaRecyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::loadQuestions);

        qaRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (isLoading || !hasMore) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm != null && lm.findLastVisibleItemPosition() >= questions.size() - 3) {
                    loadMore();
                }
            }
        });

        View tabHelp = findViewById(R.id.tab_help_container);
        View tabQa = findViewById(R.id.tab_qa_container);
        View indicatorHelp = findViewById(R.id.indicator_help);
        View indicatorQa = findViewById(R.id.indicator_qa);
        android.widget.TextView tvHelp = findViewById(R.id.tab_help);
        android.widget.TextView tvQa = findViewById(R.id.tab_qa);

        // 50:50 容器内文字居中，轻微向内偏移使视觉更集中
        tvHelp.post(() -> {
            int screenW = getResources().getDisplayMetrics().widthPixels;
            tvHelp.setTranslationX(screenW * 0.05f);
            tvQa.setTranslationX(screenW * -0.05f);
        });

        selectTab(0, tvHelp, tvQa, indicatorHelp, indicatorQa);

        tabHelp.setOnClickListener(v -> {
            if (indicatorHelp.getVisibility() == View.VISIBLE) return;
            selectTab(0, tvHelp, tvQa, indicatorHelp, indicatorQa);
            webView.setVisibility(View.VISIBLE);
            qaRecyclerView.setVisibility(View.GONE);
            fabPost.setVisibility(View.GONE);
            swipeRefresh.setVisibility(View.GONE);
        });

        tabQa.setOnClickListener(v -> {
            if (indicatorQa.getVisibility() == View.VISIBLE) return;
            selectTab(1, tvHelp, tvQa, indicatorHelp, indicatorQa);
            webView.setVisibility(View.GONE);
            qaRecyclerView.setVisibility(View.VISIBLE);
            swipeRefresh.setVisibility(View.VISIBLE);
            fabPost.setVisibility(View.VISIBLE);
            if (questions.isEmpty()) {
                loadQuestions();
            }
        });

        fabPost.setOnClickListener(v -> {
            if (!api.isLoggedIn()) {
                Toast.makeText(this, getString(R.string.qa_toast_login_to_post), Toast.LENGTH_SHORT).show();
                return;
            }
            postQuestionLauncher.launch(new Intent(this, QaPostActivity.class));
        });
    }

    private void selectTab(int index, android.widget.TextView tvHelp, android.widget.TextView tvQa,
                           View indicatorHelp, View indicatorQa) {
        boolean tab0 = index == 0;
        tvHelp.setTextColor(tab0 ? 0xFF2D8C42 : 0xFF666666);
        tvQa.setTextColor(tab0 ? 0xFF666666 : 0xFF2D8C42);
        indicatorHelp.setVisibility(tab0 ? View.VISIBLE : View.GONE);
        indicatorQa.setVisibility(tab0 ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (needRefresh && qaRecyclerView.getVisibility() == View.VISIBLE && !questions.isEmpty()) {
            needRefresh = false;
            loadQuestions();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadQuestions() {
        currentPage = 1;
        hasMore = true;
        isLoading = true;
        api.getQuestions(currentPage, 20, new QaApi.QaCallback<>() {
            @Override public void onSuccess(List<Question> result) {
                questions.clear();
                questions.addAll(result);
                adapter.notifyDataSetChanged();
                hasMore = result.size() == 20;
                isLoading = false;
                swipeRefresh.setRefreshing(false);
            }
            @Override public void onError(String error) {
                isLoading = false;
                swipeRefresh.setRefreshing(false);
                Toast.makeText(HelpActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadMore() {
        isLoading = true;
        api.getQuestions(currentPage + 1, 20, new QaApi.QaCallback<>() {
            @Override public void onSuccess(List<Question> result) {
                currentPage++;
                int start = questions.size();
                questions.addAll(result);
                adapter.notifyItemRangeInserted(start, result.size());
                hasMore = result.size() == 20;
                isLoading = false;
            }
            @Override public void onError(String error) {
                isLoading = false;
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setAllowFileAccess(true);
    }

    private void loadPage() {
        File cacheFile = new File(getFilesDir(), "banner_pages/" + CACHE_NAME);
        if (cacheFile.exists()) {
            String content = readFileContent(cacheFile.getAbsolutePath());
            if (content != null) {
                webView.loadDataWithBaseURL(ONLINE_URL, content, "text/html", "UTF-8", null);
                return;
            }
        }
        String file = CACHE_NAME;
        String lang = com.shrimpfarm.app.utils.LocaleHelper.getSavedLang(this);
        if ("en".equals(lang)) {
            String enFile = CACHE_NAME.replace(".html", "-en.html");
            try {
                getAssets().open(enFile);
                file = enFile;
            } catch (Exception ignored) {
            }
        }
        webView.loadUrl("file:///android_asset/" + file);
    }

    private String readFileContent(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) return null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
