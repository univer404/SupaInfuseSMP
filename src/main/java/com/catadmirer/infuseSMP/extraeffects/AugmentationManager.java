package com.catadmirer.infuseSMP.extraeffects;

/**
 * Applies the original Infuse augmented-slot cooldown rule.
 * Each normal copy contributes one point and each augmented copy contributes two.
 */
public final class AugmentationManager {
    public int cooldownDivisor(String[] equippedEffects, boolean[] augmentedSlots, String effect) {
        int points = 0;
        for (int slot = 0; slot < equippedEffects.length && slot < 2; slot++) {
            if (!effect.equals(equippedEffects[slot])) continue;
            points++;
            if (augmentedSlots != null && slot < augmentedSlots.length && augmentedSlots[slot]) points++;
        }
        return switch (points) {
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            default -> 1;
        };
    }
}
