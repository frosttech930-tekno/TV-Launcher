# Projector Launcher

A lightweight home-screen launcher for Android-based projectors (Android TV, Google TV,
and vendor AOSP builds). It is built for a remote control: everything works with the D-pad and OK.

## Features

- **Clock and date** in the top-left, laid out inside the TV-safe area so projector overscan doesn't clip it.
- **Quick buttons** for Wi-Fi, Bluetooth, Display (keystone, focus and picture settings usually live
  here on projectors) and Settings. If the firmware lacks a screen, the main Settings screen opens instead.
- **Favorites row**: on first run it pins whichever of YouTube, Netflix, Prime Video, Disney+, Plex,
  Kodi and VLC are installed.
- **All apps grid**: shows TV apps with their 16:9 banners and phone apps as colored tiles.
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
| `AppRepository.kt` | Finds launchable TV and phone apps, with their banners and icons |
| `AppAdapter.kt` | Focusable app cards with the zoom-on-focus animation |
| `LauncherPrefs.kt` | Saves favorites, their order, and hidden apps |
