package com.pfd.metricstool;

import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

/**
 * Shows the HUD inside immersive VR on Play for Dream / YVR OS.
 *
 * Found by reverse engineering com.yvr.vrsystemui (REA/JADX): VrSystemUI exports
 * content://com.yvr.vrsystemui.ToastContentProvider. call("yvr_common_toast_start_private", extras)
 * creates a head-locked widget in the compositor backed by a PUBLIC virtual display
 * ("@PresentationView@from_systemui…", flags PUBLIC|OWN_CONTENT_ONLY|DESTROY_CONTENT_ON_REMOVAL)
 * and returns its display id. Extras: width/height (dp), translationX/Y/Z (float, world units,
 * ~360 per metre, default z=-500), surface_time (ms, <=0 = until stopped).
 * We render our own view onto that display; stop with "yvr_common_toast_stop_private" +
 * extra identityHashCode. VrSystemUI auto-releases the widget if our process dies.
 */
public final class VrHudController implements DisplayManager.DisplayListener {
    private static final String TAG = "PFDMetrics";
    private static final Uri PROVIDER = Uri.parse("content://com.yvr.vrsystemui.ToastContentProvider");
    private static final float WORLD_UNITS_PER_M = 360f;

    private final Context ctx;
    private final Settings settings;
    private final MetricStore store;
    private final DisplayManager dm;
    private final Handler main = new Handler(Looper.getMainLooper());

    private int handle = 0;
    private int displayId = -1;
    private WindowManager wm;
    private OverlayView view;
    /** Extra background-only widgets stacked behind the HUD (see layerAlpha). */
    private final java.util.List<HudLayer> backing = new java.util.ArrayList<>();
    /**
     * Measured on device: a fully opaque pixel of a YVR toast widget only covers ~54 % of the scene
     * (the compositor blends the widget layer semi-transparently). Stacking n identical background
     * layers gives 1-(1-0.54*a)^n coverage, so up to 3 layers reach ~90 %.
     */
    private static final float LAYER_COVERAGE = 0.54f;
    private int widthDp, heightDp;
    private String placementKey = "";
    private String state = "aus";
    private int failures = 0;
    private boolean wanted;

    public VrHudController(Context ctx, Settings settings, MetricStore store) {
        this.ctx = ctx;
        this.settings = settings;
        this.store = store;
        dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
        dm.registerDisplayListener(this, main);
    }

    public String state() { return state; }
    public int displayId() { return displayId; }

    public static boolean available(Context ctx) {
        return ctx.getPackageManager().resolveContentProvider("com.yvr.vrsystemui.ToastContentProvider", 0) != null;
    }

    /** Must be called on the main thread. */
    public void sync(boolean want) {
        wanted = want;
        if (!want) { stop(); state = "aus"; return; }
        if (view == null) {
            // measure the content with a detached view to size the widget
            view = new OverlayView(ctx, store, settings);
            view.setSizeListener(this::onContentSizeChanged);
        }
        // The widget itself always sits straight ahead (translationX/Y of the YVR toast are not usable
        // for positioning, see RE notes). It is a transparent canvas large enough for every yaw/pitch;
        // OverlayView draws the panel at the requested angle inside it, so moving needs no re-creation.
        int[] wh = view.contentSize();
        float d = distanceMetres(settings) * OverlayView.PX_PER_METRE;
        float wPx = wh[0] + 2f * (float) Math.tan(Math.toRadians(Settings.MAX_YAW)) * d;
        float hPx = wh[1] + 2f * (float) Math.tan(Math.toRadians(Settings.MAX_PITCH)) * d;
        wPx = Math.min(wPx, 2048f);   // stay within safe texture/swapchain size
        hPx = Math.min(hPx, 2048f);
        float density = ctx.getResources().getDisplayMetrics().density;
        int wDp = Math.max(80, Math.round(wPx / density));
        int hDp = Math.max(40, Math.round(hPx / density));
        float alpha = layerAlpha(settings.bgOpacity());
        String key = wDp + "x" + hDp + "|" + settings.distance() + "|" + settings.quadOffsetY();
        if (handle != 0 && key.equals(placementKey)) {
            view.setBgAlpha(alpha);
            for (HudLayer l : backing) if (l.view != null) l.view.setBgAlpha(alpha);
            return;
        }
        start(wDp, hDp, key, LAYERS, alpha);
    }

    /** Number of stacked widgets (HUD + background-only layers). Fixed, so the slider never re-creates the HUD. */
    static final int LAYERS = 3;

    /** Slider 0..100 → per-layer background alpha; the coverage of all layers rises smoothly from 30 % to the maximum. */
    static float layerAlpha(int level) {
        double max = 1 - Math.pow(1 - LAYER_COVERAGE, LAYERS);
        double target = 0.30 + (max - 0.30) * level / 100.0;
        double a = (1 - Math.pow(1 - target, 1.0 / LAYERS)) / LAYER_COVERAGE;
        return (float) Math.max(0, Math.min(1, a));
    }

    private Bundle placement(int wDp, int hDp, float z) {
        Bundle b = new Bundle();
        b.putInt("width", wDp);
        b.putInt("height", hDp);
        b.putFloat("translationX", 0f);
        b.putFloat("translationY", settings.quadOffsetY());
        b.putFloat("translationZ", z);
        b.putInt("surface_time", 0); // 0 = keep until stopped
        return b;
    }

    private WindowManager.LayoutParams windowParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.setTitle("PFD Metrics VR HUD");
        return lp;
    }

    /** Distance of the HUD plane in metres (setting 1.0 = 1.4 m, like the system toasts). */
    public static float distanceMetres(Settings s) { return Math.max(0.3f, s.distance()) * 1.4f; }

    private void onContentSizeChanged() {
        main.post(() -> { if (wanted) sync(true); });
    }

    private void start(int wDp, int hDp, String key, int layers, float alpha) {
        stop();
        try {
            float z = -distanceMetres(settings) * WORLD_UNITS_PER_M;
            Bundle r = ctx.getContentResolver().call(PROVIDER, "yvr_common_toast_start_private", null, placement(wDp, hDp, z));
            if (r == null) throw new IllegalStateException("provider returned null");
            handle = r.getInt("identityHashCode", 0);
            displayId = r.getInt("mDisplayId", -1);
            Display d = dm.getDisplay(displayId);
            if (d == null) throw new IllegalStateException("display " + displayId + " not found");
            Context dctx = ctx.createDisplayContext(d)
                    .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
            wm = dctx.getSystemService(WindowManager.class);
            view = new OverlayView(dctx, store, settings);
            view.setFillParent(true);
            view.setBgAlpha(alpha);
            view.setSizeListener(this::onContentSizeChanged);
            wm.addView(view, windowParams());
            widthDp = wDp; heightDp = hDp; placementKey = key;
            android.graphics.Point sz = new android.graphics.Point();
            d.getRealSize(sz);
            // On YVR the widget created first is drawn on top (observed in the v2.8.0 test), so the background-only
            // layers are created after the HUD and placed a few millimetres further away.
            for (int i = 1; i < layers; i++) {
                Bundle br = ctx.getContentResolver().call(PROVIDER, "yvr_common_toast_start_private", null, placement(wDp, hDp, z - 1f * i));
                if (br == null) break;
                HudLayer l = new HudLayer();
                l.handle = br.getInt("identityHashCode", 0);
                l.displayId = br.getInt("mDisplayId", -1);
                Display bd = dm.getDisplay(l.displayId);
                if (bd == null) { backing.add(l); continue; }
                Context bctx = ctx.createDisplayContext(bd)
                        .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
                l.wm = bctx.getSystemService(WindowManager.class);
                l.view = new OverlayView(bctx, store, settings);
                l.view.setFillParent(true);
                l.view.setBackgroundOnly(true);
                l.view.setBgAlpha(alpha);
                l.wm.addView(l.view, windowParams());
                backing.add(l);
            }
            state = "aktiv (VR-Widget, Display " + displayId + ", " + sz.x + "x" + sz.y + " px)";
            failures = 0;
            Log.i(TAG, "VR HUD started handle=" + handle + " display=" + displayId + " size=" + sz + " result=" + r);
        } catch (Throwable e) {
            failures++;
            state = "Fehler: " + e;
            Log.w(TAG, "VR HUD start failed", e);
            stop();
            if (wanted && failures < 5) main.postDelayed(() -> { if (wanted) sync(true); }, 3000L * failures);
        }
    }

    private void stopToast(int h) {
        if (h == 0) return;
        try {
            Bundle b = new Bundle();
            b.putInt("identityHashCode", h);
            ctx.getContentResolver().call(PROVIDER, "yvr_common_toast_stop_private", null, b);
        } catch (Throwable e) {
            Log.w(TAG, "VR HUD stop failed: " + e);
        }
    }

    private void stop() {
        for (HudLayer l : backing) {
            if (l.view != null && l.wm != null) try { l.wm.removeViewImmediate(l.view); } catch (Throwable ignored) { }
            stopToast(l.handle);
        }
        backing.clear();
        if (view != null && view.getParent() != null && wm != null) {
            try { wm.removeViewImmediate(view); } catch (Throwable ignored) { }
        }
        if (handle != 0) {
            try {
                Bundle b = new Bundle();
                b.putInt("identityHashCode", handle);
                ctx.getContentResolver().call(PROVIDER, "yvr_common_toast_stop_private", null, b);
            } catch (Throwable e) {
                Log.w(TAG, "VR HUD stop failed: " + e);
            }
        }
        handle = 0;
        displayId = -1;
        wm = null;
        placementKey = "";
    }

    public void destroy() {
        main.post(() -> { wanted = false; stop(); dm.unregisterDisplayListener(this); });
    }

    @Override public void onDisplayAdded(int id) { }
    @Override public void onDisplayChanged(int id) { }
    @Override public void onDisplayRemoved(int id) {
        boolean ours = id == displayId;
        for (HudLayer l : backing) if (l.displayId == id) ours = true;
        if (ours) {
            stop();
            Log.i(TAG, "VR HUD display removed by system; re-creating");
            handle = 0; displayId = -1; wm = null; placementKey = "";
            if (view != null && view.getParent() == null) view = null;
            if (wanted) main.postDelayed(() -> { if (wanted) sync(true); }, 1000);
        }
    }
}
