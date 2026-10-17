package com.pfd.metricstool;

/** Top-level Runnable (the offline d8 chokes on inner classes from javac 21). */
final class PollTask implements Runnable {
    private final MainActivity a;
    PollTask(MainActivity a) { this.a = a; }
    @Override public void run() { a.schedulePoll(); }
}
