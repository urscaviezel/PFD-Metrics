package com.pfd.metricstool;

import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;

/** Thread-safe store of current values, graph history and session statistics. */
public final class MetricStore {
    public static final int HISTORY = 300;           // samples kept for graphs
    private static final int SESSION_CAP = 36000;    // samples kept for percentile stats (~10 h @ 1 Hz)
    private static final long STALE_MS = 4000;       // async sources older than this count as missing

    public interface Listener { void onSample(MetricStore store); }

    private static final MetricStore INSTANCE = new MetricStore();
    public static MetricStore get() { return INSTANCE; }

    private final double[] pending = new double[Metric.COUNT];
    private final long[] pendingTime = new long[Metric.COUNT];
    private final double[] current = new double[Metric.COUNT];
    private final float[][] history = new float[Metric.COUNT][HISTORY];
    private int historyPos = 0, historyLen = 0;

    private final float[][] session = new float[Metric.COUNT][];
    private final int[] sessionLen = new int[Metric.COUNT];
    private final double[] sum = new double[Metric.COUNT];
    private final double[] min = new double[Metric.COUNT];
    private final double[] max = new double[Metric.COUNT];
    private long sessionStart = System.currentTimeMillis();

    private volatile String foregroundApp = "";
    private volatile long lastRuntimeUpdate = 0, lastCompositorUpdate = 0;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private MetricStore() {
        Arrays.fill(pending, Double.NaN);
        Arrays.fill(current, Double.NaN);
        for (float[] h : history) Arrays.fill(h, Float.NaN);
        resetStats();
    }

    public synchronized void put(int metric, double value) {
        pending[metric] = value;
        pendingTime[metric] = System.currentTimeMillis();
        Metric m = Metric.ALL[metric];
        if ("runtime".equals(m.source)) lastRuntimeUpdate = pendingTime[metric];
        else if ("compositor".equals(m.source)) lastCompositorUpdate = pendingTime[metric];
    }

    /** Freeze pending values into a sample (called once per sampling interval). */
    public void commitSample() {
        long now = System.currentTimeMillis();
        synchronized (this) {
            for (int i = 0; i < Metric.COUNT; i++) {
                double v = (now - pendingTime[i] <= STALE_MS) ? pending[i] : Double.NaN;
                current[i] = v;
                history[i][historyPos] = (float) v;
                if (!Double.isNaN(v)) {
                    if (session[i] == null) session[i] = new float[1024];
                    if (sessionLen[i] < SESSION_CAP) {
                        if (sessionLen[i] == session[i].length)
                            session[i] = Arrays.copyOf(session[i], Math.min(SESSION_CAP, session[i].length * 2));
                        session[i][sessionLen[i]++] = (float) v;
                    }
                    sum[i] += v;
                    if (v < min[i]) min[i] = v;
                    if (v > max[i]) max[i] = v;
                }
            }
            historyPos = (historyPos + 1) % HISTORY;
            if (historyLen < HISTORY) historyLen++;
        }
        for (Listener l : listeners) l.onSample(this);
    }

    public synchronized double value(int metric) { return current[metric]; }

    public synchronized double[] snapshot() { return current.clone(); }

    /** Copies history oldest→newest into out (length HISTORY); returns number of valid points. */
    public synchronized int history(int metric, float[] out) {
        if (remote && remoteHist != null) {
            System.arraycopy(remoteHist, metric * HISTORY, out, 0, remoteHistLen);
            return remoteHistLen;
        }
        int n = historyLen;
        int start = (historyPos - n + HISTORY) % HISTORY;
        for (int i = 0; i < n; i++) out[i] = history[metric][(start + i) % HISTORY];
        return n;
    }

    public static final class Stats {
        public int count; public double avg, min, max, p1, p5, p50, p95, p99;
    }

    public Stats stats(int metric) {
        float[] copy;
        Stats s = new Stats();
        synchronized (this) {
            if (remote && rAvg != null) {
                s.count = rCount[metric]; s.avg = rAvg[metric]; s.min = rMin[metric]; s.max = rMax[metric];
                s.p1 = rP1[metric]; s.p99 = rP99[metric]; s.p5 = s.p50 = s.p95 = Double.NaN;
                return s;
            }
            s.count = sessionLen[metric];
            if (s.count == 0) { s.avg = s.min = s.max = s.p1 = s.p5 = s.p50 = s.p95 = s.p99 = Double.NaN; return s; }
            s.avg = sum[metric] / s.count;
            s.min = min[metric];
            s.max = max[metric];
            copy = Arrays.copyOf(session[metric], s.count);
        }
        Arrays.sort(copy);
        s.p1 = pct(copy, 0.01); s.p5 = pct(copy, 0.05); s.p50 = pct(copy, 0.5);
        s.p95 = pct(copy, 0.95); s.p99 = pct(copy, 0.99);
        return s;
    }

    private static double pct(float[] sorted, double p) {
        int idx = (int) Math.floor(p * (sorted.length - 1));
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }

    public synchronized void resetStats() {
        for (int i = 0; i < Metric.COUNT; i++) {
            sessionLen[i] = 0; sum[i] = 0;
            min[i] = Double.POSITIVE_INFINITY; max[i] = Double.NEGATIVE_INFINITY;
        }
        sessionStart = System.currentTimeMillis();
    }

    public long sessionStart() { return sessionStart; }
    public String foregroundApp() { return foregroundApp; }
    public void setForegroundApp(String pkg) { foregroundApp = pkg == null ? "" : pkg; }
    public long lastRuntimeUpdate() { return lastRuntimeUpdate; }
    public long lastCompositorUpdate() { return lastCompositorUpdate; }

    // ---------------------------------------------------------------- cross-process snapshot
    // The HUD runs in its own package (com.pfd.metricstool.hud) so that closing the UI panel —
    // which force-stops the UI package on YVR OS — does not kill the HUD. The UI mirrors the data.
    private boolean remote = false;
    private float[] remoteHist;
    private int remoteHistLen;
    private double[] rAvg, rMin, rMax, rP1, rP99;
    private int[] rCount;

    public android.os.Bundle toSnapshot() {
        android.os.Bundle b = new android.os.Bundle();
        float[] flat = new float[Metric.COUNT * HISTORY];
        float[] tmp = new float[HISTORY];
        int n = 0;
        for (int i = 0; i < Metric.COUNT; i++) {
            n = history(i, tmp);
            System.arraycopy(tmp, 0, flat, i * HISTORY, n);
        }
        double[] avg = new double[Metric.COUNT], mn = new double[Metric.COUNT], mx = new double[Metric.COUNT],
                p1 = new double[Metric.COUNT], p99 = new double[Metric.COUNT];
        int[] cnt = new int[Metric.COUNT];
        for (int i = 0; i < Metric.COUNT; i++) {
            Stats s = stats(i);
            avg[i] = s.avg; mn[i] = s.min; mx[i] = s.max; p1[i] = s.p1; p99[i] = s.p99; cnt[i] = s.count;
        }
        b.putDoubleArray("values", snapshot());
        b.putFloatArray("hist", flat);
        b.putInt("histLen", n);
        b.putDoubleArray("st_avg", avg); b.putDoubleArray("st_min", mn); b.putDoubleArray("st_max", mx);
        b.putDoubleArray("st_p1", p1); b.putDoubleArray("st_p99", p99); b.putIntArray("st_count", cnt);
        b.putLong("sessionStart", sessionStart);
        b.putLong("lastRuntime", lastRuntimeUpdate);
        b.putString("fg", foregroundApp);
        return b;
    }

    public void applySnapshot(android.os.Bundle b) {
        double[] v = b.getDoubleArray("values");
        if (v == null || v.length != Metric.COUNT) return;
        synchronized (this) {
            remote = true;
            System.arraycopy(v, 0, current, 0, Metric.COUNT);
            remoteHist = b.getFloatArray("hist");
            remoteHistLen = b.getInt("histLen");
            rAvg = b.getDoubleArray("st_avg"); rMin = b.getDoubleArray("st_min"); rMax = b.getDoubleArray("st_max");
            rP1 = b.getDoubleArray("st_p1"); rP99 = b.getDoubleArray("st_p99"); rCount = b.getIntArray("st_count");
            sessionStart = b.getLong("sessionStart", sessionStart);
            lastRuntimeUpdate = b.getLong("lastRuntime", 0);
            foregroundApp = b.getString("fg", "");
        }
        for (Listener l : listeners) l.onSample(this);
    }

    public void addListener(Listener l) { listeners.addIfAbsent(l); }
    public void removeListener(Listener l) { listeners.remove(l); }
}
