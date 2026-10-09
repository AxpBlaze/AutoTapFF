package com.kovak.autotap;

import android.app.AlertDialog;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
    private TextView droneState, startState, skillState;
    private TextView fwdState, rightState, liftState, rotateState;
    private TextView statsText;
    private TextView apiStatusText, apiExpiryText;

    private boolean droneOn, startOn, skillOn, fwdOn, rightOn, liftOn, rotateOn;

    private Handler statsHandler = new Handler(Looper.getMainLooper());
    private Handler apiRefreshHandler = new Handler(Looper.getMainLooper());

    private static final String PREFS = "axp_prefs";

    private boolean pickerActive = false;
    private View pickerView;
    private String pendingPickerType = null;

    // ============ CYAN THEME ============
    private static final int C_BG1       = 0xF0050A12;
    private static final int C_BG2       = 0xF0000208;
    private static final int C_BORDER    = 0xFF00E5FF;
    private static final int C_SURFACE   = 0xFF0A1A22;
    private static final int C_TEXT      = 0xFFFFFFFF;
    private static final int C_MUTED     = 0xFF7DD3E0;
    private static final int C_DIM       = 0xFF5B7A88;
    private static final int C_HEADER    = 0xFF00E5FF;
    private static final int C_GREEN     = 0xFF22FFA0;
    private static final int C_GREEN_BG1 = 0xFF0A2A22;
    private static final int C_GREEN_BG2 = 0xFF041812;
    private static final int C_RED       = 0xFFFF4D6D;
    private static final int C_RED_BG    = 0xFF2A0A14;
    private static final int C_CYAN_BG   = 0xFF001E28;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        buildOverlay();
        buildHideTab();
        refreshCustomButtons();
        startStatsUpdater();
        startApiRefresh();
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

    private GradientDrawable bgStroke(int fill, int stroke, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        g.setStroke(2, stroke);
        return g;
    }

    private LinearLayout makeRow(String labelText, String btnText, TextView[] stateOut,
                                  View.OnClickListener listener) {
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
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(label);

        TextView btn = new TextView(this);
        btn.setText(btnText);
        btn.setTextColor(C_TEXT);
        btn.setTextSize(11f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(24, 16, 24, 16);
        btn.setBackground(bgStroke(C_CYAN_BG, C_BORDER, 24f));
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
        rootLayout.setPadding(32, 28, 32, 28);

        GradientDrawable containerBg = bgGrad(C_BG1, C_BG2, 42f);
        containerBg.setStroke(3, C_BORDER);
        rootLayout.setBackground(containerBg);
        rootLayout.setElevation(25f);

        // Header
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView dot = new TextView(this);
        dot.setText("◆");
        dot.setTextColor(C_HEADER);
        dot.setTextSize(13f);
        headerRow.addView(dot);

        TextView header = new TextView(this);
        header.setText("  AXP · GAMING PANEL");
        header.setTextColor(C_HEADER);
        header.setTextSize(13f);
        header.setLetterSpacing(0.15f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        headerRow.addView(header);

        rootLayout.addView(headerRow);

        // Divider
        View d1 = new View(this);
        LinearLayout.LayoutParams dlp1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2);
        dlp1.setMargins(0, 10, 0, 10);
        d1.setLayoutParams(dlp1);
        d1.setBackgroundColor(C_SURFACE);
        rootLayout.addView(d1);

        // Stats
        statsText = new TextView(this);
        statsText.setText("Taps  0    ·    Idle");
        statsText.setTextColor(C_DIM);
        statsText.setTextSize(10f);
        statsText.setGravity(Gravity.CENTER);
        statsText.setPadding(0, 0, 0, 12);
        rootLayout.addView(statsText);

        // DRONE
        TextView[] dOut = new TextView[1];
        rootLayout.addView(makeRow("DRONE  ·  61s", "OFF", dOut, v -> toggleDrone()));
        droneState = dOut[0];

        // START
        TextView[] stOut = new TextView[1];
        rootLayout.addView(makeRow("START  ·  9min", "OFF", stOut, v -> toggleStart()));
        startState = stOut[0];

        // SKILL
        TextView[] skOut = new TextView[1];
        rootLayout.addView(makeRow("SKILL  ·  90s", "OFF", skOut, v -> toggleSkill()));
        skillState = skOut[0];

        // Divider
        View d2 = new View(this);
        LinearLayout.LayoutParams dlp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2);
        dlp2.setMargins(0, 10, 0, 10);
        d2.setLayoutParams(dlp2);
        d2.setBackgroundColor(C_SURFACE);
        rootLayout.addView(d2);

        // Movement header
        TextView mvHeader = new TextView(this);
        mvHeader.setText("MOVEMENT");
        mvHeader.setTextColor(C_HEADER);
        mvHeader.setTextSize(10f);
        mvHeader.setLetterSpacing(0.15f);
        mvHeader.setTypeface(Typeface.DEFAULT_BOLD);
        mvHeader.setPadding(0, 0, 0, 8);
        rootLayout.addView(mvHeader);

        // FORWARD
        TextView[] fOut = new TextView[1];
        rootLayout.addView(makeRow("FORWARD", "OFF", fOut, v -> toggleFwd()));
        fwdState = fOut[0];

        // RIGHT
        TextView[] rOut = new TextView[1];
        rootLayout.addView(makeRow("RIGHT", "OFF", rOut, v -> toggleRight()));
        rightState = rOut[0];

        // LIFT
        TextView[] lOut = new TextView[1];
        rootLayout.addView(makeRow("LIFT", "OFF", lOut, v -> toggleLift()));
        liftState = lOut[0];

        // ROTATE 120
        TextView[] roOut = new TextView[1];
        rootLayout.addView(makeRow("ROTATE  ·  120°", "OFF", roOut, v -> toggleRotate()));
        rotateState = roOut[0];

        // Divider
        View d3 = new View(this);
        LinearLayout.LayoutParams dlp3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2);
        dlp3.setMargins(0, 10, 0, 10);
        d3.setLayoutParams(dlp3);
        d3.setBackgroundColor(C_SURFACE);
        rootLayout.addView(d3);

        // Custom header
        TextView cHeader = new TextView(this);
        cHeader.setText("CUSTOM BUTTONS");
        cHeader.setTextColor(C_HEADER);
        cHeader.setTextSize(10f);
        cHeader.setLetterSpacing(0.15f);
        cHeader.setTypeface(Typeface.DEFAULT_BOLD);
        cHeader.setPadding(0, 0, 0, 8);
        rootLayout.addView(cHeader);

        btnContainer = new LinearLayout(this);
        btnContainer.setOrientation(LinearLayout.VERTICAL);
        rootLayout.addView(btnContainer);

        // ADD button
        TextView addBtn = new TextView(this);
        addBtn.setText("+   ADD  BUTTON");
        addBtn.setTextColor(C_HEADER);
        addBtn.setTextSize(11f);
        addBtn.setTypeface(Typeface.DEFAULT_BOLD);
        addBtn.setGravity(Gravity.CENTER);
        addBtn.setPadding(20, 20, 20, 20);
        addBtn.setBackground(bgStroke(C_CYAN_BG, C_BORDER, 24f));
        addBtn.setElevation(4f);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        addLp.setMargins(0, 6, 0, 6);
        addBtn.setLayoutParams(addLp);
        addBtn.setOnClickListener(v -> startCustomPicker());
        rootLayout.addView(addBtn);

        // Divider
        View d4 = new View(this);
        LinearLayout.LayoutParams dlp4 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2);
        dlp4.setMargins(0, 10, 0, 10);
        d4.setLayoutParams(dlp4);
        d4.setBackgroundColor(C_SURFACE);
        rootLayout.addView(d4);

        // API KEY header
        TextView aHeader = new TextView(this);
        aHeader.setText("API KEY");
        aHeader.setTextColor(C_HEADER);
        aHeader.setTextSize(10f);
        aHeader.setLetterSpacing(0.15f);
        aHeader.setTypeface(Typeface.DEFAULT_BOLD);
        aHeader.setPadding(0, 0, 0, 8);
        rootLayout.addView(aHeader);

        apiStatusText = new TextView(this);
        apiStatusText.setText("Not set");
        apiStatusText.setTextColor(C_DIM);
        apiStatusText.setTextSize(11f);
        apiStatusText.setTypeface(Typeface.DEFAULT_BOLD);
        apiStatusText.setPadding(0, 0, 0, 4);
        rootLayout.addView(apiStatusText);

        apiExpiryText = new TextView(this);
        apiExpiryText.setText("Expires: —");
        apiExpiryText.setTextColor(C_DIM);
        apiExpiryText.setTextSize(10f);
        apiExpiryText.setPadding(0, 0, 0, 8);
        rootLayout.addView(apiExpiryText);

        TextView setApiBtn = new TextView(this);
        setApiBtn.setText("SET  API  KEY");
        setApiBtn.setTextColor(C_HEADER);
        setApiBtn.setTextSize(11f);
        setApiBtn.setTypeface(Typeface.DEFAULT_BOLD);
        setApiBtn.setGravity(Gravity.CENTER);
        setApiBtn.setPadding(20, 18, 20, 18);
        setApiBtn.setBackground(bgStroke(C_CYAN_BG, C_BORDER, 24f));
        setApiBtn.setElevation(4f);
        LinearLayout.LayoutParams sapLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sapLp.setMargins(0, 4, 0, 4);
        setApiBtn.setLayoutParams(sapLp);
        setApiBtn.setOnClickListener(v -> showApiKeyDialog());
        rootLayout.addView(setApiBtn);

        // Divider
        View d5 = new View(this);
        LinearLayout.LayoutParams dlp5 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2);
        dlp5.setMargins(0, 10, 0, 10);
        d5.setLayoutParams(dlp5);
        d5.setBackgroundColor(C_SURFACE);
        rootLayout.addView(d5);

        // HIDE
        TextView[] hOut = new TextView[1];
        rootLayout.addView(makeRow("PANEL", "HIDE", hOut, v -> hideOverlay()));

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
        closeBtn.setPadding(24, 16, 24, 16);
        closeBtn.setBackground(bgStroke(C_RED_BG, C_RED, 24f));
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

    // ============ API DIALOG ============
    private void showApiKeyDialog() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String currentKey = p.getString("api_key", "");
        String currentServer = p.getString("api_server", "http://127.0.0.1:9000");

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 40, 40, 40);

        TextView lbl1 = new TextView(this);
        lbl1.setText("Server URL (apihub)");
        lbl1.setTextColor(0xFF000000);
        box.addView(lbl1);

        EditText serverIn = new EditText(this);
        serverIn.setText(currentServer);
        serverIn.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(serverIn);

        TextView lbl2 = new TextView(this);
        lbl2.setText("API Key");
        lbl2.setTextColor(0xFF000000);
        box.addView(lbl2);

        EditText keyIn = new EditText(this);
        keyIn.setText(currentKey);
        keyIn.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(keyIn);

        new AlertDialog.Builder(this)
                .setTitle("API KEY  ·  SETUP")
                .setView(box)
                .setPositiveButton("SAVE & VALIDATE", (d, w) -> {
                    String s = serverIn.getText().toString().trim();
                    String k = keyIn.getText().toString().trim();
                    if (s.isEmpty() || k.isEmpty()) return;
                    p.edit().putString("api_server", s).putString("api_key", k).apply();
                    apiStatusText.setText("Validating...");
                    apiStatusText.setTextColor(C_MUTED);
                    validateApiKey(s, k);
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void validateApiKey(String server, String key) {
        ApiValidator.validate(server, key, (valid, msg, expiry) -> {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            p.edit()
                    .putBoolean("api_valid", valid)
                    .putLong("api_expiry", expiry)
                    .apply();
            updateApiStatus();
        });
    }

    private void updateApiStatus() {
        if (apiStatusText == null) return;
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean valid = p.getBoolean("api_valid", false);
        long expiry = p.getLong("api_expiry", 0);

        if (valid) {
            apiStatusText.setText("● ACTIVE");
            apiStatusText.setTextColor(C_GREEN);
            if (expiry > 0) {
                long left = expiry - System.currentTimeMillis();
                if (left > 0) {
                    long secs = left / 1000;
                    long days = secs / 86400;
                    long hrs = (secs % 86400) / 3600;
                    long mins = (secs % 3600) / 60;
                    apiExpiryText.setText("Expires: " + days + "d " + hrs + "h " + mins + "m");
                } else {
                    apiExpiryText.setText("EXPIRED");
                    apiStatusText.setText("● EXPIRED");
                    apiStatusText.setTextColor(C_RED);
                }
            } else {
                apiExpiryText.setText("Expires: never");
            }
        } else {
            apiStatusText.setText("● INACTIVE");
            apiStatusText.setTextColor(C_RED);
            apiExpiryText.setText("Set key to enable features");
        }
    }

    private void startApiRefresh() {
        apiRefreshHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateApiStatus();
                SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
                String s = p.getString("api_server", "");
                String k = p.getString("api_key", "");
                if (!s.isEmpty() && !k.isEmpty()) {
                    validateApiKey(s, k);
                }
                apiRefreshHandler.postDelayed(this, 5 * 60 * 1000L);
            }
        }, 60000);
    }

    private boolean isApiValid() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!p.getBoolean("api_valid", false)) return false;
        long exp = p.getLong("api_expiry", 0);
        if (exp > 0 && exp < System.currentTimeMillis()) return false;
        return true;
    }

    // ============ CUSTOM BUTTONS ============
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

            TextView label = new TextView(this);
            label.setText(b.name);
            label.setTextColor(C_MUTED);
            label.setTextSize(11f);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(label);

            TextView del = new TextView(this);
            del.setText("×");
            del.setTextColor(C_RED);
            del.setTextSize(14f);
            del.setTypeface(Typeface.DEFAULT_BOLD);
            del.setGravity(Gravity.CENTER);
            del.setPadding(12, 8, 12, 8);
            del.setBackground(bgStroke(C_RED_BG, C_RED, 20f));
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

            TextView btn = new TextView(this);
            btn.setText(b.running ? "ON" : "OFF");
            btn.setTextColor(b.running ? C_GREEN : C_TEXT);
            btn.setTextSize(11f);
            btn.setTypeface(Typeface.DEFAULT_BOLD);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(24, 16, 24, 16);
            btn.setBackground(b.running
                    ? bgGrad(C_GREEN_BG1, C_GREEN_BG2, 24f)
                    : bgStroke(C_CYAN_BG, C_BORDER, 24f));
            btn.setMinWidth(120);
            btn.setElevation(4f);
            btn.setOnClickListener(v -> {
                if (s == null) return;
                if (b.running) s.stopCustomBtn(b);
                else s.startCustomBtn(b);
                refreshCustomButtons();
            });
            row.addView(btn);

            btnContainer.addView(row);
        }
    }

    private void startCustomPicker() {
        pendingPickerType = "custom";
        showPicker();
    }

    private void showPicker() {
        hideOverlay();

        final TextView hint = new TextView(this);
        hint.setText("\n\n\n\n\n\nTAP  TO  SET  POSITION\n\nJahan button chahiye wahan tap karo");
        hint.setTextColor(C_HEADER);
        hint.setTextSize(15f);
        hint.setTypeface(Typeface.DEFAULT_BOLD);
        hint.setGravity(Gravity.CENTER);
        hint.setBackgroundColor(0x99000000);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams pp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        pp.gravity = Gravity.TOP | Gravity.START;

        pickerView = hint;
        pickerView.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                float xp = e.getRawX() / getResources().getDisplayMetrics().widthPixels;
                float yp = e.getRawY() / getResources().getDisplayMetrics().heightPixels;

                try { wm.removeView(pickerView); } catch (Exception ex) {}
                pickerView = null;
                pickerActive = false;

                if ("custom".equals(pendingPickerType)) {
                    promptCustomName(xp, yp);
                }
                return true;
            }
            return false;
        });

        wm.addView(pickerView, pp);
        pickerActive = true;
    }

    private void promptCustomName(float xp, float yp) {
        final EditText input = new EditText(this);
        input.setHint("e.g. Fire, Jump, Zone");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle("BUTTON  NAME")
                .setView(input)
                .setPositiveButton("ADD", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = "BTN";
                    AutoTapService s = AutoTapService.instance;
                    if (s != null) {
                        s.addCustomBtn(name, xp, yp, 61000, true);
                        refreshCustomButtons();
                    }
                    showOverlay();
                })
                .setNegativeButton("CANCEL", (d, w) -> showOverlay())
                .setCancelable(false)
                .show();
    }

    private void buildHideTab() {
        TextView tab = new TextView(this);
        tab.setText("A");
        tab.setTextColor(C_TEXT);
        tab.setTextSize(18f);
        tab.setTypeface(Typeface.DEFAULT_BOLD);
        tab.setGravity(Gravity.CENTER);
        tab.setWidth(90);
        tab.setHeight(90);

        GradientDrawable tabBg = bgGrad(C_BORDER, 0xFF007A99, 50f);
        tabBg.setStroke(3, C_HEADER);
        tab.setBackground(tabBg);
        tab.setElevation(20f);

        tab.setVisibility(View.GONE);

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
                if (s != null && (droneOn || startOn || skillOn)) {
                    long el = (System.currentTimeMillis() - s.startTime) / 1000;
                    statsText.setText(String.format("Taps  %d    ·    %02d:%02d",
                            s.tapCount, el / 60, el % 60));
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

    // ============ FRIENDLY API CHECK ============
    private boolean checkApi() {
        if (isApiValid()) return true;

        new AlertDialog.Builder(this)
                .setTitle("⚡  API KEY REQUIRED")
                .setMessage("Pehle apni API key daalo.\n\nWahi key jo tumne Spy Bot website (AXP HUB) se generate ki thi.")
                .setPositiveButton("SET  API  KEY", (d, w) -> showApiKeyDialog())
                .setNegativeButton("CANCEL", null)
                .show();
        return false;
    }

    private void setBtn(TextView btn, boolean on, String onText) {
        if (on) {
            btn.setText(onText);
            btn.setTextColor(C_GREEN);
            btn.setBackground(bgGrad(C_GREEN_BG1, C_GREEN_BG2, 24f));
        } else {
            btn.setText("OFF");
            btn.setTextColor(C_TEXT);
            btn.setBackground(bgStroke(C_CYAN_BG, C_BORDER, 24f));
        }
    }

    private void toggleDrone() {
        if (!checkApi()) return;
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        droneOn = !droneOn;
        if (droneOn) { s.startDrone(); setBtn(droneState, true, "ON · 61s"); }
        else { s.stopDrone(); setBtn(droneState, false, ""); }
    }

    private void toggleStart() {
        if (!checkApi()) return;
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        startOn = !startOn;
        if (startOn) { s.startStartButton(); setBtn(startState, true, "ON · 9min"); }
        else { s.stopStartButton(); setBtn(startState, false, ""); }
    }

    private void toggleSkill() {
        if (!checkApi()) return;
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        skillOn = !skillOn;
        if (skillOn) { s.startSkillButton(); setBtn(skillState, true, "ON · 90s"); }
        else { s.stopSkillButton(); setBtn(skillState, false, ""); }
    }

    private void toggleFwd() {
        if (!checkApi()) return;
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        fwdOn = !fwdOn;
        s.setDirection(AutoTapService.DIR_FORWARD, fwdOn);
        setBtn(fwdState, fwdOn, "ON");
    }

    private void toggleRight() {
        if (!checkApi()) return;
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        rightOn = !rightOn;
        s.setDirection(AutoTapService.DIR_RIGHT, rightOn);
        setBtn(rightState, rightOn, "ON");
    }

    private void toggleLift() {
        if (!checkApi()) return;
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        liftOn = !liftOn;
        s.setDirection(AutoTapService.DIR_LIFT, liftOn);
        setBtn(liftState, liftOn, "ON");
    }

    private void toggleRotate() {
        if (!checkApi()) return;
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        rotateOn = !rotateOn;
        if (rotateOn) { s.startRotate(); setBtn(rotateState, true, "ON · 120°"); }
        else { s.stopRotate(); setBtn(rotateState, false, ""); }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AutoTapService s = AutoTapService.instance;
        if (s != null) { s.stopDrone(); s.stopStartButton(); s.stopSkillButton(); s.stopRotate(); s.saveConfig(); }
        if (overlay != null) { try { wm.removeView(overlay); } catch (Exception e) {} }
        if (hideTab != null) { try { wm.removeView(hideTab); } catch (Exception e) {} }
        if (pickerView != null) { try { wm.removeView(pickerView); } catch (Exception e) {} }
        statsHandler.removeCallbacksAndMessages(null);
        apiRefreshHandler.removeCallbacksAndMessages(null);
    }
}
