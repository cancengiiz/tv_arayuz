package tv.seans.launcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Seans ana ekranı: arayüz assets/index.html içindeki web sayfasıdır.
 * Bu aktivite onu tam ekran bir WebView'da gösterir, kumanda tuşlarını sayfaya iletir
 * ve SeansNative köprüsüyle uygulama listesi / başlatma işlerini yapar.
 */
public class MainActivity extends Activity {

    private WebView web;
    private boolean appsChanged = false;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bridge.clearCache();
            appsChanged = true;
        }
    };

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);

        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(this), "SeansNative");
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);

        setContentView(web);
        hideSystemBars();
        web.loadUrl("file:///android_asset/index.html");
        web.requestFocus();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(packageReceiver, f);
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (appsChanged) {
            appsChanged = false;
            web.reload();
        }
        web.requestFocus();
    }

    /** Uygulama zaten açıkken Home tuşuna basılırsa: alt şeride dön. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        js("window.seansHome && window.seansHome()");
    }

    /**
     * Geri, Menü ve OK tuşlarını doğrudan sayfaya iletir.
     * Yön tuşları WebView tarafından zaten ArrowLeft/Right/Up/Down olarak gönderilir.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        String key = null;
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) key = "back";
        else if (code == KeyEvent.KEYCODE_MENU) key = "menu";
        else if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER) key = "ok";

        if (key != null) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                js("window.seansKey && window.seansKey('" + key + "')");
            }
            return true; // Ana ekran asla kapanmaz
        }
        return super.dispatchKeyEvent(e);
    }

    private void js(String code) {
        if (web != null) web.evaluateJavascript(code, null);
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(packageReceiver); } catch (Exception ignored) { }
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
