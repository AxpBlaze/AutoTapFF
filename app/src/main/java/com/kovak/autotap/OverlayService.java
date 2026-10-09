package com.kovak.autotap;

import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class OverlayService extends Service {

    private WindowManager wm;
    private View overlay;
    private View hideTab;
    private LinearLayout rootLayout;
    private LinearLayout btnContainer;
    private TextView droneState, fwdState, rightState, liftState;
    private TextView statsText;

    private boolean droneOn = false;
    private boolean fwdOn = false;
    private boolean rightOn = false;
    private boolean liftOn = false;

    private Handler statsHandler = new Handler(Looper.getMainLooper());

    // Picker mode
    private boolean pickerActive = false;
    private View pickerView;
    private WindowManager.LayoutParams pickerParams;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        buildOverlay();
        buildHideTab();
        refreshCustomButtons();
        startStatsUpdater();
    }

    // ================= COLORS =================
    private static final int C_BG1       = 0xF010101F;
    private static final int C_BG2       = 0xF00A0A14;
    private static final int C_BORDER    = 0xFF6366F1;
    private static final int C_SURFACE   = 0xFF1E1E35;
    private static final int C_TEXT      = 0xFFFFFFFF;
    private static final int C_MUTED     = 0xFF9CA3AF;
    private static final int C_DIM       = 0xFF6B6B80;
    private static final int C_HEADER    = 0xFF818CF8;
    private static final int C_GREEN     = 0xFF4ADE80;
    private static final int C_GREEN_BG1 = 0xFF0F2A1F;
    private static final int C_GREEN_BG2 = 0xFF0A1A12;
    private static final int C_RED       = 0xFFEF4444;
    private static final int C_RED_BG    = 0xFF2A1414;
    private static final int C_YELLOW    = 0xFFFFD200;

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
        rlp.setMargins(0, 4, 0, 4);
        row.setLayoutParams(rlp);

        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(C_MUTED);
        label.setTextSize(11f);
        label.setLetterSpacing(0.12f);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        label.setLayoutParams(llp);
        row.addView(label);

        TextView btn = new TextView(this);
        btn.setText(btnText);
        btn.setTextColor(C_TEXT);
        btn.setTextSize(11f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(24, 18, 24, 18);
        btn.setBackground(bgSolid(btnColor, 26f));
        btn.setElevation(4f);
        btn.setMinWidth(120);
        btn.setOnClickListener(listener);
        row.addView(btn);

        if (stateOut != null) stateOut[0] = btn;
        return row;
    }

    private void buildOverlay() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(32, 30, 32, 30);

        GradientDrawable containerBg = bgGrad(C_BG1, C_BG2, 45f);
        containerBg.setStroke(3, C_BORDER);
        rootLayout.setBackground(containerBg);
        rootLayout.setElevation(25f);

        // Header
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextColor(C_GREEN);
        dot.setTextSize(12f);
        headerRow.addView(dot);
        TextView header = new TextView(this);
        header.setText("  AXP  ·  AUTO");
        header.setTextColor(C_HEADER);
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
        divider.setBackgroundColor(C_SURFACE);
        rootLayout.addView(divider);

        // Stats
        statsText = new TextView(this);
        statsText.setText("Taps  0    ·    Idle");
        statsText.setTextColor(C_DIM);
        statsText.setTextSize(10f);
        statsText.setGravity(Gravity.CENTER);
        statsText.setPadding(0, 0, 0, 12);
        rootLayout.addView(statsText);

        // DRONE
        TextView[] droneOut = new TextView[1];
        rootLayout.addView(makeRow("DRONE  ·  61s", "OFF", C_SURFACE,
                v -> toggleDrone(), droneOut));
        droneState = droneOut[0];

        // FORWARD
        TextView[] fwdOut = new TextView[1];
        rootLayout.addView(makeRow("FORWARD", "OFF", C_SURFACE,
                v -> toggleFwd(), fwdOut));
        fwdState = fwdOut[0];

        // RIGHT
        TextView[] rightOut = new TextView[1];
        rootLayout.addView(makeRow("RIGHT", "OFF", C_SURFACE,
                v -> toggleRight(), rightOut));
        rightState = rightOut[0];

        // LIFT
        TextView[] liftOut = new TextView[1];
        rootLayout.addView(makeRow("LIFT", "OFF", C_SURFACE,
                v -> toggleLift(), liftOut));
        liftState = liftOut[0];

        // Divider
        View divider2 = new View(this);
        LinearLayout.LayoutParams dlp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2);
        dlp2.setMargins(0, 10, 0, 10);
        divider2.setLayoutParams(dlp2);
        divider2.setBackgroundColor(C_SURFACE);
        rootLayout.addView(divider2);

        // Custom buttons header
        TextView customHeader = new TextView(this);
        customHeader.setText("CUSTOM BUTTONS");
        customHeader.setTextColor(C_HEADER);
        customHeader.setTextSize(10f);
        customHeader.setLetterSpacing(0.15f);
        customHeader.setTypeface(Typeface.DEFAULT_BOLD);
        customHeader.setPadding(0, 0, 0, 8);
        rootLayout.addView(customHeader);

        // Custom buttons container
        btnContainer = new LinearLayout(this);
        btnContainer.setOrientation(LinearLayout.VERTICAL);
        rootLayout.addView(btnContainer);

        // + ADD button
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        addLp.setMargins(0, 6, 0, 6);
        TextView addBtn = new TextView(this);
        addBtn.setText("+   ADD  BUTTON");
        addBtn.setTextColor(C_HEADER);
        addBtn.setTextSize(11f);
        addBtn.setTypeface(Typeface.DEFAULT_BOLD);
        addBtn.setGravity(Gravity.CENTER);
        addBtn.setPadding(20, 22, 20, 22);
        GradientDrawable addBg = bgSolid(C_SURFACE, 26f);
        addBg.setStroke(2, C_BORDER);
        addBtn.setBackground(addBg);
        addBtn.setElevation(4f);
        addBtn.setLayoutParams(addLp);
        addBtn.setOnClickListener(v -> startPicker());
        rootLayout.addView(addBtn);

        // Divider
        View divider3 = new View(this);
        LinearLayout.LayoutParams dlp3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2);
        dlp3.setMargins(0, 10, 0, 10);
        divider3.setLayoutParams(dlp3);
        divider3.setBackgroundColor(C_SURFACE);
        rootLayout.addView(divider3);

        // HIDE
        TextView[] hideOut = new TextView[1];
        rootLayout.addView(makeRow("PANEL", "HIDE", C_SURFACE,
                v -> hideOverlay(), hideOut));

        // CLOSE
        LinearLayout closeRow = new LinearLayout(this);
        closeRow.setOrientation(LinearLayout.HORIZONTAL);
        closeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView closeLabel = new TextView(this);
        closeLabel.setText("STOP");
        closeLabel.setTextColor(C_MUTED);
        closeLabel.setTextSize(11f);
        closeLabel.setTypeface(Typeface.DEFAULT_BOLD);
        closeLabel.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        closeRow.addView(closeLabel);
        TextView closeBtn = new TextView(this);
        closeBtn.setText("CLOSE");
        closeBtn.setTextColor(C_RED);
        closeBtn.setTextSize(11f);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setPadding(24, 18, 24, 18);
        closeBtn.setBackground(bgSolid(C_RED_BG, 26f));
        closeBtn.setMinWidth(120);
        closeBtn.setElevation(4f);
        closeBtn.setOnClickListener(v -> stopSelf());
        closeRow.addView(closeBtn);
        rootLayout.addView(closeRow);

        // Draggable
        rootLayout.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY;
            float touchX, touchY;
            boolean dragging = false;
            WindowManager.LayoutParams lp;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                lp = (WindowManager.LayoutParams) overlay.getLayoutParams();
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
                            wm.updateViewLayout(overlay, lp);
                        }
                        return true;
                }
                return false;
            }
        });

        scroll.addView(rootLayout);

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

        overlay = scroll;
        wm.addView(overlay, params);
    }

    // ============ CUSTOM BUTTONS RENDER ============
    private void refreshCustomButtons() {
        if (btnContainer == null) return;
        btnContainer.removeAllViews();
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;

        for (AutoTapService.CustomBtn b : s.customBtns) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.setMargins(0, 4, 0, 4);
            row.setLayoutParams(rlp);

            // Label
            TextView label = new TextView(this);
            label.setText(b.name);
            label.setTextColor(C_MUTED);
            label.setTextSize(11f);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(label);

            // Delete btn (small)
            TextView del = new TextView(this);
            del.setText("×");
            del.setTextColor(C_RED);
            del.setTextSize(14f);
            del.setTypeface(Typeface.DEFAULT_BOLD);
            del.setGravity(Gravity.CENTER);
            del.setPadding(14, 10, 14, 10);
            del.setBackground(bgSolid(C_RED_BG, 20f));
            del.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Delete?")
                        .setMessage("Remove '" + b.name + "'?")
                        .setPositiveButton("Yes", (d, w) -> {
                            if (s != null) {
                                s.removeCustomBtn(b);
                                refreshCustomButtons();
                            }
                        })
                        .setNegativeButton("No", null)
                        .show();
            });
            LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            delLp.setMargins(0, 0, 6, 0);
            del.setLayoutParams(delLp);
            row.addView(del);

            // Main btn
            TextView btn = new TextView(this);
            btn.setText(b.running ? "ON" : "OFF");
            btn.setTextColor(b.running ? C_GREEN : C_TEXT);
            btn.setTextSize(11f);
            btn.setTypeface(Typeface.DEFAULT_BOLD);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(24, 18, 24, 18);
            btn.setBackground(b.running
                    ? bgGrad(C_GREEN_BG1, C_GREEN_BG2, 26f)
                    : bgSolid(C_SURFACE, 26f));
            btn.setMinWidth(120);
            btn.setElevation(4f);
            btn.setOnClickListener(v -> {
                if (s == null) return;
                if (b.running) {
                    s.stopCustomBtn(b);
                } else {
                    s.startCustomBtn(b);
                }
                refreshCustomButtons();
            });
            row.addView(btn);

            btnContainer.addView(row);
        }
    }

    // ============ PICKER MODE ============
    private void startPicker() {
        // 1. Hide panel
        hideOverlay();

        // 2. Show fullscreen transparent picker
        pickerActive = true;

        final TextView hint = new TextView(this);
        hint.setText("TAP  TO  BUTTON\n\nScreen pe jahan tap karo, wahan button set hoga");
        hint.setTextColor(0xFFFFFFFF);
        hint.setTextSize(14f);
        hint.setTypeface(Typeface.DEFAULT_BOLD);
        hint.setGravity(Gravity.CENTER);
        hint.setBackgroundColor(0x88000000);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        pickerParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        pickerParams.gravity = Gravity.TOP | Gravity.START;
        pickerParams.x = 0; pickerParams.y = 0;

        pickerView = hint;
        pickerView.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                float xPercent = e.getRawX() / getResources().getDisplayMetrics().widthPixels;
                float yPercent = e.getRawY() / getResources().getDisplayMetrics().heightPixels;

                // Remove picker
                try { wm.removeView(pickerView); } catch (Exception ex) {}
                pickerView = null;
                pickerActive = false;

                // Prompt name
                promptNameAndAdd(xPercent, yPercent);
                return true;
            }
            return false;
        });

        wm.addView(pickerView, pickerParams);
    }

    private void promptNameAndAdd(float xPercent, float yPercent) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("BUTTON  NAME");
        final EditText input = new EditText(this);
        input.setHint("e.g. Fire, Jump, Zone");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        b.setView(input);
        b.setPositiveButton("ADD", (d, w) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) name = "BTN";
            AutoTapService s = AutoTapService.instance;
            if (s != null) {
                s.addCustomBtn(name, xPercent, yPercent, 61000, true);
                refreshCustomButtons();
            }
            showOverlay();
        });
        b.setNegativeButton("CANCEL", (d, w) -> showOverlay());
        b.setCancelable(false);
        b.show();
    }

    // ============ HIDE TAB ============
    private void buildHideTab() {
        TextView tab = new TextView(this);
        tab.setText("A");
        tab.setTextColor(C_TEXT);
        tab.setTextSize(18f);
        tab.setTypeface(Typeface.DEFAULT_BOLD);
        tab.setGravity(Gravity.CENTER);
        tab.setWidth(90);
        tab.setHeight(90);

        GradientDrawable tabBg = bgGrad(C_BORDER, 0xFF4F46E5, 50f);
        tabBg.setStroke(3, C_HEADER);
        tab.setBackground(tabBg);
        tab.setElevation(20f);

        tab.setVisibility(View.GONE);
        tab.setClickable(true);
        tab.setFocusable(false);

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
                        if (!dragging) showOverlay();
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
        overlay.setVisibility(View.GONE);
        hideTab.setVisibility(View.VISIBLE);
    }

    private void showOverlay() {
        overlay.setVisibility(View.VISIBLE);
        hideTab.setVisibility(View.GONE);
    }

    private void toggleDrone() {
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        droneOn = !droneOn;
        if (droneOn) {
            s.startDrone();
            droneState.setText("ON  ·  61s");
            droneState.setTextColor(C_GREEN);
            droneState.setBackground(bgGrad(C_GREEN_BG1, C_GREEN_BG2, 26f));
        } else {
            s.stopDrone();
            droneState.setText("OFF");
            droneState.setTextColor(C_TEXT);
            droneState.setBackground(bgSolid(C_SURFACE, 26f));
        }
    }

    private void toggleFwd() {
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        fwdOn = !fwdOn;
        s.setDirection(AutoTapService.DIR_FORWARD, fwdOn);
        if (fwdOn) {
            fwdState.setText("ON");
            fwdState.setTextColor(C_GREEN);
            fwdState.setBackground(bgGrad(C_GREEN_BG1, C_GREEN_BG2, 26f));
        } else {
            fwdState.setText("OFF");
            fwdState.setTextColor(C_TEXT);
            fwdState.setBackground(bgSolid(C_SURFACE, 26f));
        }
    }

    private void toggleRight() {
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        rightOn = !rightOn;
        s.setDirection(AutoTapService.DIR_RIGHT, rightOn);
        if (rightOn) {
            rightState.setText("ON");
            rightState.setTextColor(C_GREEN);
            rightState.setBackground(bgGrad(C_GREEN_BG1, C_GREEN_BG2, 26f));
        } else {
            rightState.setText("OFF");
            rightState.setTextColor(C_TEXT);
            rightState.setBackground(bgSolid(C_SURFACE, 26f));
        }
    }

    private void toggleLift() {
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        liftOn = !liftOn;
        s.setDirection(AutoTapService.DIR_LIFT, liftOn);
        if (liftOn) {
            liftState.setText("ON");
            liftState.setTextColor(C_GREEN);
            liftState.setBackground(bgGrad(C_GREEN_BG1, C_GREEN_BG2, 26f));
        } else {
            liftState.setText("OFF");
            liftState.setTextColor(C_TEXT);
            liftState.setBackground(bgSolid(C_SURFACE, 26f));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AutoTapService s = AutoTapService.instance;
        if (s != null) { s.stopDrone(); s.saveConfig(); }
        if (overlay != null) { try { wm.removeView(overlay); } catch (Exception e) {} }
        if (hideTab != null) { try { wm.removeView(hideTab); } catch (Exception e) {} }
        if (pickerView != null) { try { wm.removeView(pickerView); } catch (Exception e) {} }
        statsHandler.removeCallbacksAndMessages(null);
    }
}
