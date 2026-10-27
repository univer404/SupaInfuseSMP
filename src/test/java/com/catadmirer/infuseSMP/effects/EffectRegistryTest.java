package com.catadmirer.infuseSMP.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffectRegistryTest {
    @Test
    void containsAllPlayableEffectsButKeepsEarthAsAGlyphOnly() {
        assertEquals(13, EffectRegistry.playableEffects().size());
        assertTrue(EffectRegistry.isPlayable("emerald"));
        assertFalse(EffectRegistry.isPlayable("earth"));
        assertTrue(EffectRegistry.isKnownGlyph("earth"));
    }

    @Test
    void effectKeysAreCaseInsensitive() {
        assertTrue(EffectRegistry.isPlayable("THUNDER"));
        assertFalse(EffectRegistry.isPlayable("unknown"));
    }
}
