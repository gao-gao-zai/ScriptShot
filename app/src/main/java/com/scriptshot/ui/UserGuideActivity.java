package com.scriptshot.ui;

import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.scriptshot.R;

/**
 * Displays the locally bundled Chinese user guide inside a WebView.
 */
public class UserGuideActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_guide);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_user_guide);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        webView = findViewById(R.id.webview_user_guide);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true); // Enable JS for dark mode CSS injection
        settings.setDomStorageEnabled(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // Apply dark mode to WebView if system is in dark mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            settings.setForceDark(isDarkMode() ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (isDarkMode()) {
                    injectDarkModeStyles(view);
                }
            }
        });

        webView.loadUrl("file:///android_asset/user_guide_zh.html");
    }

    private boolean isDarkMode() {
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    private void injectDarkModeStyles(WebView webView) {
        String darkModeCSS = "javascript:(function() {" +
            "var style = document.createElement('style');" +
            "style.innerHTML = '" +
            "html body { background-color: #1C1B1F !important; color: #E6E1E5 !important; }' +" +
            "'html body h1, html body h2, html body h3, html body h4, html body h5, html body h6 { color: #E6E1E5 !important; }' +" +
            "'html body strong { color: #E6E1E5 !important; }' +" +
            "'html body a { color: #D0BCFF !important; }' +" +
            "'html body a:hover { color: #E8DDFF !important; }' +" +
            "'html body blockquote { color: #CAC4D0 !important; background-color: #2D2C31 !important; border-left-color: #49454F !important; }' +" +
            "'html body code { color: #E6E1E5 !important; background-color: #2D2C31 !important; }' +" +
            "'html body pre[class*=language-], code[class*=language-] { color: #E6E1E5 !important; background-color: #2D2C31 !important; }' +" +
            "'html body pre { background-color: #2D2C31 !important; border-color: #49454F !important; }' +" +
            "'html body table th { color: #E6E1E5 !important; }' +" +
            "'html body table td, html body table th { border-color: #49454F !important; }' +" +
            "'html body hr { background-color: #49454F !important; }' +" +
            "'html body kbd { color: #E6E1E5 !important; background-color: #2D2C31 !important; border-color: #49454F !important; }' +" +
            "'html body del { color: #CAC4D0 !important; }' +" +
            "'.token.comment, .token.blockquote { color: #9E9E9E !important; }' +" +
            "'.token.keyword, .token.builtin, .token.important, .token.operator, .token.rule { color: #FFB74D !important; }' +" +
            "'.token.string, .token.attr-value, .token.regex, .token.url { color: #80CBC4 !important; }' +" +
            "';" +
            "document.head.appendChild(style);" +
            "})();";
        webView.loadUrl(darkModeCSS);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
