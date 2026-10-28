# WiiOS on an LG TV (webOS)

LG TVs run webOS, not Android, so they get their own WiiOS: the same channel menu as the computer version, packaged as
an LG app. Its channels are the TV's own apps (Netflix, YouTube, Prime Video, Live TV, the HDMI inputs and so on), and
OK opens them. It's installed as an app, next to the TV's own home screen, which stays as it is.

LG only lets apps outside its store in through its free **Developer Mode**, so installing takes a computer or a phone once.

## 1. Developer Mode on the TV

1. On a phone or computer, make a free account at [webostv.developer.lge.com](https://webostv.developer.lge.com) with an
   email and password (an account made with Google or Apple sign-in can't sign in on the TV) and accept the developer
   terms once.
2. On the TV, open the **LG Content Store** (Apps), search **Developer Mode** and install it.
3. Open **Developer Mode**, sign in with that account, and switch **Dev Mode Status** on. The TV restarts.
4. Open **Developer Mode** again and switch **Key Server** on. Leave this screen up: it shows the TV's IP address and a
   six-character **passphrase** for the next step.

## 2. Install WiiOS from a computer

Download `WiiOS-webos.ipk` from the [latest release](https://github.com/BuddyCrafted/Wii-TV/releases/latest), then
either:

- **webOS Dev Manager** (easiest): install [webOS Dev Manager](https://github.com/webosbrew/dev-manager-desktop), add the
  TV with its IP address and passphrase, and drop the .ipk on its Apps page.
- **LG's command-line tools** (needs Node.js):

  ```
  npx -p @webos-tools/cli ares-setup-device --add lgtv --info "{'host':'<TV IP>','port':'9922','username':'prisoner'}"
  npx -p @webos-tools/cli ares-novacom --device lgtv --getkey      (type the passphrase)
  npx -p @webos-tools/cli ares-install --device lgtv WiiOS-webos.ipk
  ```

- **An Android phone instead of a computer**: install **Termux** (from F-Droid), then in it run `pkg install nodejs`,
  `curl -LO https://github.com/BuddyCrafted/Wii-TV/releases/latest/download/WiiOS-webos.ipk`, and the three commands
  above.
- **An iPhone** (no LG tools needed): install **iSH Shell** from the App Store, allow it on the local network, and
  paste this as one line (with the TV's IP address), typing the passphrase when asked. It's done when it prints
  `"state": "installed"`; then ctrl-c.

  ```
  apk add curl openssh-client && curl -L -o WiiOS-webos.ipk https://github.com/BuddyCrafted/Wii-TV/releases/latest/download/WiiOS-webos.ipk && curl -s -o webos_rsa http://<TV IP>:9991/webos_rsa && chmod 600 webos_rsa && eval $(ssh-agent) && ssh-add webos_rsa && cat WiiOS-webos.ipk | ssh -p 9922 -o StrictHostKeyChecking=no prisoner@<TV IP> "cat > /media/developer/temp/WiiOS-webos.ipk" && ssh -p 9922 -o StrictHostKeyChecking=no prisoner@<TV IP> 'luna-send-pub -i -f luna://com.webos.appInstallService/dev/install "{\"id\":\"com.ares.defaultName\",\"ipkUrl\":\"/media/developer/temp/WiiOS-webos.ipk\",\"subscribe\":true}"'
  ```

  (iSH's npm can't install LG's own tools, so this does what they do: fetches the TV's key, copies the app over and
  asks the TV to install it.)

WiiOS then shows up in the TV's app row. A newer version is installed the same way, over the old one.

## Keeping it installed

Developer Mode runs for a limited time, shown in the Developer Mode app as **Remain Session** with an **Extend**
button. If the time runs out, the TV removes the apps installed this way (WiiOS, YouTubeHD); switching Developer Mode
on again and reinstalling brings them back. Three things keep it running, and any one is enough:

- **WiiOS** extends it by itself each time it's opened (at most twice a day), the way the Extend button does. To see
  whether the TV lets it, note Remain Session, open WiiOS, then look again: if it's back near the full time, it works.
- **GitHub, once a day**, with nothing on at home: the repository's *Keep LG Developer Mode on* workflow
  (`.github/workflows/lg-devmode.yml`) extends every TV whose session token is in the `LG_DEVMODE_TOKENS` secret
  (Settings → Secrets and variables → Actions → New repository secret; one token per line). Get a TV's token from
  iSH with the key from the install step loaded:

  ```
  eval $(ssh-agent) && ssh-add webos_rsa && ssh -p 9922 -o StrictHostKeyChecking=no prisoner@<TV IP> cat /var/luna/preferences/devmode_enabled
  ```

  Then run the workflow once by hand (Actions → Keep LG Developer Mode on → Run workflow): a green run means LG
  took the token. If a run fails later, GitHub emails you, and that TV needs Developer Mode switched on again.
- **By hand**: press **Extend** in the Developer Mode app now and then (webOS Dev Manager can too).

Turning Developer Mode off, or signing out of it, removes the apps straight away, so leave it on.

## YouTubeHD

Each release also has `YouTubeHD-webos.ipk`: [youtube-webos](https://github.com/webosbrew/youtube-webos) by webosbrew
(GPL-3.0), YouTube's own TV app without ads and with SponsorBlock, built by `webos/youtubehd.sh` from a pinned commit
with only its id and name changed, so it installs next to LG's YouTube instead of replacing it. Install it the same
way as WiiOS (use its file name in place of `WiiOS-webos.ipk`); WiiOS shows it as the **YouTubeHD** channel, and
keeping Developer Mode alive keeps it too.

## What works

- The TV's apps as channels, moved and hidden like on the other versions. When the TV won't list its apps, WiiOS shows
  LG's usual ones; one that isn't installed says so when chosen and can be hidden.
- Closing an app opened from WiiOS (Back until it quits) comes back to WiiOS instead of Live TV; Home still goes to
  the TV's home screen. A small background service in the WiiOS app (`webos/service`) does this.
- The LG remote's arrows, OK and Back, and the Magic Remote's pointer; menu music and sounds.
- Settings → **TV Settings** opens the TV's own settings; the envelope button lists the TV's inputs (HDMI 1–4, Live TV).
- **Themes**: Settings → **Theme**, the same seven as on Android TV (Classic, Dark, Winter, Fall, Halloween, Beach,
  Island village) in the same colours, or **Automatic** (the default here), which follows the month. Winter, Fall and
  Beach have still scenery (snowflakes, leaves, surf); the animated scenes are on Android TV.

Not on LG: making WiiOS the TV's home screen, updating itself, the phone remote, profiles and the Shop.
