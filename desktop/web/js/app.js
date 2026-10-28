// The desktop menu's controller, after the Android launcher's WiiMenuActivity: builds the channel list (PC
// games and web channels, in the viewer's order), handles keyboard, mouse and game controllers, the WiiOS
// Settings pages, the Message Board, and updates from the same GitHub releases as the TV version.
import { WiiMenu, FONT } from './menu.js';
import { Audio } from './audio.js';
import { invoke, appWindow, native, kind } from './platform.js';

const canvas = document.getElementById('menu');
const audio = new Audio();
const prefs = {
  get(k, d) {
    try {
      const v = localStorage.getItem('wiios.' + k);
      return v === null ? d : JSON.parse(v);
    } catch {
      return d;
    }
  },
  set(k, v) {
    try {
      localStorage.setItem('wiios.' + k, JSON.stringify(v));
    } catch {
      // private mode: settings last until the window closes
    }
  },
};
audio.soundsOn = prefs.get('sounds', true);
audio.musicOn = prefs.get('music', true);

let info = { build: 0, version: '', os: 'web', autostart: false };
/** "1.0 (build 30)": the version people see, and the build number updates compare. */
const versionText = () => (info.version ? `${info.version} (build ${info.build})` : `build ${info.build}`);
let all = new Map(); // key -> channel, hidden ones included
let fullscreen = true;

// Web channels every computer gets: they open in the default browser, signed in as usual there. Those with
// `icons` show the service's own app icon, loaded from the service's site the way a browser shows a site's icon (nothing is
// bundled); the first address that answers is used, and offline the tile falls back to the brand colour.
const WEB = [
  { key: 'web:youtube', label: 'YouTube', target: 'https://www.youtube.com/', color: '#ff0033',
    icons: ['https://m.youtube.com/static/apple-touch-icon-180x180-precomposed.png', 'https://www.youtube.com/img/favicon_144.png'] },
  // Netflix and Spotify keep their name on a tile in the brand colour, which reads better on a TV than their icons.
  { key: 'web:netflix', label: 'Netflix', target: 'https://www.netflix.com/', color: '#141414', text: '#e50914' },
  { key: 'web:spotify', label: 'Spotify', target: 'https://open.spotify.com/', color: '#1db954' },
  { key: 'web:twitch', label: 'Twitch', target: 'https://www.twitch.tv/', color: '#9146ff',
    icons: ['https://www.twitch.tv/apple-touch-icon.png'] },
];

/** Loads the first of `urls` that answers with a picture, then calls done(img). */
function loadIcon(urls, done) {
  if (!urls.length) return;
  const img = new Image();
  img.onload = () => (img.naturalWidth >= 48 ? done(img) : loadIcon(urls.slice(1), done));
  img.onerror = () => loadIcon(urls.slice(1), done);
  img.src = urls[0];
}

function brandPaint(color, text = '#ffffff', label) {
  return (c, tw, th) => {
    const g = c.createLinearGradient(0, 0, 0, th);
    g.addColorStop(0, color);
    g.addColorStop(1, shade(color, 0.75));
    c.fillStyle = g;
    c.fillRect(0, 0, tw, th);
    c.font = `bold ${th * 0.22}px ${FONT}`;
    c.textAlign = 'center';
    c.textBaseline = 'middle';
    c.fillStyle = text;
    c.fillText(label, tw / 2, th / 2);
    c.textBaseline = 'alphabetic';
  };
}

function shade(hex, k) {
  const n = parseInt(hex.slice(1), 16);
  const ch = (sh) => Math.round(((n >> sh) & 255) * k);
  return `rgb(${ch(16)},${ch(8)},${ch(0)})`;
}

const menu = new WiiMenu(canvas, audio, {
  onChannel(ch) {
    const recent = [ch.key, ...prefs.get('recent', []).filter((k) => k !== ch.key)].slice(0, 12);
    prefs.set('recent', recent);
    invoke('launch', { target: ch.target, args: ch.args || [] }).catch((e) => menu.showNotice(`Couldn't open ${ch.label}: ${e}`));
  },
  onSettingsButton: openSettings,
  onMenuKey: openSettings,
  onSdButton() {
    invoke('open_files').catch((e) => menu.showNotice(String(e)));
  },
  onInputsButton() {
    const items = prefs.get('recent', []).map((k) => all.get(k)).filter(Boolean)
      .map((ch) => ({ label: ch.label, action: () => menu.listener.onChannel(ch) }));
    if (items.length) menu.showMenu('Message Board', items);
    else menu.showNotice('No messages yet. Channels you open show up here.');
  },
  onChannelOptions(ch) {
    menu.showMenu(ch.label, [
      { label: 'Move', action: () => menu.startMove() },
      { label: 'Hide channel', action: () => {
        prefs.set('hidden', [...new Set([...prefs.get('hidden', []), ch.key])]);
        reloadChannels();
      } },
    ]);
  },
  onOrderChanged(list) {
    const shown = list.map((c) => c.key);
    const rest = prefs.get('order', []).filter((k) => !shown.includes(k));
    prefs.set('order', [...shown, ...rest]);
  },
});

function toChannel(c) {
  const ch = { ...c, tile: null };
  if (c.image) {
    ch.image = new Image();
    ch.image.onload = () => menu.artChanged(ch);
    ch.image.src = c.image;
  }
  if (c.color) ch.paint = brandPaint(c.color, c.text, c.label);
  if (c.icons) {
    loadIcon(c.icons, (img) => {
      ch.icon = img;
      ch.paint = null;
      menu.artChanged(ch);
    });
  }
  return ch;
}

async function reloadChannels() {
  let found = [];
  try {
    found = await invoke('channels');
  } catch (e) {
    menu.showNotice(`Couldn't list your ${kind === 'webos' ? 'apps' : 'games'}: ${e}`);
  }
  // On an LG TV the TV's own apps are the channels (its YouTube, Netflix...), so no web ones.
  const list = [...(kind === 'webos' ? [] : WEB), ...found.sort((a, b) => a.label.localeCompare(b.label))].map(toChannel);
  all = new Map(list.map((c) => [c.key, c]));
  const hidden = new Set(prefs.get('hidden', []));
  const order = prefs.get('order', []);
  const rank = new Map(order.map((k, i) => [k, i]));
  const shown = list.filter((c) => !hidden.has(c.key));
  // Saved order first; channels new since then keep their default place after them.
  shown.sort((a, b) => (rank.has(a.key) ? rank.get(a.key) : 1e6 + list.indexOf(a)) - (rank.has(b.key) ? rank.get(b.key) : 1e6 + list.indexOf(b)));
  menu.setChannels(shown);
}

// ---- Settings ---------------------------------------------------------------------------------

const onOff = (v) => (v ? 'On' : 'Off');

function openSettings() {
  menu.pushSettings('WiiOS Settings', mainSettings);
}

function mainSettings() {
  if (kind === 'webos') return tvSettings();
  const items = [
    { label: 'Display', action: () => osSettings('display') },
    { label: 'Sound', action: () => osSettings('sound') },
    { label: 'Network & Internet', action: () => osSettings('network') },
    { label: 'Controllers & Bluetooth', action: () => osSettings('bluetooth') },
    { label: 'Full screen', value: onOff(fullscreen), action: () => setFullscreen(!fullscreen) },
    { label: 'Start with this computer', value: onOff(info.autostart), action: async () => {
      try {
        await invoke('set_autostart', { on: !info.autostart });
        info.autostart = !info.autostart;
      } catch (e) {
        menu.showNotice(String(e));
      }
      menu.refreshSettings();
    } },
    { label: 'Menu music', value: onOff(audio.musicOn), action: () => {
      audio.setMusic(!audio.musicOn);
      prefs.set('music', audio.musicOn);
      menu.refreshSettings();
    } },
    { label: 'Sounds', value: onOff(audio.soundsOn), action: () => {
      audio.soundsOn = !audio.soundsOn;
      prefs.set('sounds', audio.soundsOn);
      menu.refreshSettings();
    } },
  ];
  const hidden = prefs.get('hidden', []).filter((k) => all.has(k));
  if (hidden.length) items.push({ label: 'Hidden channels', value: String(hidden.length), action: showHidden });
  if (prefs.get('order', []).length) {
    items.push({ label: 'Reset channel order', action: () => {
      prefs.set('order', []);
      reloadChannels();
      menu.refreshSettings();
      menu.showNotice('Channels are back in their first order.');
    } });
  }
  items.push({ label: 'Your own channels', action: async () => {
    try {
      await invoke('open_channels_file');
      menu.showNotice('Add channels to channels.json; they appear when you come back.', 6000);
    } catch (e) {
      menu.showNotice(String(e));
    }
  } });
  items.push({ label: 'System Update', value: versionText(), action: () => menu.pushSettings('System Update', updateSettings) });
  items.push({ label: 'Power', action: () => menu.pushSettings('Power', powerSettings) });
  return items;
}

/** WiiOS Settings on an LG TV: the TV's own settings, and WiiOS's. */
function tvSettings() {
  const items = [
    { label: 'TV Settings', action: () => osSettings('') },
    { label: 'Menu music', value: onOff(audio.musicOn), action: () => {
      audio.setMusic(!audio.musicOn);
      prefs.set('music', audio.musicOn);
      menu.refreshSettings();
    } },
    { label: 'Sounds', value: onOff(audio.soundsOn), action: () => {
      audio.soundsOn = !audio.soundsOn;
      prefs.set('sounds', audio.soundsOn);
      menu.refreshSettings();
    } },
  ];
  const hidden = prefs.get('hidden', []).filter((k) => all.has(k));
  if (hidden.length) items.push({ label: 'Hidden channels', value: String(hidden.length), action: showHidden });
  if (prefs.get('order', []).length) {
    items.push({ label: 'Reset channel order', action: () => {
      prefs.set('order', []);
      reloadChannels();
      menu.refreshSettings();
      menu.showNotice('Channels are back in their first order.');
    } });
  }
  items.push({ label: 'This version', value: versionText(), action: () => {
    menu.showNotice('New versions are installed from a computer with webOS Dev Manager.', 6000);
  } });
  items.push({ label: 'Close WiiOS', action: () => invoke('power', { action: 'quit' }) });
  return items;
}

function updateSettings() {
  return [
    { label: 'Check for updates', action: () => checkForUpdate(true) },
    { label: 'Automatic updates', value: onOff(prefs.get('autoUpdate', true)), action: () => {
      prefs.set('autoUpdate', !prefs.get('autoUpdate', true));
      menu.refreshSettings();
    } },
    { label: 'This version', value: `${versionText()} · ${info.os}`, action: () => {} },
  ];
}

function powerSettings() {
  const power = (action) => invoke('power', { action }).catch((e) => menu.showNotice(String(e)));
  return [
    { label: 'Sleep', action: () => power('sleep') },
    { label: 'Restart', action: () => power('restart') },
    { label: 'Shut down', action: () => power('shutdown') },
    { label: 'Close WiiOS', action: () => power('quit') },
  ];
}

function showHidden() {
  const items = prefs.get('hidden', []).map((k) => all.get(k)).filter(Boolean).map((ch) => ({
    label: ch.label,
    action: () => {
      prefs.set('hidden', prefs.get('hidden', []).filter((k) => k !== ch.key));
      reloadChannels();
      menu.refreshSettings();
    },
  }));
  menu.showMenu('Show a channel again', items);
}

function osSettings(page) {
  invoke('open_settings', { page }).catch((e) => menu.showNotice(String(e)));
}

/**
 * Full screen on or off (Settings, F11). In the app a window gets its title bar back so it can be moved and
 * resized; opened in a browser it uses the browser's full screen.
 */
async function setFullscreen(on) {
  fullscreen = on;
  prefs.set('fullscreen', on);
  const w = appWindow();
  try {
    if (w) {
      await w.setDecorations(!on);
      await w.setFullscreen(on);
    } else if (on) {
      await document.documentElement.requestFullscreen();
    } else if (document.fullscreenElement) {
      await document.exitFullscreen();
    }
  } catch (e) {
    menu.showNotice(`Couldn't change full screen: ${e}`);
  }
  menu.refreshSettings();
}
if (!native) {
  document.addEventListener('fullscreenchange', () => {
    fullscreen = !!document.fullscreenElement;
    menu.refreshSettings();
  });
}

// ---- Updates: the same GitHub releases as the TV version ---------------------------------------

async function checkForUpdate(manual) {
  if (kind === 'webos') return;   // installed from a computer; see tvSettings
  if (!native) {
    if (manual) menu.showNotice('Updates work in the installed app.');
    return;
  }
  if (manual) menu.showNotice('Checking for updates…', 0);
  let u;
  try {
    u = await invoke('check_update');
  } catch (e) {
    if (manual) menu.showNotice(`Couldn't check for updates: ${e}`);
    return;
  }
  prefs.set('lastCheck', Date.now());
  if (!u) {
    if (manual) menu.showNotice(`WiiOS is up to date (${versionText()}).`);
    return;
  }
  menu.showNotice('', 1);
  menu.showMenu(`Update to build ${u.build}?`, [
    { label: 'Update now', action: async () => {
      menu.closeSettings();
      menu.showNotice('Updating… WiiOS restarts by itself.', 0);
      try {
        await invoke('apply_update', { url: u.url, sha256: u.sha256 || '' });
      } catch (e) {
        menu.showNotice(`The update didn't finish: ${e}`, 8000);
      }
    } },
    { label: 'Later', action: () => {} },
  ]);
}

// ---- input ------------------------------------------------------------------------------------

/** OK on a channel opens on release and shows the options when held, as on the TV. */
function onChannel() {
  return !menu.menuOpen && !menu.settings.length && !menu.moving && menu.introAlpha <= 0
    && menu.focus >= 0 && menu.focus < menu.channels.length;
}

let okDown = false, okHeldSent = false;
function okPress() {
  if (!onChannel()) return menu.key('ok');
  okDown = true;
  okHeldSent = false;
  clearTimeout(okPress.timer);
  okPress.timer = setTimeout(() => {
    if (okDown && onChannel()) {
      okHeldSent = true;
      menu.key('okHeld');
    }
  }, 600);
}
function okRelease() {
  clearTimeout(okPress.timer);
  if (okDown && !okHeldSent) menu.key('ok');
  okDown = false;
}

const KEYS = {
  ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right',
  Escape: 'back', Backspace: 'back', BrowserBack: 'back', GoBack: 'back',
  ContextMenu: 'options', PageUp: 'pageUp', PageDown: 'pageDown', Home: 'menu',
};

window.addEventListener('keydown', (e) => {
  audio.unlock();
  if (e.key === 'F11') {
    e.preventDefault();
    setFullscreen(!fullscreen);
    return;
  }
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault();
    if (!e.repeat) okPress();
    return;
  }
  // LG remotes: Back is key code 461 (webos/appinfo.json keeps webOS from treating it as page history).
  const k = KEYS[e.key] || (e.keyCode === 461 ? 'back' : null);
  if (k) {
    e.preventDefault();
    menu.key(k);
  }
});
window.addEventListener('keyup', (e) => {
  if (e.key === 'Enter' || e.key === ' ') okRelease();
});

const dpr = () => window.devicePixelRatio || 1;
const local = (e) => {
  const r = canvas.getBoundingClientRect();
  return [(e.clientX - r.left) * dpr(), (e.clientY - r.top) * dpr()];
};
canvas.addEventListener('mousemove', (e) => menu.pointerMove(...local(e)));
canvas.addEventListener('mousedown', (e) => {
  audio.unlock();
  if (e.button === 0) menu.click(...local(e));
  else if (e.button === 2) menu.click(...local(e), true);
});
canvas.addEventListener('contextmenu', (e) => e.preventDefault());
canvas.addEventListener('wheel', (e) => {
  e.preventDefault();
  menu.wheel(e.deltaY || e.deltaX);
}, { passive: false });

// Game controllers (standard mapping): A = OK (hold for options), B = Back, X/Y = options, LB/RB turn the
// page, Start = WiiOS Settings, D-pad or left stick move.
const pads = {};
const STICK = 0.6;
function pollPads() {
  for (const gp of navigator.getGamepads ? navigator.getGamepads() : []) {
    if (!gp) continue;
    const st = (pads[gp.index] ||= { b: [], dir: null, next: 0 });
    const pressed = (i) => !!(gp.buttons[i] && gp.buttons[i].pressed);
    const edge = (i) => pressed(i) && !st.b[i];
    if (gp.buttons.some((b) => b.pressed)) audio.unlock();
    if (edge(0)) okPress();
    if (!pressed(0) && st.b[0]) okRelease();
    if (edge(1)) menu.key('back');
    if (edge(2) || edge(3)) menu.key('options');
    if (edge(4)) menu.key('pageUp');
    if (edge(5)) menu.key('pageDown');
    if (edge(9) || edge(8)) menu.key('menu');
    let dir = pressed(12) ? 'up' : pressed(13) ? 'down' : pressed(14) ? 'left' : pressed(15) ? 'right' : null;
    const ax = gp.axes[0] || 0, ay = gp.axes[1] || 0;
    if (!dir && Math.max(Math.abs(ax), Math.abs(ay)) > STICK) dir = Math.abs(ax) > Math.abs(ay) ? (ax > 0 ? 'right' : 'left') : (ay > 0 ? 'down' : 'up');
    const now = performance.now();
    if (dir && (dir !== st.dir || now >= st.next)) {
      menu.key(dir);
      st.next = now + (dir !== st.dir ? 380 : 130);
    }
    st.dir = dir;
    st.b = gp.buttons.map((b) => b.pressed);
  }
  requestAnimationFrame(pollPads);
}
requestAnimationFrame(pollPads);

// Music while WiiOS is in front; quiet while a game or the browser is.
window.addEventListener('blur', () => audio.setWanted(false));
window.addEventListener('focus', () => {
  audio.setWanted(true);
  menu.invalidate();
});
document.addEventListener('visibilitychange', () => audio.setWanted(!document.hidden));

// ---- start ------------------------------------------------------------------------------------

function layout() {
  // 16:9, as large as the window allows; the menu is drawn on a 1920 x 1080 reference.
  const vw = window.innerWidth, vh = window.innerHeight;
  const w = Math.min(vw, (vh * 16) / 9), h = (w * 9) / 16;
  canvas.style.width = `${w}px`;
  canvas.style.height = `${h}px`;
  menu.resize(Math.round(w * dpr()), Math.round(h * dpr()));
}
window.addEventListener('resize', layout);

(async () => {
  try {
    info = { ...info, ...(await invoke('info')) };
  } catch {
    // keep the defaults
  }
  // The app opens full screen; a browser can only go full screen after a key or click.
  if (native) {
    if (!prefs.get('fullscreen', true)) await setFullscreen(false);
  } else {
    fullscreen = !!document.fullscreenElement;
  }
  menu.setIntroText('Before you start',
    'Sit at a comfortable distance from the screen and rest your eyes now and then. Your games, apps and files are where they always were.',
    'Press OK to continue.');
  layout();
  await reloadChannels();
  const today = new Date().toDateString();
  if (prefs.get('introDay', '') !== today) {
    prefs.set('introDay', today);
    menu.playIntro();
  }
  audio.setWanted(document.hasFocus());
  // Games installed while WiiOS was in the background show up on return (at most once a minute).
  let lastReload = Date.now();
  window.addEventListener('focus', () => {
    if (Date.now() - lastReload < 60000) return;
    lastReload = Date.now();
    reloadChannels();
  });
  if (prefs.get('autoUpdate', true) && Date.now() - prefs.get('lastCheck', 0) > 24 * 3600 * 1000) {
    setTimeout(() => checkForUpdate(false), 5000);
  }
})();
