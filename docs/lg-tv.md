# WiiOS on an LG TV (webOS)

LG TVs run webOS, not Android, so they get their own WiiOS: the same channel menu as the computer version, packaged as
an LG app. Its channels are the TV's own apps (Netflix, YouTube, Prime Video, Live TV, the HDMI inputs and so on), and
OK opens them. It's installed as an app, next to the TV's own home screen, which stays as it is.

LG only lets apps outside its store in through its free **Developer Mode**, so installing takes a computer once.

## 1. Developer Mode on the TV

1. On a phone or computer, make a free account at [webostv.developer.lge.com](https://webostv.developer.lge.com) (Sign
   in, then create an LG account if you don't have one) and accept the developer terms once.
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
  above. (An iPhone can't run LG's tools.)

WiiOS then shows up in the TV's app row. A newer version is installed the same way, over the old one.

## Keeping it installed

Developer Mode runs for a limited time, shown in the Developer Mode app with an **Extend** button. WiiOS extends it by
itself each time it's opened (at most twice a day), the way that button does, so using WiiOS now and then keeps it on.
If the TV won't let WiiOS do that, press **Extend** now and then (webOS Dev Manager can too). If the time runs out, the
TV removes apps installed this way; switching Developer Mode on again and reinstalling brings WiiOS back.

## What works

- The TV's apps as channels, moved and hidden like on the other versions. When the TV won't list its apps, WiiOS shows
  LG's usual ones; one that isn't installed says so when chosen and can be hidden.
- The LG remote's arrows, OK and Back, and the Magic Remote's pointer; menu music and sounds.
- Settings → **TV Settings** opens the TV's own settings.

Not on LG: making WiiOS the TV's home screen, updating itself, the phone remote, profiles and the Shop.
