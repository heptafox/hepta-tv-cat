# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Android TV overlay app: a small cat in a screen corner that pops a speech bubble every 30 minutes of continuous watching. Pure Java, zero dependencies, no AndroidX, no layout XML (all views built in code). Written in "ponytail" style: minimal code, `// ponytail:` comments mark deliberate shortcuts and the condition under which to upgrade them. Keep that style; do not add libraries, abstractions, or scaffolding.

## Commands

Use the checked-in Gradle wrapper (needs the Android SDK; `ANDROID_HOME` must point at it):

```sh
export ANDROID_HOME=~/Library/Android/sdk
./gradlew --offline assembleDebug
```

APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

The only test is the self-check in `Streak.java` (plain Java, no Android):

```sh
java -ea app/src/main/java/dev/hepta/tvcat/Streak.java
```

`./install.sh <tv-ip> [apk]` connects over ADB, installs, grants the two `appops` permissions plus the notification-listener grant, and launches the app (needs `adb` on PATH). Step-by-step end-user setup is in README.md. All grants (`SYSTEM_ALERT_WINDOW`, `GET_USAGE_STATS`, `allow_listener`) must be given via adb; there is no in-app permission flow.

Open source under Apache 2.0 (`LICENSE`). Pushing a `v*` tag runs `.github/workflows/build.yml`, which attaches `hepta-tv-cat.apk` to a GitHub release. README screenshots in `docs/img/` are cropped to avoid private data (Wi-Fi name, device IDs); keep it that way when adding new ones.

## Architecture

Five source files under `app/src/main/java/dev/hepta/tvcat/`:

- `Streak.java`: pure logic, no Android imports. `tick(watching, gapMs)` is called once per minute and returns true on a milestone. A gap longer than `IDLE_RESET_MIN` (TV asleep / process frozen) or that many consecutive idle ticks resets the streak. `mood()` maps minutes to a face index 0..3, `label()` to the bubble text. Any change to streak rules goes here, and `main()` must still pass.
- `CatService.java`: foreground service that owns the overlay (`TYPE_APPLICATION_OVERLAY` window with a bubble `TextView` and cat `ImageView`), runs the 1-minute `tick` on the main-thread `Handler`, and persists `min`/`last` to SharedPreferences every tick so a restart resumes the streak. "Watching" = screen interactive AND the foreground package (latest `ACTIVITY_RESUMED` from `UsageStatsManager`) is not the launcher. Without usage access the foreground package is unknown and screen-on counts as watching. Every pop-up goes through `say(text, face, showMs)` (showMs 0 = sticky), which honors `muted`. The tick also: tallies `dayMin` and `app:<pkg>` minutes (cleared on a new `day`), chimes on each hour change with `status()` (time · today · app · weather), yawns at the `bed` hour and sets `streak.lateHours`, nudges after `HOME_NUDGE_MIN` on the launcher, warns on loud `STREAM_MUSIC`, shows a sticky "No internet!" when the active network is not validated, and (if `weather` is on) fetches geojs.io + open-meteo.com once an hour on a plain `Thread` using `HttpURLConnection` and `org.json` (both framework, no dependency).
- `NotifListener.java`: `NotificationListenerService` that relays TV-side notifications (title — text) into the bubble via the static `CatService.me`. Only active if granted with `adb shell cmd notification allow_listener dev.hepta.tvcat/.NotifListener`.
- `SettingsActivity.java`: launcher entry point. Starts the service if enabled, then builds a D-pad-friendly settings column in code. It only writes SharedPreferences; the service reacts live via `onPref` (size/fade/pos) or is started/stopped directly (enabled).
- `BootReceiver.java`: starts the service on boot if `enabled`.

Shared state is the `"cat"` SharedPreferences file. Keys: `enabled`, `muted`, `weather`, `bed` (hour, -1 = off), `size` (dp), `fade` (0..90 percent transparency), `pos` (Gravity int), `min`, `last`, `day` (epoch day), `dayMin`, `app:<pkg>`. Defaults and bounds (`MIN_DP`/`MAX_DP`/`DEFAULT_DP`, `DEFAULT_FADE`/`MAX_FADE`, `DEFAULT_POS`, `BED_HOURS`/`BED_NAMES`) live as constants on `CatService` and are reused by `SettingsActivity`.

Drawables: `cat.xml` is the idle cat; `cat_happy/meh/angry/furious.xml` layer a `face_*.xml` over `cat_body.xml` and are indexed by `CatService.FACES[mood]`; `cat_sleepy.xml` (bedtime) and `cat_ears.xml` (loud) are used directly. `bubble.xml` is the speech bubble, `focus.xml` the D-pad focus highlight, `banner.xml` the TV launcher banner.

## Constraints worth knowing

- `targetSdk = 30` on purpose (matches the TV's Android 11); bump only when the TV updates.
- `minSdk = 26`, `android.useAndroidX=false`: stick to `android.*` framework classes only.
- Hardware acceleration is off app-wide in the manifest (`android:hardwareAccelerated="false"`); keep it that way unless you verify on the TV.
- Left-corner positions flip the root `LinearLayout` to RTL so the bubble stays on the inner side of the cat.
