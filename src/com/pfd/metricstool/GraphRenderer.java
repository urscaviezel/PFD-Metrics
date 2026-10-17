package com.pfd.metricstool;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Draws a single metric history as a line graph with min/avg/max guide. Shared by HUD and app UI. */
public final class GraphRenderer {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint target = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] buf = new float[MetricStore.HISTORY];
    private final Path path = new Path();
    private final Path area = new Path();

    public GraphRenderer() {
        bg.setColor(0x66000000);
        grid.setColor(0x33FFFFFF);
        grid.setStrokeWidth(1f);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        text.setColor(Color.WHITE);
        target.setColor(0x88FFFFFF);
        target.setStyle(Paint.Style.STROKE);
        target.setPathEffect(new android.graphics.DashPathEffect(new float[]{8, 6}, 0));
    }

    /** Alpha (0–255) of the graph background box; the HUD derives it from the opacity setting. */
    public void setBackgroundAlpha(int a) { bg.setColor((Math.max(0, Math.min(255, a)) << 24)); }

    public static int colorFor(int metric) {
        switch (metric) {
            case Metric.AVERAGE_FRAME_RATE: case Metric.COMPOSITOR_FPS: return 0xFF3DDC84;
            case Metric.GPU_UTILIZATION: case Metric.APP_GPU_TIME_US: return 0xFFFF9F43;
            case Metric.CPU_UTILIZATION: case Metric.APP_CPU_TIME_US: return 0xFF54A0FF;
            case Metric.STALE_FRAME_COUNT: case Metric.STALE_FRAMES_CONSECUTIVE: return 0xFFFF6B6B;
            case Metric.BATTERY_TEMPERATURE: case Metric.THERMAL_HEADROOM: return 0xFFFECA57;
            default: return 0xFFC8D6E5;
        }
    }

    /** @param refreshRate used to draw the target line for FPS graphs (NaN = none) */
    public void draw(Canvas c, RectF r, int metric, MetricStore store, float textSize, double refreshRate) {
        Metric m = Metric.ALL[metric];
        int n = store.history(metric, buf);
        int color = colorFor(metric);
        c.drawRoundRect(r, textSize * 0.4f, textSize * 0.4f, bg);

        float maxV = m.graphMax, minV = 0f;
        for (int i = 0; i < n; i++) if (!Float.isNaN(buf[i]) && buf[i] > maxV) maxV = buf[i] * 1.1f;
        if (metric == Metric.AVERAGE_FRAME_RATE && !Double.isNaN(refreshRate)) maxV = (float) Math.max(refreshRate * 1.15, 30);
        if (metric == Metric.THERMAL_HEADROOM) maxV = Math.max(maxV, 1.2f);

        float pad = textSize * 0.3f;
        float left = r.left + pad, right = r.right - pad, top = r.top + textSize + pad, bottom = r.bottom - pad;
        for (int i = 1; i < 4; i++) {
            float y = top + (bottom - top) * i / 4f;
            c.drawLine(left, y, right, y, grid);
        }
        if (metric == Metric.AVERAGE_FRAME_RATE && !Double.isNaN(refreshRate)) {
            float y = bottom - (float) ((refreshRate - minV) / (maxV - minV)) * (bottom - top);
            target.setStrokeWidth(Math.max(1f, textSize * 0.08f));
            c.drawLine(left, y, right, y, target);
        }

        path.reset(); area.reset();
        boolean started = false;
        float step = (right - left) / (MetricStore.HISTORY - 1);
        float offset = (MetricStore.HISTORY - n) * step;
        float lastX = left;
        for (int i = 0; i < n; i++) {
            float v = buf[i];
            float x = left + offset + i * step;
            if (Float.isNaN(v)) { started = false; continue; }
            float y = bottom - Math.max(0f, Math.min(1f, (v - minV) / (maxV - minV))) * (bottom - top);
            if (!started) { path.moveTo(x, y); started = true; } else path.lineTo(x, y);
            lastX = x;
        }
        line.setColor(color);
        line.setStrokeWidth(Math.max(1.5f, textSize * 0.12f));
        c.drawPath(path, line);

        text.setTextSize(textSize * 0.8f);
        text.setColor(color);
        double cur = store.value(metric);
        String label = m.label + "  " + m.format(cur) + (m.unit.isEmpty() ? "" : " " + m.unit);
        c.drawText(label, r.left + pad, r.top + textSize * 0.85f, text);
        text.setColor(0xAAFFFFFF);
        String maxLabel = m.format(maxV);
        c.drawText(maxLabel, r.right - pad - text.measureText(maxLabel), r.top + textSize * 0.85f, text);
    }
}
