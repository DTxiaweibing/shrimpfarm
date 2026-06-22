package com.shrimpfarm.app;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

public class ExpertActivity extends AppCompatActivity {

    private static final String ONLINE_URL = "https://dtxiaweibing.github.io/TIMU/promo/zhaomu.html";
    private static final String CACHE_NAME = "zhaomu.html";

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expert);

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
        setupWebView();
        loadPage();
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
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
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
