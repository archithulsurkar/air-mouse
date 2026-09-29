# Air Mouse

Use a Galaxy Watch (Wear OS 3+, Watch4 or newer) as an air mouse for its paired Android phone.

- `wear/` streams gyroscope rotation plus tap/scroll/button events over the Wearable Data Layer.
- `protocol/` holds the message paths shared by both apps.
- `mobile/` runs an AccessibilityService that draws a cursor overlay and injects taps, long presses,
  scroll swipes and Back/Home/Recents.

## Build and install

1. Open the folder in Android Studio (it creates `local.properties` with your SDK path).
2. Install `mobile` on the phone and `wear` on the watch from the **same machine**: the Data Layer only
   connects apps with the same package name *and* signing key.
   Watch over Wi-Fi ADB: Settings → Developer options → Wireless debugging → pair in Android Studio.
3. Phone: open Air Mouse → **Open accessibility settings** → Installed apps → Air Mouse → On.
4. Watch: open Air Mouse → **Start**.

## Controls (watch)

Hold the watch face towards you.

| Action | Result on phone |
| --- | --- |
| Roll your wrist | Cursor up/down |
| Raise/lower your elbow | Cursor left/right |
| Tap screen | Click |
| Hold | Long press |
| Swipe up/down, turn bezel | Scroll |
| ◁ ○ □ | Back, Home, Recents |

Cursor going the wrong way? Pause, tap **Calibrate** on the watch and follow the prompts
(hold still, do your RIGHT move, do your UP move). The watch learns your own gestures.
Sensitivity and horizontal boost are set in the phone app.

## Notes

- The cursor freezes while your finger is on the watch so taps land where you aimed.
- Sideloaded APKs on Android 13+ may show "Restricted setting" for accessibility; allow it from
  App info → ⋮ → Allow restricted settings. Installs from Android Studio are not affected.
- Tuning constants: `wear/.../MainActivity.kt` (send rate, dead zone), `wear/.../Calibrator.kt` and
  `mobile/.../CursorService.kt` (tap/scroll timing, scroll scale).
