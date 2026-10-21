// Edit this file to customise the launcher, then rebuild the APK.
export const CONFIG = {
  // Package names that get the first channels, in this order.
  pinned: [
    'com.google.android.youtube.tv',
    'com.netflix.ninja',
    'com.disney.disneyplus',
    'com.amazon.amazonvideo.livingroom',
    'com.hulu.livingroomplus',
    'com.plexapp.android',
    'com.spotify.tv.android',
    'com.google.android.youtube.tvmusic',
  ],
  // Package names that never get a channel.
  hidden: [
    'com.android.tv.settings',
    'com.google.android.tvrecommendations',
    'com.google.android.tungsten.setupwraith',
  ],
  // Defaults; the user can toggle these in the Wii-button menu (saved on device).
  music: true,
  sounds: true,
  musicVolume: 0.35,
  soundVolume: 0.6,
  // White "press A" notice on cold start.
  bootScreen: true,
  bootScreenTimeoutMs: 12000,
};
