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
    private TextView fwdState, rightState, liftState;
    private TextView rotP120, rotN120, rotP360, rotP210;
    private TextView statsText;
    private TextView apiStatusText, apiExpiryText;
    private TextView ffState, ffMaxState, altState;
    private boolean altOn;
    private boolean ffOn, ffMaxOn;

    private boolean droneOn, startOn, skillOn, fwdOn, rightOn, liftOn;
    private int activeRotate = -1; // -1 = none, 0/1/2/3 = mode

    private Handler statsHandler = new Handler(Looper.getMainLooper());
    private Handler apiRefreshHandler = new Handler(Looper.getMainLooper());

    private static final String PREFS = "axp_prefs";

    private boolean pickerActive = false;
    private View pickerView;
    private String pendingPickerType = null;

    // Cyan theme
    private static final int C_BG1       = 0xF0050A12;
    private static final int C_BG2       = 0xF0000208;
    private static final int C_BORDER    = 0xFF00E5FF;
    private static final int C_SURFACE   = 0xFF0A1A22;
    private static final int C_SLOT_BG   = 0xF0050F15;
    private static final int C_SLOT_BRD  = 0x5500E5FF;
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

    private GradientDrawable bgGrad(int c1, int c2, float r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setCornerRadius(r);
        return g;
    }
    private GradientDrawable bgSolid(int c, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(c); g.setCornerRadius(r); return g;
    }
    private GradientDrawable bgStroke(int fill, int stroke, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(r); g.setStroke(2, stroke); return g;
    }

    // ---- Slot container ----
    private LinearLayout makeSlot(String title) {
        LinearLayout slot = new LinearLayout(this);
        slot.setOrientation(LinearLayout.VERTICAL);
        slot.setPadding(20, 14, 20, 14);

        GradientDrawable sb = new GradientDrawable();
        sb.setColor(C_SLOT_BG);
        sb.setCornerRadius(20f);
        sb.setStroke(2, C_SLOT_BRD);
        slot.setBackground(sb);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 6, 0, 6);
        slot.setLayoutParams(lp);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(C_HEADER);
        t.setTextSize(9f);
        t.setLetterSpacing(0.2f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, 8);
        slot.addView(t);

        return slot;
    }

    private LinearLayout makeRow(String labelText, String btnText, TextView[] stateOut,
                                  View.OnClickListener listener) {
        return makeRow(labelText, btnText, stateOut, listener, null);
    }

    private LinearLayout makeRow(String labelText, String btnText, TextView[] stateOut,
                                  View.OnClickListener listener, View.OnLongClickListener longPress) {
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
        label.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(label);

        TextView btn = new TextView(this);
        btn.setText(btnText);
        btn.setTextColor(C_TEXT);
        btn.setTextSize(11f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(24, 14, 24, 14);
        btn.setBackground(bgStroke(C_CYAN_BG, C_BORDER, 22f));
        btn.setElevation(3f);
        btn.setMinWidth(115);
        btn.setOnClickListener(listener);
        if (longPress != null) btn.setOnLongClickListener(longPress);
        row.addView(btn);

        if (stateOut != null) stateOut[0] = btn;
        return row;
    }

    private void buildOverlay() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(24, 20, 24, 20);

        GradientDrawable cb = bgGrad(C_BG1, C_BG2, 42f);
        cb.setStroke(3, C_BORDER);
        rootLayout.setBackground(cb);
        rootLayout.setElevation(25f);

        // ---- Header (drag handle) ----
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(8, 6, 8, 12);

        TextView dot = new TextView(this);
        dot.setText("◆");
        dot.setTextColor(C_HEADER);
        dot.setTextSize(13f);
        headerRow.addView(dot);

        TextView header = new TextView(this);
        header.setText("  AXP · GAMING PANEL");
        header.setTextColor(C_HEADER);
        header.setTextSize(12f);
        header.setLetterSpacing(0.15f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        headerRow.addView(header);

        rootLayout.addView(headerRow);

        // ---- SLOT 1: PRIMARY ----
        LinearLayout slot1 = makeSlot("PRIMARY");
        TextView[] dOut = new TextView[1];
        slot1.addView(makeRow(droneLabel(), "OFF", dOut, v -> toggleDrone(),
                v -> { showIntervalDialog("drone", "DRONE", droneLabel()); return true; }));
        droneState = dOut[0];

        TextView[] stOut = new TextView[1];
        slot1.addView(makeRow(startLabel(), "OFF", stOut, v -> toggleStart(),
                v -> { showIntervalDialog("start", "START", startLabel()); return true; }));
        startState = stOut[0];

        TextView[] skOut = new TextView[1];
        slot1.addView(makeRow(skillLabel(), "OFF", skOut, v -> toggleSkill(),
                v -> { showIntervalDialog("skill", "SKILL", skillLabel()); return true; }));
        skillState = skOut[0];
        rootLayout.addView(slot1);

        // ---- SLOT 2: MOVEMENT ----
        LinearLayout slot2 = makeSlot("MOVEMENT");
        TextView[] fOut = new TextView[1];
        slot2.addView(makeRow("FORWARD", "OFF", fOut, v -> toggleFwd()));
        fwdState = fOut[0];

        TextView[] rOut = new TextView[1];
        slot2.addView(makeRow("RIGHT", "OFF", rOut, v -> toggleRight()));
        rightState = rOut[0];

        TextView[] lOut = new TextView[1];
        slot2.addView(makeRow("LIFT", "OFF", lOut, v -> toggleLift()));
        liftState = lOut[0];

        // 4 rotate modes — ek time pe ek
        LinearLayout rotRow = new LinearLayout(this);
        rotRow.setOrientation(LinearLayout.HORIZONTAL);
        rotRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rrlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rrlp.setMargins(0, 6, 0, 4);
        rotRow.setLayoutParams(rrlp);

        rotP120 = makeRotBtn("+120°", 0);
        rotN120 = makeRotBtn("-120°", 1);
        rotP360 = makeRotBtn("+360°", 2);
        rotP210 = makeRotBtn("+210°", 3);

        rotRow.addView(rotP120);
        rotRow.addView(rotN120);
        rotRow.addView(rotP360);
        rotRow.addView(rotP210);
        slot2.addView(rotRow);
        rootLayout.addView(slot2);

        // ---- SLOT 3: CUSTOM ----
        LinearLayout slot3 = makeSlot("CUSTOM BUTTONS");
        btnContainer = new LinearLayout(this);
        btnContainer.setOrientation(LinearLayout.VERTICAL);
        slot3.addView(btnContainer);

        TextView addBtn = new TextView(this);
        addBtn.setText("+   ADD  BUTTON");
        addBtn.setTextColor(C_HEADER);
        addBtn.setTextSize(11f);
        addBtn.setTypeface(Typeface.DEFAULT_BOLD);
        addBtn.setGravity(Gravity.CENTER);
        addBtn.setPadding(20, 18, 20, 18);
        addBtn.setBackground(bgStroke(C_CYAN_BG, C_BORDER, 22f));
        addBtn.setElevation(3f);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        addLp.setMargins(0, 6, 0, 0);
        addBtn.setLayoutParams(addLp);
        addBtn.setOnClickListener(v -> startCustomPicker());
        slot3.addView(addBtn);
        rootLayout.addView(slot3);

        // ---- SLOT 4: API KEY ----
        LinearLayout slot4 = makeSlot("API KEY");
        apiStatusText = new TextView(this);
        apiStatusText.setText("● NOT SET");
        apiStatusText.setTextColor(C_DIM);
        apiStatusText.setTextSize(11f);
        apiStatusText.setTypeface(Typeface.DEFAULT_BOLD);
        apiStatusText.setPadding(0, 0, 0, 3);
        slot4.addView(apiStatusText);

        apiExpiryText = new TextView(this);
        apiExpiryText.setText("Set key to enable features");
        apiExpiryText.setTextColor(C_DIM);
        apiExpiryText.setTextSize(10f);
        apiExpiryText.setPadding(0, 0, 0, 8);
        slot4.addView(apiExpiryText);

        TextView setApiBtn = new TextView(this);
        setApiBtn.setText("SET / CHANGE  KEY");
        setApiBtn.setTextColor(C_HEADER);
        setApiBtn.setTextSize(11f);
        setApiBtn.setTypeface(Typeface.DEFAULT_BOLD);
        setApiBtn.setGravity(Gravity.CENTER);
        setApiBtn.setPadding(20, 16, 20, 16);
        setApiBtn.setBackground(bgStroke(C_CYAN_BG, C_BORDER, 22f));
        setApiBtn.setElevation(3f);
        LinearLayout.LayoutParams sapLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sapLp.setMargins(0, 4, 0, 0);
        setApiBtn.setLayoutParams(sapLp);
        setApiBtn.setOnClickListener(v -> showApiKeyDialog());
        slot4.addView(setApiBtn);
        rootLayout.addView(slot4);

        // ---- AUTO LAUNCH SLOT ----
        LinearLayout slotLaunch = makeSlot("AUTO LAUNCH  ·  15s each");
        
        // force rebuild
        TextView[] altOut = new TextView[1];
        slotLaunch.addView(makeRow("FF ↔ FF MAX", "OFF", altOut, v -> toggleAlt()));
        altState = altOut[0];

        rootLayout.addView(slotLaunch);

        // ---- SLOT 5: SYSTEM ----
        LinearLayout slot5 = makeSlot("SYSTEM");
        TextView[] hOut = new TextView[1];
        slot5.addView(makeRow("PANEL", "HIDE", hOut, v -> hideOverlay()));

        statsText = new TextView(this);
        statsText.setText("Taps  0    ·    Idle");
        statsText.setTextColor(C_DIM);
        statsText.setTextSize(10f);
        statsText.setGravity(Gravity.CENTER);
        statsText.setPadding(0, 8, 0, 8);
        slot5.addView(statsText);

        LinearLayout closeRow = new LinearLayout(this);
        closeRow.setOrientation(LinearLayout.HORIZONTAL);
        closeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView closeLabel = new TextView(this);
        closeLabel.setText("STOP SERVICE");
        closeLabel.setTextColor(C_MUTED);
        closeLabel.setTextSize(11f);
        closeLabel.setTypeface(Typeface.DEFAULT_BOLD);
        closeLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        closeRow.addView(closeLabel);
        TextView closeBtn = new TextView(this);
        closeBtn.setText("CLOSE");
        closeBtn.setTextColor(C_RED);
        closeBtn.setTextSize(11f);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setPadding(24, 14, 24, 14);
        closeBtn.setBackground(bgStroke(C_RED_BG, C_RED, 22f));
        closeBtn.setMinWidth(115);
        closeBtn.setElevation(3f);
        closeBtn.setOnClickListener(v -> stopSelf());
        closeRow.addView(closeBtn);
        slot5.addView(closeRow);
        rootLayout.addView(slot5);

        // ---- Drag ONLY from header ----
        headerRow.setOnTouchListener(new View.OnTouchListener() {
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
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) dragging = true;
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
        params.x = 30; params.y = 150;

        overlay = scroll;
        wm.addView(overlay, params);
    }

    // ---- API Dialog (with overlay window type) ----
    private void showApiKeyDialog() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String currentKey = p.getString("api_key", "");
        String currentServer = p.getString("api_server", "http://127.0.0.1:9000");

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 40, 40, 40);

        TextView l1 = new TextView(this); l1.setText("Server URL (apihub)"); l1.setTextColor(0xFF000000); box.addView(l1);
        EditText serverIn = new EditText(this);
        serverIn.setText(currentServer);
        serverIn.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(serverIn);

        TextView l2 = new TextView(this); l2.setText("API Key"); l2.setTextColor(0xFF000000); box.addView(l2);
        EditText keyIn = new EditText(this);
        keyIn.setText(currentKey);
        keyIn.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(keyIn);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("API KEY  ·  SETUP")
                .setView(box)
                .setPositiveButton("SAVE & VALIDATE", (d, w) -> {
                    String s = serverIn.getText().toString().trim();
                    String k = keyIn.getText().toString().trim();
                    if (s.isEmpty() || k.isEmpty()) return;
                    p.edit().putString("api_server", s).putString("api_key", k).apply();
                    apiStatusText.setText("● VALIDATING...");
                    apiStatusText.setTextColor(C_MUTED);
                    validateApiKey(s, k);
                })
                .setNegativeButton("CANCEL", null)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.show();
    }

    private void validateApiKey(String server, String key) {
        ApiValidator.validate(server, key, (valid, msg, expiry) -> {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            p.edit().putBoolean("api_valid", valid).putLong("api_expiry", expiry).apply();
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
                    apiExpiryText.setText("Expires in " + days + "d " + hrs + "h " + mins + "m");
                } else {
                    apiExpiryText.setText("KEY EXPIRED");
                    apiStatusText.setText("● EXPIRED");
                    apiStatusText.setTextColor(C_RED);
                }
            } else {
                apiExpiryText.setText("Expires: never");
            }
        } else {
            apiStatusText.setText("● NOT SET");
            apiStatusText.setTextColor(C_RED);
            apiExpiryText.setText("Set key to enable features");
        }
    }

    private void startApiRefresh() {
        apiRefreshHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
                String s = p.getString("api_server", "");
                String k = p.getString("api_key", "");
                if (!s.isEmpty() && !k.isEmpty()) {
                    validateApiKey(s, k);
                } else {
                    updateApiStatus();
                }
                apiRefreshHandler.postDelayed(this, 5 * 60 * 1000L);
            }
        }, 60 * 1000L);
    }

    private boolean isApiValid() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!p.getBoolean("api_valid", false)) return false;
        long exp = p.getLong("api_expiry", 0);
        if (exp > 0 && exp < System.currentTimeMillis()) return false;
        return true;
    }

    // ---- Custom buttons render ----
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
            label.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(label);

            TextView del = new TextView(this);
            del.setText("×");
            del.setTextColor(C_RED);
            del.setTextSize(14f);
            del.setTypeface(Typeface.DEFAULT_BOLD);
            del.setGravity(Gravity.CENTER);
            del.setPadding(12, 6, 12, 6);
            del.setBackground(bgStroke(C_RED_BG, C_RED, 18f));
            del.setOnClickListener(v -> {
                AlertDialog dl = new AlertDialog.Builder(this)
                        .setTitle("Delete?")
                        .setMessage("Remove '" + b.name + "'?")
                        .setPositiveButton("Yes", (d, w) -> {
                            if (s != null) { s.removeCustomBtn(b); refreshCustomButtons(); }
                        })
                        .setNegativeButton("No", null)
                        .create();
                if (dl.getWindow() != null) {
                    dl.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE);
                }
                dl.show();
            });
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            dlp.setMargins(0, 0, 6, 0);
            del.setLayoutParams(dlp);
            row.addView(del);

            TextView btn = new TextView(this);
            btn.setText(b.running ? "ON" : "OFF");
            btn.setTextColor(b.running ? C_GREEN : C_TEXT);
            btn.setTextSize(11f);
            btn.setTypeface(Typeface.DEFAULT_BOLD);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(24, 14, 24, 14);
            btn.setBackground(b.running
                    ? bgGrad(C_GREEN_BG1, C_GREEN_BG2, 22f)
                    : bgStroke(C_CYAN_BG, C_BORDER, 22f));
            btn.setMinWidth(115);
            btn.setElevation(3f);
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
                if ("custom".equals(pendingPickerType)) promptCustomName(xp, yp);
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
        AlertDialog dialog = new AlertDialog.Builder(this)
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
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.show();
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
        GradientDrawable tb = bgGrad(C_BORDER, 0xFF007A99, 50f);
        tb.setStroke(3, C_HEADER);
        tab.setBackground(tb);
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
                            lp.x = startX + dx; lp.y = startY + dy;
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
        params.x = 30; params.y = 150;
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

    private void hideOverlay() { overlay.setVisibility(View.GONE); hideTab.setVisibility(View.VISIBLE); }
    private void showOverlay() { overlay.setVisibility(View.VISIBLE); hideTab.setVisibility(View.GONE); }

    private boolean checkApi() {
        if (isApiValid()) return true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("⚡  API KEY REQUIRED")
                .setMessage("Pehle apni API key daalo.\n\nWahi key jo Spy Bot website (AXP HUB) se generate ki thi.")
                .setPositiveButton("SET  API  KEY", (d, w) -> showApiKeyDialog())
                .setNegativeButton("CANCEL", null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.show();
        return false;
    }

    private void setBtn(TextView btn, boolean on, String onText) {
        if (on) {
            btn.setText(onText);
            btn.setTextColor(C_GREEN);
            btn.setBackground(bgGrad(C_GREEN_BG1, C_GREEN_BG2, 22f));
        } else {
            btn.setText(onText.isEmpty() ? "OFF" : onText);
            btn.setTextColor(C_TEXT);
            btn.setBackground(bgStroke(C_CYAN_BG, C_BORDER, 22f));
        }
    }

    private void toggleDrone() {
        if (!checkApi()) return;
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        droneOn = !droneOn;
        if (droneOn) { s.startDrone(); setBtn(droneState, true, "ON · 61s"); }
        else { s.stopDrone(); setBtn(droneState, false, "OFF"); }
    }

    private void toggleStart() {
        if (!checkApi()) return;
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        startOn = !startOn;
        if (startOn) { s.startStartButton(); setBtn(startState, true, "ON · 9min"); }
        else { s.stopStartButton(); setBtn(startState, false, "OFF"); }
    }

    private void toggleSkill() {
        if (!checkApi()) return;
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        skillOn = !skillOn;
        if (skillOn) { s.startSkillButton(); setBtn(skillState, true, "ON · 90s"); }
        else { s.stopSkillButton(); setBtn(skillState, false, "OFF"); }
    }

    private void toggleFwd() {
        if (!checkApi()) return;
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        fwdOn = !fwdOn;
        s.setDirection(AutoTapService.DIR_FORWARD, fwdOn);
        setBtn(fwdState, fwdOn, fwdOn ? "ON" : "OFF");
    }

    private void toggleRight() {
        if (!checkApi()) return;
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        rightOn = !rightOn;
        s.setDirection(AutoTapService.DIR_RIGHT, rightOn);
        setBtn(rightState, rightOn, rightOn ? "ON" : "OFF");
    }

    private void toggleLift() {
        if (!checkApi()) return;
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        liftOn = !liftOn;
        s.setDirection(AutoTapService.DIR_LIFT, liftOn);
        setBtn(liftState, liftOn, liftOn ? "ON" : "OFF");
    }

    // Make small rotate button
    private TextView makeRotBtn(String text, int mode) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(C_TEXT);
        b.setTextSize(10f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(10, 12, 10, 12);
        b.setBackground(bgStroke(C_CYAN_BG, C_BORDER, 18f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(3, 0, 3, 0);
        b.setLayoutParams(lp);
        b.setTag(mode);
        b.setOnClickListener(v -> toggleRotate(mode));
        return b;
    }

    private TextView rotBtnFor(int mode) {
        if (mode == 0) return rotP120;
        if (mode == 1) return rotN120;
        if (mode == 2) return rotP360;
        return rotP210;
    }

    private void resetRotateBtns() {
        for (int i = 0; i < 4; i++) {
            TextView b = rotBtnFor(i);
            if (b != null) {
                b.setTextColor(C_TEXT);
                b.setBackground(bgStroke(C_CYAN_BG, C_BORDER, 18f));
            }
        }
    }

    private void toggleRotate(int mode) {
        if (!checkApi()) return;
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;

        // If same mode tapped → stop
        if (activeRotate == mode) {
            s.stopRotate();
            activeRotate = -1;
            resetRotateBtns();
            return;
        }

        // Otherwise: stop old, start new
        s.startRotate(mode);
        activeRotate = mode;
        resetRotateBtns();
        TextView b = rotBtnFor(mode);
        if (b != null) {
            b.setTextColor(C_GREEN);
            b.setBackground(bgGrad(C_GREEN_BG1, C_GREEN_BG2, 18f));
        }
    }

    private void toggleFF() {
        if (!checkApi()) return;
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        ffOn = !ffOn;
        if (ffOn) {
            s.startFFLaunch();
            setBtn(ffState, true, "ON · 8s");
        } else {
            s.stopFFLaunch();
            setBtn(ffState, false, "OFF");
        }
    }

    private void toggleFFMax() {
        if (!checkApi()) return;
        AutoTapService s = AutoTapService.instance;
        if (s == null) return;
        ffMaxOn = !ffMaxOn;
        if (ffMaxOn) {
            s.startFFMaxLaunch();
            setBtn(ffMaxState, true, "ON · 8s");
        } else {
            s.stopFFMaxLaunch();
            setBtn(ffMaxState, false, "OFF");
        }
    }

    private String fmtInterval(int ms) {
        if (ms >= 60000) {
            long m = ms / 60000;
            if (ms % 60000 == 0) return m + "min";
            return m + "m" + ((ms % 60000) / 1000) + "s";
        }
        return (ms / 1000) + "s";
    }

    private String droneLabel() {
        AutoTapService sv = AutoTapService.instance;
        int ms = (sv != null) ? sv.droneIntervalMs : 61000;
        return "DRONE  ·  " + fmtInterval(ms);
    }

    private String startLabel() {
        AutoTapService sv = AutoTapService.instance;
        int ms = (sv != null) ? sv.startIntervalMs : 540000;
        return "START  ·  " + fmtInterval(ms);
    }

    private String skillLabel() {
        AutoTapService sv = AutoTapService.instance;
        int ms = (sv != null) ? sv.skillIntervalMs : 90000;
        return "SKILL  ·  " + fmtInterval(ms);
    }

    private void showIntervalDialog(String type, String title, String currentLabel) {
        AutoTapService sv = AutoTapService.instance;
        if (sv == null) return;
        int currentMs;
        if (type.equals("drone")) currentMs = sv.droneIntervalMs;
        else if (type.equals("start")) currentMs = sv.startIntervalMs;
        else currentMs = sv.skillIntervalMs;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 30, 40, 30);

        TextView info = new TextView(this);
        info.setText("Current: " + fmtInterval(currentMs));
        info.setTextColor(0xFF000000);
        info.setTextSize(14f);
        box.addView(info);

        TextView lbl = new TextView(this);
        lbl.setText("New interval (seconds):");
        lbl.setTextColor(0xFF000000);
        lbl.setPadding(0, 20, 0, 0);
        box.addView(lbl);

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(currentMs / 1000));
        box.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title + "  ·  SET INTERVAL")
                .setView(box)
                .setPositiveButton("SAVE", (d, w) -> {
                    try {
                        int secs = Integer.parseInt(input.getText().toString().trim());
                        if (secs < 1) secs = 1;
                        if (secs > 3600) secs = 3600;
                        int ms = secs * 1000;
                        if (type.equals("drone")) sv.droneIntervalMs = ms;
                        else if (type.equals("start")) sv.startIntervalMs = ms;
                        else sv.skillIntervalMs = ms;
                        sv.saveConfig();
                        refreshLabelTexts();
                    } catch (Exception e) {}
                })
                .setNegativeButton("CANCEL", null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.show();
    }

    private void refreshLabelTexts() {
        try {
            if (droneState != null) {
                View p1 = (View) droneState.getParent();
                if (p1 instanceof LinearLayout) {
                    View l = ((LinearLayout) p1).getChildAt(0);
                    if (l instanceof TextView) ((TextView) l).setText(droneLabel());
                }
            }
            if (startState != null) {
                View p2 = (View) startState.getParent();
                if (p2 instanceof LinearLayout) {
                    View l = ((LinearLayout) p2).getChildAt(0);
                    if (l instanceof TextView) ((TextView) l).setText(startLabel());
                }
            }
            if (skillState != null) {
                View p3 = (View) skillState.getParent();
                if (p3 instanceof LinearLayout) {
                    View l = ((LinearLayout) p3).getChildAt(0);
                    if (l instanceof TextView) ((TextView) l).setText(skillLabel());
                }
            }
        } catch (Exception e) {}
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AutoTapService s = AutoTapService.instance;
        if (s != null) {
            s.stopDrone(); s.stopStartButton(); s.stopSkillButton(); s.stopRotate();
            s.stopFFLaunch(); s.stopFFMaxLaunch(); s.stopAlternate();
            s.saveConfig();
        }
        if (overlay != null) { try { wm.removeView(overlay); } catch (Exception e) {} }
        if (hideTab != null) { try { wm.removeView(hideTab); } catch (Exception e) {} }
        if (pickerView != null) { try { wm.removeView(pickerView); } catch (Exception e) {} }
        statsHandler.removeCallbacksAndMessages(null);
        apiRefreshHandler.removeCallbacksAndMessages(null);
    }
}
// Fri Oct  9 18:07:45 IST 2026
