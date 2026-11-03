package com.crystalhold.td;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebViewAssetLoader;

/**
 * Hosts the game (assets/www) in a full-screen WebView and exposes a small
 * JavaScript bridge, window.AndroidApp, for rewarded/interstitial ads and the
 * "remove ads" purchase.
 */
public class MainActivity extends ComponentActivity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/assets/www/index.html";

    private WebView web;
    private AdsManager ads;
    private BillingManager billing;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#15100B"));
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (HOST.equals(uri.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "AndroidApp");
        setContentView(web);
        hideSystemBars();
        web.loadUrl(START_URL);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (web == null) {
                    finish();
                    return;
                }
                web.evaluateJavascript("(window.__appBack&&window.__appBack())?'1':'0'", value -> {
                    if (value == null || !value.contains("1")) finish();
                });
            }
        });

        billing = new BillingManager(this, this::js);
        ads = new AdsManager(this, this::js, () -> billing.isAdFree());
        ads.start();
    }

    /** Runs JavaScript in the game page on the UI thread. */
    void js(String code) {
        runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript(code, null);
        });
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.hide(WindowInsetsCompat.Type.systemBars());
        c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onPause() {
        if (web != null) web.evaluateJavascript("window.__appPause&&window.__appPause()", null);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (billing != null) billing.refresh();
    }

    @Override
    protected void onDestroy() {
        if (billing != null) billing.destroy();
        if (web != null) {
            web.removeJavascriptInterface("AndroidApp");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    /** Methods callable from the game as window.AndroidApp.xxx(). They run on a background thread. */
    private class Bridge {
        @JavascriptInterface
        public boolean isAdFree() {
            return billing != null && billing.isAdFree();
        }

        @JavascriptInterface
        public String removeAdsPrice() {
            return billing != null ? billing.price() : "";
        }

        @JavascriptInterface
        public void rewarded(String tag) {
            runOnUiThread(() -> ads.showRewarded(tag));
        }

        @JavascriptInterface
        public void interstitial() {
            runOnUiThread(() -> ads.showInterstitial());
        }

        @JavascriptInterface
        public void buyRemoveAds() {
            runOnUiThread(() -> billing.buyRemoveAds());
        }

        @JavascriptInterface
        public boolean privacyRequired() {
            return ads != null && ads.privacyOptionsRequired();
        }

        @JavascriptInterface
        public void privacyOptions() {
            runOnUiThread(() -> ads.showPrivacyOptions());
        }
    }

    /** Small functional interfaces shared by the managers. */
    interface JsSink {
        void js(String code);
    }

    interface AdFreeCheck {
        boolean isAdFree();
    }

    static String quote(String s) {
        if (s == null) return "''";
        StringBuilder b = new StringBuilder("'");
        for (char ch : s.toCharArray()) {
            if (ch == '\'' || ch == '\\') b.append('\\').append(ch);
            else if (ch == '\n' || ch == '\r') b.append(' ');
            else if (ch == '<' || ch == '>') b.append(' ');
            else b.append(ch);
        }
        return b.append("'").toString();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        View v = getWindow().getDecorView();
        v.post(this::hideSystemBars);
    }
}
