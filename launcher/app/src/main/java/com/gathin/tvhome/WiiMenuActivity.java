package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/** Home screen in the style of the old console menu; all of the drawing is in WiiMenuView. */
public class WiiMenuActivity extends Activity implements WiiMenuView.Listener {

    private static final String PREFS = "wii_menu";
    private static final String KEY_ORDER = "order";
    private static final String KEY_HIDDEN = "hidden";
    /** Prefix of the preference that holds a channel's custom picture (a file name in the tiles folder). */
    private static final String KEY_PICTURE_PREFIX = "picture.";
    private static final String KEY_RECENT = "recent";
    private static final String KEY_SOUNDS = "sounds";
    private static final String KEY_MUSIC = "music";
    /** Built-in channels have keys no package can have. */
    static final String FORECAST_KEY = "wiios.forecast";
    static final String SHOP_KEY = "wiios.shop";
    static final String GAMES_KEY = "wiios.games";
    /** Debug: freeze the menu in the reference capture's state, for comparing screenshots against it. */
    static final String EXTRA_REF_POSE = "ref_pose";
    /** Open wiiOS Settings straight away (Quick Settings' launcher row). */
    static final String EXTRA_OPEN_SETTINGS = "open_settings";

    private WiiMenuView menu;
    private WiiSounds sounds;
    private WiiMusic music;
    private SharedPreferences prefs;
    /** Every channel found on the last reload, hidden ones included, by package. */
    private final Map<String, WiiMenuView.Channel> allChannels = new LinkedHashMap<>();
    /** The welcome screen plays once per boot and whenever the screen comes back on, as at power-on. */
    private boolean introPending;
    private boolean appsDirty = true;
    private boolean resumed;
    private String liveTvPkg;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (resumed) {
                introPending = false;
                menu.playIntro();
                return;
            }
            introPending = true;
            // Power-on always lands here, even where the firmware would return to the last app. Android
            // lets the current home app start activities from the background.
            // A HOME intent, so the system brings back its home task instead of starting a second copy.
            try {
                startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .setPackage(getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException e) {
                // Not allowed on this build; the menu still shows the next time Home is pressed.
            }
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            appsDirty = true;
            if (resumed) reloadApps();
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        // English unless another language was picked in Settings > Language, whatever the TV's own is.
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        menu = new WiiMenuView(this, this);
        menu.setIntroText(getString(R.string.wii_intro_title), getString(R.string.wii_intro_body),
                getString(R.string.wii_intro_continue));
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        sounds = new WiiSounds(this, prefs.getBoolean(KEY_SOUNDS, getResources().getBoolean(R.bool.wii_sounds)));
        music = new WiiMusic(this, prefs.getBoolean(KEY_MUSIC, getResources().getBoolean(R.bool.wii_music)));
        menu.setSounds(sounds);
        menu.setSettingsLabel(getString(R.string.wii_settings_label));
        menu.setBackLabel(getString(R.string.set_back));
        menu.setLowMemory(DeviceProfile.lowMemory(this));
        setContentView(menu);
        menu.requestFocus();
        introPending = firstStartThisBoot();
        if (getIntent().getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) {
            introPending = false;
            menu.post(this::onSettingsButton);
        }
        if (getIntent().getBooleanExtra(EXTRA_REF_POSE, false)) {
            introPending = false;
            menu.setRefPose(true);
        }
        liveTvPkg = getString(R.string.live_tv_package);
        handleGet(getIntent());

        IntentFilter screen = new IntentFilter(Intent.ACTION_SCREEN_ON);
        IntentFilter packages = new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, screen, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(packageReceiver, packages, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, screen);
            registerReceiver(packageReceiver, packages);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        // Back on the menu, so any Settings visit is over: the Amazon home gets covered again.
        FireTvHomeService.coverAmazonHome();
        if (DeviceProfile.fireTv()) {
            // Once the menu has drawn, drop the picture the Home redirect put up, then refresh that picture.
            menu.postDelayed(() -> { if (resumed) FireTvHomeService.menuShown(); }, 120);
            menu.postDelayed(saveCover, 2500);
        }
        if (appsDirty) reloadApps();
        menu.start();
        music.resume();
        UpdateReceiver.ensureFireTvHome(this);
        if (getResources().getBoolean(R.bool.forecast_channel) && !Weather.fresh(this)) {
            Weather.refresh(this, (r, error) -> {
                if (r != null && !isFinishing()) reloadApps();   // new temperature on the tile
            });
        }
        maybeCheckForUpdate();
        if (introPending) {
            introPending = false;
            menu.playIntro();
        }
    }

    /**
     * Saves a half-size picture of the menu for FireTvHomeService's cover: what you see the moment you press
     * Home on Fire TV, while Fire OS holds the real menu back for its app-switch delay.
     */
    private final Runnable saveCover = () -> {
        if (!resumed || menu.getWidth() == 0 || menu.getHeight() == 0) return;
        Bitmap shot;
        try {
            shot = Bitmap.createBitmap(menu.getWidth() / 2, menu.getHeight() / 2, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(shot);
            c.scale(0.5f, 0.5f);
            menu.draw(c);
        } catch (RuntimeException | OutOfMemoryError e) {
            return;
        }
        File dir = getCacheDir();
        new Thread(() -> {
            File tmp = new File(dir, FireTvHomeService.COVER_FILE + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                shot.compress(Bitmap.CompressFormat.JPEG, 85, out);
            } catch (IOException e) {
                return;
            } finally {
                shot.recycle();
            }
            tmp.renameTo(new File(dir, FireTvHomeService.COVER_FILE));
        }, "menu-cover").start();
    };

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        FireTvHomeService.menuHidden();
        menu.removeCallbacks(saveCover);
        menu.stop();
        music.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(screenReceiver);
        unregisterReceiver(packageReceiver);
        sounds.release();
        music.release();
    }

    /**
     * `am start -n com.gathin.tvhome/.WiiMenuActivity --es get <package>` (the installer's SmartTube step): opens
     * that app's Shop page and downloads it if it isn't installed. The Shop itself isn't reachable from adb.
     */
    private void handleGet(Intent intent) {
        String pkg = intent.getStringExtra(ShopActivity.EXTRA_GET);
        if (pkg == null) return;
        intent.removeExtra(ShopActivity.EXTRA_GET);
        if (ShopCatalog.installed(this, pkg) != null) return;
        open(new Intent(this, ShopActivity.class).putExtra(ShopActivity.EXTRA_GET, pkg));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        menu.closeMenu();
        handleGet(intent);
        if (intent.getBooleanExtra(EXTRA_REF_POSE, false)) {
            introPending = false;
            menu.setRefPose(true);
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) menu.post(this::onSettingsButton);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (capturingButton) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
                capturingButton = false;
                return true;
            }
            if (!RemoteButtons.reserved(keyCode)) {
                capturingButton = false;
                pickButtonApp(keyCode);
                return true;
            }
        }
        if (menu.handleKey(keyCode, event)) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (menu.handleKeyUp(keyCode)) return true;
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // Home has nowhere to go back to; Back only closes what is open.
        menu.back();
    }

    /**
     * True on the first start since the TV booted. Android may kill the home process while a big app runs,
     * and coming back from it must not replay the welcome screen.
     */
    private boolean firstStartThisBoot() {
        int boot = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (boot < 0) return true;
        if (prefs.getInt("intro_boot", -1) == boot) return false;
        prefs.edit().putInt("intro_boot", boot).apply();
        return true;
    }

    private void reloadApps() {
        appsDirty = false;
        PackageManager pm = getPackageManager();
        Set<String> hidden = new HashSet<>(Arrays.asList(getResources().getStringArray(R.array.hidden_apps)));
        allChannels.clear();
        // TV entries first so an app's banner wins; then phone-style entries for sideloaded apps.
        addChannels(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, hidden);
        if (getResources().getBoolean(R.bool.wii_phone_apps)) addChannels(pm, Intent.CATEGORY_LAUNCHER, hidden);

        if (getResources().getBoolean(R.bool.forecast_channel)) {
            Typeface face;
            try {
                face = getResources().getFont(R.font.outfit_medium);
            } catch (RuntimeException e) {
                face = Typeface.DEFAULT_BOLD;
            }
            allChannels.put(FORECAST_KEY, new WiiMenuView.Channel(FORECAST_KEY, getString(R.string.wx_channel),
                    new WeatherBanner(Weather.cached(this), getString(R.string.wx_channel), face), null,
                    new Intent(this, ForecastActivity.class)));
        }

        allChannels.put(SHOP_KEY, new WiiMenuView.Channel(SHOP_KEY, getString(R.string.shop_channel),
                new ShopBanner(getString(R.string.shop_channel)), null, new Intent(this, ShopActivity.class)));
        allChannels.put(GAMES_KEY, new WiiMenuView.Channel(GAMES_KEY, getString(R.string.games_channel),
                new GamesBanner(getString(R.string.games_channel)), null,
                new Intent(this, ShopActivity.class).putExtra(ShopActivity.EXTRA_STORE, ShopCatalog.GAMES.id)));

        Set<String> userHidden = userHidden();
        List<WiiMenuView.Channel> channels = new ArrayList<>();
        for (WiiMenuView.Channel ch : allChannels.values()) {
            if (!userHidden.contains(ch.key)) channels.add(withCustomPicture(ch));
        }
        // The Forecast Channel comes first until it is moved, like the console's own channels.
        List<String> dock = new ArrayList<>();
        dock.add(FORECAST_KEY);
        dock.add(SHOP_KEY);
        dock.add(GAMES_KEY);
        dock.addAll(Arrays.asList(getResources().getStringArray(R.array.dock_apps)));
        ChannelOrder.sort(channels, c -> c.key, c -> c.label,
                ChannelOrder.split(prefs.getString(KEY_ORDER, "")), dock);
        menu.setChannels(channels);
    }

    /** SmartTube's stable and beta packages (new and old names), shown as a YouTube channel. */
    private static final Set<String> SMARTTUBE = new HashSet<>(Arrays.asList(
            "org.smarttube.stable", "org.smarttube.beta",
            "com.liskovsoft.smarttubetv", "com.liskovsoft.smarttubetv.beta"));

    private void addChannels(PackageManager pm, String category, Set<String> hidden) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || hidden.contains(pkg) || allChannels.containsKey(pkg)) continue;
            CharSequence label = ri.loadLabel(pm);
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            Drawable icon = banner == null ? ri.loadIcon(pm) : null;
            if (SMARTTUBE.contains(pkg)) {
                // SmartTube plays YouTube: name its channel that, so it reads like the app it replaces.
                label = "YouTube";
                banner = new NameTile("YouTube", 0xFFFF0000);
                icon = null;
            }
            Intent launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setClassName(pkg, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            allChannels.put(pkg, new WiiMenuView.Channel(pkg, label == null ? pkg : label.toString(), banner, icon, launch));
        }
    }

    private Set<String> userHidden() {
        return new HashSet<>(ChannelOrder.split(prefs.getString(KEY_HIDDEN, "")));
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
            overridePendingTransition(R.anim.app_open_enter, R.anim.app_open_exit);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onChannel(WiiMenuView.Channel channel) {
        List<String> recent = ChannelOrder.touchRecent(ChannelOrder.split(prefs.getString(KEY_RECENT, "")), channel.key);
        prefs.edit().putString(KEY_RECENT, ChannelOrder.join(recent)).apply();
        open(channel.intent);
    }

    /** The round button bottom left (and Start on a controller): the Settings screen. */
    @Override
    public void onSettingsButton() {
        menu.pushSettings(getString(R.string.set_title, getString(R.string.wii_settings_label)), this::mainSettings);
    }

    @Override
    public void onMenuKey() {
        if (!menu.settingsOpen()) onSettingsButton();
    }

    /**
     * The main Settings sheets: the device's own screens first (they keep Wi-Fi, Bluetooth, picture and sound
     * in the system's hands), then the menu's own switches.
     */
    private List<WiiMenuView.MenuItem> mainSettings() {
        List<WiiMenuView.MenuItem> items = new ArrayList<>();
        items.add(item(R.string.set_display, () -> openSetting(Settings.ACTION_DISPLAY_SETTINGS)));
        items.add(item(R.string.set_sound, () -> openSetting(Settings.ACTION_SOUND_SETTINGS)));
        items.add(item(R.string.wii_network, () -> openSetting(Settings.ACTION_WIFI_SETTINGS)));
        items.add(item(R.string.set_controllers, () -> openSetting(Settings.ACTION_BLUETOOTH_SETTINGS)));
        items.add(item(R.string.set_accounts, () -> menu.pushSettings(getString(R.string.set_accounts), this::accountSettings)));

        items.add(new WiiMenuView.MenuItem(getString(R.string.set_remote), onOff(RemoteButtons.enabled(this)), () ->
                menu.pushSettings(getString(R.string.set_remote), this::remoteSettings)));
        items.add(item(R.string.set_captions, () -> openSetting(Settings.ACTION_CAPTIONING_SETTINGS)));
        items.add(new WiiMenuView.MenuItem(getString(R.string.set_sleep), screensaverName(), () ->
                menu.pushSettings(getString(R.string.set_sleep), this::sleepSettings)));
        if (getResources().getBoolean(R.bool.forecast_channel)) {
            items.add(new WiiMenuView.MenuItem(getString(R.string.set_weather), weatherPlaceName(), () ->
                    menu.pushSettings(getString(R.string.set_weather), this::weatherSettings)));
        }
        items.add(new WiiMenuView.MenuItem(getString(R.string.set_language),
                AppLanguage.displayName(AppLanguage.current(this)), this::showLanguages));
        if (!inputItems().isEmpty()) {
            items.add(item(R.string.set_inputs, () -> menu.showMenu(getString(R.string.inputs), inputItems())));
        }
        items.add(item(R.string.set_date, () -> openSetting(Settings.ACTION_DATE_SETTINGS)));

        items.add(item(R.string.set_accessibility, () -> openSetting(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        items.add(item(R.string.set_apps, () -> openSetting(Settings.ACTION_APPLICATION_SETTINGS)));
        items.add(item(R.string.set_storage, () -> openSetting(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)));

        items.add(new WiiMenuView.MenuItem(getString(R.string.set_music), onOff(music.isEnabled()), () -> {
            music.setEnabled(!music.isEnabled());
            prefs.edit().putBoolean(KEY_MUSIC, music.isEnabled()).apply();
            menu.refreshSettings();
        }));
        items.add(new WiiMenuView.MenuItem(getString(R.string.set_sounds), onOff(sounds.isEnabled()), () -> {
            sounds.setEnabled(!sounds.isEnabled());
            prefs.edit().putBoolean(KEY_SOUNDS, sounds.isEnabled()).apply();
            menu.refreshSettings();
        }));
        int hiddenCount = 0;
        for (String key : userHidden()) if (allChannels.containsKey(key)) hiddenCount++;
        if (hiddenCount > 0) {
            items.add(new WiiMenuView.MenuItem(getString(R.string.set_hidden), String.valueOf(hiddenCount),
                    this::showHiddenChannels));
        }
        if (!prefs.getString(KEY_ORDER, "").isEmpty()) {
            items.add(item(R.string.wii_reset_order, () -> {
                prefs.edit().remove(KEY_ORDER).apply();
                reloadApps();
                menu.refreshSettings();
                Toast.makeText(this, R.string.wii_order_reset, Toast.LENGTH_SHORT).show();
            }));
        }
        items.add(item(R.string.set_all, () -> openSetting(Settings.ACTION_SETTINGS)));
        items.add(new WiiMenuView.MenuItem(getString(R.string.set_update), "build " + BuildConfig.WIIOS_BUILD, () ->
                menu.pushSettings(getString(R.string.set_update), this::updateSettings)));
        items.add(item(R.string.set_about, () -> openSetting(Settings.ACTION_DEVICE_INFO_SETTINGS)));
        return items;
    }

    // ---- remote buttons -----------------------------------------------------------------------------

    /** Waiting for a remote button to add (Settings > Remote Buttons > Add a button). */
    private boolean capturingButton;

    private List<WiiMenuView.MenuItem> remoteSettings() {
        List<WiiMenuView.MenuItem> items = new ArrayList<>();
        boolean on = RemoteButtons.enabled(this);
        items.add(new WiiMenuView.MenuItem(getString(R.string.set_remote_shortcuts), onOff(on), () -> {
            RemoteButtons.prefs(this).edit().putBoolean(RemoteButtons.KEY_ON, !on).apply();
            if (!on && !RemoteButtons.ensureService(this)) {
                Toast.makeText(this, R.string.set_remote_needs, Toast.LENGTH_LONG).show();
            }
            menu.refreshSettings();
        }));
        java.util.Map<Integer, String> all = new java.util.LinkedHashMap<>(
                RemoteKeys.parseAppKeys(getResources().getStringArray(R.array.app_keys)));
        all.putAll(RemoteButtons.overrides(this));
        for (java.util.Map.Entry<Integer, String> e : all.entrySet()) {
            int code = e.getKey();
            // "a|b|c" lists alternatives: name the first one installed.
            WiiMenuView.Channel ch = null;
            for (String p : e.getValue().split("\\|")) {
                ch = allChannels.get(p);
                if (ch != null) break;
            }
            String value = e.getValue().isEmpty() ? getString(R.string.set_remote_none) : ch != null ? ch.label : e.getValue();
            items.add(new WiiMenuView.MenuItem(RemoteButtons.name(code), value, () -> pickButtonApp(code)));
        }
        items.add(item(R.string.set_remote_add, () -> {
            capturingButton = true;
            Toast.makeText(this, R.string.set_remote_press, Toast.LENGTH_LONG).show();
        }));
        return items;
    }

    /** Which app a button opens: any channel, or nothing (the system's own behaviour). */
    private void pickButtonApp(int code) {
        List<WiiMenuView.MenuItem> items = new ArrayList<>();
        items.add(new WiiMenuView.MenuItem(getString(R.string.set_remote_none), () -> {
            RemoteButtons.set(this, code, "");
            menu.refreshSettings();
        }));
        for (WiiMenuView.Channel ch : allChannels.values()) {
            if (ch.key.startsWith("wiios.")) continue;
            items.add(new WiiMenuView.MenuItem(ch.label, () -> {
                RemoteButtons.set(this, code, ch.key);
                RemoteButtons.ensureService(this);
                menu.refreshSettings();
                Toast.makeText(this, getString(R.string.set_remote_saved, RemoteButtons.name(code), ch.label),
                        Toast.LENGTH_SHORT).show();
            }));
        }
        menu.showMenu(RemoteButtons.name(code), items);
    }

    // ---- weather ------------------------------------------------------------------------------------

    private String weatherPlaceName() {
        String saved = prefs.getString(Weather.KEY_PLACE, "");
        return saved.isEmpty() ? getString(R.string.set_wx_auto) : saved.split("\\|")[0];
    }

    private List<WiiMenuView.MenuItem> weatherSettings() {
        List<WiiMenuView.MenuItem> items = new ArrayList<>();
        items.add(new WiiMenuView.MenuItem(getString(R.string.set_wx_location), weatherPlaceName(), () -> {
            List<WiiMenuView.MenuItem> choices = new ArrayList<>();
            choices.add(new WiiMenuView.MenuItem(getString(R.string.set_wx_auto), () -> setPlace("")));
            choices.add(new WiiMenuView.MenuItem(getString(R.string.set_wx_search), this::searchPlace));
            menu.showMenu(getString(R.string.set_wx_location), choices);
        }));
        boolean f = Weather.fahrenheit(this);
        items.add(new WiiMenuView.MenuItem(getString(R.string.set_wx_units), f ? "\u00B0F" : "\u00B0C", () -> {
            prefs.edit().putString(Weather.KEY_UNITS, Weather.fahrenheit(this) ? "c" : "f").apply();
            refreshWeather();
        }));
        items.add(item(R.string.wx_title, () -> open(new Intent(this, ForecastActivity.class))));
        return items;
    }

    private void setPlace(String place) {
        prefs.edit().putString(Weather.KEY_PLACE, place).apply();
        refreshWeather();
    }

    private void refreshWeather() {
        prefs.edit().putLong(Weather.KEY_CACHE_TIME, 0).putLong(Weather.KEY_WORLD_TIME, 0).apply();
        menu.refreshSettings();
        Weather.refresh(this, (r, error) -> {
            if (isFinishing()) return;
            reloadApps();
            menu.refreshSettings();
            if (r == null) Toast.makeText(this, R.string.wx_offline, Toast.LENGTH_SHORT).show();
        });
    }

    /** Types a city name with the TV's on-screen keyboard, then picks one of the matches. */
    private void searchPlace() {
        android.widget.EditText field = new android.widget.EditText(this);
        field.setSingleLine(true);
        field.setHint(R.string.set_wx_search);
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.set_wx_search)
                .setView(field)
                .setPositiveButton(android.R.string.search_go, (d, which) -> {
                    String q = field.getText().toString().trim();
                    if (q.isEmpty()) return;
                    Weather.search(q, (found, error) -> {
                        if (isFinishing()) return;
                        if (found == null || found.isEmpty()) {
                            Toast.makeText(this, R.string.set_wx_none, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        List<WiiMenuView.MenuItem> items = new ArrayList<>();
                        for (String[] p : found) items.add(new WiiMenuView.MenuItem(p[0], () -> setPlace(p[1])));
                        menu.showMenu(getString(R.string.set_wx_location), items);
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        field.requestFocus();
    }

    // ---- over-the-air updates -----------------------------------------------------------------------

    private List<WiiMenuView.MenuItem> updateSettings() {
        List<WiiMenuView.MenuItem> items = new ArrayList<>();
        items.add(new WiiMenuView.MenuItem(getString(R.string.set_upd_version), "build " + BuildConfig.WIIOS_BUILD, () -> { }));
        items.add(item(R.string.set_upd_check, () -> checkForUpdate(true)));
        items.add(new WiiMenuView.MenuItem(getString(R.string.set_upd_auto), onOff(prefs.getBoolean(Updater.KEY_AUTO, true)), () -> {
            prefs.edit().putBoolean(Updater.KEY_AUTO, !prefs.getBoolean(Updater.KEY_AUTO, true)).apply();
            menu.refreshSettings();
        }));
        return items;
    }

    /** Once a day while automatic updates are on: look for a newer release and offer it. */
    private void maybeCheckForUpdate() {
        if (!prefs.getBoolean(Updater.KEY_AUTO, true)) return;
        long last = prefs.getLong(Updater.KEY_LAST_CHECK, 0);
        if (System.currentTimeMillis() - last < Updater.CHECK_EVERY_MS) return;
        checkForUpdate(false);
    }

    private void checkForUpdate(boolean asked) {
        prefs.edit().putLong(Updater.KEY_LAST_CHECK, System.currentTimeMillis()).apply();
        if (asked) Toast.makeText(this, R.string.upd_checking, Toast.LENGTH_SHORT).show();
        Updater.latest(this, (release, error) -> {
            if (isFinishing()) return;
            if (release == null) {
                if (asked) Toast.makeText(this, getString(R.string.upd_error, error), Toast.LENGTH_LONG).show();
                return;
            }
            if (!Updater.isNewer(release)) {
                if (asked) Toast.makeText(this, R.string.upd_latest, Toast.LENGTH_SHORT).show();
                return;
            }
            List<WiiMenuView.MenuItem> items = new ArrayList<>();
            items.add(new WiiMenuView.MenuItem(getString(R.string.upd_now, release.build), () -> {
                Toast.makeText(this, R.string.upd_downloading, Toast.LENGTH_LONG).show();
                Updater.install(this, release, (v, err) -> {
                    if (err != null) Toast.makeText(this, getString(R.string.upd_failed, err), Toast.LENGTH_LONG).show();
                });
            }));
            items.add(new WiiMenuView.MenuItem(getString(R.string.upd_later), () -> { }));
            menu.showMenu(getString(R.string.upd_title), items);
        });
    }

    private WiiMenuView.MenuItem item(int label, Runnable action) {
        return new WiiMenuView.MenuItem(getString(label), action);
    }

    private String onOff(boolean on) {
        return getString(on ? R.string.set_on : R.string.set_off);
    }

    /** Language: a pop-up list that scrolls, opened on the current language. */
    private void showLanguages() {
        List<WiiMenuView.MenuItem> items = new ArrayList<>();
        String current = AppLanguage.current(this);
        int selected = 0;
        List<String> tags = AppLanguage.tags();
        for (int i = 0; i < tags.size(); i++) {
            String tag = tags.get(i);
            boolean on = tag.equals(current);
            if (on) selected = i;
            items.add(new WiiMenuView.MenuItem(AppLanguage.displayName(tag) + (on ? "  \u2713" : ""), () -> {
                if (tag.equals(AppLanguage.current(this))) return;
                AppLanguage.set(this, tag);
                recreate();   // reloads every string in the new language; the menu opens fresh
            }));
        }
        menu.showMenu(getString(R.string.set_language), items, selected);
    }

    // ---- screensaver and sleep ---------------------------------------------------------------------

    private static final int MIN = 60_000;
    private static final int[] SS_DELAYS = {5 * MIN, 10 * MIN, 15 * MIN, 30 * MIN, 60 * MIN, SystemSettings.NEVER};
    private static final int[] SLEEP_DELAYS = {30 * MIN, 60 * MIN, 120 * MIN, 240 * MIN, 480 * MIN, SystemSettings.NEVER};
    private static final int[] DARK_MINUTES = {0, 5, 15, 30, 60};

    private String component(Class<?> cls) {
        return new android.content.ComponentName(this, cls).flattenToShortString();
    }

    private String screensaverName() {
        String ss = SystemSettings.screensaver(this);
        if (ss.contains("NowPlayingDream")) return getString(R.string.set_ss_nowplaying);
        if (ss.contains("SaverDream$Wii")) return getString(R.string.set_ss_wii);
        if (ss.contains("SaverDream$Aquarium")) return getString(R.string.set_ss_aquarium);
        if (ss.contains(getPackageName() + "/") && ss.contains("ClockDream")) return getString(R.string.set_ss_clock);
        return getString(R.string.set_ss_device);
    }

    private String duration(int ms) {
        if (ms == SystemSettings.NEVER) return getString(R.string.set_never);
        int min = ms / MIN;
        return min >= 60 && min % 60 == 0 ? getString(R.string.set_hours, min / 60) : getString(R.string.set_minutes, min);
    }

    private List<WiiMenuView.MenuItem> sleepSettings() {
        List<WiiMenuView.MenuItem> items = new ArrayList<>();
        items.add(new WiiMenuView.MenuItem(getString(R.string.set_screensaver), screensaverName(), () ->
                menu.pushSettings(getString(R.string.set_screensaver), () -> {
                    List<WiiMenuView.MenuItem> list = new ArrayList<>();
                    String now = screensaverName();
                    String[] names = {getString(R.string.set_ss_wii), getString(R.string.set_ss_aquarium),
                            getString(R.string.set_ss_nowplaying), getString(R.string.set_ss_clock),
                            getString(R.string.set_ss_device)};
                    String[] comps = {component(SaverDream.Wii.class), component(SaverDream.Aquarium.class),
                            component(NowPlayingDream.class), component(ClockDream.class), null};
                    for (int i = 0; i < names.length; i++) {
                        final String comp = comps[i];
                        list.add(new WiiMenuView.MenuItem(names[i], names[i].equals(now) ? getString(R.string.set_selected) : null, () -> {
                            if (comp == null) openSetting(Settings.ACTION_DREAM_SETTINGS);
                            else if (!SystemSettings.setScreensaver(this, comp)) needsGrant(Settings.ACTION_DREAM_SETTINGS);
                            else menu.popSettings();
                        }));
                    }
                    return list;
                })));
        items.add(new WiiMenuView.MenuItem(getString(R.string.set_ss_after), duration(SystemSettings.screensaverDelay(this)), () ->
                chooseDuration(R.string.set_ss_after, SS_DELAYS, SystemSettings.screensaverDelay(this),
                        ms -> SystemSettings.setScreensaverDelay(this, ms), Settings.ACTION_DREAM_SETTINGS)));
        items.add(new WiiMenuView.MenuItem(getString(R.string.set_sleep_after), duration(SystemSettings.sleepAfter(this)), () ->
                chooseDuration(R.string.set_sleep_after, SLEEP_DELAYS, SystemSettings.sleepAfter(this),
                        ms -> SystemSettings.setSleepAfter(this, ms), Settings.ACTION_DREAM_SETTINGS)));
        int dark = prefs.getInt(NowPlayingActivity.KEY_DARK, 0);
        items.add(new WiiMenuView.MenuItem(getString(R.string.set_dark_after),
                dark == 0 ? getString(R.string.set_never) : duration(dark * MIN), () ->
                menu.pushSettings(getString(R.string.set_dark_after), () -> {
                    List<WiiMenuView.MenuItem> list = new ArrayList<>();
                    int cur = prefs.getInt(NowPlayingActivity.KEY_DARK, 0);
                    for (int m : DARK_MINUTES) {
                        String label = m == 0 ? getString(R.string.set_never) : duration(m * MIN);
                        list.add(new WiiMenuView.MenuItem(label, m == cur ? getString(R.string.set_selected) : null, () -> {
                            prefs.edit().putInt(NowPlayingActivity.KEY_DARK, m).apply();
                            menu.popSettings();
                        }));
                    }
                    return list;
                })));
        items.add(item(R.string.set_preview, this::previewScreensaver));
        items.add(item(R.string.set_device_ss, () -> openSetting(Settings.ACTION_DREAM_SETTINGS)));
        return items;
    }

    /** Shows the chosen screensaver now: Wii or Aquarium in SaverActivity, else Now Playing. */
    private void previewScreensaver() {
        String ss = SystemSettings.screensaver(this);
        if (ss.contains("SaverDream$Wii") || ss.contains("SaverDream$Aquarium")) {
            open(new Intent(this, SaverActivity.class).putExtra(SaverActivity.EXTRA_KIND,
                    ss.contains("Aquarium") ? SaverActivity.AQUARIUM : SaverActivity.WII));
        } else {
            open(new Intent(this, NowPlayingActivity.class));
        }
    }

    private interface Writer {
        boolean write(int ms);
    }

    private void chooseDuration(int title, int[] options, int current, Writer writer, String fallback) {
        menu.pushSettings(getString(title), () -> {
            List<WiiMenuView.MenuItem> list = new ArrayList<>();
            for (int ms : options) {
                list.add(new WiiMenuView.MenuItem(duration(ms), ms == current ? getString(R.string.set_selected) : null, () -> {
                    if (writer.write(ms)) menu.popSettings();
                    else needsGrant(fallback);
                }));
            }
            return list;
        });
    }

    /** The TV refused a settings write (the ADB grants are missing): say so and open its own page. */
    private void needsGrant(String fallback) {
        Toast.makeText(this, R.string.set_needs_grant, Toast.LENGTH_LONG).show();
        openSetting(fallback);
    }

    /** A settings screen, falling back to the main Settings where a build lacks that page (Fire OS). */
    /**
     * Accounts & Sign-in. The sign-in form is always the system's own (Google's on Google TV, Amazon's on Fire TV):
     * a launcher must never see your password, and Google only lets its own screens add a Google account.
     */
    private List<WiiMenuView.MenuItem> accountSettings() {
        List<WiiMenuView.MenuItem> items = new ArrayList<>();
        if (DeviceProfile.fireTv()) {
            items.add(item(R.string.set_amazon_account, () -> openSetting(Settings.ACTION_SETTINGS)));
        } else {
            items.add(item(R.string.set_add_google, () -> {
                Intent add = new Intent(Settings.ACTION_ADD_ACCOUNT)
                        .putExtra(Settings.EXTRA_ACCOUNT_TYPES, new String[] {"com.google"})
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (add.resolveActivity(getPackageManager()) != null) open(add);
                else openSetting(Settings.ACTION_SYNC_SETTINGS);
            }));
            items.add(item(R.string.set_manage_accounts, () -> openSetting(Settings.ACTION_SYNC_SETTINGS)));
        }
        return items;
    }

    private void openSetting(String action) {
        if (DeviceProfile.fireTv()) FireTvHomeService.allowAmazonUntilHome();
        Intent intent = new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!Settings.ACTION_SETTINGS.equals(action) && intent.resolveActivity(getPackageManager()) == null) {
            intent = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        open(intent);
    }

    private void showHiddenChannels() {
        List<WiiMenuView.MenuItem> items = new ArrayList<>();
        for (String key : userHidden()) {
            WiiMenuView.Channel ch = allChannels.get(key);
            if (ch == null) continue;
            items.add(new WiiMenuView.MenuItem(ch.label, () -> {
                Set<String> hidden = userHidden();
                hidden.remove(key);
                prefs.edit().putString(KEY_HIDDEN, ChannelOrder.join(new ArrayList<>(hidden))).apply();
                reloadApps();
                menu.refreshSettings();
            }));
        }
        menu.showMenu(getString(R.string.wii_hidden_title), items);
    }

    @Override
    public void onChannelOptions(WiiMenuView.Channel channel) {
        List<WiiMenuView.MenuItem> items = new ArrayList<>();
        items.add(new WiiMenuView.MenuItem(getString(R.string.wii_move), () -> {
            menu.startMove();
            Toast.makeText(this, R.string.wii_move_hint, Toast.LENGTH_SHORT).show();
        }));
        items.add(new WiiMenuView.MenuItem(getString(R.string.wii_hide), () -> {
            Set<String> hidden = userHidden();
            hidden.add(channel.key);
            prefs.edit().putString(KEY_HIDDEN, ChannelOrder.join(new ArrayList<>(hidden))).apply();
            reloadApps();
            Toast.makeText(this, getString(R.string.wii_hidden_toast, channel.label,
                    getString(R.string.set_title, getString(R.string.wii_settings_label))), Toast.LENGTH_LONG).show();
        }));
        items.add(new WiiMenuView.MenuItem(getString(R.string.wii_change_picture), () -> pickPicture(channel)));
        if (!channel.key.startsWith("wiios.")) {
            items.add(new WiiMenuView.MenuItem(getString(R.string.library_app_info), () ->
                    open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + channel.key))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))));
        }
        menu.showMenu(channel.label, items);
    }

    /** Your own tile pictures: any .png, .jpg or .webp in this folder (put there over adb or a file manager). */
    private File tilesDir() {
        File dir = getExternalFilesDir("tiles");
        if (dir != null) dir.mkdirs();
        return dir;
    }

    private static String pictureKey(String channelKey) {
        return KEY_PICTURE_PREFIX + channelKey;
    }

    /** The channel with the picture you chose for it, if that file is still in the tiles folder. */
    private WiiMenuView.Channel withCustomPicture(WiiMenuView.Channel ch) {
        String name = prefs.getString(pictureKey(ch.key), null);
        File dir = name == null ? null : tilesDir();
        if (dir == null) return ch;
        Drawable art = loadPicture(new File(dir, name));
        return art == null ? ch : new WiiMenuView.Channel(ch.key, ch.label, art, null, ch.intent);
    }

    /** Decodes a picture at no more than about 640 px wide, 16 bits per pixel: tiles are small, RAM is not. */
    private Drawable loadPicture(File f) {
        if (!f.isFile()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= 640) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        return b == null ? null : new BitmapDrawable(getResources(), b);
    }

    /** Channel options → Change picture: every picture in the tiles folder, plus the original. */
    private void pickPicture(WiiMenuView.Channel channel) {
        File dir = tilesDir();
        File[] files = dir == null ? null : dir.listFiles((d, n) -> {
            String l = n.toLowerCase(Locale.ROOT);
            return l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".webp");
        });
        List<WiiMenuView.MenuItem> items = new ArrayList<>();
        items.add(new WiiMenuView.MenuItem(getString(R.string.wii_original_picture), () -> {
            prefs.edit().remove(pictureKey(channel.key)).apply();
            reloadApps();
        }));
        if (files != null) {
            Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File f : files) {
                String name = f.getName();
                String title = name.substring(0, name.lastIndexOf('.')).replace('_', ' ').replace('-', ' ');
                items.add(new WiiMenuView.MenuItem(title, () -> {
                    prefs.edit().putString(pictureKey(channel.key), name).apply();
                    reloadApps();
                }));
            }
        }
        if (items.size() == 1) {
            Toast.makeText(this, getString(R.string.wii_no_pictures,
                    dir == null ? "Android/data/" + getPackageName() + "/files/tiles" : dir.getPath()), Toast.LENGTH_LONG).show();
        }
        menu.showMenu(getString(R.string.wii_change_picture), items);
    }

    @Override
    public void onOrderChanged(List<WiiMenuView.Channel> order) {
        List<String> keys = new ArrayList<>();
        for (WiiMenuView.Channel ch : order) keys.add(ch.key);
        prefs.edit().putString(KEY_ORDER, ChannelOrder.join(keys)).apply();
    }

    /** SD: the TV's media player, or on a streaming stick the first media or file app installed. */
    @Override
    public void onSdButton() {
        PackageManager pm = getPackageManager();
        for (String pkg : getResources().getStringArray(R.array.sd_apps)) {
            Intent media = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (media == null) media = pm.getLaunchIntentForPackage(pkg);
            if (media != null) {
                open(media.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
        }
        Toast.makeText(this, R.string.wii_no_media, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onInputsButton() {
        List<WiiMenuView.MenuItem> items = inputItems();
        // On a TV the envelope lists the inputs; a streaming stick has none, so it is the Message Board:
        // the channels opened last, newest first. A TV gets those after its inputs.
        boolean board = items.isEmpty();
        for (String key : ChannelOrder.split(prefs.getString(KEY_RECENT, ""))) {
            WiiMenuView.Channel ch = allChannels.get(key);
            if (ch == null) continue;
            items.add(new WiiMenuView.MenuItem(getString(R.string.wii_recent, ch.label), () -> onChannel(ch)));
        }
        if (items.isEmpty()) {
            Toast.makeText(this, R.string.wii_board_empty, Toast.LENGTH_LONG).show();
            return;
        }
        menu.showMenu(getString(board ? R.string.wii_message_board : R.string.inputs), items);
    }

    /** The TV's HDMI and other passthrough inputs, then Live TV; empty on a streaming stick. */
    private List<WiiMenuView.MenuItem> inputItems() {
        List<WiiMenuView.MenuItem> items = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            Set<String> ids = new HashSet<>();
            for (TvInputInfo info : inputs) ids.add(info.getId());
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.isHidden(this)) continue;
                if (info.getParentId() != null && ids.contains(info.getParentId())) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
                CharSequence custom = info.loadCustomLabel(this);
                if (custom != null && custom.length() > 0 && !custom.toString().equals(label)) {
                    label = custom + " \u00B7 " + label;
                }
                final String id = info.getId();
                items.add(new WiiMenuView.MenuItem(label, () -> openInput(id)));
            }
        }
        Intent liveTv = liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (liveTv != null) {
            final Intent tv = liveTv.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            items.add(new WiiMenuView.MenuItem(getString(R.string.live_tv), () -> open(tv)));
        }
        return items;
    }

    private void openInput(String inputId) {
        Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
        open(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
