package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.provider.Settings;
import android.widget.Toast;

/**
 * Two jobs around an update:
 *  - the package installer's status for Updater's session: shows the system's Install confirmation when it
 *    asks for one, and a toast if the install failed;
 *  - MY_PACKAGE_REPLACED, after any update: on Fire TV, Android may switch accessibility services off when
 *    their app is replaced, which would hand Home back to Amazon. With WRITE_SECURE_SETTINGS (granted by
 *    build-and-install.sh, and kept across updates) the Home redirect is switched straight back on.
 */
public class UpdateReceiver extends BroadcastReceiver {
    static final String ACTION_STATUS = "com.gathin.tvhome.UPDATE_STATUS";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_STATUS.equals(intent.getAction())) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else if (status != PackageInstaller.STATUS_SUCCESS) {
                String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Toast.makeText(context, context.getString(R.string.upd_failed, msg == null ? "" : msg), Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            ensureFireTvHome(context);
            // Back to the (new) channel menu.
            context.startActivity(new Intent(context, WiiMenuActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    /** On Fire TV, makes sure FireTvHomeService is in the enabled accessibility services. */
    static void ensureFireTvHome(Context context) {
        if (!DeviceProfile.fireTv()) return;
        String svc = context.getPackageName() + "/" + FireTvHomeService.class.getName();
        try {
            String cur = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur != null && (cur.contains(svc) || cur.contains("FireTvHomeService"))) return;
            String next = cur == null || cur.isEmpty() ? svc : cur + ":" + svc;
            Settings.Secure.putString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next);
            Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (SecurityException ignored) {
            // No WRITE_SECURE_SETTINGS: re-run build-and-install.sh to switch the redirect back on.
        }
    }
}
