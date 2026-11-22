package com.example.batteryguard;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {

    private interface IntSink { void set(int value); }

    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(20), dp(20), dp(32));
        scroll.addView(col);
        setContentView(scroll);

        TextView intro = new TextView(this);
        intro.setText("Shows a countdown warning when your battery gets critically low, then switches "
                + "to a black \"Battery life extender\" screen that only offers the dialer.");
        intro.setTextSize(14);
        col.addView(intro, margin(0, 0, 0, 16));

        Switch guard = new Switch(this);
        guard.setText("Enable battery guard");
        guard.setTextSize(17);
        guard.setChecked(Prefs.enabled(this));
        guard.setOnCheckedChangeListener((b, on) -> {
            Prefs.setEnabled(this, on);
            if (on) BatteryGuardService.start(this);
            else BatteryGuardService.stop(this);
        });
        col.addView(guard, margin(0, 0, 0, 20));

        Switch sound = new Switch(this);
        sound.setText("Warning sounds");
        sound.setTextSize(17);
        sound.setChecked(Prefs.sound(this));
        sound.setOnCheckedChangeListener((b, on) -> Prefs.setSound(this, on));
        col.addView(sound, margin(0, 0, 0, 20));


        addSeek(col, "Warn at %d%% battery", 3, 20, Prefs.threshold(this),
                v -> Prefs.setThreshold(this, v));
        addSeek(col, "Countdown: %d seconds", 5, 30, Prefs.countdown(this),
                v -> Prefs.setCountdown(this, v));

        TextView permsTitle = new TextView(this);
        permsTitle.setText("Permissions");
        permsTitle.setTextSize(17);
        col.addView(permsTitle, margin(0, 12, 0, 6));

        statusView = new TextView(this);
        statusView.setTextSize(14);
        col.addView(statusView, margin(0, 0, 0, 8));

        addButton(col, "Allow display over other apps", v -> {
            if (Build.VERSION.SDK_INT >= 23) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        if (Build.VERSION.SDK_INT >= 33) {
            addButton(col, "Allow notifications", v ->
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1));
        }
        addButton(col, "Don't optimize battery for this app", v -> {
            if (Build.VERSION.SDK_INT >= 23) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        });

        addButton(col, "Preview the warning (demo)", v ->
                startActivity(new Intent(this, LowBatteryActivity.class)
                        .putExtra(LowBatteryActivity.EXTRA_DEMO, true)
                        .putExtra(LowBatteryActivity.EXTRA_SECONDS, Prefs.countdown(this))));

        TextView note = new TextView(this);
        note.setText("Note: an ordinary app can't power the phone off or cut radios like the built-in "
                + "feature of some phones does. This is a look-alike overlay. It closes by itself "
                + "when you plug in a charger.");
        note.setTextSize(12);
        col.addView(note, margin(0, 20, 0, 0));

        if (Prefs.enabled(this)) BatteryGuardService.start(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        boolean notif = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        statusView.setText("Display over other apps: " + (overlay ? "allowed \u2713" : "NOT allowed")
                + "\nNotifications: " + (notif ? "allowed \u2713" : "NOT allowed"));
    }

    // -------------------------------------------------------------- helpers

    private void addSeek(LinearLayout col, final String labelFmt, final int min, int max,
                         int current, final IntSink sink) {
        final TextView label = new TextView(this);
        label.setTextSize(15);
        label.setText(String.format(labelFmt, current));
        col.addView(label, margin(0, 4, 0, 0));

        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(current - min);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int v = progress + min;
                label.setText(String.format(labelFmt, v));
                if (fromUser) sink.set(v);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        col.addView(bar, margin(0, 0, 0, 12));
    }

    private void addButton(LinearLayout col, String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        col.addView(b);
    }

    private LinearLayout.LayoutParams margin(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
