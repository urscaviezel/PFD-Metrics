package com.pfd.metricstool;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The requesting UID must be TOP when logd asks LogcatManagerService for consent. */
public final class LogConsentActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView status;
    private boolean requested;
    private long requestedAt;
    private YvrPanelLifetime panelLifetime;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        panelLifetime = new YvrPanelLifetime(this, getDisplay().getDisplayId());
        panelLifetime.connect();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(32, 32, 32, 32);
        box.setBackgroundColor(0xff101318);
        status = new TextView(this);
        status.setTextColor(0xffffffff);
        status.setTextSize(20);
        status.setText(I18n.t(this, "consent_intro"));
        box.addView(status);
        Button retry = new Button(this);
        retry.setText(I18n.t(this, "consent_retry"));
        retry.setOnClickListener(v -> { requested = false; requestWhenFocused(); });
        box.addView(retry);
        Button back = new Button(this);
        back.setText(I18n.t(this, "consent_back"));
        back.setOnClickListener(v -> returnToControls());
        box.addView(back);
        setContentView(box);
        if (checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED) {
            new Settings(this).setServiceEnabled(true);
            MetricsService.start(this);
        } else {
            status.setText(I18n.t(this, "consent_permission"));
        }
    }

    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) ui.postDelayed(this::requestWhenFocused, 500);
    }

    private void requestWhenFocused() {
        if (isFinishing() || !hasWindowFocus() || requested
                || checkSelfPermission(Manifest.permission.READ_LOGS) != PackageManager.PERMISSION_GRANTED) return;
        MetricsService svc = MetricsService.instance();
        if (svc == null || svc.logSources() == null) {
            ui.postDelayed(this::requestWhenFocused, 250);
            return;
        }
        requested = true;
        requestedAt = android.os.SystemClock.elapsedRealtime();
        status.setText(I18n.t(this, "consent_intro"));
        svc.logSources().restartLogcat();
        ui.removeCallbacks(check);
        ui.postDelayed(check, 1000);
    }

    private final Runnable check = new Runnable() {
        @Override public void run() {
            MetricsService svc = MetricsService.instance();
            if (svc != null && svc.logSources().hasAuthorizedReader()) {
                status.setText(I18n.t(LogConsentActivity.this, "consent_ok"));
                ui.postDelayed(() -> { if (!isFinishing()) returnToControls(); }, 1200);
                return;
            }
            if (android.os.SystemClock.elapsedRealtime() - requestedAt > 65000) {
                status.setText(I18n.t(LogConsentActivity.this, "consent_wait"));
                return;
            }
            ui.postDelayed(this, 1000);
        }
    };

    private void returnToControls() {
        if (panelLifetime.detachOwnPackage()) {
            finish();
        } else {
            status.setText("Live logs may be active, but keeping the HUD alive failed: "
                    + panelLifetime.error() + "\nPlease keep this page open for diagnosis.");
        }
    }

    @Override public void onBackPressed() { returnToControls(); }

    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        panelLifetime.close();
        super.onDestroy();
    }
}
