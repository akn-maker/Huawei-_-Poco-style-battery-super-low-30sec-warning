# Battery Guard

Android app (Android 5.0+, no third-party libraries) that imitates the low-battery
"countdown, then Battery life extender" behaviour:

1. At the chosen battery % (default 5%) a "Battery is running out" dialog appears with a live
   countdown (default 15 s).
2. At 0 the screen turns black and shows "Battery life extender": minimum brightness,
   double-tap opens the dialer.
3. Plugging in a charger closes everything. In the black screen you can also touch and hold
   the title to leave it.

## Build
1. Install Android Studio and open this folder.
2. Let Gradle sync (it downloads what it needs), then Run > Run 'app' with your phone in USB-debugging mode,
   or Build > Build APK(s) and copy the APK to the phone.
3. In the app: turn the switch on, then grant "Display over other apps" and "Notifications".
4. Use "Preview the warning (demo)" to test without draining the battery.

Some phone brands (Xiaomi, Oppo, Vivo, Huawei, Samsung) also need: autostart allowed,
"Display pop-up windows while running in the background" allowed, and battery optimization off for the app.
