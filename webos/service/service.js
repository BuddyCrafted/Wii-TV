// WiiOS's background service on LG TVs. webOS has no "back to the app that opened me": when an app opened from
// WiiOS closes, the TV falls back to Live TV (or the last input). WiiOS asks this service to watch the app it
// opened (`watch`), and when the TV falls back to Live TV or an input straight from that app, it opens WiiOS
// again. Home, or any other app, ends the watch, so Home still goes to the TV's home screen.
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
