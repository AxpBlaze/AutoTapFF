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

    private Handler afkHandler = new Handler(Looper.getMainLooper());
    private Handler moveHandler = new Handler(Looper.getMainLooper());
    private boolean afkRunning = false;
    private boolean moveRunning = false;
    private final Random random = new Random();

    // Custom config
    public int afkIntervalMs = 61000;
    public float tapXPercent = 0.5f;
    public float tapYPercent = 0.62f;
    public boolean randomize = true;
    public int randomJitterPx = 40;
    public boolean vibrateOnTap = true;

    public int tapCount = 0;
    public long startTime = 0;

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
        afkRunning = false;
        moveRunning = false;
        super.onDestroy();
    }

    public void loadConfig() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        afkIntervalMs = p.getInt("afk_interval", 61000);
        tapXPercent = p.getFloat("tap_x", 0.5f);
        tapYPercent = p.getFloat("tap_y", 0.62f);
        randomize = p.getBoolean("randomize", true);
        vibrateOnTap = p.getBoolean("vibrate", true);
    }

    public void saveConfig() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        p.edit()
                .putInt("afk_interval", afkIntervalMs)
                .putFloat("tap_x", tapXPercent)
                .putFloat("tap_y", tapYPercent)
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

    // ============ CHARACTER TAP ============
    // Character ke upar single click
    public void characterTap() {
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        float x = w * tapXPercent;
        float y = h * tapYPercent;
        if (randomize) {
            x += random.nextInt(randomJitterPx * 2) - randomJitterPx;
            y += random.nextInt(randomJitterPx * 2) - randomJitterPx;
        }
        tap(x, y);
    }

    // ============ AFK LOOP ============
    public void startAfk() {
        if (afkRunning) return;
        afkRunning = true;
        startTime = System.currentTimeMillis();
        afkLoop();
    }

    public void stopAfk() {
        afkRunning = false;
        afkHandler.removeCallbacksAndMessages(null);
    }

    public boolean isAfkRunning() { return afkRunning; }

    private void afkLoop() {
        if (!afkRunning) return;
        characterTap();

        long delay = afkIntervalMs;
        if (randomize) delay += random.nextInt(10000) - 5000;
        if (delay < 5000) delay = 5000;
        afkHandler.postDelayed(this::afkLoop, delay);
    }

    // ============ MOVE — FORWARD ONLY ============
    // Aage ki taraf hi badhega — screen nahi ghumayega
    public void startMove() {
        if (moveRunning) return;
        moveRunning = true;
        moveLoop();
    }

    public void stopMove() {
        moveRunning = false;
        moveHandler.removeCallbacksAndMessages(null);
    }

    public boolean isMoveRunning() { return moveRunning; }

    private void moveLoop() {
        if (!moveRunning) return;

        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;

        // Forward movement: bottom-center → top-center (aage badhna)
        // Screen nahi ghumayega — sirf character aage chalega
        float startX = w * 0.5f;
        float startY = h * 0.70f;   // joystick zone
        float endX   = w * 0.5f;
        float endY   = h * 0.45f;   // up ki taraf swipe

        long dur = 800 + random.nextInt(400);
        swipe(startX, startY, endX, endY, dur);

        // Next swipe after short gap
        long delay = 1000 + random.nextInt(600);
        moveHandler.postDelayed(this::moveLoop, delay);
    }

    // ============ TEST TAP ============
    public void testTap() {
        characterTap();
    }
}
