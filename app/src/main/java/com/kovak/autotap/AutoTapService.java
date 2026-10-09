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

    private Handler afkHandler = new Handler(Looper.getMainLooper());
    private Handler moveHandler = new Handler(Looper.getMainLooper());
    private boolean afkRunning = false;
    private boolean moveRunning = false;
    private int moveDirection = 1;
    private final Random random = new Random();

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
        afkRunning = false;
        moveRunning = false;
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

    // AFK — 61 second exact
    public void startAfk() {
        if (afkRunning) return;
        afkRunning = true;
        afkLoop();
    }

    public void stopAfk() {
        afkRunning = false;
        afkHandler.removeCallbacksAndMessages(null);
    }

    private void afkLoop() {
        if (!afkRunning) return;
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;

        // Character ke upar — screen center-bottom third
        float x = w * 0.5f + random.nextInt(80) - 40;
        float y = h * 0.62f + random.nextInt(60) - 30;

        tap(x, y);
        afkHandler.postDelayed(this::afkLoop, 61000);
    }

    // MOVE continuous
    public void startMove() {
        if (moveRunning) return;
        moveRunning = true;
        moveLoop();
    }

    public void stopMove() {
        moveRunning = false;
        moveHandler.removeCallbacksAndMessages(null);
    }

    private void moveLoop() {
        if (!moveRunning) return;
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        float centerY = h * 0.6f;
        float startX, endX;

        if (moveDirection == 1) {
            startX = w * 0.25f; endX = w * 0.75f; moveDirection = -1;
        } else {
            startX = w * 0.75f; endX = w * 0.25f; moveDirection = 1;
        }

        long dur = 500 + random.nextInt(400);
        swipe(startX, centerY, endX, centerY, dur);
        moveHandler.postDelayed(this::moveLoop, 800 + random.nextInt(500));
    }
}
