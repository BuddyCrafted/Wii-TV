package com.gathin.tvhome;

import android.service.dreams.DreamService;

/** Shared body of the Wii and Aquarium screensavers; each subclass picks its view. */
abstract class SaverDream extends DreamService {
    private SaverView view;

    abstract SaverView create();

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        view = create();
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

    /** The Wii screensaver; pick it in Settings > Screensaver & sleep. */
    public static class Wii extends SaverDream {
        @Override
        SaverView create() {
            return new WiiSaverView(AppLanguage.wrap(this));
        }
    }

    /** The Aquarium screensaver; pick it in Settings > Screensaver & sleep. */
    public static class Aquarium extends SaverDream {
        @Override
        SaverView create() {
            return new AquariumView(AppLanguage.wrap(this));
        }
    }
}
