package com.pfd.metricstool;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

/**
 * Shows the HUD as a system overlay window (SYSTEM_ALERT_WINDOW, grant via
 * "adb shell appops set com.pfd.metricstool SYSTEM_ALERT_WINDOW allow").
 * The YVR shell places overlay windows itself; yaw/pitch shift the HUD inside that surface.
 */
public final class OverlayController {
    private static final String TAG = "PFDMetrics";
    private final Context ctx;
    private final Settings settings;
    private final MetricStore store;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private OverlayView view;
    private String state = "aus";
    private VrHudController vr;

    public OverlayController(Context ctx, Settings settings, MetricStore store) {
        this.ctx = ctx;
        this.settings = settings;
        this.store = store;
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        if (VrHudController.available(ctx)) vr = new VrHudController(ctx, settings, store);
    }

    public boolean vrAvailable() { return vr != null; }

    public String state() {
        if (!settings.serviceEnabled()) return "aus";
        if (settings.overlay() && !canDraw()) return "keine Overlay-Berechtigung";
        if (settings.overlay() && "vr".equals(settings.hudMode()) && vr != null) return vr.state();
        return state;
    }

    public boolean canDraw() { return android.provider.Settings.canDrawOverlays(ctx); }

    public void sync() { main.post(this::syncOnMain); }

    private void syncOnMain() {
        boolean want = settings.serviceEnabled() && settings.overlay();
        boolean useVr = vr != null && "vr".equals(settings.hudMode());
        if (vr != null) vr.sync(want && useVr && canDraw());
        if (useVr) { removeView(); state = want ? (canDraw() ? "VR" : "keine Overlay-Berechtigung") : "aus"; return; }
        if (want && !canDraw()) { state = "keine Overlay-Berechtigung"; removeView(); return; }
        if (!want) { removeView(); state = "aus"; return; }
        WindowManager.LayoutParams lp = params();
        try {
            if (view == null) {
                view = new OverlayView(ctx, store, settings);
                wm.addView(view, lp);
                Log.i(TAG, "Overlay attached");
            } else {
                wm.updateViewLayout(view, lp);
                view.requestLayout();
                view.invalidate();
            }
            state = "aktiv";
        } catch (Exception e) {
            state = "Fehler: " + e.getMessage();
            Log.w(TAG, "Overlay failed", e);
            view = null;
        }
    }

    private WindowManager.LayoutParams params() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        lp.setTitle("PFD Metrics HUD");
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        // ±45° yaw/pitch moves the HUD to the edge of the surface.
        lp.x = (int) (settings.yaw() / 45f * dm.widthPixels * 0.5f);
        lp.y = (int) (-settings.pitch() / 45f * dm.heightPixels * 0.5f);
        return lp;
    }

    private void removeView() {
        if (view != null) {
            try { wm.removeView(view); } catch (Exception ignored) { }
            view = null;
            Log.i(TAG, "Overlay removed");
        }
    }

    public void destroy() {
        main.post(this::removeView);
        if (vr != null) vr.destroy();
    }
}
