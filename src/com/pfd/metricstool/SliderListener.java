package com.pfd.metricstool;

import android.widget.SeekBar;

/** SeekBar listener as a top-level class (keeps the offline d8 toolchain happy). */
public final class SliderListener implements SeekBar.OnSeekBarChangeListener {
    public interface IntCallback { void on(int v); }
    private final IntCallback onAny, onUser;

    public SliderListener(IntCallback onAny, IntCallback onUser) { this.onAny = onAny; this.onUser = onUser; }

    @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
        onAny.on(p);
        if (fromUser) onUser.on(p);
    }
    @Override public void onStartTrackingTouch(SeekBar s) { }
    @Override public void onStopTrackingTouch(SeekBar s) { }
}
