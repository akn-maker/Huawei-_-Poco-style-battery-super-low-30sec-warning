package com.example.batteryguard;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Stage 1: "Battery is running out" dialog with a live countdown.
 * Stage 2: black "Battery life extender" screen; double-tap opens the dialer.
 */
public class LowBatteryActivity extends Activity {
    static final String EXTRA_EXTENDER = "extender";
    static final String EXTRA_SECONDS = "seconds";
    static final String EXTRA_DEMO = "demo";

    private static final int STATE_NONE = 0;
    private static final int STATE_WARNING = 1;
    private static final int STATE_EXTENDER = 2;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private TextView messageView;
    private GestureDetector detector;
    private int state = STATE_NONE;
    private int remaining;
    private boolean demo;
    private boolean receiverRegistered;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            remaining--;
            if (remaining <= 0) {
                showExtender();
            } else {
                updateMessage();
                handler.postDelayed(this, 1000);
            }
        }
    };

    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!demo) finishAndRemoveTask(); // charger plugged in -> all is well
        }
    };

    // ------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        root = new FrameLayout(this);
        setContentView(root);

        detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (state == STATE_EXTENDER) openDialer();
                return true;
            }
        });

        IntentFilter f = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(powerReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(powerReceiver, f);
        receiverRegistered = true;

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) {
            try { unregisterReceiver(powerReceiver); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && state == STATE_EXTENDER) hideSystemUi();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (state == STATE_EXTENDER) detector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (state == STATE_EXTENDER && !demo) return; // locked in, like the original
        super.onBackPressed();
    }

    // ---------------------------------------------------------------- logic

    private void handleIntent(Intent i) {
        demo = i.getBooleanExtra(EXTRA_DEMO, false);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(BatteryGuardService.ID_ALERT);

        if (!demo && isCharging()) {
            finishAndRemoveTask();
            return;
        }
        if (i.getBooleanExtra(EXTRA_EXTENDER, false)) {
            if (state != STATE_EXTENDER) showExtender();
        } else if (state == STATE_NONE) {
            showWarning(i.getIntExtra(EXTRA_SECONDS, 15));
        }
    }

    private boolean isCharging() {
        Intent s = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return s != null && s.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }

    private void openDialer() {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {}
    }

    // ------------------------------------------------------- stage 1: warn

    private void showWarning(int seconds) {
        state = STATE_WARNING;
        remaining = seconds;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root.removeAllViews();
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF241638, 0xFF0A0A12}));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(22));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xF02A2C3C);
        cardBg.setCornerRadius(dp(28));
        card.setBackground(cardBg);

        TextView title = text("Battery is running out", 18, Color.WHITE, true);
        card.addView(title, lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 0, 0, 0, 12));

        messageView = text("", 14, 0xFFE0E0E6, false);
        messageView.setGravity(Gravity.START);
        card.addView(messageView, lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 0, 0, 0, 10));

        TextView note = text("When \"Battery life extender\" is enabled, you can answer or make calls.",
                12, 0xFFA8A8B4, false);
        note.setGravity(Gravity.START);
        card.addView(note, lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 0, 0, 0, 18));

        Button ok = new Button(this);
        ok.setText("Got it");
        ok.setAllCaps(false);
        ok.setTextColor(Color.WHITE);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xFF6C7BF0);
        pill.setCornerRadius(dp(26));
        ok.setBackground(pill);
        ok.setOnClickListener(v -> {
            handler.removeCallbacks(tick);
            state = STATE_NONE;
            finishAndRemoveTask();
        });
        card.addView(ok, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

        if (demo) {
            TextView d = text("Demo mode", 11, 0xFF8888A0, false);
            card.addView(d, lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 10, 0, 0, 0));
        }

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        cp.leftMargin = dp(24);
        cp.rightMargin = dp(24);
        root.addView(card, cp);

        updateMessage();
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 1000);
    }

    private void updateMessage() {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append("Your phone will switch to \"Battery life extender\" in ");
        int start = sb.length();
        sb.append(String.valueOf(remaining)).append(remaining == 1 ? " second" : " seconds");
        sb.setSpan(new ForegroundColorSpan(0xFFFF4D4D), start, sb.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(". Charge your phone immediately.");
        messageView.setText(sb);
    }

    // --------------------------------------------------- stage 2: extender

    private void showExtender() {
        handler.removeCallbacks(tick);
        state = STATE_EXTENDER;

        Window w = getWindow();
        w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // let the screen time out
        WindowManager.LayoutParams params = w.getAttributes();
        params.screenBrightness = 0.02f; // as dim as this window may go
        w.setAttributes(params);
        hideSystemUi();

        root.removeAllViews();
        root.setBackgroundColor(Color.BLACK);

        // top: title + subtitle (touch and hold the title to leave this mode)
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = text("Battery life extender", 22, Color.WHITE, true);
        title.setOnLongClickListener(v -> {
            finishAndRemoveTask();
            return true;
        });
        top.addView(title, lp(LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0, 6));
        top.addView(text("It supports answering or making short calls.", 14, 0xFF9A9AA5, false));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        tp.topMargin = dp(96);
        root.addView(top, tp);

        // center: dial icon + hint
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView dial = text("\uD83D\uDCDE", 30, Color.WHITE, false);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xFF4C5BD4);
        dial.setBackground(circle);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(72), dp(72));
        dlp.bottomMargin = dp(14);
        mid.addView(dial, dlp);
        mid.addView(text("Double-tap to open \"Dial\"", 14, 0xFFD0D0DA, false));
        root.addView(mid, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        // bottom: red battery + message
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View battery = new View(this);
        GradientDrawable bat = new GradientDrawable();
        bat.setColor(0xFFE53935);
        bat.setCornerRadius(dp(3));
        battery.setBackground(bat);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(26), dp(13));
        blp.rightMargin = dp(8);
        row.addView(battery, blp);
        row.addView(text("Battery too low. Charge now.", 13, 0xFFBBBBC5, false));
        bottom.addView(row);
        if (demo) {
            bottom.addView(text("Demo: touch and hold the title to exit", 11, 0xFF6E6E7A, false),
                    lp(LinearLayout.LayoutParams.WRAP_CONTENT, 0, 10, 0, 0, 0));
        }
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        bp.bottomMargin = dp(48);
        root.addView(bottom, bp);
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    // -------------------------------------------------------------- helpers

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int width, int l, int t, int r, int b, int bottomDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(l), dp(t), dp(r), dp(bottomDp == 0 ? b : bottomDp));
        return p;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
