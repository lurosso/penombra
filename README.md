# Penombra

A tiny screen dimmer for Android 12 and later that goes darker than the lowest brightness, without staying in the background. About 60 KB, plain Java, no libraries, no network.

The screen's darkness is drawn as a waning moon: drag the moon down to darken, up to lighten.

## Two layers

- **Extra dim** drives Android's own *Reduce bright colors* (extra dim). Nothing of Penombra runs while it is on, and it survives a reboot. Controlling it needs one permission that can only be given from a computer, once (see below).
- **Eclipse** lays a black veil over the whole screen, up to 80%, so touches still pass through. A small foreground service with a notification runs only while it is on; the notification turns it off.

Without the computer step Penombra works with the Eclipse alone: the moon, the presets, the tile and the schedule all drive the veil.

## Also

- **Presets**: Quarter, Crescent, New moon, Pitch dark (extra dim at full plus the veil).
- **Schedule**: turns itself on and off at the chosen times, every night.
- **Quick Settings tile**: tap to turn on or off; long-press opens a small panel over the current app with the slider, the presets and the Eclipse.
- **Lock screen** (optional, off by default): some phones (realme) drop the extra dim while locked. With this option Penombra lays a similar veil on the lock screen only. It keeps an accessibility service on while the extra dim is on, so it costs a few more resources.
- **Languages**: English and Italian. On Android 13 and later the language button at the bottom of the app switches it (System, Italiano, English).

## Install

1. Download `Penombra-<version>.apk` from the Releases page and open it on the phone (allow installs from that app when Android asks).
2. Eclipse works right away. Penombra asks for **Display over other apps** on first use.
3. Optional, to unlock Extra dim: turn on USB debugging (Developer options), connect the phone to a computer with [platform-tools](https://developer.android.com/tools/releases/platform-tools) and run

   ```
   adb shell pm grant it.penombra android.permission.WRITE_SECURE_SETTINGS
   ```

   On realme and OPPO first turn on *Disable permission monitoring* in Developer options, or install with `adb install -r -g Penombra-<version>.apk`. The app shows and copies the command.

4. Optional, without the computer step: to veil the status bar and the lock screen too, turn on Penombra in Settings › Accessibility. If the switch is greyed out: App info › ⋮ › Allow restricted settings. With the computer step Penombra switches this on and off by itself, only while it is needed.

## Permissions

| Permission | Why |
|---|---|
| `WRITE_SECURE_SETTINGS` (adb only) | Switch Android's extra dim and set its level; switch Penombra's own accessibility service on only while needed. |
| `SYSTEM_ALERT_WINDOW` | Draw the Eclipse veil over other apps. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS` | Keep the veil while the Eclipse is on, with a notification to turn it off. |
| `USE_EXACT_ALARM` | The schedule. |
| `RECEIVE_BOOT_COMPLETED` | Restore the schedule and the veil after a reboot (the receiver is enabled only while needed). |
| Accessibility service (optional) | Only Android's accessibility overlay can cover the status bar, the notification shade and the lock screen. It reads nothing and handles no events. |

There is no internet permission: Penombra cannot send anything anywhere.

## Notes on some phones

- Apps cannot read the extra dim state from `Settings.Secure` on Android 12+ (only system and test-only apps can), so the normal build remembers what it set. If extra dim is changed from Android's own controls, tap the switch once.
- realme turns apps' exact alarms into alarms that may fire up to an hour late. The schedule aims each alarm early enough that it still lands on time.
- realme's System UI keeps recently used tiles bound and restarts their app after it is closed; the tile is an "active tile" so this does not happen.
- Google Play Protect may warn that Penombra "displays content over other apps": that is the Eclipse veil. Nothing is revoked.

## Build

Requirements: Android SDK with build-tools and `platforms;android-34`, JDK 17 or later, Python 3 (used only to add `classes.dex` to the package). No Gradle.

```
sh build.sh        # normal build          -> build/Penombra.apk
sh build.sh adb    # test-only build       -> build/Penombra-adb.apk
```

On Windows use Git Bash. Settings can be given as environment variables or in a `build.local` file next to the script (ignored by git): `ANDROID_HOME`, `BUILD_TOOLS`, `JAVA_HOME`, `PENOMBRA_KEYSTORE`, `PENOMBRA_KS_PASS`, `COPY_TO`. A signing key `penombra.keystore` is created on the first build: keep it, because updates must be signed with the same key.

The test-only build (`android:testOnly`) installs only with `adb install -r -t -g build/Penombra-adb.apk`. It is the only kind of build Android lets read the real extra dim state, so it notices changes made outside Penombra.

## Project layout

- `app/src/it/penombra/`: the whole app. `Dim` (the two layers and presets), `MainActivity`, `QuickPanel` (tile long-press), `EclipseService` (veil and notification), `VeilService` (accessibility veil), `DimTileService`, `Scheduler` with `ScheduleReceiver` and `BootReceiver`, `MoonView` and `MoonGlyph` (the moons), `PenSwitch`, `Ui`, `Prefs`.
- `app/res/values/strings.xml` (English) and `app/res/values-it/strings.xml` (Italian). To add a language, add `values-xx/strings.xml`, list it in `app/res/xml/locales_config.xml`, and add it to `MainActivity` (`LANGS` and the names in `languageName` / `pickLanguage`).
- `DESIGN.md`: the design system.

## License

MIT, see [LICENSE](LICENSE).
