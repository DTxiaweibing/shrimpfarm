package com.shrimpfarm.app;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.shrimpfarm.app.BaseActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

public class BannerPageActivity extends BaseActivity {

    @Override
    protected int getCurrentNavId() {
        return 0;
    }

    private WebView webView;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_banner_page);

        webView = findViewById(R.id.web_view);
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
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setAllowFileAccess(false);

        String filePath = getIntent().getStringExtra("file_path");
        String baseUrl = getIntent().getStringExtra("base_url");
        if (filePath != null) {
            if (!filePath.startsWith(getFilesDir().getAbsolutePath()) && !filePath.startsWith(getCacheDir().getAbsolutePath())) {
                Log.w("BannerPage", "blocked file path traversal: " + filePath);
            } else {
                String content = readFileContent(filePath);
                if (content != null) {
                    webView.loadDataWithBaseURL(baseUrl != null ? baseUrl : null, content, "text/html", "UTF-8", null);
                    return;
                }
            }
        }
        // 缓存文件不存在或损坏，直接加载在线页面
        if (baseUrl != null) {
            webView.loadUrl(baseUrl);
        }
    }

    private String readFileContent(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) return null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
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
