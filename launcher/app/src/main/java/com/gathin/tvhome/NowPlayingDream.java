package com.gathin.tvhome;

import android.service.dreams.DreamService;

/** The Now Playing screensaver (NowPlayingView); pick it in Settings > Screensaver & sleep. */
public class NowPlayingDream extends DreamService {
    private NowPlayingView view;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        view = new NowPlayingView(AppLanguage.wrap(this), NowPlayingActivity.darkAfterMs(this));
        setContentView(view);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        view.start();
    }

    @Override
    public void onDreamingStopped() {
        view.stop();
        super.onDreamingStopped();
    }
}
