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

## onn. 4K Pro (2026) and other Google TV boxes: what goes, what stays

This is the recommended device. On Google TV, wiiOS really replaces the home screen; it isn't layered on top the way it is on Fire TV.

**Goes away with Google's home screen (`launcherx`, disabled rather than uninstalled):**
- The sponsored rows and the "For you" / "Top picks" recommendations: the ads you see on the home screen.
- Google TV's profiles and its home-screen app rows.
- Google's ambient-mode screensaver, if you pick **Now Playing** or **Clock** in wiiOS Settings → Screensaver & Sleep.

**Stays, because it's the Android system underneath:**
- Wi-Fi, Ethernet, Bluetooth (remotes, controllers, headphones, soundbars) and HDMI-CEC.
- The Play Store, app and system updates, and your Google account.
- Chromecast built-in (casting from your phone) and Spotify Connect.
- The Google Assistant / Gemini voice button.
- Every app, including its own ads, if it has any. wiiOS only removes the ads on the home screen.

**The remote:** Google's home screen was what answered the **YouTube** and **Input** buttons. The install script sees that it's a Google TV and switches on wiiOS's `InputKeyService`, which answers them instead (`INPUT_KEY_SERVICE=0` leaves it off). If your remote has other app buttons (Netflix and so on) that stop working, find their key code with `tools/tv.py 'logcat -v time -s InputReader:D'` while pressing the button, and add `keycode:package` to `app_keys` in `launcher/app/src/main/res/values/config.xml`.

**Undo** (see [Undo](#undo)) turns Google's home screen back on, and the box is exactly as it came.

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

- **wiiOS** (bottom left): **wiiOS Settings**, a full-screen settings screen styled like the Wii's. It has four big buttons per page; press left/right to change pages and use **Back** at the bottom to go back. Every device setting opens the stick's own page, so nothing has to be rebuilt:
  - **Display & Picture**, **Sound & Speakers** (soundbar, surround, volume), **Network & Wi-Fi**, **Remotes, Controllers & Bluetooth**
  - **Captions & Subtitles**, **Screensaver & Sleep**, **Language**, **Inputs (HDMI)** (only on TVs that have them), **Date & Time**
  - **Accessibility**, **Apps**, **Storage**
  - **Menu Music** and **Menu Sounds** (on/off), **Hidden Channels** (choose one to bring it back), **Reset channel order**, **All Device Settings**, **About This Device**
- **SD**: opens your media or file app. That's the first one installed of Kodi, VLC, X-plore, Files, Photos or Plex (list: `sd_apps` in `launcher/app/src/main/res/values/config.xml`).
- **Envelope** (bottom right): the **Message Board**, with the channels you opened most recently, newest first. On a TV with HDMI ports, the inputs come first.

## Updates over the air

You only need the computer for the first install. After that, wiiOS updates itself from this repository's GitHub releases:

1. Every push to `main` builds a new APK in GitHub Actions and publishes it as release **build-N**. To publish from another branch: **Actions → CI → Run workflow**, then tick **Publish**.
2. Once a day (while **wiiOS Settings → System Update → Automatic updates** is On), the menu checks the latest release. If it's newer, it asks **Update now / Later**. You can also choose **Check for updates** there at any time.
3. **Update now** downloads the APK and installs it. On Android 12+ (onn. / Google TV) it installs silently from the second update on; Fire OS shows one **Install** confirmation. On Fire TV, the Home redirect is switched back on automatically after each update.

Every build is signed with the same key (`launcher/keys/`), so updates always install over the previous version. That key is public in the repo on purpose, and the updater only downloads from this repository's own releases. If you'd rather use a private key, see the comment in `launcher/app/build.gradle`. Devices installed with the old key then need one uninstall.

> A local build from your computer is "build 0", so any published release counts as newer and gets offered. To reinstall a local build over a newer release, uninstall wiiOS first, or build with `-PwiiosBuild=<higher number>`.

## Forecast Channel

The first channel is the **Forecast Channel**, modelled on the Wii's. Its tile shows the temperature and sky right now.

- **Opening it** shows the channel preview: a **CURRENT** card with **Wii Menu** and **Start**, as on the console.
- **Start** opens the channel's pages: grey button bars, a green title tab and city pill, the blue board, and the red "As of" strip. **▲ / ▼** (or ◀ / ▶) turn the pages:
  - **CURRENT**: temperature and conditions now.
  - **TODAY** and **TOMORROW**: high, low, chance of rain, plus humidity and wind for today.
  - **5-DAY FORECAST**: five columns with the day, an icon, the high (yellow) and the low (blue).
  - **GLOBE**: a spinning Earth (NASA's Blue Marble) with the current weather in about 45 cities worldwide. Arrows spin and tilt it, **OK** is *Next* (flies to the next city and shows its card), **⏪ / ⏩** zoom, **☰** is *Restore* (back to your city), and **Back** is *End*.
- **Data:** [Open-Meteo](https://open-meteo.com), free with no account or key. The Earth image downloads once (about 240 KB) the first time you open the channel, then stays on the device.
- **Location:** found automatically from your internet connection. To choose a city instead, go to **wiiOS Settings → Weather → Location → Search for a city**. Units (°F / °C) are on the same page.
- Like any channel, you can move it or hide it (hold OK).

## Language

The menu is **English**, whatever language the device itself is set to. To change it, go to **wiiOS Settings → Language** and pick any of the 20+ languages. The menu reloads in that language right away. Only wiiOS changes; the device's own menus keep their language. The new screens (Settings, Screensaver & Sleep and so on) are written in English so far, so other languages show English for those words until they're translated.

## Game controllers and other remotes

Anything the device can pair works as the remote. Pair it in **wiiOS Settings → Remotes, Controllers & Bluetooth**.

- **Xbox (Bluetooth models, 2016 and later), PlayStation DualShock 4 / DualSense, 8BitDo and other Bluetooth gamepads**: hold the controller's pairing button, then choose it in the list. Fire TV and Google TV both support these natively.
- On a controller: **D-pad or left stick** moves, **A** = OK, **B** = Back, **Y/X** = channel options, **LB/RB** turn the page, and **Start/Menu** opens wiiOS Settings.
- **Bluetooth keyboards and air mice** work too: arrows, Enter and Esc.
- **Your phone**: the Google TV app (onn./Google TV) or the Amazon Fire TV app (Fire TV) acts as a remote with a keyboard.
- **The TV's own remote via HDMI-CEC**: on most TVs, turn on HDMI-CEC (Anynet+, Bravia Sync, SimpLink…) and the TV remote's arrows drive the stick.

## Screensaver, music and turning the screen off

Go to **wiiOS Settings → Screensaver & Sleep**:

| Setting | What it does |
| --- | --- |
| **Screensaver** | **Now Playing** (blurred album colours fill the screen behind the artwork, title, artist and progress of whatever's playing in Spotify, YouTube Music, a podcast app…, plus the clock), **Clock**, or the device's own. |
| **Start screensaver after** | How long without a key press before the screensaver comes on (5 min to 1 hr, or Never). |
| **Turn screen off after** | How long before the device goes to sleep and the TV turns off (30 min to 8 hr, or Never). |
| **Screensaver goes dark after** | The Now Playing screensaver fades to a **black screen** after this long, while the music or podcast keeps playing. Any key brings the picture back. |
| **Preview screensaver** | Shows it now. It also works as a "music screen" you can open on purpose. |

Notes:
- **Listening with the screen dark:** use **Now Playing** plus **Screensaver goes dark after**. The audio keeps playing through the TV or soundbar while the picture is black, with no burn-in. "Turn screen off after" really puts the stick to sleep, and **that stops the audio**, so for long podcasts set it to Never or to longer than the episode.
- **Spotify:** install **Spotify** from the Play Store (Google TV) or the Amazon Appstore (Fire TV) and it gets a channel like any other app. Start music, press **Home**, and it keeps playing while you browse. **Spotify Connect** also works: pick the stick from the Spotify app on your phone, and the Now Playing screensaver shows what's on.
- The install script grants wiiOS permission to change these timers and to read what's playing. If a device refuses (some Fire OS versions), choosing a value opens the device's own screensaver page instead.

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
