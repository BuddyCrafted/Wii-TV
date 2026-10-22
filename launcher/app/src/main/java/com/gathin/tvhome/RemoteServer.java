package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The phone remote: a small web server on the TV (port PORT on the home network) that serves a remote
 * page (res/raw/remote_page.html) to a phone and takes its button presses.
 *
 * Inside WiiOS's own screens a press is delivered as the matching key, so everything works as with the
 * TV remote, and typed text goes into the focused text box. In other apps, Home and Back go through one
 * of WiiOS's accessibility services; arrows, OK and play/pause too on Android 12-13 and later, which
 * added those actions. Channels can be opened straight from the phone.
 *
 * Every request except the page itself needs the pairing PIN, which the QR code on the TV carries, so
 * nobody else on the network can drive the TV. Settings > Phone remote can switch it off or make a new PIN.
 */
final class RemoteServer {
    private RemoteServer() {}

    static final int PORT = 8765;
    static final String KEY_ON = "remote_on", KEY_PIN = "remote_pin";

    private static ServerSocket socket;
    private static ExecutorService pool;
    private static Context app;
    private static WeakReference<Activity> resumed = new WeakReference<>(null);
    private static volatile AccessibilityService service;
    private static volatile List<String[]> channels = Collections.emptyList();
    private static volatile Map<String, Intent> intents = Collections.emptyMap();
    private static int wrong;
    private static long lockedUntil;
    /** A WiiOS text box waiting for typing (in a dialog, which the activity's focus doesn't cover). */
    private static WeakReference<EditText> textTarget = new WeakReference<>(null);

    /** Lets the phone type into this box while it is on screen. */
    static void offerText(EditText e) {
        textTarget = new WeakReference<>(e);
    }

    static boolean enabled(Context c) {
        return Profiles.main(c).getBoolean(KEY_ON, true);
    }

    static String pin(Context c) {
        SharedPreferences prefs = Profiles.main(c);
        String pin = prefs.getString(KEY_PIN, null);
        if (pin == null) pin = newPin(c);
        return pin;
    }

    /** A fresh PIN: phones paired with the old one have to scan again. */
    static String newPin(Context c) {
        String pin = String.format(Locale.ROOT, "%06d", new SecureRandom().nextInt(1000000));
        Profiles.main(c).edit().putString(KEY_PIN, pin).apply();
        return pin;
    }

    /** Starts the server if it is switched on and not running yet; safe to call often. */
    static synchronized void start(Context context) {
        app = context.getApplicationContext();
        if (!enabled(app) || socket != null) return;
        if (app instanceof Application && pool == null) ((Application) app).registerActivityLifecycleCallbacks(TRACKER);
        try {
            ServerSocket s = new ServerSocket(PORT);
            socket = s;
            pool = Executors.newFixedThreadPool(2);
            Thread t = new Thread(() -> accept(s), "wii-remote");
            t.setDaemon(true);
            t.start();
        } catch (IOException e) {
            socket = null;   // port taken: try again next time
        }
    }

    static synchronized void stop() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // closing anyway
            }
            socket = null;
        }
    }

    static boolean running() {
        return socket != null;
    }

    /** The address to type or scan, e.g. http://192.168.1.50:8765, or null without a network. */
    static String url() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        return "http://" + a.getHostAddress() + ":" + PORT;
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // no network
        }
        return null;
    }

    static void attach(AccessibilityService s) {
        service = s;
        start(s);
    }

    static void detach(AccessibilityService s) {
        if (service == s) service = null;
    }

    /** The menu's channels, for the phone's channel list. */
    static void setChannels(List<String[]> keyAndLabel, Map<String, Intent> launch) {
        channels = new ArrayList<>(keyAndLabel);
        intents = new LinkedHashMap<>(launch);
    }

    private static final Application.ActivityLifecycleCallbacks TRACKER = new Application.ActivityLifecycleCallbacks() {
        @Override public void onActivityResumed(Activity a) {
            resumed = new WeakReference<>(a);
        }

        @Override public void onActivityPaused(Activity a) {
            if (resumed.get() == a) resumed = new WeakReference<>(null);
        }

        @Override public void onActivityCreated(Activity a, Bundle b) {}
        @Override public void onActivityStarted(Activity a) {}
        @Override public void onActivityStopped(Activity a) {}
        @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
        @Override public void onActivityDestroyed(Activity a) {}
    };

    // ---- HTTP ------------------------------------------------------------------------------------

    private static void accept(ServerSocket s) {
        while (!s.isClosed()) {
            try {
                Socket c = s.accept();
                c.setSoTimeout(5000);
                pool.execute(() -> serve(c));
            } catch (IOException e) {
                if (s.isClosed()) return;
            }
        }
    }

    private static void serve(Socket c) {
        try (Socket sock = c; InputStream in = new BufferedInputStream(sock.getInputStream());
             OutputStream out = sock.getOutputStream()) {
            String request = line(in);
            if (request == null) return;
            String[] parts = request.split(" ");
            if (parts.length < 2) return;
            String method = parts[0], path = parts[1];
            int length = 0;
            String pinHeader = null;
            for (String h; (h = line(in)) != null && !h.isEmpty(); ) {
                int colon = h.indexOf(':');
                if (colon < 0) continue;
                String name = h.substring(0, colon).trim().toLowerCase(Locale.ROOT), value = h.substring(colon + 1).trim();
                if (name.equals("content-length")) length = Math.min(4096, Integer.parseInt(value));
                if (name.equals("x-pin")) pinHeader = value;
            }
            byte[] body = new byte[Math.max(0, length)];
            int got = 0;
            while (got < body.length) {
                int n = in.read(body, got, body.length - got);
                if (n < 0) break;
                got += n;
            }
            String text = new String(body, 0, got, StandardCharsets.UTF_8);
            if (method.equals("GET") && (path.equals("/") || path.startsWith("/?") || path.startsWith("/#"))) {
                respond(out, 200, "text/html; charset=utf-8", page());
                return;
            }
            // Wrong PINs: after five, nothing is accepted for a minute, so guessing would take months.
            long now = SystemClock.elapsedRealtime();
            if (now < lockedUntil || !pin(app).equals(pinHeader)) {
                if (now >= lockedUntil && ++wrong >= 5) {
                    wrong = 0;
                    lockedUntil = now + 60_000;
                }
                respond(out, 403, "text/plain", "pin");
                return;
            }
            wrong = 0;
            String result;
            switch (path) {
                case "/api/hello": result = "{\"ok\":true}"; break;
                case "/api/key": result = key(text.trim()); break;
                case "/api/text": result = type(text); break;
                case "/api/channels": result = channelsJson(); break;
                case "/api/open": result = open(text.trim()); break;
                default:
                    respond(out, 404, "text/plain", "not found");
                    return;
            }
            respond(out, 200, "application/json", result);
        } catch (IOException | RuntimeException ignored) {
            // the phone went away
        }
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        boolean any = false;
        for (int ch; (ch = in.read()) >= 0; ) {
            any = true;
            if (ch == '\n') break;
            if (ch != '\r') b.write(ch);
            if (b.size() > 8192) return null;
        }
        return any ? b.toString("UTF-8") : null;
    }

    private static void respond(OutputStream out, int code, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " Error") + "\r\nContent-Type: " + type
                + "\r\nContent-Length: " + b.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(b);
        out.flush();
    }

    private static String cachedPage;

    private static String page() throws IOException {
        if (cachedPage == null) {
            try (InputStream in = app.getResources().openRawResource(R.raw.remote_page)) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
                cachedPage = b.toString("UTF-8");
            }
        }
        return cachedPage;
    }

    // ---- actions ---------------------------------------------------------------------------------

    private static final Map<String, Integer> KEYS = new LinkedHashMap<>();
    static {
        KEYS.put("up", KeyEvent.KEYCODE_DPAD_UP);
        KEYS.put("down", KeyEvent.KEYCODE_DPAD_DOWN);
        KEYS.put("left", KeyEvent.KEYCODE_DPAD_LEFT);
        KEYS.put("right", KeyEvent.KEYCODE_DPAD_RIGHT);
        KEYS.put("ok", KeyEvent.KEYCODE_DPAD_CENTER);
        KEYS.put("back", KeyEvent.KEYCODE_BACK);
        KEYS.put("menu", KeyEvent.KEYCODE_MENU);
        KEYS.put("playpause", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    // AccessibilityService global actions added in Android 12 (headset hook) and 13 (D-pad).
    private static final int GLOBAL_HEADSETHOOK = 10, GLOBAL_DPAD_UP = 16, GLOBAL_DPAD_DOWN = 17,
            GLOBAL_DPAD_LEFT = 18, GLOBAL_DPAD_RIGHT = 19, GLOBAL_DPAD_CENTER = 20;

    private static String key(String name) {
        if (name.equals("home")) {
            Intent home = new Intent(app, WiiMenuActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try {
                app.startActivity(home);
            } catch (RuntimeException e) {
                AccessibilityService s = service;
                if (s == null || !s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) return "{\"ok\":false}";
            }
            return "{\"ok\":true}";
        }
        Integer code = KEYS.get(name);
        if (code == null) return "{\"ok\":false}";
        Activity a = resumed.get();
        if (a != null && !a.isFinishing()) {
            a.runOnUiThread(() -> {
                long now = SystemClock.uptimeMillis();
                a.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0));
                a.dispatchKeyEvent(new KeyEvent(now, now + 40, KeyEvent.ACTION_UP, code, 0));
            });
            return "{\"ok\":true}";
        }
        // Another app is in front: only what the accessibility service may do.
        AccessibilityService s = service;
        if (s == null) return "{\"ok\":false,\"app\":true}";
        int action = -1;
        if (name.equals("back")) action = AccessibilityService.GLOBAL_ACTION_BACK;
        else if (Build.VERSION.SDK_INT >= 31 && name.equals("playpause")) action = GLOBAL_HEADSETHOOK;
        else if (Build.VERSION.SDK_INT >= 33) {
            switch (name) {
                case "up": action = GLOBAL_DPAD_UP; break;
                case "down": action = GLOBAL_DPAD_DOWN; break;
                case "left": action = GLOBAL_DPAD_LEFT; break;
                case "right": action = GLOBAL_DPAD_RIGHT; break;
                case "ok": action = GLOBAL_DPAD_CENTER; break;
                default: break;
            }
        }
        if (action < 0) return "{\"ok\":false,\"app\":true}";
        return s.performGlobalAction(action) ? "{\"ok\":true}" : "{\"ok\":false,\"app\":true}";
    }

    private static String type(String text) {
        Activity a = resumed.get();
        EditText box = textTarget.get();
        if (box != null && box.isAttachedToWindow()) {
            box.post(() -> {
                box.setText(text);
                box.setSelection(box.getText().length());
            });
            return "{\"ok\":true}";
        }
        if (a == null) return "{\"ok\":false,\"app\":true}";
        View v = a.getCurrentFocus();
        if (!(v instanceof EditText)) return "{\"ok\":false}";
        EditText e = (EditText) v;
        e.post(() -> {
            e.setText(text);
            e.setSelection(e.getText().length());
        });
        return "{\"ok\":true}";
    }

    private static String open(String key) {
        Intent i = intents.get(key);
        if (i == null) return "{\"ok\":false}";
        try {
            app.startActivity(new Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return "{\"ok\":true}";
        } catch (RuntimeException e) {
            return "{\"ok\":false}";
        }
    }

    private static String channelsJson() {
        StringBuilder b = new StringBuilder("[");
        for (String[] ch : channels) {
            if (b.length() > 1) b.append(',');
            b.append("{\"key\":").append(json(ch[0])).append(",\"label\":").append(json(ch[1])).append('}');
        }
        return b.append(']').toString();
    }

    static String json(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            if (ch == '"' || ch == '\\') b.append('\\').append(ch);
            else if (ch < 0x20) b.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
            else b.append(ch);
        }
        return b.append('"').toString();
    }
}
