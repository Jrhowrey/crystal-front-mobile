package com.crystalfront.mobilecommand;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Choreographer;
import android.view.Display;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity implements Choreographer.FrameCallback {
    private WebView webView;
    private static boolean nativeLoaded = false;

    static {
        try {
            System.loadLibrary("crystalfront_native");
            nativeLoaded = true;
        } catch (Throwable ignored) {
            nativeLoaded = false;
        }
    }

    private native void nativeInitEngine(long targetWorkDurationNs);
    private native void nativeReportWorkDurationNs(long actualDurationNs);

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Guarantee SurfaceFlinger Direct HWC DEVICE Overlay Promotion
        getWindow().setFormat(PixelFormat.OPAQUE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 2. Unlock LTPO 90Hz / 120Hz / 144Hz Display Mode
        float maxRefreshHz = 60.0f;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Display defaultDisplay = getWindowManager().getDefaultDisplay();
                if (defaultDisplay != null) {
                    Display.Mode[] modes = defaultDisplay.getSupportedModes();
                    Display.Mode highRateMode = null;
                    for (Display.Mode m : modes) {
                        if (highRateMode == null || m.getRefreshRate() > highRateMode.getRefreshRate()) {
                            highRateMode = m;
                        }
                    }
                    if (highRateMode != null && highRateMode.getRefreshRate() > 60.0f) {
                        maxRefreshHz = highRateMode.getRefreshRate();
                        WindowManager.LayoutParams params = getWindow().getAttributes();
                        params.preferredDisplayModeId = highRateMode.getModeId();
                        getWindow().setAttributes(params);
                    }
                }
            } catch (Throwable ignored) {}
        }
        long targetFrameIntervalNs = (long) (1000000000.0f / maxRefreshHz);

        // 3. Report AOSP GameManager Gameplay State to lock Snapdragon Adreno / ARM Mali GPU clocks
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                Object gameManager = getSystemService("game");
                if (gameManager != null) {
                    Class<?> gameStateClass = Class.forName("android.app.GameState");
                    Object stateObj = gameStateClass.getConstructor(boolean.class, int.class).newInstance(false, 2);
                    gameManager.getClass().getMethod("setGameState", gameStateClass).invoke(gameManager, stateObj);
                }
            } catch (Throwable ignored) {}
        }

        if (nativeLoaded) {
            try {
                nativeInitEngine(targetFrameIntervalNs);
            } catch (Throwable ignored) {}
        }

        // 4. Configure Hardware-Accelerated WebGL 2.0 Surface & Multi-Touch Pinch-to-Zoom
        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setContentView(webView);
        enableImmersiveFullscreen();

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        try {
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
        } catch (Throwable ignored) {}
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.addJavascriptInterface(new AdpfJsBridge(), "AndroidADPFBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/index.html");

        // 5. Register ADPF Thermal Status Listener for Dynamic SGSR Resolution Scaling
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    pm.addThermalStatusListener(new PowerManager.OnThermalStatusChangedListener() {
                        @Override
                        public void onThermalStatusChanged(int status) {
                            webView.post(new Runnable() {
                                @Override
                                public void run() {
                                    webView.evaluateJavascript(
                                        "window.__onAndroidThermalStatus && window.__onAndroidThermalStatus(" + status + ");",
                                        null
                                    );
                                }
                            });
                        }
                    });
                }
            } catch (Throwable ignored) {}
        }

        Choreographer.getInstance().postFrameCallback(this);
    }

    public class AdpfJsBridge {
        @JavascriptInterface
        public void reportWorkDurationNs(long durationNs) {
            if (nativeLoaded && durationNs > 0L) {
                try {
                    nativeReportWorkDurationNs(durationNs);
                } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!isFinishing() && !isDestroyed()) {
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    private void enableImmersiveFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enableImmersiveFullscreen();
    }

    @Override
    public void onBackPressed() {
        webView.evaluateJavascript("document.getElementById('btnOpenTitleScreen')?.click();", null);
    }
}
