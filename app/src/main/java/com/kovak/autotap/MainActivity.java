package com.kovak.autotap;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(50, 100, 50, 50);

        TextView tv = new TextView(this);
        tv.setText("AXP AutoTap Setup");
        tv.setTextSize(22);
        ll.addView(tv);

        Button b1 = new Button(this);
        b1.setText("1. Overlay Permission");
        b1.setOnClickListener(v -> startActivity(new Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + getPackageName()))));
        ll.addView(b1);

        Button b2 = new Button(this);
        b2.setText("2. Enable Accessibility");
        b2.setOnClickListener(v -> startActivity(new Intent(
            Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        ll.addView(b2);

        Button b3 = new Button(this);
        b3.setText("3. Start Overlay");
        b3.setOnClickListener(v -> startService(new Intent(
            MainActivity.this, OverlayService.class)));
        ll.addView(b3);

        Button b4 = new Button(this);
        b4.setText("4. Stop Overlay");
        b4.setOnClickListener(v -> stopService(new Intent(
            MainActivity.this, OverlayService.class)));
        ll.addView(b4);

        setContentView(ll);
    }
}
