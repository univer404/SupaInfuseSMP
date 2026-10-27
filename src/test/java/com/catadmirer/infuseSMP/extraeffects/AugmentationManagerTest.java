package com.catadmirer.infuseSMP.extraeffects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AugmentationManagerTest {
    private final AugmentationManager manager = new AugmentationManager();

    @Test
    void normalSingleEffectDoesNotReduceCooldown() {
        assertEquals(1, manager.cooldownDivisor(new String[]{"fire", ""}, new boolean[]{false, false}, "fire"));
    }

    @Test
    void augmentedSingleEffectDoublesCooldownDivisor() {
        assertEquals(2, manager.cooldownDivisor(new String[]{"fire", ""}, new boolean[]{true, false}, "fire"));
    }

    @Test
    void duplicateNormalEffectsUseTwoPointDivisor() {
        assertEquals(2, manager.cooldownDivisor(new String[]{"fire", "fire"}, new boolean[]{false, false}, "fire"));
    }

    @Test
    void oneAugmentedAndOneNormalCopyUseThreePointDivisor() {
        assertEquals(3, manager.cooldownDivisor(new String[]{"fire", "fire"}, new boolean[]{true, false}, "fire"));
    }

    @Test
    void twoAugmentedCopiesUseFourPointDivisor() {
        assertEquals(4, manager.cooldownDivisor(new String[]{"fire", "fire"}, new boolean[]{true, true}, "fire"));
    }
}
