package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The channel menu's ordering and history, kept free of Android types so plain JVM tests cover it.
 * Saved values are package names joined with commas (package names never contain one).
 */
final class ChannelOrder {
    private ChannelOrder() {}

    /** Max entries kept for the Message Board's "recently opened" list. */
    static final int RECENT_MAX = 8;

    static List<String> split(String joined) {
        List<String> out = new ArrayList<>();
        if (joined == null || joined.isEmpty()) return out;
        for (String k : joined.split(",")) {
            String t = k.trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    static String join(List<String> keys) {
        StringBuilder b = new StringBuilder();
        for (String k : keys) {
            if (b.length() > 0) b.append(',');
            b.append(k);
        }
        return b.toString();
    }

    /**
     * Order for the channels: the user's own arrangement first (as saved by a move), then the
     * configured dock_apps, then everything else by label.
     */
    static <T> void sort(List<T> items, KeyOf<T> key, LabelOf<T> label, List<String> saved, List<String> dock) {
        Collections.sort(items, new Comparator<T>() {
            @Override
            public int compare(T a, T b) {
                int c = rank(saved, key.of(a), key.of(b));
                if (c != 0) return c;
                c = rank(dock, key.of(a), key.of(b));
                if (c != 0) return c;
                return label.of(a).compareToIgnoreCase(label.of(b));
            }
        });
    }

    /** Negative when a comes first by its position in list; 0 when the list does not decide. */
    private static int rank(List<String> list, String a, String b) {
        int ia = list.indexOf(a), ib = list.indexOf(b);
        if (ia < 0 && ib < 0) return 0;
        if (ia < 0) return 1;
        if (ib < 0) return -1;
        return Integer.compare(ia, ib);
    }

    /** Moves pkg to the front of the recently opened list, capped at RECENT_MAX. */
    static List<String> touchRecent(List<String> recent, String pkg) {
        List<String> out = new ArrayList<>(recent);
        out.remove(pkg);
        out.add(0, pkg);
        while (out.size() > RECENT_MAX) out.remove(out.size() - 1);
        return out;
    }

    interface KeyOf<T> {
        String of(T t);
    }

    interface LabelOf<T> {
        String of(T t);
    }
}
