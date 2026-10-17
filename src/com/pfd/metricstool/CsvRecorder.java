package com.pfd.metricstool;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * CSV capture in the OVR Metrics Tool layout: one file per app session under
 * Android/data/com.pfd.metricstool/files/CapturedMetrics/, first column "Time Stamp" (epoch ms),
 * then one column per metric key. Empty cell = metric not available in that sample.
 */
public final class CsvRecorder {
    private static final String TAG = "PFDMetrics";
    private final File dir;
    private BufferedWriter out;
    private File currentFile;
    private String currentApp;
    private long rows;

    public CsvRecorder(File externalFilesDir) {
        dir = new File(externalFilesDir, "CapturedMetrics");
    }

    public File directory() { return dir; }
    public File currentFile() { return currentFile; }
    public long rows() { return rows; }
    public boolean isRecording() { return out != null; }

    public synchronized void write(MetricStore store) {
        String app = store.foregroundApp();
        if (app.isEmpty()) app = "unknown";
        try {
            if (out == null || !app.equals(currentApp)) open(app);
            double[] v = store.snapshot();
            StringBuilder sb = new StringBuilder(512);
            sb.append(System.currentTimeMillis()).append(',').append(app);
            for (Metric m : Metric.ALL) {
                sb.append(',');
                if (!Double.isNaN(v[m.id])) sb.append(m.format(v[m.id]));
            }
            out.write(sb.toString());
            out.newLine();
            out.flush();
            rows++;
        } catch (Exception e) {
            Log.w(TAG, "CSV write failed: " + e);
            close();
        }
    }

    private void open(String app) throws Exception {
        close();
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("cannot create " + dir);
        String ts = new SimpleDateFormat("yyyy.MM.dd-HH.mm.ss", Locale.US).format(new Date());
        currentFile = new File(dir, app + "_" + ts + ".csv");
        out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(currentFile), StandardCharsets.UTF_8));
        StringBuilder h = new StringBuilder("Time Stamp,foreground_app");
        for (Metric m : Metric.ALL) h.append(',').append(m.key);
        out.write(h.toString());
        out.newLine();
        currentApp = app;
        rows = 0;
        Log.i(TAG, "CSV capture started: " + currentFile);
    }

    public synchronized void close() {
        if (out != null) {
            try { out.close(); } catch (Exception ignored) { }
            Log.i(TAG, "CSV capture closed: " + currentFile + " rows=" + rows);
        }
        out = null;
        currentApp = null;
    }
}
