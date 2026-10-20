# Projector Launcher

A lightweight home-screen launcher for Android-based projectors (Android TV, Google TV,
and vendor AOSP builds). It is built for a remote control: everything works with the D-pad and OK.

## Features

- **Apple TV–style home screen**: full-screen artwork of movies now in theaters cross-fades every
  8 seconds with a slow zoom, showing each movie's title, year, rating and synopsis. Movie data and
  artwork come from [TMDB](https://www.themoviedb.org): add a free API key in *More → Movie carousel*.
- **Dock**: your favorite apps sit along the bottom as rounded tiles. The focused tile lifts and shows
  its name, and moving down slides up the "All apps" row.
- **Clock** in the top-right, laid out inside the TV-safe area so projector overscan doesn't clip it.
- **Quick buttons** for Wi-Fi, Bluetooth, Projector (opens the HT13's keystone/focus console,
  `com.cptp.console`, falling back to display settings), Settings and More (launcher options).
  If the firmware lacks a screen, the main Settings screen opens instead.
- **Favorites**: on first run it pins whichever of YouTube, Netflix, Prime Video, Disney+, Plex,
  Kodi and VLC are installed.
- **App tiles**: TV apps show their 16:9 banners. Phone apps get a tile tinted with their icon's color.
- **Phone remote**: *More → Phone remote* shows a QR code. Scan it with an iPhone (or any phone on the
  same Wi-Fi) for a remote with a D-pad, Back/Home/Menu, volume, play/pause and a keyboard box for
  typing passwords and searches. It sends key presses through the projector's own adb daemon, so it
  needs USB debugging left on and a one-time "Allow USB debugging?" approval; requests are protected
  by a 4-digit PIN.
- **App menu**: hold OK, or press Menu, on an app to pin or unpin it, reorder favorites, hide it,
  open App info, or uninstall it.
- **Launcher button**: restore hidden apps, or open the system "default home app" chooser.
- **Back and Home** never leave the launcher; they jump back to the top.
- Apps you install or remove appear and disappear automatically.

## Build

You need JDK 17 and the Android SDK (Android Studio installs both).

```sh
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Pushing this repo to GitHub also builds it: `.github/workflows/build.yml` runs the same build and uploads the
APK as a workflow artifact.

## Install on the projector

1. On the projector, enable **Developer options**: Settings → About, then press OK on *Build* 7 times.
   Then turn on **USB debugging** or **Network debugging**.
2. From your computer:
   ```sh
   adb connect <projector-ip>:5555   # skip if using USB
   adb install -r app-debug.apk
   ```
   With no computer, you can sideload with an app such as *Downloader* or *Send Files to TV*.
3. Press **Home**. If the projector asks which launcher to use, pick **Projector Launcher** → *Always*.

### HT13 projector (stock launcher `com.chiptrip.tv.launcher`)

After installing, make this the home screen with:
```sh
adb shell cmd package set-home-activity dev.projector.launcher/.MainActivity
```
To go back to the stock launcher:
```sh
adb shell cmd package set-home-activity com.chiptrip.tv.launcher/com.dymt.tvlauncher.activity.HomeActivity
```

### If Home still opens the stock launcher

Some Google TV builds don't let you choose a home app. Try one of these:

- Open *Launcher → Choose default home app* in this launcher.
- Or set it from adb:
  ```sh
  adb shell cmd package set-home-activity dev.projector.launcher/.MainActivity
  ```
- As a last resort, disable the stock launcher (it can be re-enabled later with `pm enable`):
  ```sh
  adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx   # Google TV
  adb shell pm disable-user --user 0 com.google.android.tvlauncher           # Android TV
  ```
  Check the package name for your device with `adb shell cmd package resolve-activity -c android.intent.category.HOME -a android.intent.action.MAIN`.

## Project layout

| File | Purpose |
| --- | --- |
| `MainActivity.kt` | Home screen, quick buttons, app menus, back/home handling |
| `AppRepository.kt` | Finds launchable TV and phone apps, with their banners, icons and accent colors |
| `AppAdapter.kt` | Focusable app cards with the bounce-on-focus animation |
| `HeroCarousel.kt` | The cross-fading, slowly zooming movie backdrops and their text |
| `MovieRepository.kt` | Fetches now-playing movies from TMDB and caches their artwork |
| `RemoteService.kt`, `RemoteServer.kt` | Foreground service and tiny web server behind the phone remote |
| `AdbBridge.kt` | Presses keys and types text on the projector through its own adb daemon |
| `assets/remote.html` | The remote page the phone opens |
| `LauncherPrefs.kt` | Saves favorites, their order, hidden apps, and the TMDB key |

This product uses the TMDB API but is not endorsed or certified by TMDB.
