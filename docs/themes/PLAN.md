# WiiOS themes: plan and previews

Dominic asked for themes on 2026-10-21. The previews in this folder were approved as the direction.
They are mockups (HTML), not screenshots: the letter squares stand in for app icons.

## Must-haves

- **Real app art, like today.** Every theme shows each app's own banner or icon, loaded from the
  installed app (PackageManager, as `WiiMenuActivity.addChannels` does now), and honours the custom tile
  pictures (`picture.<key>` preferences, files in `Android/data/com.gathin.tvhome/files/tiles`).
  The letter squares in the previews are placeholders only.
- **No third-party marks.** Themes copy a *feel*, never assets: no Apple, Windows or Nintendo logos,
  wallpapers, system fonts, sounds or characters (the island theme has no Animal Crossing characters,
  leaf logo or art). Outfit stays the font.
- **A Themes page in WiiOS Settings** to pick one, with a preview, saved in prefs.
- **1 GB sticks.** Fire TV Stick 3rd Gen (AFTSS, Fire OS 7.7.1.3, 922 MB RAM) is the test device:
  RGB_565 for static layers, half-size static backgrounds, no per-frame allocation (same rules as
  `WiiSaverView`).
- **Skip**: automatic day/night switching (Dominic said no).

## Themes

| Preview | Theme | What changes |
| --- | --- | --- |
| `classic.jpg` | Classic | Today's menu, unchanged. Default. |
| `dark.jpg` | Dark | Charcoal stripes and panel, light text, glowing accent ring. |
| `accent-color.jpg` | Accent color | Classic (or Dark) with a picked accent: focus ring, bottom-bar line, button rings, cursor. Swatches: blue `#34BEED`, green `#22C55E`, pink `#F43F5E`, purple `#A855F7`, amber `#F59E0B`, black `#111827`. |
| `winter.jpg` | Winter | Pale blue sky gradient, white panel, light snowfall (animated, cheap particles). |
| `halloween.jpg` | Halloween | Purple-black stripes, orange accent and text. |
| `beach.jpg` | Beach | Sky-to-sand gradient, sand panel, ocean-blue accent, a wave line above the panel. Optional gull/wave sounds. |
| `cinema-tv-style.jpg` | Cinema (TV-box style) | Black, a large "Continue watching" hero for the last app opened (`LastOpened`), big rounded cards below, the focused card scaled up. Clock top right. |
| `desktop-windows-style.jpg` | ~~Desktop: Windows style~~ (dropped 2026-10-22) | Wallpaper, shortcuts down the left, a centred taskbar (round WiiOS start button, search, pinned apps with running dots, weather left, clock and date right). The start button opens a Start panel: search, Pinned grid, Recently played, profile row. |
| `desktop-mac-style.jpg` | ~~Desktop: Mac style~~ (dropped 2026-10-22) | Top menu bar (WiiOS orb, Channels, File, Edit, View, Go, Window, Help; weather, Wi-Fi, volume, search, date and time), folders down the right, a "Channels" window with red/yellow/green buttons and a sidebar (Channels, Games, Shop, Settings), and a dock whose focused icon grows with its name above it. |
| `island-village.jpg` | Island village (**built**, `IslandScene`) | Sky with clouds, rolling grass, cream signboard tiles with a wooden edge, apple trees and flowers, the clock on a wooden sign, round cream buttons. |

The Wii-layout themes (Classic, Dark, Accent, Winter, Halloween, Beach) only swap palette and decorations:
a `Theme` record (stripe base/amp tint or gradient, panel tint, line/accent colour, text colour, tile
frame style, decorations) read by `WiiBase` / `WiiMenuView`. Cinema, the two desktops and Island village
change layout too: separate renderers behind the same channel list, focus model and `Listener` callbacks,
so Settings, Shop, Games, channel options (Move, Hide, Change picture, App info) and the remote all keep
working.

## Fire TV notes from 2026-10-21 (keep these working)

- `FireTvHomeService` covers the Amazon home with `menu-cover.jpg`, a picture of the menu saved by
  `WiiMenuActivity` 2.5 s after each resume. It must show the current theme, so keep saving it from the
  themed view.
- Fire OS 7.7 never delivers the remote's Home key to `onKeyEvent`; the service reacts to Amazon launcher
  window state and content changes, and Fire OS holds activity starts for 5 s after Home.
- Opening Fire TV Settings sets a pass (`allowAmazonUntilHome`) so the Amazon Settings row is not covered;
  a new Amazon home window 4 s later ends it.

## Status (2026-10-22)

- Built: Classic, Dark, Winter (snow, pine trees, sledders), Fall (falling leaves), Halloween, Beach, accent
  colour, and Island village. Island keeps the channel grid, focus, paging and every menu, with its own
  scenery (sky with drifting clouds, hills, flowers, apple trees, butterflies), cream signboard tiles showing
  each app's banner, custom picture, or icon and name, the clock and full date on a wooden sign, and round
  cream buttons.
- Dropped: the Windows-style and Mac-style desktops.
- Next: Cinema (TV-box style).
