package com.pfd.metricstool;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Two ways to get the runtime's perf lines:
 *  1) Direct: run logcat inside the app (needs READ_LOGS granted via adb; Android 13+ may ask once
 *     for log access consent while the app is in the foreground — the stream then keeps running).
 *  2) Helper: a logcat process started from adb shell (uid shell) appends to a file in the app's
 *     external files dir; the app tails it. Works without any consent dialog.
 */
public final class LogSources {
    private static final String TAG = "PFDMetrics";
    public static final String HELPER_FILE = "perf_stream.log";

    private final PerfLogParser parser;
    private final File helperFile;
    private volatile boolean running;
    private Thread logcatThread, tailThread;
    private volatile Process logcatProcess;
    private volatile long logcatLines = 0, helperLines = 0;
    private volatile String logcatState = "stopped";
    private volatile long logcatStarted = 0;
    private int generation;
    private volatile long lastDirectData;
    private volatile boolean authorizedReader;
    public boolean hasAuthorizedReader() { return running && authorizedReader; }
    public boolean hasFreshDirectData() {
        return lastDirectData != 0 && android.os.SystemClock.elapsedRealtime() - lastDirectData < 5000;
    }

    /**
     * Android 13+ only lets an app read other apps' logs after the user agreed to the system's
     * "access all device logs" prompt, and that prompt is only offered while the app is in the
     * foreground. An activity of the HUD package could call this while on top so the logcat process is started
     * (again) in that state.
     */
    public synchronized void restartLogcat() {
        if (!running || hasAuthorizedReader()) return;
        int token = ++generation;
        if (logcatProcess != null) logcatProcess.destroy();
        lastDirectData = 0;
        authorizedReader = false;
        logcatState = "running";
        logcatStarted = System.currentTimeMillis();
        logcatThread = new Thread(() -> logcatOnce(token), "pfd-logcat");
        logcatThread.setDaemon(true);
        logcatThread.start();
    }

    /** "running" is meaningless when the system silently filters the stream; report it as no access. */
    public String logcatStatus() {
        if ("running".equals(logcatState) && !hasFreshDirectData() && System.currentTimeMillis() - logcatStarted > 5000)
            return "no_access";
        return logcatState;
    }

    public LogSources(PerfLogParser parser, File externalFilesDir) {
        this.parser = parser;
        // Written by the adb shell helper; app reads it with MANAGE_EXTERNAL_STORAGE (granted via appops).
        this.helperFile = new File("/sdcard/PFDMetrics", HELPER_FILE);
    }

    public File helperFile() { return helperFile; }
    public long logcatLines() { return logcatLines; }
    public long helperLines() { return helperLines; }
    public String logcatState() { return logcatState; }

    public synchronized void start() {
        if (running) return;
        running = true;
        // Direct logcat starts only from the visible consent activity.
        tailThread = new Thread(this::tailLoop, "pfd-helper-tail");
        tailThread.setDaemon(true);
        tailThread.start();
    }

    public synchronized void stop() {
        running = false;
        authorizedReader = false;
        generation++;
        logcatState = "stopped";
        lastDirectData = 0;
        Process p = logcatProcess;
        if (p != null) p.destroy();
        if (logcatThread != null) logcatThread.interrupt();
        if (tailThread != null) tailThread.interrupt();
    }

    private void logcatOnce(int token) {
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("logcat", "-v", "epoch", "-v", "uid", "-T", "1",
                    "-s", PerfLogParser.TAG_PERF + ":I", PerfLogParser.TAG_COMPOSITOR + ":I");
            pb.redirectErrorStream(true);
            synchronized (this) {
                if (!running || token != generation) return;
                p = pb.start();
                logcatProcess = p;
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    synchronized (this) {
                        if (!running || token != generation) break;
                        if (line.startsWith("---------")) continue;
                        synchronized (parser) {
                            long before = parser.linesParsed();
                            parser.onLine(line);
                            if (parser.linesParsed() > before) {
                                logcatLines++;
                                authorizedReader = true;
                                lastDirectData = android.os.SystemClock.elapsedRealtime();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            synchronized (this) {
                if (token == generation) logcatState = "error: " + e.getMessage();
            }
            Log.w(TAG, "logcat source failed", e);
        } finally {
            if (p != null) p.destroy();
            synchronized (this) {
                if (token == generation) {
                    logcatProcess = null;
                    authorizedReader = false;
                    lastDirectData = 0;
                    if (!logcatState.startsWith("error:")) logcatState = "no_access";
                }
            }
        }
    }

    private void tailLoop() {
        long pos = -1;
        StringBuilder partial = new StringBuilder();
        byte[] buf = new byte[16384];
        while (running) {
            try {
                if (helperFile.exists()) {
                    long len = helperFile.length();
                    if (pos < 0) pos = Math.max(0, len - 4096); else if (len < pos) pos = 0; // start near end / after truncation
                    if (len > pos) {
                        try (RandomAccessFile raf = new RandomAccessFile(helperFile, "r")) {
                            raf.seek(pos);
                            int n;
                            while ((n = raf.read(buf)) > 0) {
                                pos += n;
                                partial.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                                int nl;
                                while ((nl = partial.indexOf("\n")) >= 0) {
                                    String line = partial.substring(0, nl);
                                    partial.delete(0, nl + 1);
                                    helperLines++;
                                    synchronized (parser) { parser.onLine(line); }
                                }
                            }
                        }
                    }
                } else {
                    pos = -1;
                }
                Thread.sleep(250);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                try { Thread.sleep(1000); } catch (InterruptedException ie) { break; }
            }
        }
    }
}
