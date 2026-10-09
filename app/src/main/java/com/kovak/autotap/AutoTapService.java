package com.kovak.autotap;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.accessibility.AccessibilityEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AutoTapService extends AccessibilityService {

    public static AutoTapService instance;
    private static final String PREFS = "axp_prefs";

    private Handler droneHandler = new Handler(Looper.getMainLooper());
    private Handler moveHandler = new Handler(Looper.getMainLooper());
    private boolean droneRunning = false;
    private boolean moveRunning = false;
    private final Random random = new Random();

    public float droneXPercent = 0.79f;
    public float droneYPercent = 0.83f;
    public int droneIntervalMs = 61000;
    public boolean randomize = true;
    public boolean vibrateOnTap = true;
    public int tapCount = 0;
    public long startTime = 0;

    // Active move directions (can be multiple)
    public boolean fwdActive = false;
    public boolean rightActive = false;
    public boolean liftActive = false;

    public static final int DIR_FORWARD = 1;
    public static final int DIR_RIGHT = 2;
    public static final int DIR_LIFT = 3;

    // Custom buttons
    public static class CustomBtn {
        public String name;
        public float xPercent;
        public float yPercent;
        public long intervalMs; // 0 = manual only
        public boolean loop;
        public boolean running;
        public Handler handler;

        public CustomBtn(String name, float x, float y, long interval, boolean loop) {
            this.name = name;
            this.xPercent = x;
            this.yPercent = y;
            this.intervalMs = interval;
            this.loop = loop;
            this.running = false;
        }
    }

    public List<CustomBtn> customBtns = new ArrayList<>();

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

        // Load custom buttons
        customBtns.clear();
        String json = p.getString("custom_btns", "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                CustomBtn b = new CustomBtn(
                        o.getString("name"),
                        (float) o.getDouble("x"),
                        (float) o.getDouble("y"),
                        o.getLong("interval"),
                        o.getBoolean("loop")
                );
                b.handler = new Handler(Looper.getMainLooper());
                customBtns.add(b);
            }
        } catch (Exception e) {}
    }

    public void saveConfig() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray();
            for (CustomBtn b : customBtns) {
                JSONObject o = new JSONObject();
                o.put("name", b.name);
                o.put("x", b.xPercent);
                o.put("y", b.yPercent);
                o.put("interval", b.intervalMs);
                o.put("loop", b.loop);
                arr.put(o);
            }
            p.edit()
                    .putFloat("drone_x", droneXPercent)
                    .putFloat("drone_y", droneYPercent)
                    .putInt("drone_interval", droneIntervalMs)
                    .putBoolean("randomize", randomize)
                    .putBoolean("vibrate", vibrateOnTap)
                    .putString("custom_btns", arr.toString())
                    .apply();
        } catch (Exception e) {}
    }

    private void vibrate(long ms) {
        if (!vibrateOnTap) return;
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(ms);
        } catch (Exception e) {}
    }

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

    // ============ DRONE ============
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

    public void startDrone() {
        if (droneRunning) return;
        droneRunning = true;
        startTime = System.currentTimeMillis();
        // Immediate first tap
        droneTap();
        droneLoop();
    }

    public void stopDrone() {
        droneRunning = false;
        droneHandler.removeCallbacksAndMessages(null);
    }

    public boolean isDroneRunning() { return droneRunning; }

    private void droneLoop() {
        if (!droneRunning) return;
        long delay = droneIntervalMs;
        if (randomize) delay += random.nextInt(8000) - 4000;
        if (delay < 5000) delay = 5000;
        droneHandler.postDelayed(() -> {
            if (!droneRunning) return;
            droneTap();
            droneLoop();
        }, delay);
    }

    // ============ MOVEMENT (multiple parallel) ============
    public void updateMove() {
        boolean any = fwdActive || rightActive || liftActive;
        if (any && !moveRunning) {
            moveRunning = true;
            moveLoop();
        } else if (!any && moveRunning) {
            moveRunning = false;
            moveHandler.removeCallbacksAndMessages(null);
        }
    }

    private void moveLoop() {
        if (!moveRunning) return;
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        float jx = w * 0.18f;
        float jy = h * 0.78f;

        // Fire all active directions
        if (fwdActive) {
            swipe(jx, jy, jx, jy - h * 0.10f, 300 + random.nextInt(200));
        }
        if (rightActive) {
            swipe(jx - w * 0.04f, jy, jx + w * 0.10f, jy, 300 + random.nextInt(200));
        }
        if (liftActive) {
            swipe(w * 0.5f, h * 0.55f, w * 0.5f, h * 0.30f, 400 + random.nextInt(200));
        }

        long delay = 400 + random.nextInt(300);
        moveHandler.postDelayed(this::moveLoop, delay);
    }

    public void setDirection(int dir, boolean active) {
        if (dir == DIR_FORWARD) fwdActive = active;
        if (dir == DIR_RIGHT) rightActive = active;
        if (dir == DIR_LIFT) liftActive = active;
        updateMove();
    }

    // ============ CUSTOM BUTTONS ============
    public void addCustomBtn(String name, float x, float y, long intervalMs, boolean loop) {
        CustomBtn b = new CustomBtn(name, x, y, intervalMs, loop);
        b.handler = new Handler(Looper.getMainLooper());
        customBtns.add(b);
        saveConfig();
    }

    public void removeCustomBtn(CustomBtn b) {
        if (b.running) {
            b.running = false;
            if (b.handler != null) b.handler.removeCallbacksAndMessages(null);
        }
        customBtns.remove(b);
        saveConfig();
    }

    public void startCustomBtn(CustomBtn b) {
        if (b.running) return;
        b.running = true;
        if (b.loop && b.intervalMs > 0) {
            customLoop(b);
        } else {
            // single shot
            customTap(b);
            b.running = false;
        }
    }

    public void stopCustomBtn(CustomBtn b) {
        b.running = false;
        if (b.handler != null) b.handler.removeCallbacksAndMessages(null);
    }

    private void customLoop(CustomBtn b) {
        if (!b.running) return;
        customTap(b);
        b.handler.postDelayed(() -> customLoop(b), b.intervalMs);
    }

    private void customTap(CustomBtn b) {
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        tap(w * b.xPercent, h * b.yPercent);
    }

    public void testTap() { droneTap(); }
}
