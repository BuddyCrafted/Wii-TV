# wii-tv

The Wii channel menu as the home screen of your Android TV.

![wiiOS on a Philips 65PUD7906](docs/wii-menu.jpg)

wii-tv replaces the home screen of an Android TV with a menu in the style of the Wii Menu: twelve channels to a page, the segment clock, the date, the round buttons, a pointing hand that follows your selection and sounds as you move. Each channel is one of your apps, with its own banner. When the TV turns on, a white notice screen comes up, as on the console, and OK takes you to the menu.

Español: [README.md](README.md)

## Disclaimer

wii-tv is a fan project. It is not affiliated with or endorsed by Nintendo. "Wii" is a trademark of Nintendo.

The repository contains nothing from Nintendo: no logos, fonts, images or sounds. Everything on screen is drawn in code (`WiiMenuView`, `WiiBase`, `WiiPalette`), and the sounds are synthesised by `tools/sounds/make_sounds.py`.

## Features

- **Channels**: 4 columns by 3 rows per page. The first column of the next page shows at the right edge, and the arrows turn the page. Your apps fill the channels; the rest stay empty.
- **Clock and date**: a 12-hour segment clock with AM/PM, and the short date in the TV's language.
- **Bottom buttons**:
  - **wiiOS** (left) opens Android TV Settings.
  - **SD** opens the TV's media player (USB drives).
  - **Envelope** (right) lists the HDMI inputs and live TV.
- **Pointing hand** that follows the selected channel or button.
- **Sounds** for moving, opening, turning the page, opening the inputs list and leaving the notice screen. They follow the TV volume.
- **Notice screen** at power-on. OK or the arrows close it, or it leaves by itself after 12 seconds. It does not come back when you leave an app.
- **Always starts on the menu**: turning the TV on brings the menu back even if another app was open.
- Clock screensaver (optional).

Tested on a Philips 65PUD7906/77 (Android TV 11, 1920 x 1080). It should work on any Android TV 8.0 or later with a 16:9 screen.

## Requirements

- A macOS or Linux computer on the same network as the TV. Windows works for the install too (see step 5).
- Python 3.10 or later.
- JDK 17 and the Android SDK with `platforms;android-34` and `build-tools;34.0.0`. Android Studio ships both; on macOS, `brew install openjdk@17 android-commandlinetools` also works.
- Debugging turned on on the TV (step 1).

## Install

Run every command from the repository folder, and replace `192.168.1.50` with your TV's IP address.

1. **Turn on debugging.** Settings → Device Preferences → About: select **Build** seven times. Then Settings → Device Preferences → Developer options: turn on **USB debugging**, and **Network debugging** or **ADB over network** if your TV has it. Note the TV's IP address under Settings → Network & Internet.
2. **Set up the tools.**
   ```sh
   git clone https://github.com/MauroProto/wii-tv.git
   cd wii-tv
   python3 -m venv tools/venv
   tools/venv/bin/pip install -r tools/requirements.txt
   TV_HOST=192.168.1.50 tools/venv/bin/python tools/tv.py --probe
   ```
   The first time, the TV asks whether to allow debugging. Tick "Always allow from this computer" and accept. The key that authorises your computer is kept in `tools/adbkey/`; git ignores it. Do not share it.
3. **Back up.** `TV_HOST=192.168.1.50 tools/backup.sh` saves the app lists, the current home screen, the settings and a screenshot to `backups/<model>/<date>/`. Git ignores that folder because it holds the TV's serial number and network names.
4. **(Optional) Disable vendor apps.** `TV_HOST=192.168.1.50 tools/disable.sh examples/philips-65pud7906/debloat.txt`. Apps are disabled, not uninstalled. Review the list first and put `#` in front of anything you want to keep. For other brands, [cleanyourtv](https://github.com/Gathin23/cleanyourtv/tree/main/examples) has more than a hundred tested lists.
5. **Install the menu.** `TV_HOST=192.168.1.50 launcher/build-and-install.sh` builds the app, installs it, checks that it starts, disables the stock launcher and makes **wiiOS** the home screen. If anything goes wrong, it turns the stock launcher back on. On Windows: `powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.50`.
6. **Close debugging when you are done.** Turn USB and network debugging off in Developer options (optionally "Revoke USB debugging authorisations"), restart the TV, and check with `tools/venv/bin/python tools/adb-closed.py 192.168.1.50`.

## Remote

| Key | Action |
| --- | --- |
| Arrows | Move the hand. Right from the last column turns the page; down from the bottom row goes to the buttons. |
| OK | Opens the selected channel or button. |
| Back | Closes the inputs list or the notice screen. |
| Home | Back to the menu. |

## Customise

Change what you like and run `launcher/build-and-install.sh` again.

| What | Where |
| --- | --- |
| Which apps come first, in what order | `dock_apps` in `launcher/app/src/main/res/values/config.xml` (package names; the rest follow alphabetically) |
| Apps that never get a channel | `hidden_apps`, same file |
| Left button text | `wii_settings_label` (default `wiiOS`) |
| Sounds on or off | `wii_sounds` |
| Live TV app in the inputs list | `live_tv_package` |
| Notice screen text | `wii_intro_title`, `wii_intro_body`, `wii_intro_continue` in `res/values*/strings.xml` |
| How the sounds sound | `tools/sounds/make_sounds.py`; edit it and run `python3 tools/sounds/make_sounds.py` (needs numpy) |
| Colours, sizes, positions | Constants at the top of `WiiMenuView.java`; backdrop and panels in `WiiBase.java` and `WiiPalette.java` |
| App behind the SD button | `MEDIA_PLAYER` in `WiiMenuActivity.java` |

## Undo

`TV_HOST=192.168.1.50 tools/restore.sh examples/philips-65pud7906/debloat.txt com.google.android.tvlauncher` turns the disabled apps back on and returns Home to the stock launcher (on Google TV the last argument is `com.google.android.apps.tv.launcherx`). Then `tools/venv/bin/python tools/tv.py 'pm uninstall com.gathin.tvhome'` removes wiiOS.

## Credits and licence

- Built on [cleanyourtv](https://github.com/Gathin23/cleanyourtv) by Gathin (MIT): the ADB tools, the disable and restore scripts, and the base of the launcher.
- Outfit typeface, SIL Open Font License 1.1 (`launcher/FONT-LICENSE-OFL.txt`).
- MIT licence: see [LICENSE](LICENSE).
