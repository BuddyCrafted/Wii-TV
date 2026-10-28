// Calls into the app around the menu: the desktop app (src-tauri/src/main.rs), or LG's webOS on an LG TV
// (webos/, through webOS's own PalmServiceBridge). Opened as a plain web page (for a quick look in a browser),
// it answers with sample data instead, so the menu can be developed without building either app.
const T = window.__TAURI__;
const W = typeof window.PalmServiceBridge === 'function';
export const native = !!T || W;
/** 'desktop', 'webos' or 'web' (a browser preview). */
export const kind = T ? 'desktop' : W ? 'webos' : 'web';

const SAMPLE = [
  { key: 'steam:620', label: 'Portal 2', kind: 'steam', target: 'steam://rungameid/620' },
  { key: 'steam:413150', label: 'Stardew Valley', kind: 'steam', target: 'steam://rungameid/413150' },
  { key: 'steam:1086940', label: "Baldur's Gate 3", kind: 'steam', target: 'steam://rungameid/1086940' },
  { key: 'epic:Fortnite', label: 'Fortnite', kind: 'epic', target: 'com.epicgames.launcher://apps/Fortnite?action=launch' },
];

export async function invoke(cmd, args = {}) {
  if (T) return T.core.invoke(cmd, args);
  if (W) return webos(cmd, args);
  switch (cmd) {
    case 'channels': return SAMPLE;
    case 'info': return { build: 0, os: 'web', version: 'preview', autostart: false };
    case 'check_update': return null;
    default: console.log('preview:', cmd, args); return null;
  }
}

export function appWindow() {
  return T ? T.window.getCurrentWindow() : null;
}

// ---- LG webOS ---------------------------------------------------------------------------------------

/** This app's own id (webos/appinfo.json), left out of the channels. */
const SELF = 'com.buddycrafted.wiios';

/**
 * LG's usual apps, for when the TV won't say what's installed (listing apps isn't one of webOS's public
 * services, opening them is). A missing one just says so when chosen, and can be hidden like any channel.
 */
const LG_APPS = [
  { id: 'netflix', label: 'Netflix', color: '#141414', text: '#e50914' },
  { id: 'youtube.leanback.v4', label: 'YouTube', color: '#ff0033' },
  { id: 'amazon', label: 'Prime Video', color: '#00a8e1' },
  { id: 'com.disney.disneyplus-prod', label: 'Disney+', color: '#0b1d4b' },
  { id: 'hulu', label: 'Hulu', color: '#1ce783', text: '#0b0c0f' },
  { id: 'com.apple.appletv', label: 'Apple TV', color: '#1c1c1e' },
  { id: 'spotify-beehive', label: 'Spotify', color: '#1db954' },
  { id: 'cdp-30', label: 'Plex', color: '#282a2d', text: '#e5a00d' },
  { id: 'com.webos.app.livetv', label: 'Live TV', color: '#a50034' },
  { id: 'com.webos.app.hdmi1', label: 'HDMI 1', color: '#4a4f57' },
  { id: 'com.webos.app.hdmi2', label: 'HDMI 2', color: '#4a4f57' },
  { id: 'com.webos.app.hdmi3', label: 'HDMI 3', color: '#4a4f57' },
  { id: 'com.webos.app.browser', label: 'Web Browser', color: '#2b6cb0' },
  { id: 'com.webos.app.discovery', label: 'LG Apps', color: '#a50034' },
];

/** Calls a webOS service; resolves with its answer, rejects with its error text. */
const pending = new Set();
function luna(uri, params = {}) {
  return new Promise((resolve, reject) => {
    const bridge = new window.PalmServiceBridge();
    pending.add(bridge);   // the bridge must outlive the call, or its answer is lost
    bridge.onservicecallback = (msg) => {
      pending.delete(bridge);
      let r = {};
      try {
        r = JSON.parse(msg);
      } catch {
        // an empty answer counts as done
      }
      if (r.returnValue === false) reject(new Error(r.errorText || 'not allowed'));
      else resolve(r);
    };
    bridge.call(uri, JSON.stringify(params));
  });
}

const launch = (id) => luna('luna://com.webos.applicationManager/launch', { id });

/** An app's icon, a file on the TV: shown when webOS lets this app read it, else the coloured tile stays. */
const fileUrl = (path) => (!path ? null : /^[a-z]+:/.test(path) ? path : `file://${path}`);

async function webos(cmd, args) {
  switch (cmd) {
    case 'channels': {
      try {
        const r = await luna('luna://com.webos.applicationManager/listLaunchPoints', {});
        const points = (r.launchPoints || []).filter((p) => p.id !== SELF && p.title);
        if (points.length) {
          return points.map((p) => ({
            key: `lg:${p.id}`, label: p.title, kind: 'lg', target: p.id,
            color: p.bgColor || p.iconColor || '#3a3f47',
            icons: [fileUrl(p.largeIcon), fileUrl(p.icon)].filter(Boolean),
          }));
        }
      } catch {
        // not allowed on this TV: the usual apps instead
      }
      return LG_APPS.map((a) => ({ key: `lg:${a.id}`, label: a.label, kind: 'lg', target: a.id, color: a.color, text: a.text }));
    }
    case 'launch':
      return launch(args.target).catch((e) => {
        throw new Error(/not ?found|no app|does not exist/i.test(e.message) ? 'it isn’t installed on this TV' : e.message);
      });
    case 'open_settings':
      return launch('com.palm.app.settings');
    case 'power':
      if (args.action === 'quit') return window.close();
      throw new Error('Use the remote’s power button.');
    case 'info':
      return { os: 'webOS', autostart: false, ...(window.WIIOS_INFO || { build: 0, version: '' }) };
    case 'check_update':
      return null;
    case 'renew_devmode':
      return renewDevMode();
    default:
      throw new Error('That isn\u2019t available on this TV.');
  }
}

/**
 * Keeps LG's Developer Mode from running out (which would remove WiiOS): the TV keeps its session token in a
 * file, and LG resets the session's clock when that token is sent to its developer site, as the Developer
 * Mode app's Extend button does. Returns true if it was sent; false where the TV won't let WiiOS read it.
 */
async function renewDevMode() {
  let token = '';
  try {
    token = (await (await fetch('file:///var/luna/preferences/devmode_enabled')).text()).trim();
  } catch {
    return false;
  }
  if (!/^[0-9A-Za-z]{16,}$/.test(token)) return false;
  await fetch(`https://developer.lge.com/secure/ResetDevModeSession.dev?sessionToken=${encodeURIComponent(token)}`,
    { mode: 'no-cors', cache: 'no-store' });
  return true;
}
