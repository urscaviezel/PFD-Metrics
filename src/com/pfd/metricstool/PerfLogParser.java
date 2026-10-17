package com.pfd.metricstool;

/**
 * Parses the 1 Hz performance lines that the Play for Dream (YVR) XR runtime writes to logcat:
 *
 *   PerfStats.cpp: FPS=90.00/90, Prd=22.87ms, Early=90.00, Stale=0.00, Lat=-1, Fov=0, CPU/GPU=3/5,
 *                  Free=8600MB, ATW=3.17ms, APP=2.38ms/0.82ms, GD=0.00, CPU%=0.41, GPU%=0.21,ColorSpace:6
 *   CompositorMonitor: fps:90.0, prd:23.0 ms, cpu:1.0 ms, gpu:2.2 ms, early:90.0, stale:0.0,
 *                  max_stale:0.0, sw_mode:0, drr:90.0, lat_mode:-1
 *
 * PerfStats is emitted inside every OpenXR client process (the app) and also by system processes
 * (home/shell). Lines from app processes win over system ones while both are present.
 */
public final class PerfLogParser {
    public static final String TAG_PERF = "PerfStats.cpp";
    public static final String TAG_COMPOSITOR = "CompositorMonitor";

    private final MetricStore store;
    private long lastAppPerf = 0;
    private volatile int lastPerfPid = -1;
    private volatile long linesParsed = 0;

    public PerfLogParser(MetricStore store) { this.store = store; }

    public long linesParsed() { return linesParsed; }
    public int lastPerfPid() { return lastPerfPid; }

    /** Feed one raw logcat line (any -v format containing "<tag>: <msg>"). */
    public void onLine(String line) {
        if (isStale(line)) return;   // ignore backlog (e.g. old file content after a reboot)
        int p = line.indexOf(TAG_PERF + ":");
        if (p >= 0) {
            String header = line.substring(0, p);
            String msg = line.substring(p + TAG_PERF.length() + 1).trim();
            if (!msg.startsWith("FPS=")) return;
            boolean system = isSystemProcess(header);
            long now = System.currentTimeMillis();
            if (system && now - lastAppPerf < 2500) return; // app process stats take precedence
            if (!system) lastAppPerf = now;
            lastPerfPid = pidOf(header);
            parsePerfStats(msg);
            linesParsed++;
            return;
        }
        p = line.indexOf(TAG_COMPOSITOR + ":");
        if (p >= 0) {
            String msg = line.substring(p + TAG_COMPOSITOR.length() + 1).trim();
            if (!msg.startsWith("fps:")) return;
            parseCompositor(msg);
            linesParsed++;
        }
    }

    /** Lines start with an epoch timestamp ("-v epoch"); anything older than 5 s is history, not live data. */
    private static boolean isStale(String line) {
        String t = line.trim();
        int sp = t.indexOf(' ');
        if (sp <= 0) return false;
        String first = t.substring(0, sp);
        if (first.indexOf('.') < 0 || first.indexOf('-') >= 0) return false;
        try {
            double epoch = Double.parseDouble(first);
            return System.currentTimeMillis() - (long) (epoch * 1000) > 5000;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Header tokens: [date] time [uid] pid tid level. uid 1000/"system" → system process. */
    private static boolean isSystemProcess(String header) {
        String[] t = header.trim().split("\\s+");
        // epoch format: "<epoch> <uid> <pid> <tid> <lvl>" (5) or without uid (4)
        // threadtime: "<date> <time> <uid> <pid> <tid> <lvl>" (6) or without uid (5, date present)
        String uid = null;
        if (t.length >= 6) uid = t[t.length - 4];
        else if (t.length == 5 && !t[0].contains("-")) uid = t[1];
        if (uid == null) return false;
        return uid.equals("1000") || uid.equals("system");
    }

    private static int pidOf(String header) {
        String[] t = header.trim().split("\\s+");
        if (t.length >= 3) {
            try { return Integer.parseInt(t[t.length - 3]); } catch (NumberFormatException ignored) { }
        }
        return -1;
    }

    void parsePerfStats(String msg) {
        for (String tok : msg.split(",")) {
            int eq = tok.indexOf('=');
            if (eq < 0) continue;
            String k = tok.substring(0, eq).trim();
            String v = tok.substring(eq + 1).trim();
            switch (k) {
                case "FPS": {
                    double[] a = pair(v);
                    put(Metric.AVERAGE_FRAME_RATE, a[0]);
                    put(Metric.DISPLAY_REFRESH_RATE, a[1]);
                    if (a[0] > 0) put(Metric.FRAME_TIME_MS, 1000.0 / a[0]);
                    break;
                }
                case "Prd": put(Metric.PREDICTION_MS, num(v)); break;
                case "Early": put(Metric.EARLY_FRAME_COUNT, num(v)); break;
                case "Stale": put(Metric.STALE_FRAME_COUNT, num(v)); break;
                case "Lat": put(Metric.EXTRA_LATENCY_MODE, num(v)); break;
                case "Fov": put(Metric.FOVEATION_LEVEL, num(v)); break;
                case "CPU/GPU": {
                    double[] a = pair(v);
                    put(Metric.CPU_LEVEL, a[0]);
                    put(Metric.GPU_LEVEL, a[1]);
                    break;
                }
                case "Free": put(Metric.AVAILABLE_MEMORY_MB, num(v)); break;
                case "ATW": put(Metric.TIMEWARP_GPU_TIME_US, num(v) * 1000.0); break;
                case "APP": {
                    double[] a = pair(v);
                    // APP=<cpu>/<gpu>: verified on device — second value tracks GPU%=0.99 (GPU-bound title).
                    put(Metric.APP_CPU_TIME_US, a[0] * 1000.0);
                    put(Metric.APP_GPU_TIME_US, a[1] * 1000.0);
                    break;
                }
                case "GD": put(Metric.GUARDIAN_GPU_TIME_US, num(v) * 1000.0); break;
                case "CPU%": put(Metric.CPU_UTILIZATION, ratioToPct(num(v))); break;
                case "GPU%": put(Metric.GPU_UTILIZATION, ratioToPct(num(v))); break;
                default: break;
            }
        }
    }

    void parseCompositor(String msg) {
        for (String tok : msg.split(",")) {
            int c = tok.indexOf(':');
            if (c < 0) continue;
            String k = tok.substring(0, c).trim();
            String v = tok.substring(c + 1).trim();
            switch (k) {
                case "fps": put(Metric.COMPOSITOR_FPS, num(v)); break;
                case "cpu": put(Metric.COMPOSITOR_CPU_TIME_US, num(v) * 1000.0); break;
                case "gpu": put(Metric.COMPOSITOR_GPU_TIME_US, num(v) * 1000.0); break;
                case "max_stale": put(Metric.STALE_FRAMES_CONSECUTIVE, num(v)); break;
                case "sw_mode": put(Metric.SPACEWARP_MODE, num(v)); break;
                default: break;
            }
        }
    }

    private void put(int metric, double v) { if (!Double.isNaN(v)) store.put(metric, v); }

    private static double ratioToPct(double r) { return Double.isNaN(r) ? r : (r <= 1.0 ? r * 100.0 : r); }

    private static double[] pair(String v) {
        int s = v.indexOf('/');
        if (s < 0) return new double[]{num(v), Double.NaN};
        return new double[]{num(v.substring(0, s)), num(v.substring(s + 1))};
    }

    /** Parses the leading number of a token like "22.87ms", "8600MB", "-1". */
    static double num(String s) {
        int end = 0, n = s.length();
        while (end < n) {
            char ch = s.charAt(end);
            if ((ch >= '0' && ch <= '9') || ch == '.' || ch == '-' || ch == '+') end++;
            else break;
        }
        if (end == 0) return Double.NaN;
        try { return Double.parseDouble(s.substring(0, end)); } catch (NumberFormatException e) { return Double.NaN; }
    }
}
