package com.kovak.autotap;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OverlayService extends Service {

    private WindowManager wm;
    private View overlay;
    private View hideTab;
    private LinearLayout rootLayout;
    private TextView afkBtn, moveBtn, hideBtn, closeBtn, statsText;
    private boolean afkOn = false;
    private boolean moveOn = false;

    private Handler statsHandler = new Handler(Looper.getMainLooper());

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        buildOverlay();
        buildHideTab();
        startStatsUpdater();
    }

    private GradientDrawable bg(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private GradientDrawable bgGrad(int c1, int c2, float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{c1, c2});
        g.setCornerRadius(radius);
        return g;
    }

    private TextView makeBtn(String text, int textColor, int bgColor) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(textColor);
        tv.setTextSize(13f);
        tv.setPadding(40, 28, 40, 28);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(bg(bgColor, 32f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 7, 0, 7);
        tv.setLayoutParams(lp);
        tv.setElevation(6f);
        return tv;
    }

    private void buildOverlay() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(32, 32, 32, 32);

        GradientDrawable containerBg = bgGrad(0xF010101F, 0xF00A0A14, 50f);
        containerBg.setStroke(3, 0xFF6366F1);
        rootLayout.setBackground(containerBg);
        rootLayout.setElevation(25f);

        // Header
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER);

        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextColor(0xFF4ADE80);
        dot.setTextSize(10f);
        headerRow.addView(dot);

        TextView header = new TextView(this);
        header.setText(" AXP CONTROL ");
        header.setTextColor(0xFF818CF8);
        header.setTextSize(13f);
        header.setLetterSpacing(0.15f);
        headerRow.addView(header);

        rootLayout.addView(headerRow);

        // Stats
        statsText = new TextView(this);
        statsText.setText("Taps: 0  ·  Time: 00:00");
        statsText.setTextColor(0xFF6B6B80);
        statsText.setTextSize(10f);
        statsText.setGravity(Gravity.CENTER);
        statsText.setPadding(0, 4, 0, 16);
        rootLayout.addView(statsText);

        // AFK button
        afkBtn = makeBtn("AFK  OFF", 0xFFFFFFFF, 0xFF1E1E35);
        afkBtn.setOnClickListener(v -> toggleAfk());
        rootLayout.addView(afkBtn);

        // MOVE button
        moveBtn = makeBtn("MOVE  OFF", 0xFFFFFFFF, 0xFF1E1E35);
        moveBtn.setOnClickListener(v -> toggleMove());
        rootLayout.addView(moveBtn);

        // TEST TAP
        TextView testBtn = makeBtn("TEST TAP", 0xFF818CF8, 0xFF1E1E35);
        testBtn.setOnClickListener(v -> {
            AutoTapService s = AutoTapService.instance;
            if (s != null) s.testTap();
        });
        rootLayout.addView(testBtn);

        // HIDE
        hideBtn = makeBtn("HIDE", 0xFFFFD200, 0xFF1E1E35);
        hideBtn.setOnClickListener(v -> hideOverlay());
        rootLayout.addView(hideBtn);

        // CLOSE
        closeBtn = makeBtn("CLOSE", 0xFFEF4444, 0xFF1E1E35);
        closeBtn.setOnClickListener(v -> stopSelf());
        rootLayout.addView(closeBtn);

        rootLayout.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY;
            float touchX, touchY;
            boolean dragging = false;
            WindowManager.LayoutParams lp;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                lp = (WindowManager.LayoutParams) rootLayout.getLayoutParams();
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = lp.x; startY = lp.y;
                        touchX = e.getRawX(); touchY = e.getRawY();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int)(e.getRawX() - touchX);
                        int dy = (int)(e.getRawY() - touchY);
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) dragging = true;
                        if (dragging) {
                            lp.x = startX + dx; lp.y = startY + dy;
                            wm.updateViewLayout(rootLayout, lp);
                        }
                        return true;
                }
                return false;
            }
        });

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 30; params.y = 200;

        overlay = rootLayout;
        wm.addView(overlay, params);
    }

    private void buildHideTab() {
        TextView tab = new TextView(this);
        tab.setText("A");
        tab.setTextColor(0xFFFFFFFF);
        tab.setTextSize(16f);
        tab.setGravity(Gravity.CENTER);
        tab.setWidth(90);
        tab.setHeight(90);

        GradientDrawable tabBg = bgGrad(0xFF6366F1, 0xFF4F46E5, 50f);
        tabBg.setStroke(3, 0xFF818CF8);
        tab.setBackground(tabBg);
        tab.setElevation(20f);

        tab.setVisibility(View.GONE);
        tab.setOnClickListener(v -> showOverlay());

        tab.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY;
            float touchX, touchY;
            WindowManager.LayoutParams lp;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                lp = (WindowManager.LayoutParams) tab.getLayoutParams();
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = lp.x; startY = lp.y;
                        touchX = e.getRawX(); touchY = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = startX + (int)(e.getRawX() - touchX);
                        lp.y = startY + (int)(e.getRawY() - touchY);
                        wm.updateViewLayout(tab, lp);
                        return true;
                }
                return false;
            }
        });

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                90, 90, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 30; params.y = 200;

        hideTab = tab;
        wm.addView(hideTab, params);
    }

    private void startStatsUpdater() {
        statsHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                AutoTapService s = AutoTapService.instance;
                if (s != null && afkOn) {
                    long elapsed = (System.currentTimeMillis() - s.startTime) / 1000;
                    long mins = elapsed / 60;
                    long secs = elapsed % 60;
                    statsText.setText(String.format("Taps: %d  ·  Time: %02d:%02d",
                            s.tapCount, mins, secs));
                } else {
                    statsText.setText("Taps: " + (s != null ? s.tapCount : 0) + "  ·  Idle");
                }
                statsHandler.postDelayed(this, 2000);
            }
        }, 2000);
    }

    private void hideOverlay() {
        rootLayout.setVisibility(View.GONE);
        hideTab.setVisibility(View.VISIBLE);
    }

    private void showOverlay() {
        rootLayout.setVisibility(View.VISIBLE);
        hideTab.setVisibility(View.GONE);
    }

    private void toggleAfk() {
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        afkOn = !afkOn;
        if (afkOn) {
            s.startAfk();
            afkBtn.setText("AFK  ON  ·  61s");
            afkBtn.setTextColor(0xFF4ADE80);
            afkBtn.setBackground(bgGrad(0xFF0F2A1F, 0xFF0A1A12, 32f));
        } else {
            s.stopAfk();
            afkBtn.setText("AFK  OFF");
            afkBtn.setTextColor(0xFFFFFFFF);
            afkBtn.setBackground(bg(0xFF1E1E35, 32f));
        }
    }

    private void toggleMove() {
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        moveOn = !moveOn;
        if (moveOn) {
            s.startMove();
            moveBtn.setText("MOVE  ON");
            moveBtn.setTextColor(0xFF4ADE80);
            moveBtn.setBackground(bgGrad(0xFF0F2A1F, 0xFF0A1A12, 32f));
        } else {
            s.stopMove();
            moveBtn.setText("MOVE  OFF");
            moveBtn.setTextColor(0xFFFFFFFF);
            moveBtn.setBackground(bg(0xFF1E1E35, 32f));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AutoTapService s = AutoTapService.instance;
        if (s != null) { s.stopAfk(); s.stopMove(); s.saveConfig(); }
        if (overlay != null) { try { wm.removeView(overlay); } catch (Exception e) {} }
        if (hideTab != null) { try { wm.removeView(hideTab); } catch (Exception e) {} }
        statsHandler.removeCallbacksAndMessages(null);
    }
}
