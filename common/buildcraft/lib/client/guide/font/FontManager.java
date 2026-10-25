/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.guide.font;

import java.awt.Font;
import java.util.HashMap;
import java.util.Map;

public enum FontManager {
    INSTANCE;

    private static final boolean ENABLE_SMOOTH_FONT = Boolean.getBoolean("buildcraft.guide.smooth_font");

    private final Map<String, IFontRenderer> fonts = new HashMap<>();
    private final Map<String, GuideFont> loadedFonts = new HashMap<>();

    public IFontRenderer getOrLoadFont(String name, int size) {
        if (size <= 0) throw new IllegalArgumentException("Font size must be positive");
        IFontRenderer registered = fonts.get(name);
        if (registered != null) return registered;
        if (ENABLE_SMOOTH_FONT) {
            return loadedFonts.computeIfAbsent(name + "\u0000" + size, key -> new GuideFont(new Font(name, 0, size)));
        } else {
            return MinecraftFont.INSTANCE;
        }
    }

    public void clearTextures() {
        loadedFonts.values().forEach(GuideFont::close);
        for (IFontRenderer font : fonts.values()) if (font instanceof GuideFont smooth) smooth.close();
    }

    public void registerFont(String name, IFontRenderer font) {
        if (font == null) throw new NullPointerException("font");
        if (fonts.containsKey(name)) {
            throw new IllegalStateException("Cannot register the font \"" + name + "\" twice!");
        }
        fonts.put(name, font);
    }
}
