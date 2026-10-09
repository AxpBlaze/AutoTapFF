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
    private int moveDirection = 1;
    private final Random random = new Random();

    // Custom config
    public int afkIntervalMs = 61000;
    public float tapXPercent = 0.5f;
    public float tapYPercent = 0.62f;
    public boolean randomize = true;
    public int randomJitterPx = 40;
    public boolean vibrateOnTap = true;
    public boolean appWhitelistOnly = false;

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
        appWhitelistOnly = p.getBoolean("whitelist", false);
    }

    public void saveConfig() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        p.edit()
                .putInt("afk_interval", afkIntervalMs)
                .putFloat("tap_x", tapXPercent)
                .putFloat("tap_y", tapYPercent)
                .putBoolean("randomize", randomize)
                .putBoolean("vibrate", vibrateOnTap)
                .putBoolean("whitelist", appWhitelistOnly)
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

    // ============ AFK ============
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
    public boolean isMoveRunning() { return moveRunning; }

    private void afkLoop() {
        if (!afkRunning) return;

        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;

        float x = w * tapXPercent;
        float y = h * tapYPercent;

        if (randomize) {
            x += random.nextInt(randomJitterPx * 2) - randomJitterPx;
            y += random.nextInt(randomJitterPx * 2) - randomJitterPx;
        }

        tap(x, y);

        long delay = afkIntervalMs;
        if (randomize) {
            delay += random.nextInt(10000) - 5000; // ±5 sec
        }
        if (delay < 5000) delay = 5000;

        afkHandler.postDelayed(this::afkLoop, delay);
    }

    // ============ MOVE ============
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

    // ============ TEST TAP ============
    public void testTap() {
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        tap(w * tapXPercent, h * tapYPercent);
    }
}
