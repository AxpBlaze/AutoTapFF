package com.kovak.autotap;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OverlayService extends Service {

    private WindowManager wm;
    private View overlay;
    private View hideTab;
    private LinearLayout rootLayout;
    private TextView droneState, fwdState, rightState, liftState;
    private TextView statsText;

    private boolean droneOn = false;
    private boolean fwdOn = false;
    private boolean rightOn = false;
    private boolean liftOn = false;

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

    private GradientDrawable bgGrad(int c1, int c2, float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{c1, c2});
        g.setCornerRadius(radius);
        return g;
    }

    private GradientDrawable bgSolid(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private LinearLayout makeRow(String labelText, String btnText, int btnColor,
                                  View.OnClickListener listener, TextView[] stateOut) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, 5, 0, 5);
        row.setLayoutParams(rlp);

        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(0xFF9CA3AF);
        label.setTextSize(11f);
        label.setLetterSpacing(0.12f);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        label.setLayoutParams(llp);
        row.addView(label);

        TextView btn = new TextView(this);
        btn.setText(btnText);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(11f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(28, 20, 28, 20);
        btn.setBackground(bgSolid(btnColor, 28f));
        btn.setElevation(4f);
        btn.setMinWidth(130);
        btn.setOnClickListener(listener);
        row.addView(btn);

        stateOut[0] = btn;
        return row;
    }

    private void buildOverlay() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(35, 35, 35, 35);

        GradientDrawable containerBg = bgGrad(0xF010101F, 0xF00A0A14, 45f);
        containerBg.setStroke(3, 0xFF6366F1);
        rootLayout.setBackground(containerBg);
        rootLayout.setElevation(25f);

        // Header
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextColor(0xFF4ADE80);
        dot.setTextSize(12f);
        headerRow.addView(dot);

        TextView header = new TextView(this);
        header.setText("  AXP  ·  AUTO");
        header.setTextColor(0xFF818CF8);
        header.setTextSize(13f);
        header.setLetterSpacing(0.2f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        headerRow.addView(header);

        rootLayout.addView(headerRow);

        // Divider
        View divider = new View(this);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2);
        dlp.setMargins(0, 10, 0, 10);
        divider.setLayoutParams(dlp);
        divider.setBackgroundColor(0xFF1E1E35);
        rootLayout.addView(divider);

        // Stats
        statsText = new TextView(this);
        statsText.setText("Taps  0    ·    Idle");
        statsText.setTextColor(0xFF6B6B80);
        statsText.setTextSize(10f);
        statsText.setGravity(Gravity.CENTER);
        statsText.setPadding(0, 0, 0, 12);
        rootLayout.addView(statsText);

        // DRONE row
        TextView[] droneOut = new TextView[1];
        rootLayout.addView(makeRow("DRONE  ·  61s", "OFF", 0xFF1E1E35,
                v -> toggleDrone(), droneOut));
        droneState = droneOut[0];

        // FORWARD row
        TextView[] fwdOut = new TextView[1];
        rootLayout.addView(makeRow("FORWARD", "OFF", 0xFF1E1E35,
                v -> toggleFwd(), fwdOut));
        fwdState = fwdOut[0];

        // RIGHT row
        TextView[] rightOut = new TextView[1];
        rootLayout.addView(makeRow("RIGHT", "OFF", 0xFF1E1E35,
                v -> toggleRight(), rightOut));
        rightState = rightOut[0];

        // LIFT row
        TextView[] liftOut = new TextView[1];
        rootLayout.addView(makeRow("LIFT", "OFF", 0xFF1E1E35,
                v -> toggleLift(), liftOut));
        liftState = liftOut[0];

        // HIDE row
        TextView[] hideOut = new TextView[1];
        rootLayout.addView(makeRow("PANEL", "HIDE", 0xFF1E1E35,
                v -> hideOverlay(), hideOut));

        // CLOSE row
        TextView[] closeOut = new TextView[1];
        LinearLayout closeRow = makeRow("STOP", "CLOSE", 0xFF2A1414,
                v -> stopSelf(), closeOut);
        ((TextView) closeRow.getChildAt(1)).setTextColor(0xFFEF4444);
        rootLayout.addView(closeRow);

        // Draggable
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
                        if (Math.abs(dx) > 12 || Math.abs(dy) > 12) dragging = true;
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
        tab.setTextSize(18f);
        tab.setTypeface(Typeface.DEFAULT_BOLD);
        tab.setGravity(Gravity.CENTER);
        tab.setWidth(90);
        tab.setHeight(90);

        GradientDrawable tabBg = bgGrad(0xFF6366F1, 0xFF4F46E5, 50f);
        tabBg.setStroke(3, 0xFF818CF8);
        tab.setBackground(tabBg);
        tab.setElevation(20f);

        tab.setVisibility(View.GONE);
        tab.setClickable(true);
        tab.setFocusable(true);
        tab.setOnClickListener(v -> showOverlay());

        tab.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY;
            float touchX, touchY;
            boolean dragging = false;
            WindowManager.LayoutParams lp;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                lp = (WindowManager.LayoutParams) tab.getLayoutParams();
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
                            lp.x = startX + dx;
                            lp.y = startY + dy;
                            wm.updateViewLayout(tab, lp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging) {
                            // tap → show panel
                            v.performClick();
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
                if (s != null && droneOn) {
                    long elapsed = (System.currentTimeMillis() - s.startTime) / 1000;
                    long mins = elapsed / 60;
                    long secs = elapsed % 60;
                    statsText.setText(String.format("Taps  %d    ·    %02d:%02d",
                            s.tapCount, mins, secs));
                } else {
                    int t = (s != null ? s.tapCount : 0);
                    statsText.setText("Taps  " + t + "    ·    Idle");
                }
                statsHandler.postDelayed(this, 2000);
            }
        }, 2000);
    }

    private void hideOverlay() {
        rootLayout.setVisibility(View.GONE);
        hideTab.setVisibility(View.VISIBLE);
        hideTab.bringToFront();
    }

    private void showOverlay() {
        rootLayout.setVisibility(View.VISIBLE);
        hideTab.setVisibility(View.GONE);
        rootLayout.bringToFront();
    }

    private void toggleDrone() {
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        droneOn = !droneOn;
        if (droneOn) {
            s.startDrone();
            droneState.setText("ON  ·  61s");
            droneState.setTextColor(0xFF4ADE80);
            droneState.setBackground(bgGrad(0xFF0F2A1F, 0xFF0A1A12, 28f));
        } else {
            s.stopDrone();
            droneState.setText("OFF");
            droneState.setTextColor(0xFFFFFFFF);
            droneState.setBackground(bgSolid(0xFF1E1E35, 28f));
        }
    }

    private void toggleFwd() {
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        // stop others first
        if (rightOn) { rightOn = false; resetBtn(rightState, "OFF"); }
        if (liftOn) { liftOn = false; resetBtn(liftState, "OFF"); }
        fwdOn = !fwdOn;
        if (fwdOn) {
            s.startMove(AutoTapService.DIR_FORWARD);
            fwdState.setText("ON  ·  FWD");
            fwdState.setTextColor(0xFF4ADE80);
            fwdState.setBackground(bgGrad(0xFF0F2A1F, 0xFF0A1A12, 28f));
        } else {
            s.stopMove();
            resetBtn(fwdState, "OFF");
        }
    }

    private void toggleRight() {
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        if (fwdOn) { fwdOn = false; resetBtn(fwdState, "OFF"); }
        if (liftOn) { liftOn = false; resetBtn(liftState, "OFF"); }
        rightOn = !rightOn;
        if (rightOn) {
            s.startMove(AutoTapService.DIR_RIGHT);
            rightState.setText("ON  ·  R");
            rightState.setTextColor(0xFF4ADE80);
            rightState.setBackground(bgGrad(0xFF0F2A1F, 0xFF0A1A12, 28f));
        } else {
            s.stopMove();
            resetBtn(rightState, "OFF");
        }
    }

    private void toggleLift() {
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        if (fwdOn) { fwdOn = false; resetBtn(fwdState, "OFF"); }
        if (rightOn) { rightOn = false; resetBtn(rightState, "OFF"); }
        liftOn = !liftOn;
        if (liftOn) {
            s.startMove(AutoTapService.DIR_LIFT);
            liftState.setText("ON  ·  LIFT");
            liftState.setTextColor(0xFF4ADE80);
            liftState.setBackground(bgGrad(0xFF0F2A1F, 0xFF0A1A12, 28f));
        } else {
            s.stopMove();
            resetBtn(liftState, "OFF");
        }
    }

    private void resetBtn(TextView btn, String text) {
        if (btn == null) return;
        btn.setText(text);
        btn.setTextColor(0xFFFFFFFF);
        btn.setBackground(bgSolid(0xFF1E1E35, 28f));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AutoTapService s = AutoTapService.instance;
        if (s != null) { s.stopDrone(); s.stopMove(); s.saveConfig(); }
        if (overlay != null) { try { wm.removeView(overlay); } catch (Exception e) {} }
        if (hideTab != null) { try { wm.removeView(hideTab); } catch (Exception e) {} }
        statsHandler.removeCallbacksAndMessages(null);
    }
}
