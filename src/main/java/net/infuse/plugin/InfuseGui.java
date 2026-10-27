package net.infuse.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

/** Builds the configurable Infuse menus. Menu layout lives in gui.yml. */
final class InfuseGui {
    private final InfusePlugin plugin;
    private YamlConfiguration config;

    InfuseGui(InfusePlugin plugin) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "gui.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    void reload() { this.config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "gui.yml")); }

    Inventory buildMain(Player player) {
        int size = inventorySize("main.rows", 3);
        Inventory inventory = Bukkit.createInventory(player, size, plugin.getMiniMessage().deserialize(config.getString("main.title", "<light_purple>Infuse")));
        ItemStack filler = plugin.named(material("main.filler", Material.BLACK_STAINED_GLASS_PANE), config.getString("main.filler-name", " "));
        for (int slot = 0; slot < size; slot++) inventory.setItem(slot, filler);

        String[] equipped = plugin.slots(player.getUniqueId());
        set(inventory, config.getInt("main.primary-slot", 11), plugin.named(
                material("main.primary.material", Material.NETHER_STAR),
                config.getString("main.primary.name", "<#C4B5FD>Primary Effect") + " <white>" + plugin.display(equipped[0])));
        set(inventory, config.getInt("main.secondary-slot", 15), plugin.named(
                material("main.secondary.material", Material.AMETHYST_SHARD),
                config.getString("main.secondary.name", "<#A855F7>Secondary Effect") + " <white>" + plugin.display(equipped[1])));
        set(inventory, config.getInt("main.effects-slot", 13), plugin.named(
                material("main.effects.material", Material.ENCHANTED_BOOK), config.getString("main.effects.name", "<#C4B5FD>All Effects")));
        set(inventory, config.getInt("main.close-slot", 22), plugin.named(Material.BARRIER, "<red>Close"));
        return inventory;
    }

    Inventory buildEffects() {
        int size = inventorySize("effects.rows", 6);
        Inventory inventory = Bukkit.createInventory(null, size, plugin.getMiniMessage().deserialize(config.getString("effects.title", "<light_purple>Choose an Effect")));
        ItemStack filler = plugin.named(material("effects.filler", Material.BLACK_STAINED_GLASS_PANE), config.getString("effects.filler-name", " "));
        for (int slot = 0; slot < size; slot++) inventory.setItem(slot, filler);
        List<Integer> positions = config.getIntegerList("effects.slots");
        for (int i = 0; i < InfusePlugin.EFFECTS.size() && i < positions.size(); i++) {
            int slot = positions.get(i);
            if (slot >= 0 && slot < size) inventory.setItem(slot, plugin.createEffectItem(InfusePlugin.EFFECTS.get(i), InfusePlugin.EFFECTS.get(i).equals("earth") || InfusePlugin.EFFECTS.get(i).equals("ender")));
        }
        set(inventory, config.getInt("effects.close-slot", 49), plugin.named(Material.BARRIER, "<red>Close"));
        set(inventory, config.getInt("effects.back-slot", 45), plugin.named(Material.ARROW, "<yellow>Back"));
        return inventory;
    }

    private int inventorySize(String path, int fallbackRows) {
        int rows = Math.max(1, Math.min(6, config.getInt(path, fallbackRows)));
        return rows * 9;
    }

    private Material material(String path, Material fallback) {
        Material material = Material.matchMaterial(config.getString(path, fallback.name()));
        return material == null || !material.isItem() ? fallback : material;
    }

    private void set(Inventory inventory, int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, item);
    }
}
