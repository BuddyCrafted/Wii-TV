// Thin wrapper over the Android JavaScript interface (window.WiiTV).
// In a desktop browser the native side is missing, so mock data is used.

const native = typeof window !== 'undefined' ? window.WiiTV : undefined;

export const isNative = !!native;

const MOCK_APPS = [
  ['com.google.android.youtube.tv', 'YouTube', '#e62117'],
  ['com.netflix.ninja', 'Netflix', '#141414'],
  ['com.disney.disneyplus', 'Disney+', '#0e2a6b'],
  ['com.plexapp.android', 'Plex', '#282a2d'],
  ['com.spotify.tv.android', 'Spotify', '#1db954'],
  ['com.hulu.livingroomplus', 'Hulu', '#1ce783'],
  ['com.amazon.amazonvideo.livingroom', 'Prime Video', '#00a8e1'],
  ['org.videolan.vlc', 'VLC', '#ff8800'],
  ['com.twitch.android.app', 'Twitch', '#9146ff'],
  ['tv.pluto.android', 'Pluto TV', '#2b2b2b'],
  ['com.android.vending', 'Play Store', '#01875f'],
  ['com.google.android.apps.photos', 'Photos', '#fbbc04'],
  ['com.crunchyroll.crunchyroid', 'Crunchyroll', '#f47521'],
  ['com.retroarch', 'RetroArch', '#4b4b4b'],
].map(([pkg, label, color]) => ({ pkg, label, color, hasBanner: false, mock: true }));

export async function getApps() {
  if (!native) return MOCK_APPS;
  try {
    return JSON.parse(native.getApps());
  } catch (e) {
    console.error('getApps failed', e);
    return [];
  }
}

export function launch(pkg) {
  if (native) return native.launchApp(pkg);
  console.info('[mock] launch', pkg);
  return true;
}

export function openSystemSettings() {
  if (native) return native.openSettings();
  console.info('[mock] open Android settings');
  return true;
}

// Art is served by the native AppArtHandler, relative to index.html (same origin).
export function bannerUrl(pkg) {
  return `app-art/banner/${encodeURIComponent(pkg)}`;
}

export function iconUrl(pkg) {
  return `app-art/icon/${encodeURIComponent(pkg)}`;
}
