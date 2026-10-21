# Make the Wii menu your home screen: Fire TV Stick, onn. 4K, Google TV

This guide puts the wiiOS channel menu on a streaming stick or box as **the screen you land on when you press Home or turn the TV on**. It is written for:

- **Amazon Fire TV Stick / Fire TV Cube** (Fire OS 7 and 8)
- **onn. 4K / onn. 4K Pro** and other **Google TV** devices (Chromecast with Google TV, etc.)
- any other **Android TV** box

## What this does and doesn't change

The device keeps its normal operating system. wiiOS only replaces the **home screen**. Everything else keeps working the way it does now, because it's still the device's own software doing it:

- **Wi-Fi, Ethernet, Bluetooth, remotes**: the wiiOS button opens the device's own Settings, Network and Bluetooth screens.
- **Apps, sign-ins, the app store, system updates**: unchanged. New apps show up as channels automatically.
- **Casting, voice and the volume keys**: unchanged.

How it becomes the home screen depends on the device:

| Device | How wiiOS takes over Home | What happens to the stock home |
| --- | --- | --- |
| **Fire TV** | A small accessibility service ("Wii menu as Fire TV home") catches the Home button and opens wiiOS. When the Amazon home screen appears anyway (at power-on, or when you back out of an app), wiiOS goes straight back on top. | Left installed and enabled. Fire OS won't let it be disabled, and nothing is lost. |
| **onn. / Google TV / Android TV** | wiiOS is set as the default Home app. | The stock launcher is *disabled*, not uninstalled. One command puts it back (see [Undo](#undo)). |

## What you need

- A computer (Windows, macOS or Linux) on the **same Wi-Fi** as the stick.
- **Python 3.10 or newer** ([python.org](https://www.python.org/downloads/)). On Windows, tick "Add python.exe to PATH" during install.
- This repository: **Code → Download ZIP** on GitHub, then unzip it. Or `git clone` it.
- The app itself, either way:
  - **Easiest:** download the ready-made APK. On GitHub open **Actions → CI**, pick the latest green run, download **wiiOS-apk** and unzip it to get `app-release.apk`. Nothing else to install.
  - **Or build it yourself:** install JDK 17 and the Android SDK (Android Studio includes both). The install script builds the APK for you.

## Step 1: turn on developer mode and ADB

**Fire TV**
1. Settings → **My Fire TV** → **About**. Select your device name (e.g. "Fire TV Stick 4K") **7 times** until it says you're a developer.
2. Back to **My Fire TV** → **Developer options** → turn on **ADB debugging**.
3. Settings → My Fire TV → About → **Network**: write down the **IP address** (e.g. `192.168.1.50`).

**onn. 4K / Google TV**
1. Settings → **System** → **About** → select **Android TV OS build** **7 times**.
2. Settings → System → **Developer options** → turn on **USB debugging**.
3. Settings → **Network & Internet** → your Wi-Fi: write down the **IP address**.

> **Google TV with only "Wireless debugging":** some newer builds don't listen on the usual port 5555. Turn on **Wireless debugging**, choose **Pair device with pairing code**, and on the computer, with Google's [platform-tools](https://developer.android.com/tools/releases/platform-tools), run `adb pair <ip>:<pair-port>` and enter the code. Then run `adb connect <ip>:<port>` and `adb tcpip 5555`. Port 5555 now stays open until the device restarts. The tools below reuse that `adb` key, so you won't be asked again.

## Step 2: set up the tools (once)

Open a terminal (Windows: PowerShell) in the repository folder. Replace `192.168.1.50` with your device's IP.

**macOS / Linux**
```sh
python3 -m venv tools/venv
tools/venv/bin/pip install -r tools/requirements.txt
TV_HOST=192.168.1.50 tools/venv/bin/python tools/tv.py --probe
```

**Windows (PowerShell)**
```powershell
python -m venv tools\venv
tools\venv\Scripts\pip install -r tools\requirements.txt
$env:TV_HOST = "192.168.1.50"; tools\venv\Scripts\python tools\tv.py --probe
```

The first time, the TV asks **"Allow USB debugging?"**. Tick **Always allow from this computer** and choose **Allow**. `--probe` then prints the model and Android version.

## Step 3: install wiiOS and make it Home

One command. It detects the type of device and does the right thing for it.

**macOS / Linux**
```sh
# with the downloaded APK:
APK_FILE=~/Downloads/app-release.apk TV_HOST=192.168.1.50 launcher/build-and-install.sh
# or build it yourself:
TV_HOST=192.168.1.50 launcher/build-and-install.sh
```

**Windows (PowerShell)**
```powershell
# with the downloaded APK:
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.50 -ApkFile $HOME\Downloads\app-release.apk
# or build it yourself:
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.50
```

What you'll see:

- **Fire TV:** `Fire TV detected: installing with the Home button redirect…`, then `FireTvHomeService enabled` and `Installed.`
- **onn. / Google TV:** `Stock launcher: com.google.android.apps.tv.launcherx`, then `Home now resolves to com.gathin.tvhome/.WiiMenuActivity` and `Installed.` If something goes wrong, the script turns the stock launcher back on before it stops.

Press **Home** on the remote. You're on the channel menu. Turn the TV off and on: the white notice screen appears, and OK takes you to the menu.

## Step 4 (recommended): turn debugging off again

When it all works, go back to **Developer options** and turn **ADB / USB debugging** (and Wireless debugging) **off**. Anyone on your network could otherwise reach the device. Turn it back on only when you want to update or undo. You can check it's closed with:

```sh
tools/venv/bin/python tools/adb-closed.py 192.168.1.50
```

## Using it

| Remote | What it does |
| --- | --- |
| Arrows | Move the hand. Right on the last column turns the page; down from the bottom row reaches the buttons. |
| OK | Open the channel or button. |
| **Hold OK** on a channel (or press ☰ Menu on Fire TV) | Channel options: **Move**, **Hide channel**, **App info**. |
| In **Move**: arrows, then OK | The channel lifts and travels with the arrows; OK (or Back) puts it down. Your order is saved. |
| Back | Closes whatever is open. |
| Home | Back to the menu. **Fire TV: hold Home** for Settings. |

The round buttons and the SD card:

- **wiiOS** (bottom left): the menu with **Settings**, **Network & Wi-Fi**, **Remotes & Bluetooth**, **Music On/Off**, **Sounds On/Off**, **Hidden channels** (choose one to bring it back) and **Reset channel order**.
- **SD**: opens your media or file app. That's the first one installed of Kodi, VLC, X-plore, Files, Photos or Plex (list: `sd_apps` in `launcher/app/src/main/res/values/config.xml`).
- **Envelope** (bottom right): the **Message Board**, with the channels you opened most recently, newest first. On a TV with HDMI ports, the inputs come first.

## Undo

**Fire TV**: uninstall it and Fire OS is exactly as before:
```sh
TV_HOST=192.168.1.50 tools/venv/bin/python tools/tv.py 'pm uninstall com.gathin.tvhome'
```

**onn. / Google TV**: turn the stock launcher back on, then uninstall:
```sh
TV_HOST=192.168.1.50 tools/venv/bin/python tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'
TV_HOST=192.168.1.50 tools/venv/bin/python tools/tv.py 'pm uninstall com.gathin.tvhome'
```
(On older Android TV boxes the stock launcher is `com.google.android.tvlauncher`.)

## Troubleshooting

- **Fire TV: Home still opens the Amazon screen.** Fire OS turns accessibility services off when an app is reinstalled, and sometimes after a system update. Run the install command again; it re-enables the service. To check: `tools/tv.py 'settings get secure enabled_accessibility_services'` should list `com.gathin.tvhome/.FireTvHomeService`.
- **Fire TV: the Amazon screen flashes for a moment** after power-on or when an app closes. That's expected: Fire OS always starts its own home first, and wiiOS covers it a fraction of a second later.
- **Google TV: "Home resolves to com.google.android.tungsten.setupwraith".** Google TV's setup wizard also claims Home once the stock launcher is off. Disable it with `TV_HOST=… tools/disable.sh <list>`, using a list file containing `com.google.android.tungsten.setupwraith`, then run the install again. The script won't disable it while your saved Wi-Fi network still belongs to it, because that would drop the device off Wi-Fi. If it refuses, forget the Wi-Fi network in Settings → Network & Internet, join it again, and retry.
- **"could not ask the TV which launcher owns Home"**: check the IP, that debugging is on, and that you accepted the prompt on the TV. Run `tools/tv.py --probe` to test the connection.
- **No sound or music.** They follow the TV's volume. Check **Sounds** and **Music** in the wiiOS menu.
- **An app is missing from the channels.** Check it isn't under **Hidden channels** in the wiiOS menu, or in `hidden_apps` in `config.xml`.
