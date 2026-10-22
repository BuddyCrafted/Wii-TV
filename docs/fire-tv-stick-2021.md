# WiiOS on a 2020–2022 Fire TV Stick

This page is for the Fire TV Sticks Amazon sold from 2020 to 2022. To check which one you have, go to **Settings → My Fire TV → About**:

| Model | Fire OS | RAM | CPU / GPU | Picture | WiiOS menu runs at |
| --- | --- | --- | --- | --- | --- |
| Fire TV Stick (3rd Gen) | 7 (Android 9) | 1 GB | 4 × 1.7 GHz / PowerVR GE8300 | 1080p | 1920 × 1080 |
| Fire TV Stick Lite | 7 (Android 9) | 1 GB | 4 × 1.7 GHz / PowerVR GE8300 | 1080p | 1920 × 1080 |
| Fire TV Stick 4K Max (1st Gen, 2021) | 7 (Android 9) | 2 GB | 4 × 1.8 GHz / PowerVR GE9215 | 4K | 1920 × 1080 (videos in apps still play in 4K) |

Specs are from [Amazon's device specifications](https://developer.amazon.com/docs/device-specs/device-specifications-fire-tv-streaming-media-player.html).

## What WiiOS does on these sticks

- **It doesn't replace Fire OS.** These sticks' bootloaders are locked, so the operating system can't be swapped. WiiOS is an app that becomes the screen you see. Fire OS keeps handling Wi-Fi, Bluetooth, the Appstore, updates and your Amazon account underneath.
- **Home button:** the "Wii menu as Fire TV home" accessibility service catches Home and opens WiiOS. Holding Home opens Settings. When Fire OS shows its own home anyway (at power-on, or when you back out of an app), WiiOS comes straight back on top. The Amazon home can flash for a split second; that's normal.
- **Built for 1 GB of RAM:** on the 3rd Gen and Lite, channel art is kept at 16 bits per pixel, which halves its memory. The menu music is generated once and then cached on the stick, so returning to the menu after a big app is quick even when Fire OS has closed WiiOS in the background. The install script also pre-compiles the app (`cmd package compile -m speed`) so it starts faster.
- **Layout:** the whole menu is drawn on a 1920 × 1080 canvas and scaled to the screen, which is exactly what all three sticks give apps. Nothing has to be adjusted per model.
- **Remote:** arrows and OK move and select. **Back** goes back. **☰ Menu** on a channel opens Move / Hide / App info; elsewhere it opens WiiOS Settings. **⏪ / ⏩** turn the page. Holding **Home** opens Fire TV Settings.

## Connecting the computer: Wi-Fi (recommended)

You only need the computer for the first install; after that, WiiOS updates itself from GitHub. You don't need the stick to build the app either, because the build happens on the computer or on GitHub.

1. On the stick: **Settings → My Fire TV → About**, then press OK on the device name 7 times until it says you're a developer.
2. **Settings → My Fire TV → Developer options → ADB debugging → On**.
3. **Settings → My Fire TV → About → Network**: note the **IP address**.
4. On the computer, follow Steps 2–3 of [streaming-stick-setup.md](streaming-stick-setup.md), using that IP. The install script sees that it's a Fire TV and sets up the Home redirect.
5. The first time, the stick shows **"Allow USB debugging?"**. Tick **Always allow from this computer**, then press **OK**.

## Connecting by USB cable (optional, not guaranteed)

Amazon's documentation says most Fire TV devices only accept ADB over the network. People have reported ADB over USB on the stick's micro-USB port, but mostly on older models and on Mac or Linux. Windows needs an extra USB driver. If you want to try it:

- **Use a cable that carries data.** Many micro-USB cables are charge-only.
- **Power:** a computer's USB port often can't supply what the stick needs (the included wall adapter gives 1 A). The stick may show an "insufficient power" warning or keep rebooting. A **powered USB hub** between the computer and the stick fixes that.
- **Turning on ADB debugging makes the stick's USB port data-only.** It's off-limits for other uses, like a USB drive or an OTG adapter, while debugging is on.
- The repository's install script talks over Wi-Fi only. Over USB, install with Google's `adb` ([platform-tools](https://developer.android.com/tools/releases/platform-tools)) and run the same set-up commands by hand:

```sh
adb devices                      # the stick should be listed as "device"
adb install -r app-release.apk   # the APK from GitHub Actions (WiiOS-apk) or launcher/app/build/outputs/apk/release/
adb shell pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS
adb shell appops set com.gathin.tvhome WRITE_SETTINGS allow
adb shell appops set com.gathin.tvhome REQUEST_INSTALL_PACKAGES allow
adb shell cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.MediaListener
adb shell cmd package compile -m speed -f com.gathin.tvhome
# Home redirect. This replaces the list of enabled accessibility services. If you use another one, add it after a colon.
adb shell settings put secure enabled_accessibility_services com.gathin.tvhome/.FireTvHomeService
adb shell settings put secure accessibility_enabled 1
adb shell am start -n com.gathin.tvhome/.WiiMenuActivity
```

The same commands also work over Wi-Fi with Google's `adb`, after `adb connect <ip>:5555`.

## Things that behave differently on Fire OS 7

- **Updates:** Fire OS 7 is Android 9, so every over-the-air update shows one **Install** confirmation. Silent updates need Android 12 or later.
- **Settings pages:** Fire TV's settings app doesn't support every standard Android settings shortcut. When it doesn't, a WiiOS Settings button opens the main Fire TV Settings instead of the exact page. Once the stick is connected we can query it (`adb shell cmd package query-activities …`) and point each button at the right Fire TV page.
- **Screensaver:** Fire OS uses Amazon's photo screensaver by default. Choosing **Now Playing** or **Clock** in WiiOS Settings → Screensaver & Sleep writes the Android screensaver setting. Whether this Fire OS version honours it hasn't been checked on a real stick yet; **Preview screensaver** works either way.
- **No HDMI inputs:** a stick has none, so the envelope button is the Message Board (your recently opened channels).
- **After a Fire OS system update**, if Home opens the Amazon screen again, run the install script once more. It re-enables the Home redirect.
