package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/**
 * Plays a web game full screen: a TV has no browser of its own on Google TV, so the Games channel's web titles
 * (AirConsole, cloud gaming sites, party games played from phones) open here. Cookies are kept, so a sign-in
 * stays signed in. Back goes back a page, and leaves from the first one.
 */
public class WebPlayActivity extends Activity {
    static final String EXTRA_URL = "url";
    static final String EXTRA_TITLE = "title";

    private WebView web;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null || !url.startsWith("https://")) {
            finish();
            return;
        }
        try {
            web = new WebView(this);
        } catch (RuntimeException e) {
            // No WebView installed (or it's updating).
            Toast.makeText(this, R.string.games_no_web, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        // Look like Chrome rather than an embedded view: some cloud gaming sites turn embedded browsers away.
        s.setUserAgentString(s.getUserAgentString().replace("; wv", "").replaceAll(" Version/\\d+\\.\\d+", ""));
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(web);
        web.requestFocus();
        web.loadUrl(url);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (web != null && (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) && web.canGoBack()) {
            web.goBack();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
