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
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
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

public class HelpActivity extends AppCompatActivity {
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
        adapter = new QaListAdapter(questions, question -> {
            needRefresh = true;  // 进入详情页，返回时自动刷新
            Intent intent = new Intent(HelpActivity.this, QaDetailActivity.class);
            intent.putExtra("question_id", question.id);
            startActivity(intent);
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

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        tabLayout.addTab(tabLayout.newTab().setText("使用帮助"));
        tabLayout.addTab(tabLayout.newTab().setText("问答社区"));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    webView.setVisibility(View.VISIBLE);
                    qaRecyclerView.setVisibility(View.GONE);
                    fabPost.setVisibility(View.GONE);
                    swipeRefresh.setVisibility(View.GONE);
                } else {
                    webView.setVisibility(View.GONE);
                    qaRecyclerView.setVisibility(View.VISIBLE);
                    swipeRefresh.setVisibility(View.VISIBLE);
                    fabPost.setVisibility(View.VISIBLE);
                    if (questions.isEmpty()) {
                        loadQuestions();
                    }
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        fabPost.setOnClickListener(v -> {
            if (!api.isLoggedIn()) {
                Toast.makeText(this, "请先登录后再发布问题", Toast.LENGTH_SHORT).show();
                return;
            }
            postQuestionLauncher.launch(new Intent(this, QaPostActivity.class));
        });
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
        webView.loadUrl("file:///android_asset/" + CACHE_NAME);
    }

    private String readFileContent(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) return null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
