package com.catadmirer.infuseSMP.extraeffects;

import com.catadmirer.infuseSMP.Infuse;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Gives Emerald players custom random enchant offers without requiring bookshelves. */
public final class EmeraldEnchantingListener implements Listener {
    private static final int[] OFFER_COSTS = {2, 6, 30};
    private final Infuse plugin;
    private final Map<java.util.UUID, List<Map<Enchantment, Integer>>> pendingOffers = new java.util.HashMap<>();

    public EmeraldEnchantingListener(Infuse plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        Player player = event.getEnchanter();
        if (!plugin.hasEffect(player, "emerald")) {
            pendingOffers.remove(player.getUniqueId());
            return;
        }

        EnchantmentOffer[] offers = event.getOffers();
        if (offers == null || offers.length < 3) return;
        List<Map<Enchantment, Integer>> generated = new ArrayList<>(3);
        for (int index = 0; index < 3; index++) {
            int level = randomOfferLevel(index);
            Map<Enchantment, Integer> enchants = generateEnchantments(event.getItem(), level);
            if (enchants.isEmpty()) {
                generated.add(Map.of());
                continue;
            }
            Map.Entry<Enchantment, Integer> primary = enchants.entrySet().iterator().next();
            offers[index] = new EnchantmentOffer(primary.getKey(), primary.getValue(), OFFER_COSTS[index]);
            generated.add(enchants);
        }
        pendingOffers.put(player.getUniqueId(), generated);
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        if (!plugin.hasEffect(player, "emerald")) {
            pendingOffers.remove(player.getUniqueId());
            return;
        }
        List<Map<Enchantment, Integer>> offers = pendingOffers.remove(player.getUniqueId());
        if (offers == null || offers.isEmpty()) return;
        int selected = Math.max(0, Math.min(offers.size() - 1, event.getWhichButton()));
        Map<Enchantment, Integer> chosen = offers.get(selected);
        if (chosen.isEmpty()) return;
        event.getEnchantsToAdd().clear();
        event.getEnchantsToAdd().putAll(chosen);
        event.setExpLevelCost(selected + 1);
        player.playSound(player.getLocation(), "block.enchantment_table.use", 1.0f, 1.2f);
    }

    private int randomOfferLevel(int index) {
        return switch (index) {
            case 0 -> ThreadLocalRandom.current().nextInt(2, 11);
            case 1 -> ThreadLocalRandom.current().nextInt(6, 22);
            default -> 30;
        };
    }

    private Map<Enchantment, Integer> generateEnchantments(ItemStack item, int level) {
        List<Enchantment> candidates = new ArrayList<>();
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            if (!enchantment.canEnchantItem(item)) continue;
            boolean conflictsWithExisting = item.getEnchantments().keySet().stream()
                    .anyMatch(existing -> enchantment.conflictsWith(existing) || existing.conflictsWith(enchantment));
            if (!conflictsWithExisting) candidates.add(enchantment);
        }
        Collections.shuffle(candidates);
        Map<Enchantment, Integer> result = new LinkedHashMap<>();
        int maximumEnchantments = level >= 30 ? 3 : level >= 6 ? 2 : 1;
        for (Enchantment candidate : candidates) {
            if (!compatible(candidate, result)) continue;
            int maximum = Math.max(1, candidate.getMaxLevel());
            int chosenLevel = ThreadLocalRandom.current().nextInt(1, maximum + 1);
            result.put(candidate, chosenLevel);
            if (result.size() >= maximumEnchantments) break;
        }
        return result;
    }

    private boolean compatible(Enchantment candidate, Map<Enchantment, Integer> selected) {
        for (Enchantment existing : selected.keySet()) {
            if (candidate.conflictsWith(existing) || existing.conflictsWith(candidate)) return false;
        }
        return true;
    }
}
