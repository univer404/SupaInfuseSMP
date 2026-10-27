package com.catadmirer.infuseSMP.effects;

import java.util.List;
import java.util.Locale;

/**
 * Central list of effect keys and resource-pack glyph ordering.
 *
 * Keeping these lists in one place prevents menus, item creation, and
 * PlaceholderAPI icons from drifting out of sync.
 */
public final class EffectRegistry {
    private static final List<String> PLAYABLE_EFFECTS = List.of(
            "emerald", "ender", "feather", "fire", "frost", "haste", "heart",
            "invisibility", "ocean", "regeneration", "speed", "strength", "thunder"
    );

    private static final List<String> ICON_EFFECTS = List.of(
            "earth", "emerald", "ender", "feather", "fire", "frost", "haste",
            "heart", "invisibility", "ocean", "regeneration", "speed", "strength", "thunder"
    );

    private EffectRegistry() {}

    public static List<String> playableEffects() {
        return PLAYABLE_EFFECTS;
    }

    public static List<String> iconEffects() {
        return ICON_EFFECTS;
    }

    public static boolean isPlayable(String key) {
        return key != null && PLAYABLE_EFFECTS.contains(key.toLowerCase(Locale.ROOT));
    }

    public static boolean isKnownGlyph(String key) {
        return key != null && ICON_EFFECTS.contains(key.toLowerCase(Locale.ROOT));
    }
}
