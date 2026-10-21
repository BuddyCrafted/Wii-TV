# Which devices wiiOS runs on

wiiOS is one app (one APK) that works on Android-based TV devices. The install script detects the kind of device and sets it up the right way, so the same build serves every row marked as supported below.

| Device | Price (US, 2026) | How wiiOS becomes the home screen | Status |
| --- | --- | --- | --- |
| **onn. 4K Pro (2026)** – Walmart | ~$60 | Replaces Google's home screen (disabled, not deleted) | **Recommended** |
| onn. 4K Google TV Stick (2026) – Walmart | ~$40 | Same as above | Supported; less memory and storage |
| onn. 4K Plus, older onn. 4K boxes | ~$20–30 | Same as above | Supported |
| Chromecast with Google TV, Google TV Streamer | $100–150 | Same as above | Supported, but costs more for no gain |
| Other Android TV / Google TV boxes and TVs (TCL, Hisense, Sony, Philips, Xiaomi, Nvidia Shield…) | — | Same as above (the original wii-tv project was built on a Philips TV) | Supported; some brands need their own launcher package named (`STOCK_LAUNCHER=`) |
| Fire TV Stick 3rd Gen / Lite / 4K / 4K Max (Fire OS 7–8) | used | A Home-button redirect on top of Fire OS (Amazon's home can't be disabled) | Supported, with the limits in [fire-tv-stick-2021.md](fire-tv-stick-2021.md) |
| **Raspberry Pi 5 + LineageOS Android TV** | ~$80–110 all-in | wiiOS is the only home screen on an open Android you control | Supported (Android 15/16). No official Play Store; Netflix and similar play in SD |
| Fire TV Stick 4K Select and later (Vega OS) | — | — | **Not possible.** Vega OS isn't Android and doesn't run Android apps |
| Roku, Apple TV, Samsung (Tizen), LG (webOS) | — | — | **Not possible.** Not Android |

## Several options at once

- **One app per device, with a different setup for each.** The APK is identical everywhere. On first install, `launcher/build-and-install.sh` checks what it's talking to (Amazon → Home redirect; Google TV → replace launcherx and handle the YouTube and Input buttons; other Android TV → replace its launcher) and does the matching steps.
- **Per-device tuning happens at run time.** `DeviceProfile` lowers memory use on 1 GB devices, and the Forecast Channel's globe renders smaller there.
- **Updates come to every device from the same place.** Once installed, each device pulls new builds from this repository's GitHub releases (wiiOS Settings → System Update).
- **New device types plug in at two points:** a detection branch in the install script (how Home is taken over) and, if needed, a `DeviceProfile` entry (what the hardware can handle). The menu, channels and settings don't change.

## Remote buttons

- **Google TV:** the remote's app buttons (YouTube and others) keep working. Google's home screen used to answer some of them; wiiOS's button helper answers them now. In **wiiOS Settings → Remote Buttons** you can:
  - switch shortcut buttons off,
  - change which app any button opens,
  - choose **Add a button** and press a remote button to give it an app.
- **Fire TV:** Fire OS answers its own app buttons (Netflix, Prime Video…), so they keep working. Remote Buttons can add others, but this hasn't been checked on a real Fire Stick.
