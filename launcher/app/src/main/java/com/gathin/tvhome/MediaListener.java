package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so the Now Playing screensaver may read other apps' media sessions (MediaSessionManager
 * requires an enabled notification listener for that). It ignores every notification. Enabled over ADB by
 * build-and-install.sh: cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.MediaListener
 */
public class MediaListener extends NotificationListenerService {
}
