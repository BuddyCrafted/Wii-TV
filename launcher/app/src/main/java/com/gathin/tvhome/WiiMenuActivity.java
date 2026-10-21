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
    private static final String KEY_RECENT = "recent";
    private static final String KEY_SOUNDS = "sounds";
    private static final String KEY_MUSIC = "music";
    /** Debug: freeze the menu in the reference capture's state, for comparing screenshots against it. */
    static final String EXTRA_REF_POSE = "ref_pose";

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        menu = new WiiMenuView(this, this);
        menu.setIntroText(getString(R.string.wii_intro_title), getString(R.string.wii_intro_body),
                getString(R.string.wii_intro_continue));
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        sounds = new WiiSounds(this, prefs.getBoolean(KEY_SOUNDS, getResources().getBoolean(R.bool.wii_sounds)));
        music = new WiiMusic(prefs.getBoolean(KEY_MUSIC, getResources().getBoolean(R.bool.wii_music)));
        menu.setSounds(sounds);
        menu.setSettingsLabel(getString(R.string.wii_settings_label));
        setContentView(menu);
        menu.requestFocus();
        introPending = firstStartThisBoot();
        if (getIntent().getBooleanExtra(EXTRA_REF_POSE, false)) {
            introPending = false;
            menu.setRefPose(true);
        }
        liveTvPkg = getString(R.string.live_tv_package);

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
        if (appsDirty) reloadApps();
        menu.start();
        music.resume();
        if (introPending) {
            introPending = false;
            menu.playIntro();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        menu.closeMenu();
        if (intent.getBooleanExtra(EXTRA_REF_POSE, false)) {
            introPending = false;
            menu.setRefPose(true);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
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

        Set<String> userHidden = userHidden();
        List<WiiMenuView.Channel> channels = new ArrayList<>();
        for (WiiMenuView.Channel ch : allChannels.values()) {
            if (!userHidden.contains(ch.key)) channels.add(ch);
        }
        ChannelOrder.sort(channels, c -> c.key, c -> c.label,
                ChannelOrder.split(prefs.getString(KEY_ORDER, "")),
                Arrays.asList(getResources().getStringArray(R.array.dock_apps)));
        menu.setChannels(channels);
    }

    private void addChannels(PackageManager pm, String category, Set<String> hidden) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || hidden.contains(pkg) || allChannels.containsKey(pkg)) continue;
            CharSequence label = ri.loadLabel(pm);
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            Drawable icon = banner == null ? ri.loadIcon(pm) : null;
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

    /** The round button bottom left: a menu of the system's own settings screens and the menu's switches. */
    @Override
    public void onSettingsButton() {
        showWiiMenu(0);
    }

    private void showWiiMenu(int selected) {
        List<WiiMenuView.MenuItem> items = new ArrayList<>();
        items.add(new WiiMenuView.MenuItem(getString(R.string.wii_settings), () -> openSetting(Settings.ACTION_SETTINGS)));
        items.add(new WiiMenuView.MenuItem(getString(R.string.wii_network), () -> openSetting(Settings.ACTION_WIFI_SETTINGS)));
        items.add(new WiiMenuView.MenuItem(getString(R.string.wii_bluetooth), () -> openSetting(Settings.ACTION_BLUETOOTH_SETTINGS)));
        items.add(new WiiMenuView.MenuItem(getString(music.isEnabled() ? R.string.wii_music_on : R.string.wii_music_off), () -> {
            music.setEnabled(!music.isEnabled());
            prefs.edit().putBoolean(KEY_MUSIC, music.isEnabled()).apply();
            showWiiMenu(3);
        }));
        items.add(new WiiMenuView.MenuItem(getString(sounds.isEnabled() ? R.string.wii_sounds_on : R.string.wii_sounds_off), () -> {
            sounds.setEnabled(!sounds.isEnabled());
            prefs.edit().putBoolean(KEY_SOUNDS, sounds.isEnabled()).apply();
            showWiiMenu(4);
        }));
        int hiddenCount = 0;
        for (String key : userHidden()) if (allChannels.containsKey(key)) hiddenCount++;
        if (hiddenCount > 0) {
            items.add(new WiiMenuView.MenuItem(getString(R.string.wii_hidden, hiddenCount), this::showHiddenChannels));
        }
        if (!prefs.getString(KEY_ORDER, "").isEmpty()) {
            items.add(new WiiMenuView.MenuItem(getString(R.string.wii_reset_order), () -> {
                prefs.edit().remove(KEY_ORDER).apply();
                reloadApps();
                Toast.makeText(this, R.string.wii_order_reset, Toast.LENGTH_SHORT).show();
            }));
        }
        menu.showMenu(getString(R.string.wii_menu_title, getString(R.string.wii_settings_label)), items, selected);
    }

    /** A settings screen, falling back to the main Settings where a build lacks that page (Fire OS). */
    private void openSetting(String action) {
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
                    getString(R.string.wii_settings_label)), Toast.LENGTH_LONG).show();
        }));
        items.add(new WiiMenuView.MenuItem(getString(R.string.library_app_info), () ->
                open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + channel.key))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))));
        menu.showMenu(channel.label, items);
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

    private void openInput(String inputId) {
        Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
        open(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
