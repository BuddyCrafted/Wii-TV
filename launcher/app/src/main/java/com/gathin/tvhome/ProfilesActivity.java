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
 * "Who's watching?": pick a profile with the remote (or add one), and the look editor for a new or existing
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
    private int editId, look, row;
    private String name;
    private boolean confirmDelete;

    private static final int ROW_NAME = Avatar.FIELDS, ROW_DONE = Avatar.FIELDS + 1, ROW_DELETE = Avatar.FIELDS + 2;

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
        if (getIntent().hasExtra(EXTRA_EDIT)) {
            onlyEdit = true;
            startEdit(getIntent().getIntExtra(EXTRA_EDIT, EXTRA_NEW));
        } else {
            ids = Profiles.ids(this);
            focus = Math.max(0, ids.indexOf(Profiles.current(this)));
            showPicker();
        }
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
        int[] looks = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            names.add(Profiles.name(this, ids.get(i)));
            looks[i] = Profiles.look(this, ids.get(i));
        }
        boolean canAdd = Profiles.nextId(this) >= 0;
        focus = Math.min(focus, ids.size() - (canAdd ? 0 : 1));
        view.showPicker(getString(R.string.prof_who), getString(R.string.prof_pick_hint), getString(R.string.prof_add),
                names, looks, canAdd, focus);
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
            look = Avatar.defaultLook(editId);
            name = getString(R.string.prof_default_name, editId + 1);
            editId = -1 - editId;   // negative while it doesn't exist yet
        } else {
            editId = id;
            look = Profiles.look(this, id);
            name = Profiles.name(this, id);
        }
        showEditor();
    }

    private boolean isNew() {
        return editId < 0;
    }

    private int realId() {
        return isNew() ? -1 - editId : editId;
    }

    private void showEditor() {
        List<String> rows = new ArrayList<>();
        int[] labels = {R.string.prof_skin, R.string.prof_hair, R.string.prof_style, R.string.prof_shirt};
        for (int i = 0; i < Avatar.FIELDS; i++) {
            rows.add("◀  " + getString(labels[i]) + "  " + (Avatar.get(look, i) + 1) + "/" + Avatar.count(i) + "  ▶");
        }
        rows.add(getString(R.string.prof_name, name));
        rows.add(getString(R.string.prof_done));
        if (!isNew() && realId() != 0) rows.add(getString(confirmDelete ? R.string.prof_delete_confirm : R.string.prof_delete));
        view.showEditor(getString(isNew() ? R.string.prof_new_title : R.string.prof_edit_title),
                getString(R.string.prof_edit_hint), look, name, rows.toArray(new String[0]), row);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A;
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            sounds.play(WiiSounds.BACK);
            if (editing && !onlyEdit) showPicker();
            else finish();
            return true;
        }
        return editing ? editKey(keyCode, ok) : pickKey(keyCode, ok);
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
        if ((keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) && row < Avatar.FIELDS) {
            int d = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
            look = Avatar.with(look, row, Avatar.get(look, row) + d);
            sounds.play(WiiSounds.MOVE);
            showEditor();
            return true;
        }
        if (!ok) return keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (row < Avatar.FIELDS) {
            // OK on a look row steps it on, for remotes where left/right feel awkward.
            look = Avatar.with(look, row, Avatar.get(look, row) + 1);
            sounds.play(WiiSounds.MOVE);
            showEditor();
        } else if (row == ROW_NAME) {
            askName();
        } else if (row == ROW_DONE) {
            sounds.play(WiiSounds.SELECT);
            if (isNew()) {
                Profiles.add(this, realId(), name, look);
                Profiles.setCurrent(this, realId());
            } else {
                Profiles.save(this, realId(), name, look);
            }
            finish();
        } else if (row == ROW_DELETE) {
            if (!confirmDelete) {
                confirmDelete = true;
                showEditor();
            } else {
                sounds.play(WiiSounds.BACK);
                Profiles.remove(this, realId());
                if (onlyEdit) finish();
                else showPicker();
            }
        }
        return true;
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
                    if (!n.isEmpty()) name = n.length() > 16 ? n.substring(0, 16) : n;
                    showEditor();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        field.requestFocus();
    }
}
