package com.kovak.autotap;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.accessibility.AccessibilityEvent;
import java.util.Random;

public class AutoTapService extends AccessibilityService {

    public static AutoTapService instance;
    private static final String PREFS = "axp_prefs";

    private Handler droneHandler = new Handler(Looper.getMainLooper());
    private Handler moveHandler = new Handler(Looper.getMainLooper());
    private boolean droneRunning = false;
    private boolean moveRunning = false;
    private final Random random = new Random();

    // Drone coordinates (percent of screen)
    // Photo ke hisaab se drone icon roughly right-bottom area mein hai
    public float droneXPercent = 0.79f;   // right side
    public float droneYPercent = 0.83f;   // bottom-right drone area

    public int droneIntervalMs = 61000;
    public boolean randomize = true;
    public boolean vibrateOnTap = true;

    public int tapCount = 0;
    public long startTime = 0;

    // Movement direction
    public static final int DIR_FORWARD = 1;
    public static final int DIR_RIGHT = 2;
    public static final int DIR_LIFT = 3;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        loadConfig();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        instance = null;
        droneRunning = false;
        moveRunning = false;
        super.onDestroy();
    }

    public void loadConfig() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        droneXPercent = p.getFloat("drone_x", 0.79f);
        droneYPercent = p.getFloat("drone_y", 0.83f);
        droneIntervalMs = p.getInt("drone_interval", 61000);
        randomize = p.getBoolean("randomize", true);
        vibrateOnTap = p.getBoolean("vibrate", true);
    }

    public void saveConfig() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        p.edit()
                .putFloat("drone_x", droneXPercent)
                .putFloat("drone_y", droneYPercent)
                .putInt("drone_interval", droneIntervalMs)
                .putBoolean("randomize", randomize)
                .putBoolean("vibrate", vibrateOnTap)
                .apply();
    }

    private void vibrate(long ms) {
        if (!vibrateOnTap) return;
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(ms);
        } catch (Exception e) {}
    }

    // ============ TAP ============
    public void tap(float x, float y) {
        Path p = new Path(); p.moveTo(x, y);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, 50));
        dispatchGesture(b.build(), null, null);
        tapCount++;
        vibrate(40);
    }

    public void swipe(float x1, float y1, float x2, float y2, long dur) {
        Path p = new Path(); p.moveTo(x1, y1); p.lineTo(x2, y2);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, dur));
        dispatchGesture(b.build(), null, null);
        vibrate(60);
    }

    // ============ DRONE TAP ============
    public void droneTap() {
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        float x = w * droneXPercent;
        float y = h * droneYPercent;
        if (randomize) {
            x += random.nextInt(20) - 10;
            y += random.nextInt(20) - 10;
        }
        tap(x, y);
    }

    // ============ DRONE LOOP — 61s ============
    public void startDrone() {
        if (droneRunning) return;
        droneRunning = true;
        startTime = System.currentTimeMillis();
        droneLoop();
    }

    public void stopDrone() {
        droneRunning = false;
        droneHandler.removeCallbacksAndMessages(null);
    }

    public boolean isDroneRunning() { return droneRunning; }

    private void droneLoop() {
        if (!droneRunning) return;
        droneTap();

        long delay = droneIntervalMs;
        if (randomize) delay += random.nextInt(8000) - 4000;
        if (delay < 5000) delay = 5000;
        droneHandler.postDelayed(this::droneLoop, delay);
    }

    // ============ MOVE — DIRECTION BASED ============
    public void startMove(int direction) {
        if (moveRunning) return;
        moveRunning = true;
        moveLoop(direction);
    }

    public void stopMove() {
        moveRunning = false;
        moveHandler.removeCallbacksAndMessages(null);
    }

    public boolean isMoveRunning() { return moveRunning; }

    private void moveLoop(int dir) {
        if (!moveRunning) return;
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;

        float sx, sy, ex, ey;

        // Joystick area roughly bottom-left (screen ka 15-25% left, 75-85% down)
        float jx = w * 0.18f;
        float jy = h * 0.78f;

        switch (dir) {
            case DIR_FORWARD:
                // Bottom → top (up ki taraf)
                sx = jx;
                sy = jy;
                ex = jx;
                ey = jy - h * 0.10f;
                break;
            case DIR_RIGHT:
                // Left → right
                sx = jx - w * 0.04f;
                sy = jy;
                ex = jx + w * 0.10f;
                ey = jy;
                break;
            case DIR_LIFT:
                // Screen ke center mein up swipe (lift/higher jump gesture)
                sx = w * 0.5f;
                sy = h * 0.55f;
                ex = w * 0.5f;
                ey = h * 0.30f;
                break;
            default:
                sx = jx; sy = jy; ex = jx; ey = jy - h * 0.10f;
        }

        long dur = 300 + random.nextInt(200);
        swipe(sx, sy, ex, ey, dur);

        long delay = 400 + random.nextInt(300);
        moveHandler.postDelayed(() -> moveLoop(dir), delay);
    }

    // ============ TEST TAP ============
    public void testTap() {
        droneTap();
    }
}
