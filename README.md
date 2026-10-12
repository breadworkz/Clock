# Material Clock

> ! This project is currently in beta expect a lot of bugs

![Kotlin](https://ziadoua.github.io/m3-Markdown-Badges/badges/Kotlin/kotlin1.svg)
![Android](https://ziadoua.github.io/m3-Markdown-Badges/badges/Android/android3.svg)
![GitHub Badge](https://ziadoua.github.io/m3-Markdown-Badges/badges/Github/github3.svg)
![Stars](https://m3-markdown-badges.vercel.app/stars/6/1/breadworkz/Clock)
![Issues](https://m3-markdown-badges.vercel.app/issues/8/1/breadworkz/Clock)
![Licence GPLv3](https://ziadoua.github.io/m3-Markdown-Badges/badges/LicenceGPLv3/licencegplv32.svg)
![Android Screenshot](https://raw.githubusercontent.com/breadworkz/Clock/master/AndroidScreenshot.svg)

A Material 3 Expressive clock app for Android, built entirely in Jetpack Compose. It has alarms, a world clock, timers and a stopwatch, plus a home-screen analog clock widget you can reshape.

It is based on [rbouaf/material-clock](https://github.com/rbouaf/material-clock), so huge thanks to him.

## Features

**Alarms**
- One-shot or repeating alarms with a custom label, per-alarm ringtone and vibration
- Pick which days repeat, with a configurable week start (system default, Saturday, Sunday or Monday)
- Snooze (default 10 min) and auto-silence (default 10 min, or never)
- Rings over the lock screen with a full-screen Snooze / Dismiss screen
- Rearmed automatically after a reboot, time change or timezone change
- Handles "set an alarm for…" requests from Assistant and other apps

**World clock**
- Add cities by searching real IANA time zones, so daylight saving is handled by the platform
- Shows the offset from your home zone, including half- and quarter-hour zones
- Rows invert at night; optional seconds and 12/24-hour override

**Timers**
- Multiple labelled countdowns with start, pause and resume
- Stored as deadlines, so they stay accurate if the app is killed
- Live notification you can control from the shade, with its own sound and vibration

**Stopwatch**
- Hundredths-of-a-second display with laps
- Highlights the fastest and slowest lap
- Runs on elapsed-realtime, so changing the clock or crossing a DST change can't move it

**Clock widget**
- An analog clock you can shape: circle, squircle, rounded square, flower and more
- Five presets (Classic, Bare, Roman, Railway, Bloom) or a fully custom face
- Eleven hand styles, several numeral systems (including Roman and Eastern Arabic), tick and index options, optional date and a seconds hand
- Follows the app's colours or its own palette

**Theming**
- Dynamic colour from your wallpaper on Android 12+, with static fallbacks
- Eleven Expressive palettes (Baseline, Expressive, Concept, Violet, Coral, Ember, Citron, Forest, Lagoon, Azure, Fuchsia)
- System, light or dark mode, plus an AMOLED black option
- Google Sans Flex variable font

## Privacy

- No accounts, no analytics, no crash reporting, no ad IDs
- **No `INTERNET` permission**, so Android itself prevents the app from using the network
- Alarms, timers, cities and settings are stored in the app's private storage

| Permission | Why |
| --- | --- |
| `USE_EXACT_ALARM` (`SCHEDULE_EXACT_ALARM` up to API 32) | Ring at the exact minute you set |
| `POST_NOTIFICATIONS` | Ringing alarm and live timer/stopwatch notifications |
| `USE_FULL_SCREEN_INTENT` | Show the alarm over a locked screen |
| `RECEIVE_BOOT_COMPLETED` | Re-arm alarms after a reboot |
| `FOREGROUND_SERVICE` (media playback) | Keep alarm audio playing while it rings |
| `VIBRATE`, `WAKE_LOCK` | Vibration and keeping the screen on while ringing |

## Requirements

- Android 8.0 (API 26) or newer
- Dynamic colour needs Android 12 (API 31) or newer; older versions use the built-in palettes

## Building

You'll need Android Studio with JDK 17. The project uses Android Gradle Plugin 9.2, Kotlin 2.4 and compileSdk 37.

```sh
git clone https://github.com/breadworkz/Clock.git
cd Clock
./gradlew assembleDebug
```

The debug build installs alongside a release build (application ID `app.materialclock.debug`). The APK ends up in `app/build/outputs/apk/debug/`.

Material 3 Expressive components currently require the alpha Compose BOM (`compose-bom-alpha`) and `material3` 1.5.0 alpha, which are pinned in `gradle/libs.versions.toml`.

### Release builds

`./gradlew assembleRelease` produces a minified, resource-shrunk APK. To sign it with your own key, add these to `~/.gradle/gradle.properties` (not the project's):

```properties
clockStoreFile=/path/to/keystore.jks
clockStorePassword=...
clockKeyAlias=...
clockKeyPassword=...
```

Without them the release build falls back to the debug key. That is fine for sideloading a personal build, but not for distribution.

## Project structure

```
app/src/main/java/app/materialclock/
├── alarm/    Alarm and timer scheduling, ringing service and screen, notifications
├── core/     Models: Alarm, WorldCity, ClockTimer, Stopwatch
├── data/     DataStore-backed settings, widget configuration and presets
├── ui/       Compose app shell, screens, sheets and theme (palettes, type, clock face)
└── widget/   Home-screen widget provider, config screen and canvas renderers
tools/        Scripts that generate the palettes and the bundled font subsets
licenses/     Third-party licence texts (SIL OFL 1.1)
```

Regenerating the palettes (`tools/gen_palettes.py`) needs `pip install materialyoucolor`.

## Built with

- Kotlin and Jetpack Compose
- Material 3 Expressive (`androidx.compose.material3`)
- AndroidX DataStore, Lifecycle and `androidx.graphics.shapes`

## Credits

- [rbouaf/material-clock](https://github.com/rbouaf/material-clock), which this app is based on
- Google Sans Flex (© Google LLC) and Noto Sans Arabic (© The Noto Project Authors), used under the [SIL Open Font License 1.1](licenses/OFL-1.1.txt)

Google, Google Sans, Material, Android and Pixel are trademarks of Google LLC. This app is not affiliated with or endorsed by Google.

## License

Released under the [GNU General Public License v3.0](LICENSE). The bundled fonts keep their own licences (see above).


