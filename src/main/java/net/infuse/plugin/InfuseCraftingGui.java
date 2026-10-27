package net.infuse.plugin;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** The 3x3 ritual-style crafting GUI ported from recipes.sk and craft.sk. */
final class InfuseCraftingGui implements Listener {
    private static final int[] INPUT_SLOTS = {12, 13, 14, 21, 22, 23, 30, 31, 32};
    private static final List<String> EFFECT_ORDER = List.of("emerald", "feather", "fire", "frost", "haste", "heart", "invisibility", "ocean", "regeneration", "speed", "strength", "thunder", "ender");
    static final Map<String, List<String>> RECIPES = recipes();
    private final InfusePlugin plugin;
    private final Map<UUID, Inventory> openMenus = new HashMap<>();

    InfuseCraftingGui(InfusePlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onBrewingStandUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block.getType() != Material.BREWING_STAND || !player.isSneaking()) return;
        event.setCancelled(true);
        open(player);
    }

    private void open(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 45, plugin.getMiniMessage().deserialize("<gradient:#A2C81C:#21CD76>Potion Crafter"));
        ItemStack filler = plugin.named(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        for (int slot : INPUT_SLOTS) inventory.setItem(slot, null);
        inventory.setItem(40, plugin.named(Material.CRAFTING_TABLE, "<#81D681><bold>CRAFT"));
        inventory.setItem(44, plugin.named(Material.BARRIER, "<#E13030><bold>EXIT"));
        openMenus.put(player.getUniqueId(), inventory);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inventory = openMenus.get(player.getUniqueId());
        if (inventory == null || event.getView().getTopInventory() != inventory) return;
        int slot = event.getRawSlot();
        if (slot >= inventory.getSize()) return;
        if (slot == 44) { event.setCancelled(true); player.closeInventory(); return; }
        if (slot == 40) { event.setCancelled(true); craft(player, inventory); return; }
        if (!isInput(slot)) { event.setCancelled(true); return; }
        if (event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.DOUBLE_CLICK || event.getClick() == ClickType.CREATIVE) {
            event.setCancelled(true);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (openMenus.get(player.getUniqueId()) != inventory) return;
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getAmount() > 1) {
                int extra = item.getAmount() - 1;
                item.setAmount(1);
                ItemStack overflow = item.clone();
                overflow.setAmount(extra);
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(overflow);
                leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
        });
    }

    private void craft(Player player, Inventory inventory) {
        String effect = match(inventory);
        if (effect == null) {
            player.sendMessage(plugin.getMiniMessage().deserialize("<red>✖ <gray>Invalid recipe."));
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1f);
            return;
        }
        FileConfiguration config = plugin.getConfig();
        int limit = config.getInt("effects." + effect + ".craft-limit", -1);
        int crafted = config.getInt("crafting.crafted." + effect, 0);
        if (limit >= 0 && crafted >= limit) {
            player.sendMessage(plugin.getMiniMessage().deserialize("<red>✖ <gray>Craft limit reached for " + effect + "."));
            return;
        }
        int augmentLimit = config.getInt("effects." + effect + ".augment-limit", 0);
        boolean augmented = augmentLimit > 0 && crafted < augmentLimit;
        for (int slot : INPUT_SLOTS) inventory.setItem(slot, null);
        config.set("crafting.crafted." + effect, crafted + 1);
        plugin.saveConfig();
        ItemStack output = plugin.createEffectItem(effect, augmented);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(output);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1f);
        player.sendMessage(plugin.getMiniMessage().deserialize("<green>✔ <gray>Crafted " + (augmented ? "augmented " : "") + effect + " effect."));
        player.closeInventory();
    }

    private String match(Inventory inventory) {
        for (String effect : EFFECT_ORDER) {
            List<String> recipe = RECIPES.get(effect);
            boolean matches = true;
            for (int i = 0; i < INPUT_SLOTS.length; i++) {
                ItemStack item = inventory.getItem(INPUT_SLOTS[i]);
                Material expected = Material.matchMaterial(recipe.get(i));
                if (item == null || item.getType() == Material.AIR || expected == null || item.getType() != expected) {
                    matches = false;
                    break;
                }
            }
            if (matches) return effect;
        }
        return null;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inventory = openMenus.remove(player.getUniqueId());
        if (inventory == null || event.getInventory() != inventory) return;
        for (int slot : INPUT_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
            leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            inventory.setItem(slot, null);
        }
    }

    private boolean isInput(int slot) {
        for (int input : INPUT_SLOTS) if (slot == input) return true;
        return false;
    }

    private static Map<String, List<String>> recipes() {
        Map<String, List<String>> recipes = new LinkedHashMap<>();
        recipes.put("emerald", List.of("WILD_ARMOR_TRIM_SMITHING_TEMPLATE", "SNIFFER_EGG", "WILD_ARMOR_TRIM_SMITHING_TEMPLATE", "OMINOUS_BOTTLE", "EMERALD_BLOCK", "OMINOUS_BOTTLE", "ENDER_CHEST", "ENCHANTING_TABLE", "ENDER_CHEST"));
        recipes.put("feather", List.of("BREEZE_ROD", "PHANTOM_MEMBRANE", "BREEZE_ROD", "HEAVY_CORE", "FEATHER", "HEAVY_CORE", "BREEZE_ROD", "PHANTOM_MEMBRANE", "BREEZE_ROD"));
        recipes.put("fire", List.of("NETHERITE_UPGRADE_SMITHING_TEMPLATE", "RESPAWN_ANCHOR", "NETHERITE_UPGRADE_SMITHING_TEMPLATE", "NETHERITE_INGOT", "MUSIC_DISC_PIGSTEP", "NETHERITE_INGOT", "NETHERITE_UPGRADE_SMITHING_TEMPLATE", "RESPAWN_ANCHOR", "NETHERITE_UPGRADE_SMITHING_TEMPLATE"));
        recipes.put("frost", List.of("FLOW_ARMOR_TRIM_SMITHING_TEMPLATE", "VERDANT_FROGLIGHT", "FLOW_ARMOR_TRIM_SMITHING_TEMPLATE", "GOAT_HORN", "MUSIC_DISC_CREATOR", "GOAT_HORN", "FLOW_ARMOR_TRIM_SMITHING_TEMPLATE", "VERDANT_FROGLIGHT", "FLOW_ARMOR_TRIM_SMITHING_TEMPLATE"));
        recipes.put("haste", List.of("DIAMOND_BLOCK", "BEACON", "DIAMOND_BLOCK", "GOLD_BLOCK", "DEEPSLATE_EMERALD_ORE", "GOLD_BLOCK", "DIAMOND_BLOCK", "NETHERITE_PICKAXE", "DIAMOND_BLOCK"));
        recipes.put("heart", List.of("WATER_BOTTLE", "TOTEM_OF_UNDYING", "WATER_BOTTLE", "ENCHANTED_GOLDEN_APPLE", "BEETROOT", "ENCHANTED_GOLDEN_APPLE", "WATER_BOTTLE", "TOTEM_OF_UNDYING", "WATER_BOTTLE"));
        recipes.put("invisibility", List.of("ENDER_EYE", "MUSIC_DISC_5", "ENDER_EYE", "SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE", "OMINOUS_TRIAL_KEY", "SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE", "ENDER_EYE", "RECOVERY_COMPASS", "ENDER_EYE"));
        recipes.put("ocean", List.of("TIDE_ARMOR_TRIM_SMITHING_TEMPLATE", "HEART_OF_THE_SEA", "TIDE_ARMOR_TRIM_SMITHING_TEMPLATE", "TRIDENT", "CONDUIT", "TRIDENT", "TIDE_ARMOR_TRIM_SMITHING_TEMPLATE", "HEART_OF_THE_SEA", "TIDE_ARMOR_TRIM_SMITHING_TEMPLATE"));
        recipes.put("regeneration", List.of("END_CRYSTAL", "HONEY_BLOCK", "END_CRYSTAL", "VERDANT_FROGLIGHT", "AXOLOTL_BUCKET", "VERDANT_FROGLIGHT", "END_CRYSTAL", "MUSIC_DISC_CREATOR_MUSIC_BOX", "END_CRYSTAL"));
        recipes.put("speed", List.of("RABBIT_FOOT", "SADDLE", "RABBIT_FOOT", "DUNE_ARMOR_TRIM_SMITHING_TEMPLATE", "DIAMOND_HORSE_ARMOR", "EYE_ARMOR_TRIM_SMITHING_TEMPLATE", "RABBIT_FOOT", "NETHERITE_BOOTS", "RABBIT_FOOT"));
        recipes.put("strength", List.of("PLAYER_HEAD", "RIB_ARMOR_TRIM_SMITHING_TEMPLATE", "PLAYER_HEAD", "NETHERITE_SWORD", "WITHER_ROSE", "NETHERITE_AXE", "PLAYER_HEAD", "RIB_ARMOR_TRIM_SMITHING_TEMPLATE", "PLAYER_HEAD"));
        recipes.put("thunder", List.of("BOLT_ARMOR_TRIM_SMITHING_TEMPLATE", "LODESTONE", "BOLT_ARMOR_TRIM_SMITHING_TEMPLATE", "TRIDENT", "CREEPER_HEAD", "TRIDENT", "BOLT_ARMOR_TRIM_SMITHING_TEMPLATE", "LODESTONE", "BOLT_ARMOR_TRIM_SMITHING_TEMPLATE"));
        recipes.put("ender", List.of("BLACK_SHULKER_BOX", "DRAGON_HEAD", "BLACK_SHULKER_BOX", "SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE", "DRAGON_EGG", "SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE", "BLACK_SHULKER_BOX", "ELYTRA", "BLACK_SHULKER_BOX"));
        return Collections.unmodifiableMap(recipes);
    }
}
