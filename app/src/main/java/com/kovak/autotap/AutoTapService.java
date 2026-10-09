package com.kovak.autotap;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import java.util.Random;

public class AutoTapService extends AccessibilityService {
    public static AutoTapService instance;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        instance = null;
        running = false;
        super.onDestroy();
    }

    public void tap(float x, float y) {
        Path p = new Path(); p.moveTo(x, y);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, 50));
        dispatchGesture(b.build(), null, null);
    }

    public void swipe(float x1, float y1, float x2, float y2, long dur) {
        Path p = new Path(); p.moveTo(x1, y1); p.lineTo(x2, y2);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, dur));
        dispatchGesture(b.build(), null, null);
    }

    public void jiggle() {
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        Random r = new Random();
        float x = w * 0.5f + r.nextInt(200) - 100;
        float y = h * 0.65f + r.nextInt(200) - 100;
        tap(x, y);
    }

    public void startAfk() { running = true; afkLoop(); }
    public void stopAfk() { running = false; handler.removeCallbacksAndMessages(null); }

    private void afkLoop() {
        if (!running) return;
        jiggle();
        Random r = new Random();
        handler.postDelayed(this::afkLoop, 40000 + r.nextInt(15000));
    }
}
