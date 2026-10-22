# WiiOS on a computer (Windows and Linux)

The same channel menu as the TV version, as a full-screen app for a gaming PC, a living-room PC or a laptop
hooked up to the TV. It looks and sounds the same, because it's drawn from the same measurements, sounds and
music as the TV version, and it updates from the same GitHub releases.

## Download

Every release on the [Releases page](https://github.com/BuddyCrafted/Wii-TV/releases) has one file per device:

| File | For |
| --- | --- |
| `WiiOS-build-N.apk` | TVs and streaming sticks (see [streaming-stick-setup.md](streaming-stick-setup.md)) |
| `WiiOS-windows-x64.exe` | Windows 10 and 11 (64-bit) |
| `WiiOS-linux-x86_64.AppImage` | Linux (64-bit), most distributions |

**Windows:** download the `.exe` and run it. There's nothing to install. The first time, Windows SmartScreen
may say *"Windows protected your PC"*, because the app isn't signed with a paid certificate. Choose
**More info → Run anyway**. It uses Microsoft Edge WebView2, which Windows 10 and 11 already have.

**Linux:** download the AppImage, make it runnable (`chmod +x WiiOS-linux-x86_64.AppImage`, or tick "Allow
executing" in its properties), and run it.

## From a flash drive

Copy the `.exe` (or the AppImage) onto a USB stick and run it from there on any computer. Updates replace the
file on the stick, so the stick stays current. Each computer keeps its own channel order and settings.

## Using it

- **Channels** are your installed **Steam** and **Epic Games** games (with their Steam artwork), plus YouTube,
  Netflix, Spotify and Twitch, which open in your web browser. Games you install while WiiOS is open show up
  when you come back to it.
- **Mouse:** the hand follows the mouse like the Wii Remote's pointer. Click to open, right-click a channel for
  Move / Hide, and use the scroll wheel to turn pages.
- **Game controllers** (Xbox, PlayStation, most USB and Bluetooth pads): D-pad or left stick move, **A** opens
  (hold for Move / Hide), **B** goes back, **LB/RB** turn the page, and **Start** opens WiiOS Settings.
- **Keyboard:** arrows, **Enter** (hold for Move / Hide), **Esc** to go back, **Page Up/Down** to turn pages,
  **Home** for Settings, and **F11** to switch full screen on and off.
- **WiiOS button:** Settings. **SD card:** your files. **Envelope:** the Message Board, with the channels you
  opened recently.

## WiiOS Settings

Display, Sound, Network & Internet and Controllers & Bluetooth open the computer's own settings pages (Windows
Settings, or GNOME/KDE settings on Linux). The other settings:

- **Full screen** on or off (or press **F11**). WiiOS starts full screen; switched off, it becomes a normal window you can move and resize, and it remembers your choice.
- **Start with this computer**: opens WiiOS when you sign in, so the PC starts into the Wii menu like a console.
- **Menu music** and **Sounds** on or off.
- **Hidden channels** and **Reset channel order**.
- **Your own channels**: opens `channels.json`. Add anything that opens, such as an emulator, Kodi, a folder or
  a website, then come back to WiiOS:

  ```json
  {
    "channels": [
      { "name": "Kodi", "open": "C:\\Program Files\\Kodi\\kodi.exe", "image": "C:\\Pictures\\kodi.png" },
      { "name": "RetroArch", "open": "C:\\RetroArch\\retroarch.exe", "args": ["--fullscreen"] },
      { "name": "Xbox Cloud Gaming", "open": "https://www.xbox.com/play" }
    ]
  }
  ```

  `image` is optional: a picture file or an `https://` link.
- **System Update**: check now, or switch automatic updates off.
- **Power**: Sleep, Restart, Shut down, or Close WiiOS.

## Version numbers

Every version of WiiOS (TV, Windows, Linux) shares one version number, set in the `VERSION` file at the top of
the repository: **1.0**, then **1.1, 1.2…** for new features, **1.0.1**-style for fixes only, and **2.0** for a big
milestone. Each release also has a build number (`build 30`) that always goes up; updates compare that, so a
release is never skipped whatever the version says. Releases are titled like *WiiOS 1.0 (build 30)*.

## Updates

WiiOS checks for a newer release once a day, and you can check any time in **Settings → System Update**. When
there is one, it asks first, then downloads its own file, checks it against the checksum GitHub lists for it,
swaps it in and restarts. The TV and computer versions come from the same release, so they stay in step.

## Not there yet

These are planned for the computer version: the Forecast Channel, the Shop and Games channels, the language
switch, a Mac version, and a bootable USB stick that starts straight into WiiOS.

## Building it yourself

You need Node.js 22 and Rust (plus, on Linux, `libwebkit2gtk-4.1-dev` and `librsvg2-dev`):

```sh
cd desktop
npm ci
npx tauri build            # Linux: the AppImage in src-tauri/target/release/bundle/appimage/
npx tauri build --no-bundle   # just the program, e.g. wiios.exe on Windows
node --test tests/*.test.mjs  # checks it still reads the TV version's shared sources correctly
```

`scripts/prepare.mjs` copies the shared parts from the Android launcher (the colour tables in `WiiPalette.java`,
the menu tune in `MenuMusic.java`, the interface sounds and the font) into `web/gen/` before each build, so
there's one source for each. For a quick look without building, serve `desktop/web` with any web server and open
it in a browser; it shows sample channels.
