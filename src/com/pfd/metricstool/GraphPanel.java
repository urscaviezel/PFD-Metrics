package com.pfd.metricstool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Shows the graphs enabled for the HUD, or FPS / frame time / CPU% / GPU% by default. */
public final class GraphPanel extends View {
    private final GraphRenderer renderer = new GraphRenderer();
    private final RectF r = new RectF();
    private final Settings settings;
    private final MetricStore store;

    public GraphPanel(Context c, Settings settings, MetricStore store) {
        super(c);
        this.settings = settings;
        this.store = store;
    }

    private float dp(int v) { return v * getResources().getDisplayMetrics().density; }

    @Override protected void onDraw(Canvas c) {
        List<Integer> ids = new ArrayList<>();
        for (Metric m : Metric.ALL) if (settings.graphEnabled(m.id)) ids.add(m.id);
        if (ids.isEmpty()) ids.addAll(Arrays.asList(Metric.AVERAGE_FRAME_RATE, Metric.FRAME_TIME_MS,
                Metric.CPU_UTILIZATION, Metric.GPU_UTILIZATION));
        int n = Math.min(ids.size(), 6);
        float gap = dp(8);
        float h = (getHeight() - gap * (n - 1)) / n;
        float ts = Math.min(dp(16), h / 4f);
        double refresh = store.value(Metric.DISPLAY_REFRESH_RATE);
        for (int i = 0; i < n; i++) {
            float top = i * (h + gap);
            r.set(0, top, getWidth(), top + h);
            renderer.draw(c, r, ids.get(i), store, ts, refresh);
        }
    }
}
