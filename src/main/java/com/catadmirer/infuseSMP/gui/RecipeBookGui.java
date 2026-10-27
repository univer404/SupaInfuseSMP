package com.catadmirer.infuseSMP.gui;

import com.catadmirer.infuseSMP.Infuse;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Separate recipe browser GUI; all positions and titles are configured in gui.yml. */
public final class RecipeBookGui implements Listener {
    private static final List<String> EFFECTS = List.of("emerald", "feather", "fire", "frost", "haste", "heart", "invisibility", "ocean", "regeneration", "speed", "strength", "thunder", "ender");
    private static final int[] RECIPE_SLOTS = {11, 12, 13, 20, 21, 22, 29, 30, 31};
    private final Infuse plugin;
    private YamlConfiguration gui;
    private final Map<UUID, Inventory> openMenus = new HashMap<>();
    private final Map<UUID, String> currentPage = new HashMap<>();

    public RecipeBookGui(Infuse plugin) {
        this.plugin = plugin;
        this.gui = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "gui.yml"));
    }

    public void reload() { this.gui = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "gui.yml")); }

    public void open(Player player) { openMain(player); }

    private void openMain(Player player) {
        Inventory inventory = newMenu(player, gui.getString("recipes.title", "<gradient:#A2C81C:#21CD76>Recipes"));
        List<Integer> slots = gui.getIntegerList("recipes.slots");
        for (int i = 0; i < EFFECTS.size() && i < slots.size(); i++) {
            int slot = slots.get(i);
            if (valid(inventory, slot)) inventory.setItem(slot, plugin.createEffectItem(EFFECTS.get(i), EFFECTS.get(i).equals("earth") || EFFECTS.get(i).equals("ender")));
        }
        finish(player, inventory, "main");
    }

    private void openRecipe(Player player, String effect) {
        Inventory inventory = newMenu(player, gui.getString("recipes.title", "<gradient:#A2C81C:#21CD76>Recipes"));
        List<String> recipe = InfuseCraftingGui.RECIPES.get(effect);
        if (recipe != null) {
            for (int i = 0; i < RECIPE_SLOTS.length; i++) {
                Material material = Material.matchMaterial(recipe.get(i));
                if (material != null) inventory.setItem(RECIPE_SLOTS[i], new ItemStack(material));
            }
            set(inventory, gui.getInt("recipes.result-slot", 24), plugin.createEffectItem(effect, effect.equals("earth") || effect.equals("ender")));
        }
        set(inventory, gui.getInt("recipes.back-slot", 36), plugin.named(Material.ARROW, "<gray><bold>← Back"));
        finish(player, inventory, effect);
    }

    private Inventory newMenu(Player player, String title) {
        int rows = Math.max(1, Math.min(6, gui.getInt("recipes.rows", 5)));
        Inventory inventory = Bukkit.createInventory(player, rows * 9, plugin.getMiniMessage().deserialize(title));
        Material fillerMaterial = Material.matchMaterial(gui.getString("recipes.filler", "BLACK_STAINED_GLASS_PANE"));
        if (fillerMaterial == null || !fillerMaterial.isItem()) fillerMaterial = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack filler = plugin.named(fillerMaterial, gui.getString("recipes.filler-name", " "));
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
        return inventory;
    }

    private void finish(Player player, Inventory inventory, String page) {
        set(inventory, gui.getInt("recipes.close-slot", 44), plugin.named(Material.BARRIER, "<red><bold>EXIT"));
        openMenus.put(player.getUniqueId(), inventory);
        currentPage.put(player.getUniqueId(), page);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory menu = openMenus.get(player.getUniqueId());
        if (menu == null || event.getView().getTopInventory() != menu) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= menu.getSize()) return;
        if (slot == gui.getInt("recipes.close-slot", 44)) {
            openMenus.remove(player.getUniqueId());
            currentPage.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }
        String page = currentPage.getOrDefault(player.getUniqueId(), "main");
        if (!page.equals("main")) {
            if (slot == gui.getInt("recipes.back-slot", 36)) openMain(player);
            return;
        }
        List<Integer> slots = gui.getIntegerList("recipes.slots");
        for (int i = 0; i < slots.size() && i < EFFECTS.size(); i++) {
            if (slots.get(i) == slot) { openRecipe(player, EFFECTS.get(i)); return; }
        }
    }

    private boolean valid(Inventory inventory, int slot) { return slot >= 0 && slot < inventory.getSize(); }
    private void set(Inventory inventory, int slot, ItemStack item) {
        if (valid(inventory, slot)) inventory.setItem(slot, item);
    }
}
