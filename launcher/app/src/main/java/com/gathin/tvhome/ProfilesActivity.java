package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;

/**
 * "Who's watching?": pick a profile with the remote (or add one), and the profile editor (its Mii and name) for a new or existing
 * profile. Choosing sets Profiles' current one and closes; the menu notices when it comes back to the front
 * and switches to that profile's theme and channels. EXTRA_EDIT opens the editor straight away for a profile
 * id (EXTRA_NEW for a new one).
 */
public class ProfilesActivity extends Activity {
    static final String EXTRA_EDIT = "edit";
    static final int EXTRA_NEW = -1;

    private ProfilesView view;
    private WiiSounds sounds;
    private List<Integer> ids;
    private int focus;

    // Editor state
    private boolean editing, onlyEdit;
    private int editId, row;
    private String name;
    /** Typed by hand: then choosing another Mii leaves the name alone. */
    private boolean nameTyped;
    private boolean confirmDelete;
    private List<Mii> miis = new ArrayList<>();
    private int miiIndex;

    private static final int ROW_MII = 0, ROW_MAKE = 1, ROW_NAME = 2, ROW_DONE = 3, ROW_DELETE = 4;
    private static final int REQ_MAKE = 7;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theme theme = Theme.load(Profiles.currentPrefs(this));
        view = new ProfilesView(this, Theme.argb(theme.accent));
        setContentView(view);
        sounds = new WiiSounds(this, Profiles.main(this).getBoolean("sounds", getResources().getBoolean(R.bool.wii_sounds)));
        MiiPack.get(this);
        loadMiis();
        if (getIntent().hasExtra(EXTRA_EDIT)) {
            onlyEdit = true;
            startEdit(getIntent().getIntExtra(EXTRA_EDIT, EXTRA_NEW));
        } else {
            ids = Profiles.ids(this);
            focus = Math.max(0, ids.indexOf(Profiles.current(this)));
            showPicker();
        }
    }

    private void loadMiis() {
        miis = new MiiStore(this).loadOrSeed(new java.util.Random(), MiiChannelView.SEED_COUNT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        view.stop();
    }

    @Override
    protected void onDestroy() {
        sounds.release();
        super.onDestroy();
    }

    private void showPicker() {
        editing = false;
        ids = Profiles.ids(this);
        List<String> names = new ArrayList<>();
        List<Mii> faces = new ArrayList<>();
        for (int id : ids) {
            names.add(Profiles.name(this, id));
            faces.add(Profiles.mii(this, id, miis));
        }
        boolean canAdd = Profiles.nextId(this) >= 0;
        focus = Math.min(focus, ids.size() - (canAdd ? 0 : 1));
        view.showPicker(getString(R.string.prof_who), getString(R.string.prof_pick_hint), getString(R.string.prof_add),
                names, faces, canAdd, focus);
    }

    private void startEdit(int id) {
        editing = true;
        confirmDelete = false;
        row = 0;
        if (id == EXTRA_NEW) {
            editId = Profiles.nextId(this);
            if (editId < 0) {
                finish();
                return;
            }
            Mii m = Profiles.mii(this, editId, miis);
            miiIndex = Math.max(0, miis.indexOf(m));
            name = m.label();
            nameTyped = false;
            editId = -1 - editId;   // negative while it doesn't exist yet
        } else {
            editId = id;
            Mii m = Profiles.mii(this, id, miis);
            miiIndex = Math.max(0, miis.indexOf(m));
            name = Profiles.name(this, id);
            nameTyped = !name.equals(m.label());
        }
        showEditor();
    }

    private boolean isNew() {
        return editId < 0;
    }

    private int realId() {
        return isNew() ? -1 - editId : editId;
    }

    private Mii chosen() {
        return miis.isEmpty() ? Profiles.mii(this, realId(), miis) : miis.get(Math.floorMod(miiIndex, miis.size()));
    }

    private void showEditor() {
        Mii m = chosen();
        List<String> rows = new ArrayList<>();
        rows.add("\u25C0  " + getString(R.string.prof_mii, m.label()) + "  " + (miis.isEmpty() ? "" : (miiIndex + 1) + "/" + miis.size())
                + "  \u25B6");
        rows.add(getString(R.string.prof_make_mii));
        rows.add(getString(R.string.prof_name, name));
        rows.add(getString(R.string.prof_done));
        if (!isNew() && realId() != 0) rows.add(getString(confirmDelete ? R.string.prof_delete_confirm : R.string.prof_delete));
        view.showEditor(getString(isNew() ? R.string.prof_new_title : R.string.prof_edit_title),
                getString(R.string.prof_edit_hint), m, name, rows.toArray(new String[0]), row);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A;
        if (heldOk && isOk(keyCode)) return true;   // the rest of a held OK that opened the editor
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            sounds.play(WiiSounds.BACK);
            if (editing && !onlyEdit) showPicker();
            else finish();
            return true;
        }
        if (!editing && ok) {
            // OK acts when it's let go, so holding it can open the editor instead.
            if (event.getRepeatCount() == 0) {
                event.startTracking();
                heldOk = false;
            }
            return true;
        }
        if (!editing && keyCode == KeyEvent.KEYCODE_MENU) {
            editFocused();
            return true;
        }
        return editing ? editKey(keyCode, ok) : pickKey(keyCode, ok);
    }

    private boolean heldOk;

    private static boolean isOk(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A;
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (!editing && isOk(keyCode)) {
            heldOk = true;
            editFocused();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (heldOk && isOk(keyCode)) {
            heldOk = false;
            return true;
        }
        if (!editing && isOk(keyCode)) {
            if (!heldOk && event.isTracking() && !event.isCanceled()) pickKey(keyCode, true);
            heldOk = false;
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /** Hold OK (or Menu) on a profile in Who's watching: its editor, where it can be changed or deleted. */
    private void editFocused() {
        if (focus >= ids.size()) return;   // the Add card
        sounds.play(WiiSounds.SELECT);
        startEdit(ids.get(focus));
    }

    private boolean pickKey(int keyCode, boolean ok) {
        int n = view.cards();
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int next = Math.max(0, Math.min(n - 1, focus + (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)));
            if (next != focus) {
                focus = next;
                sounds.play(WiiSounds.MOVE);
                showPicker();
            }
            return true;
        }
        if (ok) {
            sounds.play(WiiSounds.SELECT);
            if (focus >= ids.size()) {
                startEdit(EXTRA_NEW);
            } else {
                Profiles.setCurrent(this, ids.get(focus));
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
            return true;
        }
        return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    /** Steps to another Mii; the name follows it unless one was typed. */
    private void stepMii(int d) {
        if (miis.isEmpty()) return;
        miiIndex = Math.floorMod(miiIndex + d, miis.size());
        if (!nameTyped) name = chosen().label();
        sounds.play(WiiSounds.MOVE);
        showEditor();
    }

    private boolean editKey(int keyCode, boolean ok) {
        int rows = ROW_DONE + 1 + (!isNew() && realId() != 0 ? 1 : 0);
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            int next = Math.max(0, Math.min(rows - 1, row + (keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1)));
            if (next != row) {
                row = next;
                confirmDelete = false;
                sounds.play(WiiSounds.MOVE);
                showEditor();
            }
            return true;
        }
        if ((keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) && row == ROW_MII) {
            stepMii(keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            return true;
        }
        if (!ok) return keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (row == ROW_MII) {
            stepMii(1);   // OK steps on too, for remotes where left/right feel awkward
        } else if (row == ROW_MAKE) {
            sounds.play(WiiSounds.SELECT);
            startActivityForResult(new android.content.Intent(this, MiiChannelActivity.class)
                    .putExtra(MiiChannelActivity.EXTRA_MAKE, true), REQ_MAKE);
        } else if (row == ROW_NAME) {
            askName();
        } else if (row == ROW_DONE) {
            sounds.play(WiiSounds.SELECT);
            String miiId = chosen().id;
            if (isNew()) {
                Profiles.add(this, realId(), name, miiId);
                Profiles.setCurrent(this, realId());
            } else {
                Profiles.save(this, realId(), name, miiId);
            }
            if (!onlyEdit && !isNew()) showPicker();   // edited from Who's watching: still choosing
            else finish();
        } else if (row == ROW_DELETE) {
            if (!confirmDelete) {
                confirmDelete = true;
                showEditor();
            } else {
                sounds.play(WiiSounds.BACK);
                Profiles.remove(this, realId());
                if (onlyEdit || Profiles.ids(this).size() < 2) {
                    finish();   // nobody left to choose between: straight to the menu
                } else {
                    focus = 0;
                    showPicker();
                }
            }
        }
        return true;
    }

    @Override
    protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQ_MAKE) return;
        loadMiis();
        String id = data == null ? null : data.getStringExtra(MiiChannelActivity.EXTRA_MII_ID);
        if (id != null) {
            for (int i = 0; i < miis.size(); i++) {
                if (miis.get(i).id.equals(id)) {
                    miiIndex = i;
                    if (!nameTyped) name = miis.get(i).label();
                    row = ROW_DONE;
                }
            }
        }
        if (editing) showEditor();
    }

    private void askName() {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setText(name);
        field.setSelectAllOnFocus(true);
        RemoteServer.offerText(field);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.prof_name_title)
                .setView(field)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String n = field.getText().toString().trim();
                    if (!n.isEmpty()) {
                        name = n.length() > 16 ? n.substring(0, 16) : n;
                        nameTyped = true;
                    }
                    showEditor();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        field.requestFocus();
    }
}
