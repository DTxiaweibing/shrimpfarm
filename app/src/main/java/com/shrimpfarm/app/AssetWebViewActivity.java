package com.shrimpfarm.app;

import android.content.res.AssetManager;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.shrimpfarm.app.BaseActivity;
import com.shrimpfarm.app.utils.LocaleHelper;
import androidx.appcompat.widget.Toolbar;

public class AssetWebViewActivity extends BaseActivity {

    @Override
    protected int getCurrentNavId() {
        return 0;
    }

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_FILE = "file";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asset_webview);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String file = getIntent().getStringExtra(EXTRA_FILE);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title != null ? title : "");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        WebView webView = findViewById(R.id.web_view);
        webView.setWebViewClient(new WebViewClient());
        webView.getSettings().setJavaScriptEnabled(false);
        if (file != null) {
            webView.loadUrl("file:///android_asset/" + pickLangFile(file));
        }
    }

    private String pickLangFile(String file) {
        String lang = LocaleHelper.getSavedLang(this);
        if ("en".equals(lang) && file.endsWith(".html")) {
            String enFile = file.replace(".html", "-en.html");
            try {
                AssetManager am = getAssets();
                am.open(enFile);
                return enFile;
            } catch (Exception ignored) {
            }
        }
        return file;
    }
}
