package com.gathin.tvhome;

import android.content.SharedPreferences;

/**
 * A look for the channel menu (docs/themes/PLAN.md). Classic is the menu exactly as measured; every other
 * theme recolours the same layers: WiiBase maps its greys onto the theme's colour pairs (stripes, panel,
 * buttons, empty slots) and WiiMenuView takes the rest (accent, clock, date, tile frames, the cursor) from
 * here. Channel art is never recoloured: banners and icons stay the apps' own.
 */
final class Theme {

    static final String KEY_THEME = "theme";
    static final String KEY_ACCENT = "theme_accent";

    enum Deco { NONE, SNOW, WAVE, LEAVES }

    final String id;
    /** Colours below are 0xRRGGBB (opaque) unless they end in an alpha byte in front. */
    final boolean classic;
    /** Background stripes: dark and light ends, at the top of the screen and at the panel line. */
    final int bgTopDark, bgTopLight, bgBottomDark, bgBottomLight;
    final int panelDark, panelLight;
    final int buttonDark, buttonLight;
    final int slotDark, slotLight;
    /** The blue line, the button rings, focus glows, page arrows' rim, cursor shading. */
    final int accent;
    final int clock, date, wordmark, tileBorder;
    /** Channels without a banner: the tile behind their icon, and their name. */
    final int iconTileTop, iconTileBottom, iconTileText;
    final Deco deco;

    private Theme(String id, boolean classic, int bgTopDark, int bgTopLight, int bgBottomDark, int bgBottomLight,
                  int panelDark, int panelLight, int buttonDark, int buttonLight, int slotDark, int slotLight,
                  int accent, int clock, int date, int wordmark, int tileBorder,
                  int iconTileTop, int iconTileBottom, int iconTileText, Deco deco) {
        this.id = id;
        this.classic = classic;
        this.bgTopDark = bgTopDark;
        this.bgTopLight = bgTopLight;
        this.bgBottomDark = bgBottomDark;
        this.bgBottomLight = bgBottomLight;
        this.panelDark = panelDark;
        this.panelLight = panelLight;
        this.buttonDark = buttonDark;
        this.buttonLight = buttonLight;
        this.slotDark = slotDark;
        this.slotLight = slotLight;
        this.accent = accent;
        this.clock = clock;
        this.date = date;
        this.wordmark = wordmark;
        this.tileBorder = tileBorder;
        this.iconTileTop = iconTileTop;
        this.iconTileBottom = iconTileBottom;
        this.iconTileText = iconTileText;
        this.deco = deco;
    }

    /** Same theme, another accent (the colour picker). */
    Theme withAccent(int rgb) {
        return new Theme(id, classic, bgTopDark, bgTopLight, bgBottomDark, bgBottomLight, panelDark, panelLight,
                buttonDark, buttonLight, slotDark, slotLight, rgb, clock, date, wordmark, tileBorder,
                iconTileTop, iconTileBottom, iconTileText, deco);
    }

    /** The menu as measured; WiiBase draws it unchanged. Accent is the original line blue. */
    static final Theme CLASSIC = new Theme("classic", true, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0x3FC0EC, 0x9A9A9A, 0x7C7C7C, 0xA4A4A4, 0xB5B5B5, 0xFFFFFF, 0xE9E9E9, 0x7A7A7A, Deco.NONE);

    static final Theme DARK = new Theme("dark", false,
            0x15171B, 0x262930, 0x15171B, 0x262930,
            0x1C1F24, 0x30343B, 0x272A31, 0x434852, 0x24272D, 0x30333A,
            0x3FD0FF, 0xB9BDC5, 0xCDD0D7, 0xCDD0D7, 0x454A53, 0x30333A, 0x24272D, 0xD3D6DC, Deco.NONE);

    static final Theme WINTER = new Theme("winter", false,
            0xB4D6F6, 0xD8ECFF, 0xE4F0FB, 0xFFFFFF,
            0xD7E5F2, 0xFFFFFF, 0xD5E3F1, 0xFFFFFF, 0xD3E2F0, 0xEEF5FB,
            0x6FB6FF, 0x7D96B0, 0x5C7896, 0x7F9AB5, 0xB7CDE2, 0xFFFFFF, 0xEAF3FB, 0x5C7896, Deco.SNOW);

    static final Theme FALL = new Theme("fall", false,
            0xEDD3AE, 0xFAEBD4, 0xE9CBA0, 0xF6E2C2,
            0xB9773E, 0xDDA466, 0xE2BF92, 0xFFF3E0, 0xE5CCA8, 0xF3E3C9,
            0xE8742A, 0xFFF1DC, 0xFFF6E8, 0x9A6230, 0xC9A57A, 0xFFF8EC, 0xF2E2C8, 0x7A4A1E, Deco.LEAVES);

    static final Theme HALLOWEEN = new Theme("halloween", false,
            0x1A1222, 0x2B1F38, 0x1A1222, 0x2B1F38,
            0x24192F, 0x3B2B4D, 0x30233F, 0x4E3B66, 0x2A2037, 0x3A2C4B,
            0xFF8A1F, 0xFFA64D, 0xFFBE72, 0xFFBE72, 0x5A4672, 0x3A2B4C, 0x2B2038, 0xFFC27A, Deco.NONE);

    static final Theme BEACH = new Theme("beach", false,
            0xB5E1F8, 0xE2F5FF, 0xF2E1BC, 0xFFF5DF,
            0xE2C78E, 0xF6E3B8, 0xEEDDB6, 0xFFF8E6, 0xE7D9B8, 0xF7EFDA,
            0x16A3C4, 0x3E8597, 0x2F6E80, 0x3B7F93, 0xBCD7DC, 0xFFFFFF, 0xF3EEDF, 0x2F6E80, Deco.WAVE);

    static final Theme[] ALL = {CLASSIC, DARK, WINTER, FALL, HALLOWEEN, BEACH};

    /** Accent picker: 0 means the theme's own. Names in strings.xml (accent_*), same order. */
    static final int[] ACCENTS = {0, 0x34BEED, 0x22C55E, 0xF43F5E, 0xA855F7, 0xF59E0B, 0x111827};

    static Theme byId(String id) {
        for (Theme t : ALL) if (t.id.equals(id)) return t;
        return CLASSIC;
    }

    static Theme load(SharedPreferences prefs) {
        Theme t = byId(prefs.getString(KEY_THEME, CLASSIC.id));
        int accent = prefs.getInt(KEY_ACCENT, 0);
        return accent != 0 ? t.withAccent(accent) : t;
    }

    /** True when the menu should be drawn exactly as measured (Classic with its own accent). */
    boolean untouched() {
        return classic && accent == CLASSIC.accent;
    }

    // ---- colour helpers, shared with WiiBase and WiiMenuView --------------------------------------------

    static int mix(int a, int b, float t) {
        if (t <= 0f) return a & 0xFFFFFF;
        if (t >= 1f) return b & 0xFFFFFF;
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Opaque ARGB from 0xRRGGBB. */
    static int argb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }
}
