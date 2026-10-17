package com.pfd.metricstool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** The in-headset HUD: stat list (two columns) plus optional graphs. Size follows content × scale. */
public final class OverlayView extends View implements MetricStore.Listener {
    private final MetricStore store;
    private final Settings settings;
    private final GraphRenderer graph = new GraphRenderer();
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    { frame.setStyle(Paint.Style.STROKE); frame.setColor(0xFFFF00FF); }
    private float baseText = 28f;
    private boolean fillParent = false;      // VR widget mode: view fills the virtual display
    private Runnable sizeListener;

    public void setFillParent(boolean v) { fillParent = v; }

    private float bgAlpha = -1f;              // 0..1 set by VrHudController; <0 = derive from settings
    private boolean backgroundOnly = false;   // backing layer: draw just the panel background
    public void setBgAlpha(float a) { bgAlpha = a; postInvalidate(); }
    public void setBackgroundOnly(boolean v) { backgroundOnly = v; }
    public void setSizeListener(Runnable r) { sizeListener = r; }

    /** UI "100 %" corresponds to half the original drawing size (user calibration on the PFD). */
    public static final float SCALE_BASE = 0.5f;
    /** Virtual-display pixels per metre of the YVR widget (kWorldDPIRatio = 1/360 in libvrsystemui). */
    public static final float PX_PER_METRE = 360f;

    private float hudScale() { return settings.scale() * SCALE_BASE; }

    /** Panel size in px at the current scale (used to size the VR canvas). */
    public int[] contentSize() { return measureContent(baseText * hudScale()); }

    public OverlayView(Context ctx, MetricStore store, Settings settings) {
        super(ctx);
        this.store = store;
        this.settings = settings;
        bg.setColor(0xB0101318);
        label.setColor(0xFFB0BEC5);
        label.setTypeface(Typeface.MONOSPACE);
        value.setColor(Color.WHITE);
        value.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        title.setColor(0xFF3DDC84);
        title.setTypeface(Typeface.DEFAULT_BOLD);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); store.addListener(this); }
    @Override protected void onDetachedFromWindow() { store.removeListener(this); super.onDetachedFromWindow(); }

    @Override public void onSample(MetricStore s) {
        post(this::requestLayoutIfNeeded);
        postInvalidate();
    }

    private int lastW, lastH;
    private String lastShape = "";
    private void requestLayoutIfNeeded() {
        String shape = enabledStats().size() + "/" + enabledGraphs().size();
        if (!shape.equals(lastShape)) {
            boolean first = lastShape.isEmpty();
            lastShape = shape;
            if (!first && sizeListener != null) sizeListener.run();
        }
        if (fillParent) return;
        int[] wh = measureContent(textSize());
        if (wh[0] != lastW || wh[1] != lastH) requestLayout();
    }

    private float textSize() {
        if (fillParent) return baseText * hudScale();
        return baseText * hudScale() / Math.max(0.25f, settings.distance());
    }

    private List<Integer> enabledStats() {
        List<Integer> l = new ArrayList<>();
        if (!settings.stats()) return l;
        for (Metric m : Metric.ALL) if (settings.statEnabled(m.id)) l.add(m.id);
        return l;
    }

    private List<Integer> enabledGraphs() {
        List<Integer> l = new ArrayList<>();
        if (!settings.graphs()) return l;
        for (Metric m : Metric.ALL) if (settings.graphEnabled(m.id)) l.add(m.id);
        return l;
    }

    private int[] measureContent(float ts) {
        int stats = enabledStats().size();
        int graphs = enabledGraphs().size();
        int cols = settings.columns();
        int rows = (stats + cols - 1) / cols;
        float w = ts * 10f * cols;
        float h = ts * 1.6f + rows * ts * 1.25f + graphs * ts * 4.2f + ts * 0.6f;
        return new int[]{(int) w, (int) h};
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        if (fillParent) { super.onMeasure(wSpec, hSpec); return; }
        int[] wh = measureContent(textSize());
        lastW = wh[0]; lastH = wh[1];
        setMeasuredDimension(wh[0], wh[1]);
    }

    @Override protected void onDraw(Canvas c) {
        float ts = textSize();
        float pad = ts * 0.5f;
        int[] pwh = fillParent ? measureContent(ts) : new int[]{getWidth(), getHeight()};
        int pw = pwh[0], ph = pwh[1];
        if (fillParent) {
            // The VR widget is a large transparent head-locked canvas; yaw/pitch move the panel inside it.
            float d = VrHudController.distanceMetres(settings) * PX_PER_METRE;
            float cx = getWidth() / 2f + (float) Math.tan(Math.toRadians(settings.yaw())) * d;
            float cy = getHeight() / 2f - (float) Math.tan(Math.toRadians(settings.pitch())) * d;
            float left = Math.max(0, Math.min(getWidth() - pw, cx - pw / 2f));
            float top = Math.max(0, Math.min(getHeight() - ph, cy - ph / 2f));
            if (settings.showFrame()) {
                // Calibration aid: outline of the whole VR widget surface plus a centre cross.
                frame.setStrokeWidth(4f);
                c.drawRect(2, 2, getWidth() - 2, getHeight() - 2, frame);
                c.drawLine(getWidth() / 2f - 30, getHeight() / 2f, getWidth() / 2f + 30, getHeight() / 2f, frame);
                c.drawLine(getWidth() / 2f, getHeight() / 2f - 30, getWidth() / 2f, getHeight() / 2f + 30, frame);
            }
            c.translate(left, top);
        }
        rect.set(0, 0, pw, ph);
        // Slider 0..100 maps to 35..100 % alpha: even the lightest setting keeps the text readable.
        int alpha = bgAlpha >= 0 ? Math.round(255f * Math.min(1f, bgAlpha))
                : Math.round(255f * (0.35f + 0.65f * settings.bgOpacity() / 100f));
        bg.setColor((alpha << 24) | 0x0A0C10);
        if (backgroundOnly) { c.drawRoundRect(rect, ts * 0.6f, ts * 0.6f, bg); return; }
        graph.setBackgroundAlpha(Math.round(alpha * 0.8f));
        // Dark outline behind all text for contrast against bright scenes.
        float sh = Math.max(1.5f, ts * 0.12f);
        label.setShadowLayer(sh, 0, 0, 0xFF000000);
        value.setShadowLayer(sh, 0, 0, 0xFF000000);
        title.setShadowLayer(sh, 0, 0, 0xFF000000);
        c.drawRoundRect(rect, ts * 0.6f, ts * 0.6f, bg);

        title.setTextSize(ts * 0.9f);
        String app = store.foregroundApp();
        boolean live = System.currentTimeMillis() - store.lastRuntimeUpdate() < 3000;
        String header = (live ? "● " : "○ ") + (app.isEmpty() ? "PFD Metrics" : shortName(app));
        title.setColor(live ? 0xFF3DDC84 : 0xFFFF6B6B);
        c.drawText(header, pad, ts * 1.1f, title);

        label.setTextSize(ts * 0.8f);
        value.setTextSize(ts * 0.9f);
        List<Integer> stats = enabledStats();
        int cols = settings.columns();
        float colW = (pw - pad * 2) / cols;
        float y = ts * 1.6f + ts;
        for (int i = 0; i < stats.size(); i++) {
            Metric m = Metric.ALL[stats.get(i)];
            float x = pad + (i % cols) * colW;
            if (i > 0 && i % cols == 0) y += ts * 1.25f;
            double v = store.value(m.id);
            c.drawText(m.label, x, y, label);
            String vs = m.format(v) + (m.unit.isEmpty() || Double.isNaN(v) ? "" : m.unit);
            value.setColor(colorForValue(m.id, v));
            c.drawText(vs, x + colW - pad - value.measureText(vs), y, value);
        }
        y += ts * 0.6f;
        double refresh = store.value(Metric.DISPLAY_REFRESH_RATE);
        for (int id : enabledGraphs()) {
            rect.set(pad, y, pw - pad, y + ts * 3.9f);
            graph.draw(c, rect, id, store, ts, refresh);
            y += ts * 4.2f;
        }
    }

    private int colorForValue(int metric, double v) {
        if (Double.isNaN(v)) return 0xFF78909C;
        if (metric == Metric.AVERAGE_FRAME_RATE) {
            double target = store.value(Metric.DISPLAY_REFRESH_RATE);
            if (!Double.isNaN(target)) {
                if (v >= target * 0.97) return 0xFF3DDC84;
                if (v >= target * 0.85) return 0xFFFECA57;
                return 0xFFFF6B6B;
            }
        }
        if (metric == Metric.STALE_FRAME_COUNT && v > 0) return 0xFFFF6B6B;
        if ((metric == Metric.CPU_UTILIZATION || metric == Metric.GPU_UTILIZATION) && v > 90) return 0xFFFF6B6B;
        return Color.WHITE;
    }

    private static String shortName(String pkg) {
        int d = pkg.lastIndexOf('.');
        String s = d >= 0 && d < pkg.length() - 1 ? pkg.substring(d + 1) : pkg;
        return s.length() > 22 ? s.substring(0, 22) : s;
    }
}
