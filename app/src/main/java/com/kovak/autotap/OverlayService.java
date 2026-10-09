package com.kovak.autotap;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

public class OverlayService extends Service {
    private WindowManager wm;
    private View overlay;
    private boolean torch = false;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        createOverlay();
    }

    private void createOverlay() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setBackgroundColor(0xCC000000);
        ll.setPadding(30, 30, 30, 30);

        Button afk = new Button(this);
        afk.setText("AFK:OFF");
        afk.setOnClickListener(v -> {
            AutoTapService s = AutoTapService.instance;
            if (s == null) return;
            Button b = (Button) v;
            if (b.getText().toString().equals("AFK:OFF")) {
                s.startAfk(); b.setText("AFK:ON");
            } else {
                s.stopAfk(); b.setText("AFK:OFF");
            }
        });
        ll.addView(afk);

        Button tr = new Button(this);
        tr.setText("TORCH");
        tr.setOnClickListener(v -> toggleTorch());
        ll.addView(tr);

        Button mv = new Button(this);
        mv.setText("MOVE");
        mv.setOnClickListener(v -> {
            AutoTapService s = AutoTapService.instance;
            if (s == null) return;
            int w = getResources().getDisplayMetrics().widthPixels;
            int h = getResources().getDisplayMetrics().heightPixels;
            s.swipe(w * 0.3f, h * 0.7f, w * 0.7f, h * 0.7f, 400);
        });
        ll.addView(mv);

        Button ex = new Button(this);
        ex.setText("EXIT");
        ex.setOnClickListener(v -> stopSelf());
        ll.addView(ex);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = 30; p.y = 200;

        overlay = ll;
        wm.addView(overlay, p);
    }

    private void toggleTorch() {
        try {
            CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String id = cm.getCameraIdList()[0];
            torch = !torch;
            cm.setTorchMode(id, torch);
        } catch (Exception e) {}
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlay != null) {
            try { wm.removeView(overlay); } catch (Exception e) {}
        }
    }
}
