// WiiOS's background service on LG TVs, for what its menu page isn't allowed to do itself.
// `watch`: webOS has no "back to the app that opened me": when an app opened from WiiOS closes, the TV falls back
// to Live TV (or the last input). WiiOS asks this service to watch the app it opened, and when the TV falls back
// to Live TV or an input straight from that app, it opens WiiOS again. Home, or any other app, ends the watch, so
// Home still goes to the TV's home screen.
// `apps`: the TV's apps with their own names and icons, read from their folders (the page can't read them).
const fs = require('fs');
const Service = require('webos-service');

const SELF = 'com.buddycrafted.wiios';
const service = new Service(`${SELF}.service`);

/** Where the TV lands when an app closes on its own. */
const FALLBACK = /^com\.webos\.app\.(livetv|hdmi\d|externalinput\..*)$/;
/** How long the opened app has to come up before the watch gives up. */
const START_MS = 30000;

let current = null;

function stop() {
  const w = current;
  if (!w) return;
  current = null;
  if (w.sub) w.sub.cancel();
  if (w.keep) service.activityManager.complete(w.keep, () => {});
}

service.register('watch', (msg) => {
  stop();
  const target = msg.payload && msg.payload.target;
  if (!target || FALLBACK.test(target)) {
    msg.respond({ returnValue: true, watching: false });
    return;
  }
  const w = (current = { target, seen: false, started: Date.now() });
  // Keeps this service running while it watches; webOS stops idle services after a few seconds.
  service.activityManager.create('wiios-return', (activity) => {
    if (current === w) w.keep = activity;
    else service.activityManager.complete(activity, () => {});
  });
  w.sub = service.subscribe('luna://com.webos.applicationManager/getForegroundAppInfo', { subscribe: true });
  w.sub.on('response', (m) => {
    if (current !== w) return;
    const p = m.payload || {};
    if (p.returnValue === false) {
      console.error(`wiios: can't watch the foreground app: ${p.errorText}`);
      stop();
      return;
    }
    const id = p.appId;
    if (!id) return;   // between apps
    if (id === target) {
      w.seen = true;
      return;
    }
    if (!w.seen) {
      if (Date.now() - w.started > START_MS) stop();
      return;
    }
    stop();
    if (FALLBACK.test(id)) service.call('luna://com.webos.applicationManager/launch', { id: SELF }, () => {});
  });
  msg.respond({ returnValue: true, watching: true });
});

/** Where webOS keeps apps: installed from LG's store, in Developer Mode, then the TV's own. */
const STORE_APPS = '/media/cryptofs/apps/usr/palm/applications/';
const DEV_APPS = '/media/developer/apps/usr/palm/applications/';
const BUILT_IN_APPS = '/usr/palm/applications/';
/** Icons larger than this are left out, to keep the answer small. */
const MAX_ICON = 400 * 1024;
const TYPES = { png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', webp: 'image/webp', gif: 'image/gif' };

/** An app's biggest icon as a data: URL, or null. */
function icon(dir, info) {
  for (const f of [info.extraLargeIcon, info.mediumLargeIcon, info.largeIcon, info.icon]) {
    if (!f || /^[a-z]+:/.test(f)) continue;
    const type = TYPES[(f.split('.').pop() || '').toLowerCase()];
    if (!type) continue;
    try {
      const path = dir + f.replace(/^\/+/, '');
      if (fs.statSync(path).size > MAX_ICON) continue;
      return `data:${type};base64,${fs.readFileSync(path).toString('base64')}`;
    } catch (e) {
      // missing or unreadable: try the next size
    }
  }
  return null;
}

/** The apps in one folder, or only those in `only`. */
function scan(dir, only) {
  let ids = [];
  try {
    ids = only ? only.filter((id) => fs.existsSync(dir + id)) : fs.readdirSync(dir);
  } catch (e) {
    return [];
  }
  const apps = [];
  for (const id of ids) {
    let info;
    try {
      info = JSON.parse(fs.readFileSync(`${dir}${id}/appinfo.json`, 'utf8'));
    } catch (e) {
      continue;
    }
    if (!info || !info.id || info.id === SELF || info.visible === false || info.noWindow || !info.title) continue;
    apps.push({ id: info.id, title: info.title, color: info.bgColor || info.iconColor || null,
      icon: icon(`${dir}${id}/`, info) });
  }
  return apps;
}

service.register('apps', (msg) => {
  const builtIn = (msg.payload && msg.payload.builtIn) || [];
  const seen = new Set();
  const apps = [];
  for (const list of [scan(STORE_APPS), scan(DEV_APPS), scan(BUILT_IN_APPS, builtIn)]) {
    for (const a of list) {
      if (seen.has(a.id)) continue;
      seen.add(a.id);
      apps.push(a);
    }
  }
  msg.respond({ returnValue: true, apps });
});
