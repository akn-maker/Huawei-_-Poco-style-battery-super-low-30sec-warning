package com.example.batteryguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;

/**
 * Foreground service that listens for battery updates.
 * (ACTION_BATTERY_CHANGED cannot be declared in the manifest, so a service must stay alive.)
 */
public class BatteryGuardService extends Service {
    static final String CH_STATUS = "guard_status";
    static final String CH_ALERT = "guard_alert";
    static final int ID_STATUS = 1;
    static final int ID_ALERT = 2;

    /** At or below this %, skip the countdown (if "Got it" was pressed) and go to extender mode. */
    private static final int EXTENDER_LEVEL = 1;

    private boolean warned;
    private boolean extended;
    private boolean registered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handle(intent);
        }
    };

    static void start(Context c) {
        Intent i = new Intent(c, BatteryGuardService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, BatteryGuardService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
        registered = true; // the current (sticky) battery state is delivered immediately
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = buildStatusNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_STATUS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(ID_STATUS, n);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (registered) {
            try { unregisterReceiver(receiver); } catch (Exception ignored) {}
            registered = false;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ---------------------------------------------------------------- logic

    private void handle(Intent i) {
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) return;
        int pct = level * 100 / scale;
        boolean charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        int threshold = Prefs.threshold(this);

        // Charger connected, or battery recovered: re-arm for the next discharge.
        if (charging || pct > threshold + 1) {
            if (warned || extended) {
                warned = false;
                extended = false;
                cancelAlert();
            }
            return;
        }

        if (pct <= EXTENDER_LEVEL && !extended) {
            extended = true;
            warned = true;
            launch(true);
        } else if (pct <= threshold && !warned) {
            warned = true;
            launch(false);
        }
    }

    private void launch(boolean extender) {
        Intent a = new Intent(this, LowBatteryActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(LowBatteryActivity.EXTRA_EXTENDER, extender)
                .putExtra(LowBatteryActivity.EXTRA_SECONDS, Prefs.countdown(this));

        // Fallback path: a full-screen-intent notification (works when the screen is off / locked).
        postAlert(a, extender);
        // Direct path: works when the "display over other apps" permission is granted.
        try {
            startActivity(a);
        } catch (Exception ignored) {
            // Blocked by the OS; the notification above is the fallback.
        }
    }

    // -------------------------------------------------------- notifications

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CH_STATUS, "Battery guard status", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(
                CH_ALERT, "Low battery alerts", NotificationManager.IMPORTANCE_HIGH));
    }

    private Notification.Builder builder(String channel) {
        return Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, channel)
                : new Notification.Builder(this);
    }

    private static int piFlags() {
        return Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                : PendingIntent.FLAG_UPDATE_CURRENT;
    }

    @SuppressWarnings("deprecation")
    private Notification buildStatusNotification() {
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class), piFlags());
        Notification.Builder nb = builder(CH_STATUS)
                .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
                .setContentTitle("Battery guard is on")
                .setContentText("Warns you at " + Prefs.threshold(this) + "% battery")
                .setContentIntent(pi)
                .setOngoing(true);
        if (Build.VERSION.SDK_INT < 26) nb.setPriority(Notification.PRIORITY_MIN);
        return nb.build();
    }

    @SuppressWarnings("deprecation")
    private void postAlert(Intent activityIntent, boolean extender) {
        try {
            PendingIntent pi = PendingIntent.getActivity(this, extender ? 1 : 0, activityIntent, piFlags());
            Notification.Builder nb = builder(CH_ALERT)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
                    .setContentTitle("Battery is running out")
                    .setContentText("Charge your phone immediately.")
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setFullScreenIntent(pi, true)
                    .setContentIntent(pi)
                    .setAutoCancel(true);
            if (Build.VERSION.SDK_INT < 26) nb.setPriority(Notification.PRIORITY_HIGH);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(ID_ALERT, nb.build());
        } catch (Exception ignored) {
            // Missing notification permission: nothing else we can do.
        }
    }

    private void cancelAlert() {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(ID_ALERT);
    }
}
