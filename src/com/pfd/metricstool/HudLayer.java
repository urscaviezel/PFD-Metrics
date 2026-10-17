package com.pfd.metricstool;

import android.view.WindowManager;

/** One stacked YVR toast widget (background-only backing layer). */
final class HudLayer {
    int handle;
    int displayId = -1;
    WindowManager wm;
    OverlayView view;
}
