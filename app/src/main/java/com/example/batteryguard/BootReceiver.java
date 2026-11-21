package com.example.batteryguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && Prefs.enabled(context)) {
            try {
                BatteryGuardService.start(context);
            } catch (Exception ignored) {
                // Some OEMs block starting services at boot; the user can reopen the app.
            }
        }
    }
}
