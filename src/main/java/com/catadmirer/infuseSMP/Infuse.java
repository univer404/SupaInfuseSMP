package com.catadmirer.infuseSMP;

import com.catadmirer.infuseSMP.effects.EffectRegistry;
import com.catadmirer.infuseSMP.effects.SparkAbilityManager;
import com.catadmirer.infuseSMP.extraeffects.AugmentationManager;
import com.catadmirer.infuseSMP.extraeffects.EmeraldEnchantingListener;
import com.catadmirer.infuseSMP.managers.MessageManager;
import com.catadmirer.infuseSMP.util.trust.TrustManager;
import com.catadmirer.infuseSMP.gui.CraftingGui;
import com.catadmirer.infuseSMP.gui.InfuseGui;
import com.catadmirer.infuseSMP.gui.RecipeBookGui;
import com.catadmirer.infuseSMP.util.regions.RegionManager;
import com.catadmirer.infuseSMP.commands.InfuseCommandHandler;
import com.catadmirer.infuseSMP.listeners.DamageListener;
import com.catadmirer.infuseSMP.listeners.EffectItemListener;
import com.catadmirer.infuseSMP.listeners.PlayerLifecycleListener;
import com.catadmirer.infuseSMP.listeners.SparkControlListener;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Infuse extends JavaPlugin {
    public static final List<String> EFFECTS = EffectRegistry.playableEffects();
    private static final List<String> ICON_EFFECTS = EffectRegistry.iconEffects();
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Map<UUID, String[]> equipped = new HashMap<>();
    private final Map<UUID, boolean[]> augmented = new HashMap<>();
    private final Map<UUID, String[]> controls = new HashMap<>();
    private final TrustManager trustManager = new TrustManager();
    private final AugmentationManager augmentationManager = new AugmentationManager();
    private final Map<UUID, List<String>> primaryHistory = new HashMap<>();
    private final Map<UUID, List<String>> secondaryHistory = new HashMap<>();
    private final Map<UUID, Boolean> displayEnabled = new HashMap<>();
    private final Map<UUID, Integer> selectionMode = new HashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, Integer> hitCounters = new HashMap<>();
    private final Map<String, Integer> hitPairs = new HashMap<>();
    private final Map<UUID, Deque<DamageSample>> recentDamage = new HashMap<>();
    private final Map<UUID, Double> hasteStacks = new HashMap<>();
    private final Map<UUID, org.bukkit.attribute.AttributeModifier> hasteModifiers = new HashMap<>();
    private final Map<UUID, org.bukkit.attribute.AttributeModifier> hasteBreakSpeedModifiers = new HashMap<>();
    private final Map<UUID, org.bukkit.attribute.AttributeModifier> hasteSparkBreakSpeedModifiers = new HashMap<>();
    private final Map<UUID, org.bukkit.attribute.AttributeModifier> hasteSparkAttackSpeedModifiers = new HashMap<>();
    private final Map<UUID, Double> speedStacks = new HashMap<>();
    private final Map<UUID, org.bukkit.attribute.AttributeModifier> speedModifiers = new HashMap<>();
    private final Map<UUID, org.bukkit.attribute.AttributeModifier> frostSpeedModifiers = new HashMap<>();
    private final Map<UUID, org.bukkit.attribute.AttributeModifier> frostKnockbackModifiers = new HashMap<>();
    private final Map<String, Long> sparkEnds = new HashMap<>();
    private final Map<UUID, Long> trustActionCooldowns = new HashMap<>();
    private final Set<UUID> invisiblePlayers = new HashSet<>();
    private final Map<UUID, Set<UUID>> invisibilitySparkOwners = new HashMap<>();
    private final Map<UUID, BukkitTask> invisibilitySparkTasks = new HashMap<>();
    private final Set<UUID> regenFoodWindow = new HashSet<>();
    private final Map<UUID, Integer> oceanAir = new HashMap<>();
    private final Map<UUID, Long> oceanLastActive = new HashMap<>();
    private final Set<UUID> fireGlidingPlayers = new HashSet<>();
    private final Map<UUID, Integer> windCharges = new HashMap<>();
    private final Map<UUID, List<WindChargeVisual>> windChargeVisuals = new HashMap<>();
    private final Map<UUID, Long> windChargeCooldowns = new HashMap<>();
    private final Set<UUID> lightningChainAttackers = new HashSet<>();
    private final Map<UUID, Long> enderCurses = new HashMap<>();
    private final Set<UUID> enderDamageShareGuard = new HashSet<>();
    private final Set<UUID> enderSparkCooldownReset = new HashSet<>();
    private NamespacedKey effectKey;
    private MessageManager messageManager;
    private SparkAbilityManager sparkAbilityManager;
    private InfuseGui gui;
    private RecipeBookGui recipeGui;
    private RegionManager regionManager;
    private BukkitTask passiveTask;

    @Override public void onEnable() {
        saveDefaultConfig();
        saveResource("gui.yml", false);
        saveResource("messages.yml", false);
        effectKey = new NamespacedKey(this, "effect");
        messageManager = new MessageManager(this, mini);
        sparkAbilityManager = new SparkAbilityManager(this);
        gui = new InfuseGui(this);
        recipeGui = new RecipeBookGui(this);
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            try {
                Class<?> implementation = Class.forName("com.catadmirer.infuseSMP.util.regions.WorldGuardRegionManager");
                regionManager = (RegionManager) implementation.getConstructor(Infuse.class).newInstance(this);
            } catch (ReflectiveOperationException | LinkageError exception) {
                getLogger().warning("WorldGuard integration could not be loaded: " + exception.getMessage());
            }
        }
        loadData();
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                Class<?> expansionType = Class.forName("com.catadmirer.infuseSMP.expansions.InfusePlaceholders");
                Object expansion = expansionType.getConstructor(Infuse.class).newInstance(this);
                expansionType.getMethod("register").invoke(expansion);
                getLogger().info("PlaceholderAPI expansion registered.");
            } catch (ReflectiveOperationException | LinkageError exception) {
                getLogger().warning("PlaceholderAPI expansion could not be loaded: " + exception.getMessage());
            }
        }
        InfuseCommandHandler commandHandler = new InfuseCommandHandler(this);
        for (String commandName : List.of("infuse", "trust", "use", "swap", "recipes", "control", "item",
                "ldrain", "rdrain", "conf", "mapregion", "toggledisplay", "resetcraft", "resetcooldown")) {
            PluginCommand pluginCommand = getCommand(commandName);
            if (pluginCommand == null) {
                getLogger().warning("Command '" + commandName + "' is missing from plugin.yml.");
                continue;
            }
            pluginCommand.setExecutor(commandHandler);
            pluginCommand.setTabCompleter(commandHandler);
        }
        getServer().getPluginManager().registerEvents(new DamageListener(this), this);
        getServer().getPluginManager().registerEvents(new EffectItemListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(this), this);
        getServer().getPluginManager().registerEvents(new SparkControlListener(this), this);
        getServer().getPluginManager().registerEvents(new EmeraldEnchantingListener(this), this);
        getServer().getPluginManager().registerEvents(new CraftingGui(this), this);
        getServer().getPluginManager().registerEvents(recipeGui, this);
        passiveTask = Bukkit.getScheduler().runTaskTimer(this, this::applyPassives, 20L, 20L);
        getLogger().info("Infuse initialized. " + EFFECTS.size() + " effect identifiers registered.");
    }

    @Override public void onDisable() {
        if (passiveTask != null) passiveTask.cancel();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player hidden : Bukkit.getOnlinePlayers()) viewer.showPlayer(this, hidden);
            clearHasteStack(viewer);
            clearSpeedStack(viewer);
        }
        saveData();
    }

    private void loadData() {
        var data = getConfig().getConfigurationSection("players");
        if (data == null) return;
        for (String id : data.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(id);
                equipped.put(uuid, new String[]{data.getString(id + ".primary", ""), data.getString(id + ".secondary", "")});
                augmented.put(uuid, new boolean[]{data.getBoolean(id + ".primary-augmented", false), data.getBoolean(id + ".secondary-augmented", false)});
                controls.put(uuid, new String[]{data.getString(id + ".primary-bind", "none"), data.getString(id + ".secondary-bind", "none")});
                primaryHistory.put(uuid, new ArrayList<>(data.getStringList(id + ".history-primary")));
                secondaryHistory.put(uuid, new ArrayList<>(data.getStringList(id + ".history-secondary")));
                displayEnabled.put(uuid, data.getBoolean(id + ".display-enabled", true));
                Set<UUID> list = new HashSet<>();
                for (String friend : data.getStringList(id + ".trusted")) try { list.add(UUID.fromString(friend)); } catch (IllegalArgumentException ignored) {}
                trustManager.setTrusted(uuid, list);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveData() {
        getConfig().set("players", null);
        Set<UUID> playerIds = new HashSet<>(equipped.keySet());
        playerIds.addAll(trustManager.playerIds());
        for (UUID id : playerIds) {
            String path = "players." + id;
            String[] values = equipped.getOrDefault(id, new String[]{"", ""});
            getConfig().set(path + ".primary", values[0]);
            getConfig().set(path + ".secondary", values[1]);
            boolean[] isAugmented = augmented.getOrDefault(id, new boolean[]{false, false});
            getConfig().set(path + ".primary-augmented", isAugmented[0]);
            getConfig().set(path + ".secondary-augmented", isAugmented[1]);
            String[] control = controls.getOrDefault(id, new String[]{"none", "none"});
            getConfig().set(path + ".primary-bind", control[0]);
            getConfig().set(path + ".secondary-bind", control[1]);
            getConfig().set(path + ".trusted", trustManager.getTrusted(id).stream().map(UUID::toString).toList());
            getConfig().set(path + ".history-primary", primaryHistory.getOrDefault(id, List.of()));
            getConfig().set(path + ".history-secondary", secondaryHistory.getOrDefault(id, List.of()));
            getConfig().set(path + ".display-enabled", displayEnabled.getOrDefault(id, true));
        }
        saveConfig();
    }

    public net.kyori.adventure.text.minimessage.MiniMessage getMiniMessage() { return mini; }

    private String message(String key) {
        return messageManager.get(key);
    }
    private void tell(CommandSender sender, String key) { sender.sendMessage(mini.deserialize(message(key))); }
    private String clean(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }
    public boolean isTrusted(UUID ownerId, UUID targetId) {
        return trustManager.isTrusted(ownerId, targetId);
    }

    public void markEnderSparkCooldownReset(UUID playerId) {
        enderSparkCooldownReset.add(playerId);
    }

    public boolean hasEffect(Player player, String effect) {
        return player != null && !disabled(player) && Arrays.asList(slots(player.getUniqueId())).contains(effect);
    }

    public String[] slots(UUID id) { return equipped.computeIfAbsent(id, ignored -> new String[]{"", ""}); }
    private boolean disabled(Player p) {
        if (getConfig().getStringList("settings.disabled-worlds").contains(p.getWorld().getName())) return true;
        return regionManager != null && regionManager.isDisabled(p.getLocation());
    }

    public boolean handleCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("mapregion")) return mapRegionCommand(sender, args);
        if (command.getName().equalsIgnoreCase("conf")) return configCommand(sender, args);
        if (command.getName().equalsIgnoreCase("toggledisplay")) return toggleDisplay(sender);
        if (command.getName().equalsIgnoreCase("resetcooldown")) return resetCooldownCommand(sender, args);
        if (command.getName().equalsIgnoreCase("resetcraft")) return resetCraftCommand(sender, args);
        if (command.getName().equalsIgnoreCase("item")) return itemCommand(sender, args);
        if (command.getName().equalsIgnoreCase("ldrain") || command.getName().equalsIgnoreCase("pdrain")) return drainEffect(sender, "primary");
        if (command.getName().equalsIgnoreCase("rdrain") || command.getName().equalsIgnoreCase("sdrain")) return drainEffect(sender, "secondary");
        if (command.getName().equalsIgnoreCase("trust")) {
            if (label.equalsIgnoreCase("untrust") && args.length > 0) return trustCommand(sender, new String[]{"remove", args[0]});
            return trustCommand(sender, args);
        }
        if (command.getName().equalsIgnoreCase("control")) return controlCommand(sender, args);
        if (command.getName().equalsIgnoreCase("use")) {
            if (!(sender instanceof Player p)) { tell(sender, "player-only"); return true; }
            String slot = args.length == 0 ? "primary" : args[0].equalsIgnoreCase("p") ? "primary" : args[0].equalsIgnoreCase("s") ? "secondary" : args[0].toLowerCase(Locale.ROOT);
            if (!slot.equals("primary") && !slot.equals("secondary")) { p.sendMessage("Usage: /use <primary|secondary>"); return true; }
            spark(p, slot); return true;
        }
        if (command.getName().equalsIgnoreCase("swap")) {
            if (!(sender instanceof Player p)) { tell(sender, "player-only"); return true; }
            String[] values = slots(p.getUniqueId());
            String temp = values[0]; values[0] = values[1]; values[1] = temp;
            boolean[] isAugmented = augmented.computeIfAbsent(p.getUniqueId(), ignored -> new boolean[]{false, false});
            boolean tempAugmented = isAugmented[0]; isAugmented[0] = isAugmented[1]; isAugmented[1] = tempAugmented;
            addHistory(p.getUniqueId(), 0, values[0].isBlank() ? "none" : values[0], isAugmented[0]);
            addHistory(p.getUniqueId(), 1, values[1].isBlank() ? "none" : values[1], isAugmented[1]);
            saveData(); p.sendMessage(mini.deserialize("<green>Swapped your Infuse effects.")); return true;
        }
        if (command.getName().equalsIgnoreCase("recipes")) {
            if (!(sender instanceof Player p)) { tell(sender, "player-only"); return true; }
            recipeGui.open(p);
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            if (!(sender instanceof Player p)) { tell(sender, "player-only"); return true; }
            openMain(p); return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("infuse.admin")) { tell(sender, "no-permission"); return true; }
            reloadConfig(); saveDefaultConfig(); gui.reload(); recipeGui.reload(); messageManager.reload(); sender.sendMessage(mini.deserialize(message("reload"))); return true;
        }
        if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("get") || args[0].equalsIgnoreCase("history")) {
            return adminInfuseCommand(sender, args);
        }
        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("infuse.admin")) { tell(sender, "no-permission"); return true; }
            if (args.length < 3) { sender.sendMessage("/infuse give <player> <effect> [augmented]"); return true; }
            Player target = Bukkit.getPlayerExact(args[1]); String effect = clean(args[2]);
            if (target == null) { tell(sender, "not-player"); return true; }
            if (!EFFECTS.contains(effect)) { tell(sender, "unknown-effect"); return true; }
            boolean augmented = (args.length > 3 && args[3].equalsIgnoreCase("augmented")) || effect.equals("earth") || effect.equals("ender");
            target.getInventory().addItem(createEffectItem(effect, augmented));
            return true;
        }
        if (args[0].equalsIgnoreCase("clear")) {
            if (!(sender instanceof Player p)) { tell(sender, "player-only"); return true; }
            if (args.length < 2 || !args[1].matches("primary|secondary|both")) { p.sendMessage("Usage: /infuse clear <primary|secondary|both>"); return true; }
            String[] s = slots(p.getUniqueId());
            boolean[] isAugmented = augmented.computeIfAbsent(p.getUniqueId(), ignored -> new boolean[]{false, false});
            if (args[1].equalsIgnoreCase("primary") || args[1].equalsIgnoreCase("both")) { s[0] = ""; isAugmented[0] = false; addHistory(p.getUniqueId(), 0, "none", false); }
            if (args[1].equalsIgnoreCase("secondary") || args[1].equalsIgnoreCase("both")) { s[1] = ""; isAugmented[1] = false; addHistory(p.getUniqueId(), 1, "none", false); }
            saveData(); p.sendMessage(mini.deserialize(message("effect-cleared"))); return true;
        }
        if (args[0].equalsIgnoreCase("spark")) {
            if (!(sender instanceof Player p)) { tell(sender, "player-only"); return true; }
            spark(p); return true;
        }
        sender.sendMessage("Usage: /infuse [gui|give|clear|spark|reload]");
        return true;
    }

    private boolean drainEffect(CommandSender sender, String slot) {
        if (!(sender instanceof Player player)) { tell(sender, "player-only"); return true; }
        int index = slot.equals("primary") ? 0 : 1;
        String[] current = slots(player.getUniqueId());
        boolean[] isAugmented = augmented.computeIfAbsent(player.getUniqueId(), ignored -> new boolean[]{false, false});
        String effect = current[index];
        if (effect == null || effect.isBlank()) {
            player.sendMessage(mini.deserialize("<red>You don't have an effect in your " + slot + " slot."));
            return true;
        }
        String cooldownKey = player.getUniqueId() + ":" + effect;
        if (sparkEnds.getOrDefault(cooldownKey, 0L) > System.currentTimeMillis() || cooldowns.getOrDefault(cooldownKey, 0L) > System.currentTimeMillis()) {
            player.sendMessage(mini.deserialize("<red>You cannot remove this effect while it is active or on cooldown."));
            return true;
        }
        ItemStack item = createEffectItem(effect, isAugmented[index]);
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(mini.deserialize("<red>Your inventory is full."));
            return true;
        }
        player.getInventory().addItem(item);
        current[index] = "";
        isAugmented[index] = false;
        addHistory(player.getUniqueId(), index, "none", false);
        saveData();
        player.playSound(player.getLocation(), "item.bottle.fill", 0.5f, 0.8f);
        player.sendMessage(mini.deserialize("<yellow>Drained <white>" + title(effect) + "<yellow> from your " + slot + " slot."));
        return true;
    }

    private void addHistory(UUID playerId, int slot, String effect, boolean isAugmented) {
        Map<UUID, List<String>> historyMap = slot == 0 ? primaryHistory : secondaryHistory;
        List<String> history = historyMap.computeIfAbsent(playerId, ignored -> new ArrayList<>());
        history.add(effect + ":" + isAugmented + ":" + System.currentTimeMillis());
        while (history.size() > 30) history.remove(0);
    }

    private boolean toggleDisplay(CommandSender sender) {
        if (!(sender instanceof Player player)) { tell(sender, "player-only"); return true; }
        boolean enabled = !displayEnabled.getOrDefault(player.getUniqueId(), true);
        displayEnabled.put(player.getUniqueId(), enabled);
        saveData();
        player.sendMessage(mini.deserialize("<green>Infuse HUD " + (enabled ? "enabled" : "disabled") + "."));
        return true;
    }

    private boolean resetCooldownCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { tell(sender, "player-only"); return true; }
        String[] current = slots(player.getUniqueId());
        int[] selected = args.length > 0 && args[0].equalsIgnoreCase("primary") ? new int[]{0}
                : args.length > 0 && args[0].equalsIgnoreCase("secondary") ? new int[]{1} : new int[]{0, 1};
        for (int index : selected) {
            String effect = current[index];
            if (effect == null || effect.isBlank()) continue;
            String key = player.getUniqueId() + ":" + effect;
            if (sparkEnds.getOrDefault(key, 0L) > System.currentTimeMillis()) {
                player.sendMessage(mini.deserialize("<red>Your " + (index == 0 ? "primary" : "secondary") + " Spark is still active; wait for it to end."));
                return true;
            }
        }
        for (int index : selected) {
            String effect = current[index];
            if (effect != null && !effect.isBlank()) cooldowns.remove(player.getUniqueId() + ":" + effect);
        }
        player.sendMessage(mini.deserialize("<green>Reset cooldown for " + (selected.length == 2 ? "both" : selected[0] == 0 ? "primary" : "secondary") + " slot(s)."));
        return true;
    }

    private boolean resetCraftCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("infuse.admin")) { tell(sender, "no-permission"); return true; }
        if (args.length == 0) { sender.sendMessage("/resetcraft <effect|all>"); return true; }
        if (args[0].equalsIgnoreCase("all")) {
            getConfig().set("crafting.crafted", null);
            saveConfig();
            sender.sendMessage(mini.deserialize("<green>Reset craft counters for all effects."));
            return true;
        }
        String effect = clean(args[0]);
        if (!EFFECTS.contains(effect)) { tell(sender, "unknown-effect"); return true; }
        int old = getConfig().getInt("crafting.crafted." + effect, 0);
        getConfig().set("crafting.crafted." + effect, 0);
        saveConfig();
        sender.sendMessage(mini.deserialize("<green>Reset <white>" + title(effect) + "<green> craft counter. Previous value: <white>" + old));
        return true;
    }

    private boolean mapRegionCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("infuse.admin")) { tell(sender, "no-permission"); return true; }
        if (!(sender instanceof Player player)) { tell(sender, "player-only"); return true; }
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard") || regionManager == null) {
            player.sendMessage(mini.deserialize("<red>/mapregion requires WorldGuard to be installed."));
            return true;
        }
        if (args.length == 0) { player.sendMessage("/mapregion <add|delete|list|clear> [region-id]"); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 2) { player.sendMessage("/mapregion add <region-id>"); return true; }
                if (!regionManager.add(player.getWorld().getName(), args[1])) {
                    player.sendMessage(mini.deserialize("<red>WorldGuard region not found: " + args[1]));
                    return true;
                }
                player.sendMessage(mini.deserialize("<green>Infuse disabled in <white>" + args[1] + "<green> (" + regionManager.bounds(player.getWorld().getName(), args[1]) + ")."));
            }
            case "delete" -> {
                if (args.length < 2) { player.sendMessage("/mapregion delete <region-id>"); return true; }
                if (regionManager.remove(player.getWorld().getName(), args[1])) player.sendMessage(mini.deserialize("<green>Removed disabled region <white>" + args[1] + "<green>."));
                else player.sendMessage(mini.deserialize("<red>That region is not mapped for Infuse."));
            }
            case "list" -> {
                List<String> mapped = regionManager.list();
                if (mapped.isEmpty()) player.sendMessage(mini.deserialize("<gray>No Infuse-disabled regions are mapped."));
                else {
                    player.sendMessage(mini.deserialize("<yellow>Infuse-disabled regions:"));
                    for (String entry : mapped) {
                        String[] parts = entry.split("\\|", 2);
                        if (parts.length == 2) player.sendMessage(mini.deserialize("<gray>• <white>" + parts[1] + " <dark_gray>(" + parts[0] + ") <gray>" + regionManager.bounds(parts[0], parts[1])));
                    }
                }
            }
            case "clear" -> {
                regionManager.clear();
                player.sendMessage(mini.deserialize("<green>Cleared all Infuse-disabled regions."));
            }
            default -> player.sendMessage("/mapregion <add|delete|list|clear> [region-id]");
        }
        return true;
    }

    private boolean configCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("infuse.admin")) { tell(sender, "no-permission"); return true; }
        if (args.length == 0) { sender.sendMessage("/conf <get|set|list> ..."); return true; }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            if (args.length < 3) { sender.sendMessage("/conf list <show|add|remove|clear> <primary_effects|secondary_effects> [effect]"); return true; }
            String listName = args[2].toLowerCase(Locale.ROOT).replace('_', '-');
            String path = switch (listName) {
                case "primary-effects" -> "settings.primary-effects";
                case "secondary-effects" -> "settings.secondary-effects";
                default -> null;
            };
            if (path == null) { sender.sendMessage("Supported lists: primary_effects, secondary_effects"); return true; }
            List<String> values = new ArrayList<>(getConfig().getStringList(path));
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "show" -> sender.sendMessage(path + " = " + values);
                case "add" -> {
                    if (args.length < 4) { sender.sendMessage("Specify a value to add."); return true; }
                    String value = clean(args[3]);
                    if (!EFFECTS.contains(value)) { tell(sender, "unknown-effect"); return true; }
                    if (!values.contains(value)) values.add(value);
                    getConfig().set(path, values); saveConfig(); sender.sendMessage("Updated " + path + ": " + values);
                }
                case "remove" -> {
                    if (args.length < 4) { sender.sendMessage("Specify a value to remove."); return true; }
                    values.remove(clean(args[3])); getConfig().set(path, values); saveConfig(); sender.sendMessage("Updated " + path + ": " + values);
                }
                case "clear" -> { values.clear(); getConfig().set(path, values); saveConfig(); sender.sendMessage("Cleared " + path); }
                default -> sender.sendMessage("/conf list <show|add|remove|clear> <primary_effects|secondary_effects> [effect]");
            }
            return true;
        }
        if (!action.equals("get") && !action.equals("set")) { sender.sendMessage("/conf <get|set|list> ..."); return true; }
        if (args.length < (action.equals("get") ? 2 : 3)) { sender.sendMessage("/conf get <key> | /conf get <effect> <key> | /conf set <key> <value> | /conf set <effect> <key> <value>"); return true; }
        String path;
        String value = null;
        if (action.equals("get")) {
            if (args.length >= 3 && EFFECTS.contains(clean(args[1]))) path = "effects." + clean(args[1]) + "." + normalizeKey(args[2]);
            else path = normalizeConfigPath(String.join(".", Arrays.copyOfRange(args, 1, args.length)));
        } else if (args.length >= 4 && EFFECTS.contains(clean(args[1])) && !args[1].contains(".")) {
            path = "effects." + clean(args[1]) + "." + normalizeKey(args[2]);
            value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        } else if (args.length >= 4 && (args[1].equalsIgnoreCase("settings") || args[1].equalsIgnoreCase("crafting"))) {
            path = normalizeConfigPath(args[1] + "." + args[2]);
            value = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        } else {
            path = normalizeConfigPath(args[1]);
            value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        }
        if (action.equals("get")) {
            sender.sendMessage(mini.deserialize("<yellow>" + path + " <gray>= <white>" + String.valueOf(getConfig().get(path, "<unset>"))));
            return true;
        }
        Object parsed = parseConfigValue(value);
        getConfig().set(path, parsed);
        saveConfig();
        sender.sendMessage(mini.deserialize("<green>Updated <white>" + path + "<green> to <white>" + parsed));
        return true;
    }

    private String normalizeKey(String key) { return key.toLowerCase(Locale.ROOT).replace('_', '-'); }

    private String normalizeConfigPath(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace('_', '-');
        if (normalized.startsWith("settings.") || normalized.startsWith("effects.") || normalized.startsWith("crafting.")) return normalized;
        return "settings." + normalized;
    }

    private Object parseConfigValue(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) return Boolean.parseBoolean(value);
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(value); } catch (NumberFormatException ignored) {}
        if (value.contains(",")) return Arrays.stream(value.split(",")).map(String::trim).filter(part -> !part.isEmpty()).toList();
        return value;
    }

    private boolean itemCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("infuse.admin")) { tell(sender, "no-permission"); return true; }
        if (!(sender instanceof Player player)) { tell(sender, "player-only"); return true; }
        if (args.length == 0) { player.sendMessage("/item <effect|all> [true|false]"); return true; }
        if (args[0].equalsIgnoreCase("all")) {
            for (String effect : EFFECTS) {
                if (effect.equals("earth")) continue;
                if (effect.equals("ender")) {
                    player.getInventory().addItem(createEffectItem(effect, true));
                } else {
                    player.getInventory().addItem(createEffectItem(effect, false));
                    player.getInventory().addItem(createEffectItem(effect, true));
                }
            }
            return true;
        }
        String effect = clean(args[0]);
        if (!EFFECTS.contains(effect)) { tell(sender, "unknown-effect"); return true; }
        boolean isAugmented = (args.length > 1 && Boolean.parseBoolean(args[1])) || effect.equals("earth") || effect.equals("ender");
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(createEffectItem(effect, isAugmented));
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        return true;
    }

    private boolean adminInfuseCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("infuse.admin")) { tell(sender, "no-permission"); return true; }
        String action = args[0].toLowerCase(Locale.ROOT);
        String playerName = null, effect = null, slot = null;
        boolean isAugmented = false;
        List<String> positional = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String value = args[i];
            if (value.startsWith("-p=")) playerName = value.substring(3);
            else if (value.startsWith("-e=")) effect = clean(value.substring(3));
            else if (value.startsWith("-s=")) slot = value.substring(3).toLowerCase(Locale.ROOT);
            else if (value.startsWith("-a=")) isAugmented = Boolean.parseBoolean(value.substring(3));
            else if (!value.startsWith("-")) positional.add(value);
        }
        if (playerName == null && !positional.isEmpty()) playerName = positional.remove(0);
        if (effect == null && !positional.isEmpty() && EFFECTS.contains(clean(positional.get(0)))) effect = clean(positional.remove(0));
        if (slot == null && !positional.isEmpty()) slot = positional.remove(0).toLowerCase(Locale.ROOT);
        if (!positional.isEmpty() && positional.get(0).equalsIgnoreCase("augmented")) isAugmented = true;
        Player target;
        if (playerName == null) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Specify a player with -p=<name>."); return true; }
            target = player;
        } else {
            target = Bukkit.getPlayerExact(playerName);
            if (target == null) { tell(sender, "not-player"); return true; }
        }
        String[] current = slots(target.getUniqueId());
        boolean[] aug = augmented.computeIfAbsent(target.getUniqueId(), ignored -> new boolean[]{false, false});
        if (action.equals("get")) {
            if ("primary".equals(slot) || "p".equals(slot)) sender.sendMessage(mini.deserialize("<yellow>[primary] <white>" + display(current[0]) + " <gray>(augmented: " + aug[0] + ")"));
            else if ("secondary".equals(slot) || "s".equals(slot)) sender.sendMessage(mini.deserialize("<yellow>[secondary] <white>" + display(current[1]) + " <gray>(augmented: " + aug[1] + ")"));
            else sender.sendMessage(mini.deserialize("<yellow>" + target.getName() + " <gray>Primary: <white>" + display(current[0]) + " <gray>(aug: " + aug[0] + ") <gray>| Secondary: <white>" + display(current[1]) + " <gray>(aug: " + aug[1] + ")"));
            return true;
        }
        if (action.equals("history")) {
            if (slot == null || (!slot.equals("primary") && !slot.equals("p") && !slot.equals("secondary") && !slot.equals("s"))) {
                sender.sendMessage("Usage: /infuse history -p=<player> -s=<primary|secondary>"); return true;
            }
            List<String> entries = (slot.equals("primary") || slot.equals("p") ? primaryHistory : secondaryHistory).getOrDefault(target.getUniqueId(), List.of());
            sender.sendMessage(mini.deserialize("<yellow>History: " + target.getName() + " [" + slot + "]"));
            if (entries.isEmpty()) sender.sendMessage(mini.deserialize("<gray>No history found."));
            else for (int i = 0; i < entries.size(); i++) {
                String[] parts = entries.get(i).split(":");
                sender.sendMessage(mini.deserialize("<gray>" + (i + 1) + ". <white>" + parts[0] + " <gray>(augmented: " + (parts.length > 1 ? parts[1] : "false") + ") <dark_gray>@ <gray>" + (parts.length > 2 ? parts[2] : "?")));
            }
            return true;
        }
        if (action.equals("remove")) {
            if (slot == null || slot.equals("both")) {
                current[0] = ""; current[1] = ""; aug[0] = false; aug[1] = false;
                addHistory(target.getUniqueId(), 0, "none", false); addHistory(target.getUniqueId(), 1, "none", false);
            } else if (slot.equals("primary") || slot.equals("p")) {
                current[0] = ""; aug[0] = false; addHistory(target.getUniqueId(), 0, "none", false);
            } else if (slot.equals("secondary") || slot.equals("s")) {
                current[1] = ""; aug[1] = false; addHistory(target.getUniqueId(), 1, "none", false);
            } else { sender.sendMessage("Slot must be primary, secondary, or both."); return true; }
            saveData();
            sender.sendMessage(mini.deserialize("<yellow>Removed Infuse effect from " + target.getName() + "."));
            return true;
        }
        if (effect == null || !EFFECTS.contains(effect)) { sender.sendMessage("/infuse add -p=<player> -e=<effect> [-s=primary|secondary] [-a=true]"); return true; }
        if (effect.equals("earth") || effect.equals("ender")) isAugmented = true;
        String selectedSlot = slot == null ? "both" : slot;
        String[] next = current.clone();
        boolean[] nextAug = aug.clone();
        if (selectedSlot.equals("both")) {
            next[0] = effect; next[1] = effect; nextAug[0] = isAugmented; nextAug[1] = isAugmented;
        } else if (selectedSlot.equals("primary") || selectedSlot.equals("p")) {
            next[0] = effect; nextAug[0] = isAugmented;
        } else if (selectedSlot.equals("secondary") || selectedSlot.equals("s")) {
            next[1] = effect; nextAug[1] = isAugmented;
        } else { sender.sendMessage("Slot must be primary, secondary, or both."); return true; }
        equipped.put(target.getUniqueId(), next);
        augmented.put(target.getUniqueId(), nextAug);
        for (int slotIndex = 0; slotIndex < 2; slotIndex++) {
            if (!Objects.equals(current[slotIndex], next[slotIndex]) || aug[slotIndex] != nextAug[slotIndex]) {
                addHistory(target.getUniqueId(), slotIndex, next[slotIndex].isBlank() ? "none" : next[slotIndex], nextAug[slotIndex]);
            }
        }
        saveData();
        sender.sendMessage(mini.deserialize("<green>Applied " + (isAugmented ? "augmented " : "") + title(effect) + " to " + target.getName() + "."));
        return true;
    }

    private boolean controlCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { tell(sender, "player-only"); return true; }
        if (args.length < 2) { player.sendMessage("Usage: /control <primary|secondary> <swap|drop|sneak|sneak-drop|sneak-swap|none>"); return true; }
        String slot = args[0].equalsIgnoreCase("p") ? "primary" : args[0].equalsIgnoreCase("s") ? "secondary" : args[0].toLowerCase(Locale.ROOT);
        String action = args[1].toLowerCase(Locale.ROOT);
        if (!slot.equals("primary") && !slot.equals("secondary")) { player.sendMessage("Slot must be primary or secondary."); return true; }
        if (!List.of("swap", "drop", "sneak", "sneak-drop", "sneak-swap", "none").contains(action)) {
            player.sendMessage("Action must be swap, drop, sneak, sneak-drop, sneak-swap, or none."); return true;
        }
        String[] values = controls.computeIfAbsent(player.getUniqueId(), ignored -> new String[]{"none", "none"});
        values[slot.equals("primary") ? 0 : 1] = action;
        saveData();
        player.sendMessage(mini.deserialize("<green>Bound " + slot + " Spark to <white>" + action + "<green>."));
        return true;
    }

    private boolean beginTrustAction(Player player) {
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0, getConfig().getLong("settings.trust-list-delay-seconds", 10)) * 1000L;
        long until = trustActionCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (until > now) {
            player.sendMessage(mini.deserialize("<red>Please wait " + ((until - now + 999) / 1000) + "s before changing your trust list again."));
            return false;
        }
        trustActionCooldowns.put(player.getUniqueId(), now + cooldown);
        return true;
    }

    private boolean trustCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { tell(sender, "player-only"); return true; }
        if (args.length == 0) { p.sendMessage("/trust <add|remove|list|clear> [player]"); return true; }
        Set<UUID> list = trustManager.getOrCreate(p.getUniqueId());
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 2) { p.sendMessage("/trust add <player>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { tell(p, "not-player"); return true; }
                if (target.equals(p)) { p.sendMessage("You cannot trust yourself."); return true; }
                int max = getConfig().getInt("settings.trust-list-max", 3);
                if (!list.contains(target.getUniqueId()) && list.size() >= max) { tell(p, "trust-limit"); return true; }
                if (!beginTrustAction(p)) return true;
                UUID ownerId = p.getUniqueId(), targetId = target.getUniqueId();
                String targetName = target.getName();
                long delay = Math.max(0, getConfig().getLong("settings.trust-list-delay-seconds", 10));
                p.sendMessage(mini.deserialize("<yellow>Trust update queued. It will apply in " + delay + " seconds."));
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    Set<UUID> current = trustManager.getOrCreate(ownerId);
                    if (!current.contains(targetId) && current.size() >= getConfig().getInt("settings.trust-list-max", 3)) {
                        Player owner = Bukkit.getPlayer(ownerId);
                        if (owner != null) tell(owner, "trust-limit");
                        return;
                    }
                    current.add(targetId); saveData();
                    Player owner = Bukkit.getPlayer(ownerId);
                    if (owner != null) owner.sendMessage(mini.deserialize(message("trust-added").replace("{player}", targetName)));
                }, delay * 20L);
            }
            case "remove" -> {
                if (args.length < 2) { p.sendMessage("/trust remove <player>"); return true; }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (!beginTrustAction(p)) return true;
                UUID ownerId = p.getUniqueId(), targetId = target.getUniqueId();
                String targetName = args[1];
                long delay = Math.max(0, getConfig().getLong("settings.trust-list-delay-seconds", 10));
                p.sendMessage(mini.deserialize("<yellow>Trust update queued. It will apply in " + delay + " seconds."));
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    trustManager.getOrCreate(ownerId).remove(targetId);
                    saveData();
                    Player owner = Bukkit.getPlayer(ownerId);
                    if (owner != null) owner.sendMessage(mini.deserialize(message("trust-removed").replace("{player}", targetName)));
                }, delay * 20L);
            }
            case "list" -> p.sendMessage("Trusted players: " + list.stream().map(id -> Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse(id.toString())).toList());
            case "clear" -> { list.clear(); saveData(); p.sendMessage("Trust list cleared."); }
            default -> p.sendMessage("/trust <add|remove|list|clear> [player]");
        }
        return true;
    }

    private void openMain(Player p) {
        p.openInventory(gui.buildMain(p));
    }
    private void openEffects(Player p, int slot) {
        p.openInventory(gui.buildEffects());
    }
    public ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material); ItemMeta meta=item.getItemMeta();
        meta.displayName(mini.deserialize(name)); item.setItemMeta(meta); return item;
    }
    public ItemStack createEffectItem(String effect, boolean augmented) {
        ItemStack item = new ItemStack(Material.POTION);
        org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) item.getItemMeta();
        String color = getConfig().getString("effects." + effect + ".color", "#A855F7");
        java.awt.Color parsedColor;
        try { parsedColor = java.awt.Color.decode(color); }
        catch (NumberFormatException ignored) { parsedColor = new java.awt.Color(168, 85, 247); }
        meta.setColor(org.bukkit.Color.fromRGB(parsedColor.getRed(), parsedColor.getGreen(), parsedColor.getBlue()));
        int modelId = switch (effect) {
            case "emerald" -> 414001;
            case "feather" -> 414002;
            case "fire" -> 414003;
            case "frost" -> 414004;
            case "thunder" -> 414005;
            case "haste" -> 414006;
            case "heart" -> 414007;
            case "invisibility" -> 414008;
            case "ocean" -> 414009;
            case "regeneration" -> 414010;
            case "speed" -> 414011;
            case "strength" -> 414012;
            case "ender" -> 414013;
            case "earth" -> 414014;
            default -> 414000;
        };
        meta.setCustomModelData(modelId + (augmented ? 100 : 0));
        meta.displayName(mini.deserialize("<bold><" + color + ">" + (augmented ? "Augmented " : "") + title(effect) + " Effect"));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(mini.deserialize("<gray>Passive effects:"));
        lore.add(mini.deserialize("<dark_gray>• " + switch (effect) {
            case "emerald" -> "Improved XP and loot";
            case "feather" -> "Reduced fall damage and wind mobility";
            case "fire" -> "Fire resistance and fire attacks";
            case "frost" -> "Freezing attacks and ice mobility";
            case "thunder" -> "Lightning strikes on attacks";
            case "haste" -> "Faster mining and tool enhancements";
            case "heart" -> "Extra health and absorption";
            case "invisibility" -> "Invisibility and stealth";
            case "ocean" -> "Water breathing and aquatic mobility";
            case "regeneration" -> "Improved health regeneration";
            case "speed" -> "Increased movement speed";
            case "strength" -> "Improved melee damage";
            case "ender" -> "Ender curses and teleportation";
            default -> "Earth protection";
        }));
        lore.add(mini.deserialize("<gray>Spark: <white>Right-click while holding this item"));
        lore.add(mini.deserialize("<dark_gray>Model: " + modelId + (augmented ? " (augmented)" : "")));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(effectKey, PersistentDataType.STRING, effect + (augmented ? ":augmented" : ""));
        item.setItemMeta(meta);
        return item;
    }
    public String title(String effect) { return effect.substring(0,1).toUpperCase(Locale.ROOT)+effect.substring(1); }
    public String display(String effect) { return effect == null || effect.isBlank() ? "None" : title(effect); }

    public void convertFireSnowballs(PlayerInteractEvent event) {
        if (event.getItem() == null || event.getItem().getType() != Material.SNOWBALL || !event.getAction().isRightClick()) return;
        Player player = event.getPlayer();
        if (disabled(player) || !Arrays.asList(slots(player.getUniqueId())).contains("fire")) return;
        String key = player.getUniqueId() + ":fire-snowball";
        long now = System.currentTimeMillis();
        long until = cooldowns.getOrDefault(key, 0L);
        if (until > now) {
            event.setCancelled(true);
            player.sendMessage(mini.deserialize("<red>Fireball is on cooldown for " + ((until - now + 999) / 1000) + "s."));
            return;
        }
        event.setCancelled(true);
        long cooldown = Math.max(0, getConfig().getLong("effects.fire.snowball-cooldown-seconds", 15));
        cooldowns.put(key, now + cooldown * 1000L);
        org.bukkit.inventory.EquipmentSlot hand = event.getHand();
        ItemStack held = hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        if (held.getAmount() > 1) held.setAmount(held.getAmount() - 1);
        else {
            if (hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(null);
            else player.getInventory().setItemInMainHand(null);
        }
        org.bukkit.entity.SmallFireball fireball = player.getWorld().spawn(player.getEyeLocation(), org.bukkit.entity.SmallFireball.class);
        fireball.setShooter(player);
        fireball.setVelocity(player.getLocation().getDirection().multiply(2.0));
        fireball.setIsIncendiary(true);
        fireball.setYield(1.0f);
        player.playSound(player.getLocation(), "entity.blaze.shoot", 0.8f, 1.2f);
    }

    public void invisibilityRangedHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter) ||
                !(event.getHitEntity() instanceof org.bukkit.entity.LivingEntity target) || disabled(shooter)) return;
        if (target instanceof Player victim && trustManager.isTrusted(shooter.getUniqueId(), victim.getUniqueId())) return;
        List<String> active = Arrays.asList(slots(shooter.getUniqueId()));
        if (active.contains("invisibility")) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                    Math.max(1, getConfig().getInt("effects.invisibility.blindness-duration-ticks", 200)), 0, true, false, true));
        }
        if (active.contains("fire")) {
            boolean sparkActive = sparkEnds.getOrDefault(shooter.getUniqueId() + ":fire", 0L) > System.currentTimeMillis();
            int burnTicks = getConfig().getInt(sparkActive ? "effects.fire.eternal-ticks" : "effects.fire.ignite-ticks", sparkActive ? 32767 : 100);
            target.setFireTicks(Math.max(target.getFireTicks(), burnTicks));
            target.getWorld().spawnParticle(org.bukkit.Particle.FLAME, target.getLocation().add(0, 1, 0), 8, 0.25, 0.35, 0.25, 0.02);
        }
        if (active.contains("frost")) {
            target.setFreezeTicks(Math.max(target.getFreezeTicks(), getConfig().getInt("effects.frost.freeze-ticks", 600)));
            target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,
                    Math.max(1, getConfig().getInt("effects.frost.mining-fatigue-duration-ticks", 200)),
                    Math.max(0, getConfig().getInt("effects.frost.mining-fatigue-amplifier", 1)), true, false, true));
        }
        if (active.contains("ender") && target instanceof Player victim) applyEnderCurse(shooter, victim);
    }

    public void onProjectileShoot(org.bukkit.event.entity.EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter) || disabled(shooter)) return;
        List<String> active = Arrays.asList(slots(shooter.getUniqueId()));
        if (active.contains("speed")) {
            double multiplier = getConfig().getDouble("effects.speed.projectile-speed-multiplier", 1.2);
            event.getProjectile().setVelocity(event.getProjectile().getVelocity().multiply(multiplier));
        }
        if (active.contains("strength") && event.getProjectile() instanceof org.bukkit.entity.AbstractArrow arrow) {
            arrow.setPierceLevel(4);
        }
    }

    public void infuseTridentEffects(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Trident trident) || !(trident.getShooter() instanceof Player player) || disabled(player)) return;
        if (Arrays.asList(slots(player.getUniqueId())).contains("thunder")) {
            String key = player.getUniqueId() + ":thunder-trident";
            long now = System.currentTimeMillis();
            if (cooldowns.getOrDefault(key, 0L) <= now) {
                cooldowns.put(key, now + Math.max(0, getConfig().getLong("effects.thunder.trident-cooldown-seconds", 10)) * 1000L);
                org.bukkit.Location hitLocation = event.getHitEntity() != null ? event.getHitEntity().getLocation() : event.getHitBlock() != null ? event.getHitBlock().getLocation() : trident.getLocation();
                player.getWorld().strikeLightning(hitLocation);
            }
        }
        if (Arrays.asList(slots(player.getUniqueId())).contains("ocean") &&
                event.getHitEntity() instanceof org.bukkit.entity.LivingEntity target &&
                !(target instanceof Player trustedTarget && trustManager.isTrusted(player.getUniqueId(), trustedTarget.getUniqueId()))) {
            String pullKey = player.getUniqueId() + ":ocean-trident";
            long now = System.currentTimeMillis();
            if (cooldowns.getOrDefault(pullKey, 0L) <= now) {
                double distance = player.getLocation().distance(target.getLocation());
                double pull = (Math.min(getConfig().getDouble("effects.ocean.trident-pull-cap", 16.0), distance)
                        - getConfig().getDouble("effects.ocean.trident-pull-offset", 1.5))
                        * getConfig().getDouble("effects.ocean.trident-pull-coefficient", 0.27);
                if (pull > 0) {
                    cooldowns.put(pullKey, now + Math.max(0, getConfig().getLong("effects.ocean.trident-pull-cooldown-seconds", 5)) * 1000L);
                    player.setCooldown(Material.TRIDENT, Math.max(1, (int) (getConfig().getLong("effects.ocean.trident-pull-cooldown-seconds", 5) * 20L)));
                    target.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (!target.isValid() || target.isDead()) return;
                        org.bukkit.util.Vector direction = player.getLocation().toVector().subtract(target.getLocation().toVector());
                        if (direction.lengthSquared() > 0.001) {
                            target.setVelocity(new org.bukkit.util.Vector(0, 0.2, 0).add(direction.normalize().multiply(pull)));
                        }
                        target.getWorld().spawnParticle(org.bukkit.Particle.BUBBLE,
                                target.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.02);
                        target.getWorld().playSound(target.getLocation(), "block.bubble_column.upwards_inside", 1.0f, 1.0f);
                        player.playSound(player.getLocation(), "block.bubble_column.upwards_inside", 1.0f, 1.0f);
                    }, 1L);
                }
            }
        }
    }

    public void convertDragonBreath(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || event.getItem() == null || event.getItem().getType() != Material.DRAGON_BREATH) return;
        Player player = event.getPlayer();
        if (disabled(player) || !Arrays.asList(slots(player.getUniqueId())).contains("ender")) return;
        String key = player.getUniqueId() + ":ender-dragon-fireball";
        long now = System.currentTimeMillis();
        long until = cooldowns.getOrDefault(key, 0L);
        if (until > now) {
            event.setCancelled(true);
            player.sendMessage(mini.deserialize("<red>Dragon breath is recharging for " + ((until - now + 999) / 1000) + "s."));
            return;
        }
        event.setCancelled(true);
        ItemStack held = event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        if (held.getAmount() > 1) held.setAmount(held.getAmount() - 1);
        else if (event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(null);
        else player.getInventory().setItemInMainHand(null);
        org.bukkit.entity.DragonFireball fireball = player.getWorld().spawn(player.getEyeLocation(), org.bukkit.entity.DragonFireball.class);
        fireball.setShooter(player);
        fireball.setVelocity(player.getLocation().getDirection().multiply(
                getConfig().getDouble("effects.ender.dragon-fireball-speed", 2.0)));
        cooldowns.put(key, now + Math.max(0, getConfig().getLong("effects.ender.dragon-fireball-cooldown-seconds", 10)) * 1000L);
        player.playSound(player.getLocation(), "entity.ender_dragon.shoot", 1.0f, 1.0f);
    }

    public void applyEnderCurse(Player owner, Player target) {
        if (owner.equals(target) || trustManager.isTrusted(owner.getUniqueId(), target.getUniqueId())) return;
        UUID targetId = target.getUniqueId();
        long duration = Math.max(1, getConfig().getLong("effects.ender.curse-duration-seconds", 15)) * 1000L;
        long expiresAt = System.currentTimeMillis() + duration;
        enderCurses.put(targetId, expiresAt);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (enderCurses.getOrDefault(targetId, 0L) <= System.currentTimeMillis()) enderCurses.remove(targetId);
        }, Math.max(1, duration / 50L));
    }

    public void shareCursedDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) return;
        if (enderDamageShareGuard.contains(attacker.getUniqueId()) || disabled(attacker)) return;
        if (!Arrays.asList(slots(attacker.getUniqueId())).contains("ender")) return;
        if (enderCurses.getOrDefault(victim.getUniqueId(), 0L) <= System.currentTimeMillis()) {
            enderCurses.remove(victim.getUniqueId());
            return;
        }
        double damage = event.getFinalDamage();
        if (damage <= 0) return;
        enderDamageShareGuard.add(attacker.getUniqueId());
        try {
            for (Player cursed : Bukkit.getOnlinePlayers()) {
                if (cursed.equals(victim) || cursed.equals(attacker) || disabled(cursed)) continue;
                if (enderCurses.getOrDefault(cursed.getUniqueId(), 0L) <= System.currentTimeMillis()) {
                    enderCurses.remove(cursed.getUniqueId());
                    continue;
                }
                cursed.damage(damage, attacker);
            }
        } finally {
            enderDamageShareGuard.remove(attacker.getUniqueId());
        }
    }

    private boolean isEmeraldConsumable(Material material) {
        return switch (material) {
            case GOLDEN_CARROT, GOLDEN_APPLE, ENCHANTED_GOLDEN_APPLE, COOKIE, MELON_SLICE,
                    PUMPKIN_PIE, BEETROOT_SOUP, MUSHROOM_STEW -> true;
            default -> false;
        };
    }

    public void prepareRegenerationFood(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || event.getItem() == null || !event.getItem().getType().isEdible()) return;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (disabled(player) || !Arrays.asList(slots(id)).contains("regeneration") || player.getFoodLevel() < 20) return;
        regenFoodWindow.add(id);
        player.setFoodLevel(19);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            regenFoodWindow.remove(id);
            Player online = Bukkit.getPlayer(id);
            if (online != null && Arrays.asList(slots(id)).contains("regeneration")) online.setFoodLevel(20);
        }, 2L);
    }

    public void onFoodLevelChange(org.bukkit.event.entity.FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || disabled(player)) return;
        UUID id = player.getUniqueId();
        if (!Arrays.asList(slots(id)).contains("regeneration") || regenFoodWindow.contains(id)) return;
        event.setFoodLevel(20);
    }

    public void onEffectPotionConsume(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;
        String encoded = item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer().get(effectKey, PersistentDataType.STRING) : null;
        if (encoded == null) {
            if (disabled(player)) return;
            String[] activeSlots = slots(player.getUniqueId());
            if (Arrays.asList(activeSlots).contains("heart")) {
                boolean goldenApple = item.getType() == Material.ENCHANTED_GOLDEN_APPLE;
                int amplifier = Math.max(0, getConfig().getInt(goldenApple ? "effects.heart.egap-absorption-tier" : "effects.heart.food-absorption-tier", goldenApple ? 4 : 0));
                int duration = getConfig().getInt(goldenApple ? "effects.heart.egap-absorption-duration-ticks" : "effects.heart.food-absorption-duration-ticks", 1200);
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, amplifier, true, false, true));
            }
            if (Arrays.asList(activeSlots).contains("regeneration") && item.getType().isEdible()) {
                float saturation = (float) getConfig().getDouble("effects.regeneration.food-saturation", 3.0);
                player.setSaturation(Math.min(20.0f, player.getSaturation() + saturation));
                player.setFoodLevel(20);
            }
            if (Arrays.asList(activeSlots).contains("emerald") && isEmeraldConsumable(item.getType())) {
                String key = player.getUniqueId() + ":emerald";
                boolean sparkActive = sparkEnds.getOrDefault(key, 0L) > System.currentTimeMillis();
                double chance = getConfig().getDouble(sparkActive ? "effects.emerald.spark-no-consume-chance" : "effects.emerald.no-consume-chance", sparkActive ? 0.75 : 0.5);
                if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < chance) {
                    ItemStack refund = item.clone();
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(refund);
                        leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
                    }, 1L);
                }
            }
            return;
        }
        event.setCancelled(true);
        String[] parts = encoded.split(":");
        equipEffect(player, parts[0], parts.length > 1 && parts[1].equalsIgnoreCase("augmented"), true);
    }

    private void applySpeedStack(Player player, double change) {
        UUID id = player.getUniqueId();
        var attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null) return;
        double minimum = getConfig().getDouble("effects.speed.max-penalty", -0.5);
        double maximum = getConfig().getDouble("effects.speed.stack-cap", 1.0);
        double amount = Math.max(minimum, Math.min(maximum, speedStacks.getOrDefault(id, 0.0) + change));
        speedStacks.put(id, amount);
        org.bukkit.attribute.AttributeModifier previous = speedModifiers.remove(id);
        if (previous != null) attribute.removeModifier(previous);
        if (Math.abs(amount) < 0.0001) { speedStacks.remove(id); return; }
        org.bukkit.attribute.AttributeModifier modifier = new org.bukkit.attribute.AttributeModifier(
                new NamespacedKey(this, "speed_stack_" + id), amount, org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        attribute.addModifier(modifier);
        speedModifiers.put(id, modifier);
    }

    private void addHasteStack(Player player, double change) {
        UUID id = player.getUniqueId();
        var attribute = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attribute == null) return;
        org.bukkit.attribute.AttributeModifier previous = hasteModifiers.remove(id);
        if (previous != null) attribute.removeModifier(previous);
        double stack = Math.max(0.0, Math.min(1.0, hasteStacks.getOrDefault(id, 0.0) + change));
        if (stack <= 0.0001) {
            hasteStacks.remove(id);
            return;
        }
        hasteStacks.put(id, stack);
        org.bukkit.attribute.AttributeModifier modifier = new org.bukkit.attribute.AttributeModifier(
                new NamespacedKey(this, "haste_stack_" + id), stack,
                org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        attribute.addModifier(modifier);
        hasteModifiers.put(id, modifier);
    }

    private void decayHasteStack(Player player) {
        double stack = hasteStacks.getOrDefault(player.getUniqueId(), 0.0);
        if (stack <= 0) return;
        addHasteStack(player, getConfig().getDouble("effects.haste.decay-amount", -0.1));
    }

    private void updateHasteBreakSpeed(Player player, boolean enabled) {
        UUID id = player.getUniqueId();
        var attribute = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attribute == null) return;
        org.bukkit.attribute.AttributeModifier previous = hasteBreakSpeedModifiers.remove(id);
        if (previous != null) attribute.removeModifier(previous);
        if (!enabled) return;
        double multiplier = Math.max(1.0, getConfig().getDouble("effects.haste.break-speed-multiplier", 1.5));
        org.bukkit.attribute.AttributeModifier modifier = new org.bukkit.attribute.AttributeModifier(
                new NamespacedKey(this, "haste_break_speed_" + id), multiplier - 1.0,
                org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        attribute.addModifier(modifier);
        hasteBreakSpeedModifiers.put(id, modifier);
    }

    public void activateHasteSpark(Player player) {
        UUID id = player.getUniqueId();
        var attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        var breakSpeed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attackSpeed != null) {
            org.bukkit.attribute.AttributeModifier old = hasteSparkAttackSpeedModifiers.remove(id);
            if (old != null) attackSpeed.removeModifier(old);
            org.bukkit.attribute.AttributeModifier modifier = new org.bukkit.attribute.AttributeModifier(
                    new NamespacedKey(this, "haste_spark_attack_speed_" + id),
                    getConfig().getDouble("effects.haste.spark-atkspd-increase", 0.5),
                    org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            attackSpeed.addModifier(modifier);
            hasteSparkAttackSpeedModifiers.put(id, modifier);
        }
        if (breakSpeed != null) {
            org.bukkit.attribute.AttributeModifier old = hasteSparkBreakSpeedModifiers.remove(id);
            if (old != null) breakSpeed.removeModifier(old);
            org.bukkit.attribute.AttributeModifier modifier = new org.bukkit.attribute.AttributeModifier(
                    new NamespacedKey(this, "haste_spark_break_speed_" + id),
                    getConfig().getDouble("effects.haste.spark-break-speed-multiplier", 1024.0),
                    org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            breakSpeed.addModifier(modifier);
            hasteSparkBreakSpeedModifiers.put(id, modifier);
        }
    }

    private void clearHasteSpark(Player player) {
        UUID id = player.getUniqueId();
        var attackSpeed = player.getAttribute(Attribute.ATTACK_SPEED);
        var breakSpeed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        org.bukkit.attribute.AttributeModifier attack = hasteSparkAttackSpeedModifiers.remove(id);
        org.bukkit.attribute.AttributeModifier breaking = hasteSparkBreakSpeedModifiers.remove(id);
        if (attackSpeed != null && attack != null) attackSpeed.removeModifier(attack);
        if (breakSpeed != null && breaking != null) breakSpeed.removeModifier(breaking);
    }

    private void transferExperience(Player from, Player to, int amount) {
        if (amount <= 0) return;
        from.giveExp(-amount);
        to.giveExp(amount);
    }

    public void onExperienceGain(org.bukkit.event.player.PlayerExpChangeEvent event) {
        if (disabled(event.getPlayer())) return;
        String[] activeSlots = slots(event.getPlayer().getUniqueId());
        if (!Arrays.asList(activeSlots).contains("emerald")) return;
        boolean sparkActive = sparkEnds.getOrDefault(event.getPlayer().getUniqueId() + ":emerald", 0L) > System.currentTimeMillis();
        double extraMultiplier = getConfig().getDouble("effects.emerald.xp-gain-per-hit", 1.0);
        if (sparkActive) extraMultiplier += getConfig().getDouble("effects.emerald.spark-xp-gain", 2.0);
        event.setAmount(Math.max(0, (int) Math.round(event.getAmount() * (1.0 + extraMultiplier))));
    }

    private boolean equipEffect(Player player, String effect, boolean isAugmented, boolean consumeItem) {
        return equipEffect(player, effect, isAugmented, consumeItem, null);
    }

    private boolean equipEffect(Player player, String effect, boolean isAugmented, boolean consumeItem, Integer forcedSlot) {
        if (disabled(player)) {
            player.sendMessage(mini.deserialize("<red>Infuse effects are disabled in this world."));
            return false;
        }
        if (consumeItem && !hasEffectItem(player, effect, isAugmented)) {
            player.sendMessage(mini.deserialize("<red>You need to have that effect item in your inventory."));
            return false;
        }
        int target = forcedSlot == null ? (player.isSneaking() ? 1 : 0) : Math.max(0, Math.min(1, forcedSlot));
        if (getConfig().getBoolean("settings.lock-effects", false)) {
            if (getConfig().getStringList("settings.primary-effects").contains(effect)) target = 0;
            else if (getConfig().getStringList("settings.secondary-effects").contains(effect)) target = 1;
        }
        String[] current = slots(player.getUniqueId());
        boolean[] currentAugmented = augmented.computeIfAbsent(player.getUniqueId(), ignored -> new boolean[]{false, false});
        if (!getConfig().getBoolean("settings.allow-same-effects", false) && effect.equals(current[1 - target])) {
            player.sendMessage(mini.deserialize("<red>You already have this effect in your other slot."));
            return false;
        }
        if (effect.equals(current[target])) {
            player.sendMessage(mini.deserialize("<red>You already have this effect equipped in that slot."));
            return false;
        }
        if (!current[target].isBlank()) {
            String currentKey = player.getUniqueId() + ":" + current[target];
            long activeUntil = sparkEnds.getOrDefault(currentKey, 0L);
            long cooldownUntil = cooldowns.getOrDefault(currentKey, 0L);
            if (activeUntil > System.currentTimeMillis() || cooldownUntil > System.currentTimeMillis()) {
                player.sendMessage(mini.deserialize("<red>You cannot replace that effect until its cycle finishes."));
                return false;
            }
        }
        if (consumeItem && !removeOneEffectItem(player, effect, isAugmented)) return false;
        if (!current[target].isBlank()) {
            ItemStack oldItem = createEffectItem(current[target], currentAugmented[target]);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(oldItem);
            leftovers.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        current[target] = effect;
        currentAugmented[target] = isAugmented;
        addHistory(player.getUniqueId(), target, effect, isAugmented);
        saveData();
        player.sendMessage(mini.deserialize("<green>Applied <white>" + (isAugmented ? "Augmented " : "") + title(effect) + "<green> to your " + (target == 0 ? "primary" : "secondary") + " slot."));
        player.playSound(player.getLocation(), "block.enchantment_table.use", 1.0f, target == 0 ? 1.1f : 1.4f);
        return true;
    }

    private boolean hasEffectItem(Player player, String effect, boolean isAugmented) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            String encoded = item.getItemMeta().getPersistentDataContainer().get(effectKey, PersistentDataType.STRING);
            if (encoded == null) continue;
            String[] parts = encoded.split(":");
            if (parts[0].equals(effect) && (parts.length > 1 && parts[1].equalsIgnoreCase("augmented")) == isAugmented) return true;
        }
        return false;
    }

    private boolean removeOneEffectItem(Player player, String effect, boolean isAugmented) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || !item.hasItemMeta()) continue;
            String encoded = item.getItemMeta().getPersistentDataContainer().get(effectKey, PersistentDataType.STRING);
            if (encoded == null) continue;
            String[] parts = encoded.split(":");
            if (!parts[0].equals(effect) || (parts.length > 1 && parts[1].equalsIgnoreCase("augmented")) != isAugmented) continue;
            if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
            else player.getInventory().setItem(slot, null);
            return true;
        }
        return false;
    }

    public void inventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = mini.serialize(e.getView().title());
        if (title.contains("Infuse") && !title.contains("Choose an Effect")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 22) { p.closeInventory(); return; }
            if (e.getRawSlot() == 13) { openEffects(p, 0); return; }
            if (e.getRawSlot() == 11) { selectionMode.put(p.getUniqueId(), 0); openEffects(p, 0); return; }
            if (e.getRawSlot() == 15) { selectionMode.put(p.getUniqueId(), 1); openEffects(p, 1); return; }
        } else if (title.contains("Choose an Effect")) {
            e.setCancelled(true);
            if (e.getRawSlot() == 49) { p.closeInventory(); return; }
            if (e.getRawSlot() == 45) { openMain(p); return; }
            ItemStack clicked=e.getCurrentItem(); if(clicked==null) return;
            String encoded=clicked.getItemMeta().getPersistentDataContainer().get(effectKey,PersistentDataType.STRING);
            if(encoded==null) return;
            String effect=encoded.split(":")[0];
            equipEffect(p, effect, encoded.endsWith(":augmented"), true, selectionMode.getOrDefault(p.getUniqueId(), 0));
            openMain(p);
        }
    }

    private void spark(Player p) {
        spark(p, "primary");
    }
    private int cooldownDivisor(UUID playerId, String effect) {
        return augmentationManager.cooldownDivisor(
                slots(playerId), augmented.getOrDefault(playerId, new boolean[]{false, false}), effect);
    }

    private void spark(Player p, String requestedSlot) {
        String[] s=slots(p.getUniqueId());
        String effect = requestedSlot == null ? (!s[1].isBlank()?s[1]:s[0]) : s[requestedSlot.equalsIgnoreCase("secondary") ? 1 : 0];
        if(effect.isBlank()) { p.sendMessage(mini.deserialize("<red>You have no Infuse effect equipped.")); return; }
        if(disabled(p)) return;
        long now = System.currentTimeMillis();
        String key = p.getUniqueId() + ":" + effect;
        long activeUntil = sparkEnds.getOrDefault(key, 0L);
        long cooldownUntil = cooldowns.getOrDefault(key, 0L);
        if (activeUntil > now) {
            p.sendMessage(mini.deserialize("<red>That Spark is already active for " + ((activeUntil - now + 999) / 1000) + "s."));
            return;
        }
        if (cooldownUntil > now) {
            p.sendMessage(mini.deserialize("<red>That Spark is on cooldown for " + ((cooldownUntil - now + 999) / 1000) + "s."));
            return;
        }
        long baseCooldownSeconds = Math.max(0, getConfig().getLong("effects." + effect + ".cooldown-seconds", 60));
        long cooldownSeconds = baseCooldownSeconds / cooldownDivisor(p.getUniqueId(), effect);
        int durationTicks = (int) Math.max(1, getConfig().getLong("effects." + effect + ".spark-seconds", 15) * 20L);
        cooldowns.remove(key);
        sparkEnds.put(key, now + durationTicks * 50L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (sparkEnds.getOrDefault(key, 0L) <= System.currentTimeMillis()) {
                sparkEnds.remove(key);
                if (effect.equals("haste")) clearHasteSpark(p);
                if (effect.equals("ender") && enderSparkCooldownReset.remove(p.getUniqueId())) cooldowns.remove(key);
                else cooldowns.put(key, System.currentTimeMillis() + cooldownSeconds * 1000L);
            }
        }, durationTicks);
        p.playSound(p.getLocation(), getConfig().getString("settings.use-sound", "custom.activate"), 1.0f, 1.0f);
        sparkAbilityManager.activate(p, effect, durationTicks);
        p.sendMessage(mini.deserialize("<#A855F7>"+title(effect)+" Spark activated."));
    }

    private void addWindCharge(Player player) {
        UUID id = player.getUniqueId();
        int maximum = Math.max(0, getConfig().getInt("effects.feather.max-wind-charge", 3));
        List<WindChargeVisual> visuals = windChargeVisuals.computeIfAbsent(id, ignored -> new ArrayList<>());
        if (visuals.size() >= maximum) return;

        ItemStack innerItem = windChargeDisplayItem(414900);
        ItemStack outerItem = windChargeDisplayItem(414901);
        org.bukkit.entity.ItemDisplay inner = player.getWorld().spawn(player.getEyeLocation(), org.bukkit.entity.ItemDisplay.class);
        org.bukkit.entity.ItemDisplay outer = player.getWorld().spawn(player.getEyeLocation(), org.bukkit.entity.ItemDisplay.class);
        inner.setItemStack(innerItem);
        outer.setItemStack(outerItem);
        inner.setTeleportDuration(1);
        outer.setTeleportDuration(1);
        inner.setPersistent(false);
        outer.setPersistent(false);
        inner.setInvulnerable(true);
        outer.setInvulnerable(true);

        WindChargeVisual visual = new WindChargeVisual(inner, outer);
        visuals.add(visual);
        windCharges.put(id, visuals.size());
        int index = visuals.size() - 1;
        final long[] age = {0L};
        visual.task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!player.isOnline() || !inner.isValid() || !outer.isValid() ||
                    !Arrays.asList(slots(id)).contains("feather")) {
                disposeWindChargeVisual(id, visual);
                return;
            }
            double angle = age[0] * 0.12 + index * (Math.PI * 2.0 / Math.max(1, maximum));
            org.bukkit.Location eye = player.getEyeLocation();
            inner.teleport(eye.clone().add(Math.cos(angle) * 0.35, Math.sin(angle * 0.5) * 0.12, Math.sin(angle) * 0.35));
            outer.teleport(eye.clone().add(Math.cos(-angle) * 0.55, Math.sin(angle * 0.5 + 1.2) * 0.18, Math.sin(-angle) * 0.55));
            age[0]++;
        }, 0L, 1L);
    }

    private ItemStack windChargeDisplayItem(int customModelData) {
        ItemStack item = new ItemStack(Material.WIND_CHARGE);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(customModelData);
            meta.displayName(net.kyori.adventure.text.Component.empty());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void removeWindChargeVisual(Player player) {
        UUID id = player.getUniqueId();
        List<WindChargeVisual> visuals = windChargeVisuals.get(id);
        if (visuals != null && !visuals.isEmpty()) {
            disposeWindChargeVisual(id, visuals.get(0));
        } else {
            windCharges.put(id, Math.max(0, windCharges.getOrDefault(id, 0) - 1));
        }
    }

    private void disposeWindChargeVisual(UUID ownerId, WindChargeVisual visual) {
        if (visual.task != null) visual.task.cancel();
        if (visual.inner.isValid()) visual.inner.remove();
        if (visual.outer.isValid()) visual.outer.remove();
        List<WindChargeVisual> visuals = windChargeVisuals.get(ownerId);
        if (visuals != null) {
            visuals.remove(visual);
            if (visuals.isEmpty()) windChargeVisuals.remove(ownerId);
            windCharges.put(ownerId, visuals.size());
        }
    }

    private void clearWindChargeVisuals(UUID ownerId) {
        List<WindChargeVisual> visuals = windChargeVisuals.remove(ownerId);
        if (visuals != null) {
            for (WindChargeVisual visual : new ArrayList<>(visuals)) {
                if (visual.task != null) visual.task.cancel();
                if (visual.inner.isValid()) visual.inner.remove();
                if (visual.outer.isValid()) visual.outer.remove();
            }
        }
        windCharges.remove(ownerId);
    }

    private static final class WindChargeVisual {
        private final org.bukkit.entity.ItemDisplay inner;
        private final org.bukkit.entity.ItemDisplay outer;
        private BukkitTask task;

        private WindChargeVisual(org.bukkit.entity.ItemDisplay inner, org.bukkit.entity.ItemDisplay outer) {
            this.inner = inner;
            this.outer = outer;
        }
    }

    public void activateSpeedDash(Player player, int durationTicks) {
        org.bukkit.util.Vector direction = player.getLocation().getDirection().setY(0).normalize();
        if (direction.lengthSquared() < 0.001) direction = new org.bukkit.util.Vector(0, 0, 1);
        final org.bukkit.util.Vector dashDirection = direction.clone();
        int iterations = Math.max(1, getConfig().getInt("effects.speed.dash-iteration", 14));
        double initialPower = getConfig().getDouble("effects.speed.dash-power", 2.6);
        double damping = Math.max(0.0, Math.min(1.0, getConfig().getDouble("effects.speed.dash-damping", 0.6)));
        double radius = Math.max(0.1, getConfig().getDouble("effects.speed.dash-effect-radius", 1.5));
        double damage = Math.max(0.0, getConfig().getDouble("effects.speed.dash-true-damage", 2.0));
        Set<UUID> alreadyHit = new HashSet<>();
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, 1, true, false, true));
        new org.bukkit.scheduler.BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (!player.isOnline() || disabled(player) || tick >= iterations) {
                    cancel();
                    return;
                }
                double power = initialPower * Math.pow(damping, tick);
                player.setVelocity(dashDirection.clone().multiply(power).setY(Math.max(0.1, player.getVelocity().getY())));
                for (org.bukkit.entity.Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                    if (!(entity instanceof org.bukkit.entity.LivingEntity living) || living.equals(player) || living.isDead()) continue;
                    if (living instanceof Player target && trustManager.isTrusted(player.getUniqueId(), target.getUniqueId())) continue;
                    if (!alreadyHit.add(living.getUniqueId())) continue;
                    if (getConfig().getBoolean("settings.allow-true-damage", true) && damage > 0) living.damage(damage, player);
                    player.getWorld().spawnParticle(org.bukkit.Particle.SWEEP_ATTACK,
                            living.getLocation().add(0, 1, 0), 4, 0.3, 0.3, 0.3, 0);
                }
                player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, player.getLocation(), 6, 0.25, 0.2, 0.25, 0.02);
                tick++;
            }
        }.runTaskTimer(this, 0L, Math.max(1, getConfig().getInt("effects.speed.dash-interval-ticks", 1)));
    }

    public void activateOceanSpark(Player caster) {
        int iterations = Math.max(1, getConfig().getInt("effects.ocean.pull-iterations", 50));
        int interval = Math.max(1, getConfig().getInt("effects.ocean.pull-interval-ticks", 2));
        double radius = Math.max(0.1, getConfig().getDouble("effects.ocean.pull-radius", 6.0));
        double strength = Math.max(0.0, getConfig().getDouble("effects.ocean.pull-strength", 0.1));
        final int[] elapsed = {0};
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            if (!caster.isOnline() || disabled(caster) || elapsed[0] >= iterations) { task.cancel(); return; }
            elapsed[0]++;
            for (Player target : caster.getWorld().getPlayers()) {
                if (target.equals(caster) || disabled(target) || trustManager.isTrusted(caster.getUniqueId(), target.getUniqueId())) continue;
                if (target.getLocation().distanceSquared(caster.getLocation()) > radius * radius) continue;
                org.bukkit.util.Vector direction = caster.getLocation().toVector().subtract(target.getLocation().toVector());
                if (direction.lengthSquared() < 0.001) continue;
                target.setVelocity(target.getVelocity().add(direction.normalize().multiply(strength)));
            }
            if (caster.getLocation().getBlock().isLiquid()) caster.setRemainingAir(caster.getMaximumAir());
            caster.getWorld().spawnParticle(org.bukkit.Particle.BUBBLE_COLUMN_UP,
                    caster.getLocation().add(0, 0.5, 0), 6, radius * 0.25, 0.4, radius * 0.25, 0.02);
            if (elapsed[0] >= iterations) task.cancel();
        }, 0L, interval);
    }

    public void activateThunderSpark(Player caster) {
        double maximumRadius = 20.0;
        List<org.bukkit.entity.LivingEntity> nearby = new ArrayList<>();
        for (org.bukkit.entity.Entity entity : caster.getNearbyEntities(maximumRadius, maximumRadius, maximumRadius)) {
            if (!(entity instanceof org.bukkit.entity.LivingEntity living) || living.equals(caster) || living.isDead()) continue;
            if (living instanceof Player target && trustManager.isTrusted(caster.getUniqueId(), target.getUniqueId())) continue;
            nearby.add(living);
        }
        double radius = Math.min(nearby.size() * 3.0 + 3.0, maximumRadius);
        double radiusSquared = radius * radius;
        List<org.bukkit.entity.LivingEntity> targets = nearby.stream()
                .filter(target -> target.getLocation().distanceSquared(caster.getLocation()) <= radiusSquared)
                .toList();
        caster.getWorld().spawnParticle(org.bukkit.Particle.FLAME, caster.getLocation().add(0, 1, 0),
                1, radius, 0.1, radius, 0.05);
        int strikes = Math.max(1, getConfig().getInt("effects.thunder.strike-time-max", 5));
        double damage = Math.max(0.0, getConfig().getDouble("effects.thunder.true-damage", 1.0));
        for (int strike = 0; strike < strikes; strike++) {
            long delay = strike * 20L;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!caster.isOnline()) return;
                lightningChainAttackers.add(caster.getUniqueId());
                try {
                    for (org.bukkit.entity.LivingEntity target : targets) {
                        if (target.isDead() || !target.isValid()) continue;
                        caster.getWorld().strikeLightningEffect(target.getLocation());
                        if (getConfig().getBoolean("settings.allow-true-damage", true) && damage > 0) {
                            target.damage(damage, caster);
                        }
                    }
                } finally {
                    lightningChainAttackers.remove(caster.getUniqueId());
                }
            }, delay);
        }
    }

    public void activateFeatherShockwave(Player player) {
        final int[] ticks = {0};
        final double[] maxFallDistance = {0.0};
        final boolean[] leftGround = {false};
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            if (!player.isOnline() || ticks[0] > 100) { task.cancel(); return; }
            ticks[0]++;
            maxFallDistance[0] = Math.max(maxFallDistance[0], player.getFallDistance());
            if (!player.isOnGround()) leftGround[0] = true;
            if (!leftGround[0] || !player.isOnGround() || ticks[0] < 4) return;
            task.cancel();
            if (maxFallDistance[0] <= getConfig().getDouble("effects.feather.fall-trigger-threshold", 4.0)) return;
            double radius = Math.max(getConfig().getDouble("effects.feather.min-shockwave-radius", 2.0),
                    Math.min(getConfig().getDouble("effects.feather.max-shockwave-radius", 8.0), maxFallDistance[0] / 4.0));
            org.bukkit.Location impact = player.getLocation();
            player.playSound(impact, "item.mace.smash_ground_heavy", 1.0f, 1.0f);
            player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, impact, 60, radius / 3.0, 0.25, radius / 3.0, 0.05);
            for (org.bukkit.entity.Entity entity : player.getNearbyEntities(radius, radius, radius)) {
                if (!(entity instanceof org.bukkit.entity.LivingEntity living) || living.equals(player)) continue;
                if (entity instanceof Player target && trustManager.getTrusted(player.getUniqueId()).contains(target.getUniqueId())) continue;
                living.damage(getConfig().getDouble("effects.feather.shockwave-true-damage", 2.0), player);
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,
                        getConfig().getInt("effects.feather.slow-falling-duration-ticks", 100), 0, true, false, true));
            }
            if (!player.isSneaking()) {
                org.bukkit.util.Vector direction = player.getLocation().getDirection();
                player.setVelocity(direction.multiply(radius / Math.max(1.0, getConfig().getDouble("effects.feather.bounce-coefficient", 4.0))));
            }
        }, 1L, 1L);
    }

    public void activateRegenerationSpark(Player caster) {
        int iterations = Math.max(1, getConfig().getInt("effects.regeneration.spark-heal-iteration", 15));
        double totalHealing = recentDamageAmount(caster.getUniqueId()) * getConfig().getDouble("effects.regeneration.spark-heal-percentage", 0.2);
        double healingPerTick = totalHealing / iterations;
        final int[] completed = {0};
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            if (!caster.isOnline() || completed[0] >= iterations) { task.cancel(); return; }
            healTrustedNearby(caster, getConfig().getDouble("effects.regeneration.spark-heal-radius", 8), healingPerTick);
            caster.getWorld().spawnParticle(org.bukkit.Particle.HEART, caster.getLocation().add(0, 1, 0), 3, 0.4, 0.5, 0.4, 0);
            completed[0]++;
            if (completed[0] >= iterations) task.cancel();
        }, 0L, 20L);
    }

    private void rollFirstJoin(Player player, String selectedEffect) {
        if (!player.isOnline()) return;
        if (!getConfig().getBoolean("settings.do-roll-animation", true)) {
            player.sendTitle(title(selectedEffect) + " Effect", "Your first Infuse", 10, 40, 10);
            setFreePrimaryEffect(player, selectedEffect);
            return;
        }
        player.playSound(player.getLocation(), "block.respawn_anchor.charge", 0.7f, 0.5f);
        player.playSound(player.getLocation(), "block.beacon.activate", 0.7f, 2.0f);
        List<String> choices = EFFECTS.stream().filter(effect -> !effect.equals("earth") && !effect.equals("ender")).toList();
        final int[] ticks = {0};
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            if (!player.isOnline()) { task.cancel(); return; }
            int index = (ticks[0] / 2) % choices.size();
            player.sendTitle(title(choices.get(index)) + " Effect", "Rolling your first Infuse...", 0, 2, 0);
            if (ticks[0] % 10 == 0) player.playSound(player.getLocation(), "block.respawn_anchor.charge", 0.3f, 0.8f + (ticks[0] / 100.0f));
            ticks[0] += 2;
            if (ticks[0] >= 100) {
                task.cancel();
                setFreePrimaryEffect(player, selectedEffect);
                player.sendTitle(title(selectedEffect) + " Effect", "Your first Infuse", 10, 40, 10);
                player.playSound(player.getLocation(), "entity.player.levelup", 0.8f, 1.2f);
            }
        }, 0L, 2L);
    }

    private void setFreePrimaryEffect(Player player, String effect) {
        String[] current = slots(player.getUniqueId());
        boolean[] isAugmented = augmented.computeIfAbsent(player.getUniqueId(), ignored -> new boolean[]{false, false});
        current[0] = effect;
        isAugmented[0] = false;
        addHistory(player.getUniqueId(), 0, effect, false);
        saveData();
    }

    public String placeholderValue(OfflinePlayer offlinePlayer, String identifier) {
        UUID playerId = offlinePlayer.getUniqueId();
        String key = identifier.toLowerCase(Locale.ROOT);
        int slot = key.endsWith("_secondary") ? 1 : 0;
        String[] current = slots(playerId);
        String effect = current[slot];
        boolean[] isAugmented = augmented.getOrDefault(playerId, new boolean[]{false, false});
        switch (key) {
            case "effect_name_primary", "effect_name_secondary" -> {
                return effect == null || effect.isBlank() ? "" : title(effect);
            }
            case "effect_augmented_primary", "effect_augmented_secondary" -> {
                return Boolean.toString(effect != null && !effect.isBlank() && isAugmented[slot]);
            }
            case "effect_icon_primary", "effect_icon_secondary" -> {
                if (effect == null || effect.isBlank()) return "";
                return new String(Character.toChars(effectIconCodePoint(effect, isAugmented[slot])));
            }
            case "effect_timer_main_primary", "effect_timer_main_secondary" -> {
                long remaining = remainingEffectMillis(playerId, effect);
                if (remaining <= 0) return "";
                long totalSeconds = (remaining + 999) / 1000;
                return (totalSeconds / 60) + ":" + String.format(Locale.ROOT, "%02d", totalSeconds % 60);
            }
            case "effect_timer_alt_primary", "effect_timer_alt_secondary" -> {
                long remaining = remainingEffectMillis(playerId, effect);
                if (remaining <= 0) return "";
                long ticks = (remaining + 49) / 50;
                long minutes = ticks / 1200;
                long seconds = (ticks / 20) % 60;
                long centiseconds = (ticks % 20) * 5;
                return minutes + ":" + String.format(Locale.ROOT, "%02d:%02d", seconds, centiseconds);
            }
            case "effect_spark_ticks_primary", "effect_spark_ticks_secondary" -> {
                if (effect == null || effect.isBlank()) return "0";
                return Long.toString(remainingTicks(sparkEnds.getOrDefault(playerId + ":" + effect, 0L)));
            }
            case "effect_cooldown_ticks_primary", "effect_cooldown_ticks_secondary" -> {
                if (effect == null || effect.isBlank()) return "0";
                return Long.toString(remainingTicks(cooldowns.getOrDefault(playerId + ":" + effect, 0L)));
            }
            default -> { return ""; }
        }
    }

    private long remainingEffectMillis(UUID playerId, String effect) {
        if (effect == null || effect.isBlank()) return 0;
        String key = playerId + ":" + effect;
        long now = System.currentTimeMillis();
        long active = sparkEnds.getOrDefault(key, 0L);
        return Math.max(0, (active > now ? active : cooldowns.getOrDefault(key, 0L)) - now);
    }

    private long remainingTicks(long untilMillis) {
        return Math.max(0, (untilMillis - System.currentTimeMillis() + 49) / 50);
    }

    private int effectIconCodePoint(String effect, boolean isAugmented) {
        int codePoint = 0xE000;
        for (String currentEffect : ICON_EFFECTS) {
            if (currentEffect.equals(effect) || (currentEffect.equals("earth") && effect.equals("earth"))) {
                if (currentEffect.equals("earth") || currentEffect.equals("ender")) return codePoint + 20;
                return codePoint + (isAugmented ? 41 : 20);
            }
            codePoint += currentEffect.equals("earth") || currentEffect.equals("ender") ? 42 : 84;
        }
        return 0xE000;
    }

    private String displaySlot(UUID playerId, int index) {
        String[] current = slots(playerId);
        String effect = current[index];
        if (effect == null || effect.isBlank()) return (index == 0 ? "Primary" : "Secondary") + ": <dark_gray>None";
        String key = playerId + ":" + effect;
        long now = System.currentTimeMillis();
        String status;
        long activeUntil = sparkEnds.getOrDefault(key, 0L);
        long cooldownUntil = cooldowns.getOrDefault(key, 0L);
        if (activeUntil > now) status = "<green>Active";
        else if (cooldownUntil > now) status = "<red>" + ((cooldownUntil - now + 999) / 1000) + "s";
        else status = "<green>Ready";
        String color = getConfig().getString("effects." + effect + ".color", "#A855F7");
        return "<" + color + ">" + (index == 0 ? "P" : "S") + ": " + title(effect) + " <dark_gray>[" + status + "<dark_gray>]";
    }

    private void extinguishNearbyWater(Player player) {
        int radius = Math.max(1, (int) Math.ceil(getConfig().getDouble("effects.fire.burn-radius", 6.0)));
        org.bukkit.Location origin = player.getLocation();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z > radius * radius) continue;
                    org.bukkit.block.Block block = origin.getWorld().getBlockAt(
                            origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z);
                    if (block.getType() == Material.WATER) block.setType(Material.AIR, false);
                }
            }
        }
    }

    private void updateFrostModifiers(Player player, boolean onIceOrSnow) {
        UUID id = player.getUniqueId();
        var movement = player.getAttribute(Attribute.MOVEMENT_SPEED);
        var knockback = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (onIceOrSnow) {
            boolean sparkActive = sparkEnds.getOrDefault(id + ":frost", 0L) > System.currentTimeMillis();
            double speedMultiplier = Math.max(1.0, getConfig().getDouble(
                    sparkActive ? "effects.frost.spark-move-speed" : "effects.frost.move-speed", sparkActive ? 2.0 : 1.5));
            if (movement != null) {
                org.bukkit.attribute.AttributeModifier previous = frostSpeedModifiers.remove(id);
                if (previous != null) movement.removeModifier(previous);
                org.bukkit.attribute.AttributeModifier modifier = new org.bukkit.attribute.AttributeModifier(
                        new NamespacedKey(this, "frost_speed_" + id), speedMultiplier - 1.0,
                        org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1);
                movement.addModifier(modifier);
                frostSpeedModifiers.put(id, modifier);
            }
            if (knockback != null && !frostKnockbackModifiers.containsKey(id)) {
                org.bukkit.attribute.AttributeModifier modifier = new org.bukkit.attribute.AttributeModifier(
                        new NamespacedKey(this, "frost_knockback_" + id),
                        Math.max(0.0, Math.min(1.0, getConfig().getDouble("effects.frost.knockback-resistance", 0.3))),
                        org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER);
                knockback.addModifier(modifier);
                frostKnockbackModifiers.put(id, modifier);
            }
        } else {
            org.bukkit.attribute.AttributeModifier speed = frostSpeedModifiers.remove(id);
            if (movement != null && speed != null) movement.removeModifier(speed);
            org.bukkit.attribute.AttributeModifier resistance = frostKnockbackModifiers.remove(id);
            if (knockback != null && resistance != null) knockback.removeModifier(resistance);
        }
    }

    private void applyOceanAura() {
        long now = System.currentTimeMillis();
        Set<UUID> affected = new HashSet<>();
        for (Player caster : Bukkit.getOnlinePlayers()) {
            if (disabled(caster) || !Arrays.asList(slots(caster.getUniqueId())).contains("ocean")) continue;
            double radius = getConfig().getDouble("effects.ocean.passive-radius", 4.0);
            for (Player target : caster.getWorld().getPlayers()) {
                if (target.equals(caster) || disabled(target) || target.getLocation().distanceSquared(caster.getLocation()) > radius * radius) continue;
                if (trustManager.isTrusted(caster.getUniqueId(), target.getUniqueId())) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 60, 0, true, false, true));
                    continue;
                }
                if (!target.getEyeLocation().getBlock().isLiquid() || target.hasPotionEffect(PotionEffectType.WATER_BREATHING)) continue;
                UUID targetId = target.getUniqueId();
                int respiration = 0;
                ItemStack helmet = target.getInventory().getHelmet();
                if (helmet != null) respiration = (enchantment("respiration") == null ? 0 : helmet.getEnchantmentLevel(enchantment("respiration")));
                int maxAir = 15 + respiration * 15;
                int remaining = oceanAir.getOrDefault(targetId, maxAir) - 1;
                if (remaining <= 0) {
                    remaining = 0;
                    double drownDamage = getConfig().getDouble("effects.ocean.drown-damage", 2.0);
                    if (target.getWorld().hasStorm()) drownDamage *= getConfig().getDouble("effects.ocean.rain-damage-multiplier", 2.0);
                    target.damage(drownDamage, caster);
                }
                oceanAir.put(targetId, remaining);
                oceanLastActive.put(targetId, now);
                affected.add(targetId);
            }
        }
        for (UUID targetId : new HashSet<>(oceanAir.keySet())) {
            if (affected.contains(targetId)) continue;
            Player target = Bukkit.getPlayer(targetId);
            if (target == null) { oceanAir.remove(targetId); oceanLastActive.remove(targetId); continue; }
            if (now - oceanLastActive.getOrDefault(targetId, 0L) < 1100L) continue;
            int respiration = 0;
            ItemStack helmet = target.getInventory().getHelmet();
            if (helmet != null) respiration = (enchantment("respiration") == null ? 0 : helmet.getEnchantmentLevel(enchantment("respiration")));
            int maxAir = 15 + respiration * 15;
            int remaining = oceanAir.getOrDefault(targetId, 0) + 2;
            if (remaining >= maxAir) { oceanAir.remove(targetId); oceanLastActive.remove(targetId); }
            else oceanAir.put(targetId, remaining);
        }
    }

    private org.bukkit.enchantments.Enchantment enchantment(String key) {
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
    }

    private void updateEffectToolEnchantments(Player player, Set<String> active) {
        ItemStack[] heldItems = {player.getInventory().getItemInMainHand(), player.getInventory().getItemInOffHand()};
        for (ItemStack item : heldItems) {
            if (item == null || item.getType() == Material.AIR) continue;
            org.bukkit.enchantments.Enchantment looting = enchantment("looting");
            org.bukkit.enchantments.Enchantment fortune = enchantment("fortune");
            org.bukkit.enchantments.Enchantment unbreaking = enchantment("unbreaking");
            org.bukkit.enchantments.Enchantment loyalty = enchantment("loyalty");

            if (active.contains("emerald") && isLootingTool(item)) applyToolEnchant(item, looting, 5);
            else removeEnchantIfLevel(item, looting, 5);

            if (active.contains("haste") && isHasteTool(item)) {
                applyToolEnchant(item, unbreaking, 10);
                if (isFortuneTool(item)) applyToolEnchant(item, fortune, 5);
            } else {
                removeEnchantIfLevel(item, unbreaking, 10);
                removeEnchantIfLevel(item, fortune, 5);
            }

            if (active.contains("ocean") && item.getType() == Material.TRIDENT) applyToolEnchant(item, loyalty, 5);
            else removeEnchantIfLevel(item, loyalty, 5);
        }
    }

    private boolean isLootingTool(ItemStack item) {
        String type = item.getType().name();
        return type.endsWith("_SWORD") || type.endsWith("_AXE");
    }

    private boolean isFortuneTool(ItemStack item) {
        String type = item.getType().name();
        return type.endsWith("_PICKAXE") || type.endsWith("_SHOVEL") || type.endsWith("_HOE");
    }

    private boolean isHasteTool(ItemStack item) {
        return isLootingTool(item) || isFortuneTool(item);
    }

    private void removeEnchantIfLevel(ItemStack item, org.bukkit.enchantments.Enchantment enchantment, int customLevel) {
        if (item == null || enchantment == null || !item.containsEnchantment(enchantment)) return;
        if (item.getEnchantmentLevel(enchantment) == customLevel) item.removeEnchantment(enchantment);
    }

    private void applyToolEnchant(ItemStack item, org.bukkit.enchantments.Enchantment enchantment, int level) {
        if (item == null || item.getType() == Material.AIR || enchantment == null) return;
        boolean overwrite = getConfig().getBoolean("settings.overwrite-enchants", true);
        if (!overwrite && item.containsEnchantment(enchantment)) return;
        String key = enchantment.getKey().getKey();
        boolean supported = switch (key) {
            case "looting" -> isLootingTool(item);
            case "fortune" -> isFortuneTool(item);
            case "unbreaking" -> isHasteTool(item);
            case "loyalty" -> item.getType() == Material.TRIDENT;
            default -> false;
        };
        if (supported) item.addUnsafeEnchantment(enchantment, level);
    }

    private void healTrustedNearby(Player caster, double radius, double amount) {
        if (amount <= 0) return;
        caster.setHealth(Math.min(caster.getMaxHealth(), caster.getHealth() + amount));
        for (Player target : caster.getWorld().getPlayers()) {
            if (target.equals(caster) || !trustManager.getTrusted(caster.getUniqueId()).contains(target.getUniqueId())) continue;
            if (target.getLocation().distanceSquared(caster.getLocation()) > radius * radius) continue;
            target.setHealth(Math.min(target.getMaxHealth(), target.getHealth() + amount));
        }
    }

    public void activateInvisibility(Player player, int durationTicks) {
        UUID casterId = player.getUniqueId();
        invisiblePlayers.add(casterId);
        player.setCollidable(false);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, durationTicks, 0, true, false, true));
        int interval = Math.max(1, getConfig().getInt("effects.invisibility.invis-interval-ticks", 4));
        int iterations = Math.max(1, (durationTicks + interval - 1) / interval);
        final int[] elapsed = {0};
        final BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!player.isOnline() || disabled(player) || elapsed[0] >= iterations) {
                taskHolder[0].cancel();
                finishInvisibilitySpark(player);
                return;
            }
            elapsed[0]++;
            double maxRadius = Math.max(1.0, getConfig().getDouble("effects.invisibility.max-spark-radius", 12.0));
            double baseRadius = Math.max(1.0, getConfig().getDouble("effects.invisibility.base-spark-radius", 8.0));
            double teammateCoefficient = Math.max(0.0, getConfig().getDouble("effects.invisibility.teammate-coefficient", 1.0));
            long trustedNearby = player.getWorld().getPlayers().stream()
                    .filter(target -> !target.equals(player) && !disabled(target))
                    .filter(target -> trustManager.isTrusted(casterId, target.getUniqueId()))
                    .filter(target -> target.getLocation().distanceSquared(player.getLocation()) <= maxRadius * maxRadius)
                    .count();
            double radius = Math.min(maxRadius, baseRadius + trustedNearby * teammateCoefficient);
            double radiusSquared = radius * radius;
            for (Player target : player.getWorld().getPlayers()) {
                if (target.equals(player) || disabled(target) ||
                        target.getLocation().distanceSquared(player.getLocation()) > radiusSquared) continue;
                if (trustManager.isTrusted(casterId, target.getUniqueId())) {
                    UUID targetId = target.getUniqueId();
                    target.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, interval * 2 + 2, 0, true, false, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, interval * 2 + 2, 0, true, false, true));
                    invisibilitySparkOwners.computeIfAbsent(targetId, ignored -> new HashSet<>()).add(casterId);
                    for (Player viewer : Bukkit.getOnlinePlayers()) {
                        if (viewer.equals(player) || viewer.equals(target)) viewer.showPlayer(this, target);
                        else viewer.hidePlayer(this, target);
                    }
                } else {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, interval * 2 + 2, 0, true, false, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, interval * 2 + 2, 0, true, false, true));
                }
            }
            player.getWorld().spawnParticle(org.bukkit.Particle.SQUID_INK,
                    player.getLocation().add(0, 1, 0), 12, radius * 0.45, 0.8, radius * 0.45, 0.01);
        }, 0L, interval);
        invisibilitySparkTasks.put(casterId, taskHolder[0]);
    }

    private void finishInvisibilitySpark(Player caster) {
        UUID casterId = caster.getUniqueId();
        invisibilitySparkTasks.remove(casterId);
        for (UUID targetId : new HashSet<>(invisibilitySparkOwners.keySet())) {
            Set<UUID> owners = invisibilitySparkOwners.get(targetId);
            if (owners == null || !owners.remove(casterId)) continue;
            if (!owners.isEmpty()) continue;
            invisibilitySparkOwners.remove(targetId);
            Player target = Bukkit.getPlayer(targetId);
            if (target == null) continue;
            boolean hasPassiveInvisibility = Arrays.asList(slots(targetId)).contains("invisibility");
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!hasPassiveInvisibility || viewer.equals(target) ||
                        trustManager.isTrusted(targetId, viewer.getUniqueId())) {
                    viewer.showPlayer(this, target);
                } else {
                    viewer.hidePlayer(this, target);
                }
            }
        }
        if (!Arrays.asList(slots(casterId)).contains("invisibility")) {
            invisiblePlayers.remove(casterId);
            caster.setCollidable(true);
            for (Player viewer : Bukkit.getOnlinePlayers()) viewer.showPlayer(this, caster);
        }
    }

    private void stopInvisibilitySpark(Player player) {
        BukkitTask task = invisibilitySparkTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        finishInvisibilitySpark(player);
    }

    private void applyPassives() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String[] activeSlots = slots(player.getUniqueId());
            if (disabled(player)) {
                var attribute = player.getAttribute(Attribute.MAX_HEALTH);
                if (attribute != null && (attribute.getBaseValue() == 30.0 || attribute.getBaseValue() == 40.0)) {
                    attribute.setBaseValue(20.0);
                    if (player.getHealth() > 20.0) player.setHealth(20.0);
                }
                if (invisiblePlayers.remove(player.getUniqueId())) {
                    player.setCollidable(true);
                    for (Player viewer : Bukkit.getOnlinePlayers()) viewer.showPlayer(this, player);
                }
                clearSpeedStack(player);
                clearHasteStack(player);
                updateHasteBreakSpeed(player, false);
                updateFrostModifiers(player, false);
                updateEffectToolEnchantments(player, Set.of());
                clearWindChargeVisuals(player.getUniqueId());
                if (fireGlidingPlayers.remove(player.getUniqueId())) {
                    ItemStack chest = player.getInventory().getChestplate();
                    if (chest == null || chest.getType() != Material.ELYTRA) player.setGliding(false);
                }
                continue;
            }
            Set<String> active = new HashSet<>(Arrays.asList(activeSlots));
            active.remove("");
            updateEffectToolEnchantments(player, active);
            if (!active.contains("speed")) clearSpeedStack(player);
            if (!active.contains("haste")) { clearHasteStack(player); updateHasteBreakSpeed(player, false); }
            if (!active.contains("feather")) clearWindChargeVisuals(player.getUniqueId());
            if (active.contains("speed")) player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, true, false, true));
            if (active.contains("haste")) {
                decayHasteStack(player);
                updateHasteBreakSpeed(player, true);
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 0, true, false, true));
            }
            if (active.contains("strength")) player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, true, false, true));
            if (active.contains("regeneration")) player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, true, false, true));
            if (active.contains("fire")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40, 0, true, false, true));
                if (player.getLocation().getBlock().getType() == Material.LAVA && player.isSneaking()) {
                    fireGlidingPlayers.add(player.getUniqueId());
                    player.setGliding(true);
                    org.bukkit.util.Vector swim = player.getLocation().getDirection().multiply(getConfig().getDouble("effects.fire.swim-power", 0.4));
                    player.setVelocity(swim.setY(player.getVelocity().getY()));
                } else if (fireGlidingPlayers.remove(player.getUniqueId())) {
                    ItemStack chest = player.getInventory().getChestplate();
                    if (chest == null || chest.getType() != Material.ELYTRA) player.setGliding(false);
                }
                if (player.getFireTicks() > 0) extinguishNearbyWater(player);
            }
            if (active.contains("emerald")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 40, 2, true, false, true));
            }
            if (active.contains("ocean")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 40, 0, true, false, true));
                if (player.isInWater()) player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 40, 0, true, false, true));
            }
            if (active.contains("frost")) {
                player.setFreezeTicks(0);
                Material feet = player.getLocation().getBlock().getType();
                Material underfoot = player.getLocation().clone().subtract(0, 1, 0).getBlock().getType();
                boolean onIceOrSnow = feet.name().contains("ICE") || underfoot.name().contains("ICE")
                        || feet.name().contains("SNOW") || underfoot.name().contains("SNOW");
                updateFrostModifiers(player, onIceOrSnow);
                if (onIceOrSnow) player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, true, false, true));
                if (feet == Material.POWDER_SNOW) player.setVelocity(player.getVelocity().setY(Math.max(0.1, player.getVelocity().getY())));
                if (getConfig().getBoolean("effects.frost.lava-walk", true)) {
                    org.bukkit.block.Block lava = player.getLocation().clone().subtract(0, 1, 0).getBlock();
                    if (lava.getType() == Material.LAVA) {
                        boolean source = !(lava.getBlockData() instanceof org.bukkit.block.data.Levelled levelled) || levelled.getLevel() == 0;
                        lava.setType(source ? Material.OBSIDIAN : Material.COBBLESTONE, false);
                    }
                }
            } else {
                updateFrostModifiers(player, false);
            }
            if (active.contains("heart")) {
                var attribute = player.getAttribute(Attribute.MAX_HEALTH);
                if (attribute != null && attribute.getBaseValue() < 30.0) attribute.setBaseValue(30.0);
            } else {
                var attribute = player.getAttribute(Attribute.MAX_HEALTH);
                if (attribute != null && attribute.getBaseValue() == 30.0) attribute.setBaseValue(20.0);
            }
            if (active.contains("invisibility")) {
                invisiblePlayers.add(player.getUniqueId());
                player.setCollidable(false);
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, true, false, true));
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    if (viewer.equals(player) || trustManager.getTrusted(player.getUniqueId()).contains(viewer.getUniqueId())) viewer.showPlayer(this, player);
                    else viewer.hidePlayer(this, player);
                }
            } else if (invisiblePlayers.remove(player.getUniqueId())) {
                player.setCollidable(true);
                for (Player viewer : Bukkit.getOnlinePlayers()) viewer.showPlayer(this, player);
            }
            if (displayEnabled.getOrDefault(player.getUniqueId(), true)) {
                String primaryText = displaySlot(player.getUniqueId(), 0);
                String secondaryText = displaySlot(player.getUniqueId(), 1);
                player.sendActionBar(mini.deserialize("<gray>✦ <white>" + primaryText + " <dark_gray>│ <white>" + secondaryText));
            }
            if (active.contains("ender")) {
                for (Player other : player.getWorld().getPlayers()) {
                    if (other.equals(player) || trustManager.getTrusted(player.getUniqueId()).contains(other.getUniqueId())) continue;
                    double glowRadius = getConfig().getDouble("effects.ender.untrust-glow-radius", 5.0);
                    if (other.getLocation().distanceSquared(player.getLocation()) <= glowRadius * glowRadius) {
                        other.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, true, false, true));
                    }
                }
            }
        }
        applyOceanAura();
    }

    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null && hasEffect(killer, "invisibility")) {
            String deathMessage = event.getDeathMessage();
            if (deathMessage != null) {
                event.setDeathMessage(deathMessage.replace(killer.getName(),
                        "§k" + "i".repeat(Math.max(3, killer.getName().length())) + "§r"));
            }
        }
        UUID id = victim.getUniqueId();
        String[] current = slots(id);
        boolean[] isAugmented = augmented.getOrDefault(id, new boolean[]{false, false});
        if (current[0] != null && !current[0].isBlank()) event.getDrops().add(createEffectItem(current[0], isAugmented[0]));
        if (current[1] != null && !current[1].isBlank()) event.getDrops().add(createEffectItem(current[1], isAugmented[1]));
        if (current[0] != null && !current[0].isBlank()) addHistory(id, 0, "none", false);
        if (current[1] != null && !current[1].isBlank()) addHistory(id, 1, "none", false);
        current[0] = ""; current[1] = "";
        isAugmented[0] = false; isAugmented[1] = false;
        saveData();
        invisiblePlayers.remove(id);
        clearWindChargeVisuals(id);
        windChargeCooldowns.remove(id);
        stopInvisibilitySpark(victim);
        enderCurses.remove(id);
        enderSparkCooldownReset.remove(id);
        regenFoodWindow.remove(id);
        recentDamage.remove(id);
        oceanAir.remove(id);
        oceanLastActive.remove(id);
        fireGlidingPlayers.remove(id);
        clearHasteStack(victim);
        clearSpeedStack(victim);
        updateFrostModifiers(victim, false);
        cooldowns.keySet().removeIf(key -> key.startsWith(id + ":"));
        sparkEnds.keySet().removeIf(key -> key.startsWith(id + ":"));
        hitCounters.keySet().removeIf(key -> key.startsWith(id + ":"));
        hitPairs.keySet().removeIf(key -> key.startsWith(id + ":") || key.endsWith(":" + id));
        victim.setCollidable(true);
        for (Player viewer : Bukkit.getOnlinePlayers()) viewer.showPlayer(this, victim);
        var maxHealth = victim.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(20.0);
        if (getConfig().getBoolean("settings.head-drop", false) && victim.getKiller() != null && !victim.getKiller().equals(victim)) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            org.bukkit.inventory.meta.SkullMeta skull = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            skull.setOwningPlayer(victim);
            head.setItemMeta(skull);
            event.getDrops().add(head);
        }
    }

    public void environmentalDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || disabled(player)) return;
        String[] active = slots(player.getUniqueId());
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL && Arrays.asList(active).contains("feather")) event.setCancelled(true);
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FLY_INTO_WALL && Arrays.asList(active).contains("feather")) event.setCancelled(true);
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.DROWNING && Arrays.asList(active).contains("ocean")) event.setCancelled(true);
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL && Arrays.asList(active).contains("fire")) {
            org.bukkit.Location location = player.getLocation();
            for (int x = -1; x <= 1 && !event.isCancelled(); x++) {
                for (int y = -1; y <= 1 && !event.isCancelled(); y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (location.getWorld().getBlockAt(location.getBlockX() + x, location.getBlockY() + y, location.getBlockZ() + z).getType() == Material.LAVA) {
                            event.setCancelled(true);
                            break;
                        }
                    }
                }
            }
        }
    }
    public void invisibilityMobTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player target) || disabled(target)) return;
        if (Arrays.asList(slots(target.getUniqueId())).contains("invisibility")) event.setCancelled(true);
    }

    public void invisibilityPotionImmunity(org.bukkit.event.entity.EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || disabled(player) || event.getNewEffect() == null) return;
        if (!Arrays.asList(slots(player.getUniqueId())).contains("invisibility")) return;
        PotionEffectType type = event.getNewEffect().getType();
        if (type == PotionEffectType.WITHER || type == PotionEffectType.DARKNESS) event.setCancelled(true);
    }

    public void damage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || disabled(attacker)) return;
        if (!(event.getEntity() instanceof org.bukkit.entity.LivingEntity target)) return;
        boolean trustedTarget = target instanceof Player victim && trustManager.getTrusted(attacker.getUniqueId()).contains(victim.getUniqueId());
        String[] activeSlots = slots(attacker.getUniqueId());
        Set<String> active = new HashSet<>(Arrays.asList(activeSlots));
        active.remove("");
        if (target instanceof Player victim) {
            if (active.contains("emerald")) {
                if (trustedTarget) {
                    event.setCancelled(true);
                    transferExperience(attacker, victim, getConfig().getInt("effects.emerald.xp-share-amount", 1));
                } else {
                    transferExperience(victim, attacker, getConfig().getInt("effects.emerald.xp-steal-amount", 2));
                }
            }
            if (active.contains("heart") && trustedTarget) {
                double transferHealth = Math.min(attacker.getHealth() - 10.0, 10.0);
                if (transferHealth > 0) {
                    event.setCancelled(true);
                    int amplifier = Math.max(0, (int) (transferHealth / 2.0));
                    int duration = Math.max(1, (int) (480.0 / transferHealth * 20.0));
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, amplifier, true, true, true));
                    attacker.setHealth(Math.min(attacker.getMaxHealth(), getConfig().getDouble("effects.heart.return-after-hit", 10.0)));
                    attacker.playSound(attacker.getLocation(), "entity.player.levelup", 1.0f, 1.2f);
                }
            }
            String pairKey = victim.getUniqueId() + ":" + attacker.getUniqueId();
            int hitsAgainstVictim = hitPairs.merge(pairKey, 1, Integer::sum);
            if (Arrays.asList(slots(victim.getUniqueId())).contains("emerald") &&
                    hitsAgainstVictim % Math.max(1, getConfig().getInt("effects.emerald.trigger-hits", 10)) == 0) {
                int cooldownTicks = Math.max(0, getConfig().getInt("effects.emerald.hit-item-cooldown-ticks", 200));
                for (Material material : List.of(Material.GOLDEN_CARROT, Material.GOLDEN_APPLE,
                        Material.ENCHANTED_GOLDEN_APPLE, Material.COOKIE, Material.MELON_SLICE,
                        Material.PUMPKIN_PIE, Material.BEETROOT_SOUP, Material.MUSHROOM_STEW,
                        Material.EXPERIENCE_BOTTLE)) {
                    attacker.setCooldown(material, cooldownTicks);
                }
            }
        }
        if (trustedTarget) return;
        if (active.contains("haste")) {
            addHasteStack(attacker, getConfig().getDouble("effects.haste.attack-speed-stack", 0.2));
            int iframeTicks = Math.max(0, getConfig().getInt("effects.haste.iframe-duration", 19));
            Bukkit.getScheduler().runTaskLater(this, () -> target.setNoDamageTicks(iframeTicks), 1L);
        }
        if (active.contains("speed")) applySpeedStack(attacker, getConfig().getDouble("effects.speed.hit-increase", 0.1));
        if (target instanceof Player speedVictim && Arrays.asList(slots(speedVictim.getUniqueId())).contains("speed")) {
            applySpeedStack(speedVictim, getConfig().getDouble("effects.speed.hit-decrease", -0.1));
        }
        if (active.contains("strength")) {
            if (!(target instanceof Player)) event.setDamage(event.getDamage() * getConfig().getDouble("effects.strength.mob-damage-multiplier", 2.0));
            double lostHealth = Math.max(0, attacker.getMaxHealth() - attacker.getHealth());
            event.setDamage(event.getDamage() + lostHealth * getConfig().getDouble("effects.strength.hp-loss-attack-bonus", 0.3));
            boolean sparkActive = sparkEnds.getOrDefault(attacker.getUniqueId() + ":strength", 0L) > System.currentTimeMillis();
            if (sparkActive) {
                boolean critical = attacker.getFallDistance() > 0 && !attacker.isOnGround() && !attacker.isSprinting() &&
                        !attacker.isInWater() && !attacker.hasPotionEffect(PotionEffectType.SLOW_FALLING);
                event.setDamage(event.getDamage() * (critical ? getConfig().getDouble("effects.strength.double-crit-multiplier", 1.334) : 1.5));
                attacker.getWorld().spawnParticle(critical ? org.bukkit.Particle.END_ROD : org.bukkit.Particle.CRIT,
                        target.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.2);
                if (!critical) attacker.playSound(attacker.getLocation(), "entity.player.attack.crit", 1.0f, 1.0f);
            }
            if (sparkActive && target instanceof Player victim && victim.isBlocking() &&
                    (attacker.getInventory().getItemInMainHand().getType().name().endsWith("_AXE"))) {
                victim.setCooldown(Material.SHIELD, getConfig().getInt("effects.strength.shield-disable-ticks", 200));
                ItemStack shield = victim.getInventory().getItemInOffHand().getType() == Material.SHIELD
                        ? victim.getInventory().getItemInOffHand() : victim.getInventory().getItemInMainHand().getType() == Material.SHIELD
                        ? victim.getInventory().getItemInMainHand() : null;
                if (shield != null && shield.hasItemMeta() && shield.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable) {
                    damageable.setDamage(damageable.getDamage() + getConfig().getInt("effects.strength.shield-durability-damage", 20));
                    shield.setItemMeta((org.bukkit.inventory.meta.ItemMeta) damageable);
                    if (damageable.getDamage() >= shield.getType().getMaxDurability()) shield.setAmount(0);
                }
                event.setDamage(event.getDamage() + event.getDamage() / Math.max(1.0, getConfig().getDouble("effects.strength.shield-penetration", 2.0)));
            }
        }
        if (active.contains("feather") && attacker.getFallDistance() > getConfig().getDouble("effects.feather.wind-burst-fall-min", 2.0)) {
            float fallDistance = attacker.getFallDistance();
            attacker.setVelocity(attacker.getVelocity().setY(0));
            attacker.setVelocity(attacker.getVelocity().setY(getConfig().getDouble("effects.feather.wind-burst-power", 1.0)));
            event.setDamage(event.getDamage() + fallDistance * getConfig().getDouble("effects.feather.wind-burst-damage-multiplier", 0.5));
        }
        if (active.contains("fire")) {
            boolean sparkActive = sparkEnds.getOrDefault(attacker.getUniqueId() + ":fire", 0L) > System.currentTimeMillis();
            if (sparkActive) {
                target.setFireTicks(Math.max(target.getFireTicks(), getConfig().getInt("effects.fire.eternal-ticks", 200)));
                attacker.getWorld().spawnParticle(org.bukkit.Particle.FLAME, target.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);
                attacker.playSound(attacker.getLocation(), "entity.blaze.shoot", 1.0f, 1.0f);
            } else if (countHit(attacker, "fire") % Math.max(1, getConfig().getInt("effects.fire.trigger-hits", 5)) == 0) {
                target.setFireTicks(Math.max(target.getFireTicks(), getConfig().getInt("effects.fire.ignite-ticks", 100)));
                attacker.getWorld().spawnParticle(org.bukkit.Particle.FLAME, target.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.05);
                attacker.playSound(attacker.getLocation(), "entity.blaze.shoot", 1.0f, 1.0f);
            }
        }
        if (active.contains("thunder") && !lightningChainAttackers.contains(attacker.getUniqueId())) {
            int threshold = Math.max(1, getConfig().getInt("effects.thunder.trigger-hits", 10));
            if (attacker.getWorld().hasStorm()) threshold = Math.max(1, threshold / 2);
            if (countHit(attacker, "thunder") % threshold == 0) {
                attacker.getWorld().strikeLightningEffect(target.getLocation());
                if (getConfig().getBoolean("settings.allow-true-damage", true)) {
                    event.setDamage(event.getDamage() + getConfig().getDouble("effects.thunder.true-damage", 1.0));
                }
                chainLightning(attacker, target, getConfig().getInt("effects.thunder.lightning-chain-max", 10),
                        getConfig().getDouble("effects.thunder.lightning-chain-radius", 12.0));
            }
        }
        if (active.contains("frost")) {
            boolean frostSparkActive = sparkEnds.getOrDefault(attacker.getUniqueId() + ":frost", 0L) > System.currentTimeMillis();
            int frostHits = countHit(attacker, "frost");
            boolean frostTriggered = frostSparkActive || frostHits % Math.max(1, getConfig().getInt("effects.frost.trigger-hits", 10)) == 0;
            if (frostTriggered) {
                target.setFreezeTicks(Math.max(target.getFreezeTicks(), getConfig().getInt("effects.frost.freeze-ticks", 600)));
                target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,
                        Math.max(1, getConfig().getInt("effects.frost.mining-fatigue-duration-ticks", 200)),
                        Math.max(0, getConfig().getInt("effects.frost.mining-fatigue-amplifier", 1)), true, false, true));
                if (!frostSparkActive && target instanceof Player frostVictim) {
                    long cooldownSeconds = Math.max(0, getConfig().getLong("effects.frost.wind-charge-cooldown-seconds", 10));
                    windChargeCooldowns.put(frostVictim.getUniqueId(), System.currentTimeMillis() + cooldownSeconds * 1000L);
                    frostVictim.setCooldown(Material.WIND_CHARGE, (int) (cooldownSeconds * 20L));
                }
            }
        }
        if (active.contains("invisibility") && countHit(attacker, "invisibility") % getConfig().getInt("effects.invisibility.trigger-hits", 10) == 0) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 0, true, false, true));
        }
        if (active.contains("regeneration")) {
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 1, true, false, true));
            if (target instanceof Player victim && countHit(attacker, "regeneration") % Math.max(1, getConfig().getInt("effects.regeneration.trigger-hits", 10)) == 0 && victim.getSaturation() > 0) {
                victim.setSaturation(Math.max(0, victim.getSaturation() - 1));
            }
        }
        if (target instanceof Player victim && Arrays.asList(slots(victim.getUniqueId())).contains("feather")) {
            int pairHits = hitPairs.getOrDefault(victim.getUniqueId() + ":" + attacker.getUniqueId(), 0);
            int triggerHits = Math.max(1, getConfig().getInt("effects.feather.trigger-hits", 3));
            int maxCharges = Math.max(0, getConfig().getInt("effects.feather.max-wind-charge", 2));
            if (pairHits > 0 && pairHits % triggerHits == 0 && windCharges.getOrDefault(victim.getUniqueId(), 0) < maxCharges) {
                addWindCharge(victim);
                victim.playSound(victim.getLocation(), "entity.wind_charge.wind_burst", 0.6f, 1.4f);
            }
        }
    }

    private void chainLightning(Player attacker, org.bukkit.entity.LivingEntity firstTarget, int maxTargets, double radius) {
        if (maxTargets <= 1 || radius <= 0) return;
        Set<UUID> hitEntities = new HashSet<>();
        hitEntities.add(firstTarget.getUniqueId());
        org.bukkit.entity.LivingEntity previous = firstTarget;
        lightningChainAttackers.add(attacker.getUniqueId());
        try {
            for (int hit = 1; hit < maxTargets; hit++) {
                org.bukkit.entity.LivingEntity next = null;
                double closestDistance = radius * radius;
                for (org.bukkit.entity.Entity entity : previous.getNearbyEntities(radius, radius, radius)) {
                    if (!(entity instanceof org.bukkit.entity.LivingEntity living) || living.isDead()) continue;
                    if (living.equals(attacker) || hitEntities.contains(living.getUniqueId())) continue;
                    if (living instanceof Player targetPlayer && trustManager.isTrusted(attacker.getUniqueId(), targetPlayer.getUniqueId())) continue;
                    double distance = previous.getLocation().distanceSquared(living.getLocation());
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        next = living;
                    }
                }
                if (next == null) break;
                hitEntities.add(next.getUniqueId());
                attacker.getWorld().strikeLightningEffect(next.getLocation());
                next.damage(Math.max(0.0, getConfig().getDouble("effects.thunder.true-damage", 1.0)), attacker);
                previous = next;
            }
        } finally {
            lightningChainAttackers.remove(attacker.getUniqueId());
        }
    }

    public void recordDamageDealt(Player attacker, double amount) {
        if (attacker == null || amount <= 0 || !Double.isFinite(amount)) return;
        UUID id = attacker.getUniqueId();
        DamageSample sample = new DamageSample(System.currentTimeMillis(), amount);
        recentDamage.computeIfAbsent(id, ignored -> new ArrayDeque<>()).addLast(sample);
        long windowTicks = Math.max(1, getConfig().getLong("settings.recent-damage-window-seconds", 30)) * 20L;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Deque<DamageSample> samples = recentDamage.get(id);
            if (samples == null) return;
            samples.remove(sample);
            if (samples.isEmpty()) recentDamage.remove(id);
        }, windowTicks);
    }

    public double recentDamageAmount(UUID playerId) {
        Deque<DamageSample> samples = recentDamage.get(playerId);
        if (samples == null || samples.isEmpty()) return 0.0;
        long cutoff = System.currentTimeMillis() - Math.max(1, getConfig().getLong("settings.recent-damage-window-seconds", 30)) * 1000L;
        while (!samples.isEmpty() && samples.peekFirst().time() < cutoff) samples.removeFirst();
        if (samples.isEmpty()) {
            recentDamage.remove(playerId);
            return 0.0;
        }
        return samples.stream().mapToDouble(DamageSample::amount).sum();
    }

    private record DamageSample(long time, double amount) {}

    private int countHit(Player player, String effect) {
        String key = player.getUniqueId() + ":" + effect;
        int count = hitCounters.getOrDefault(key, 0) + 1;
        hitCounters.put(key, count);
        return count;
    }
    public void dropControl(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        String action = player.isSneaking() ? "sneak-drop" : "drop";
        triggerBoundSpark(player, action, event);
    }

    public void swapControl(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        String action = player.isSneaking() ? "sneak-swap" : "swap";
        triggerBoundSpark(player, action, event);
    }

    public void sneakControl(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        String[] configured = controls.getOrDefault(player.getUniqueId(), new String[]{"none", "none"});
        for (int i = 0; i < configured.length; i++) {
            if (configured[i].equalsIgnoreCase("sneak")) {
                spark(player, i == 0 ? "primary" : "secondary");
                break;
            }
        }
        UUID playerId = player.getUniqueId();
        int charges = windCharges.getOrDefault(playerId, 0);
        long cooldownUntil = windChargeCooldowns.getOrDefault(playerId, 0L);
        if (charges > 0 && !disabled(player) && cooldownUntil <= System.currentTimeMillis()) {
            removeWindChargeVisual(player);
            org.bukkit.entity.WindCharge charge = player.getWorld().spawn(player.getEyeLocation(), org.bukkit.entity.WindCharge.class);
            charge.setShooter(player);
            charge.setVelocity(player.getLocation().getDirection().multiply(
                    getConfig().getDouble("effects.feather.wind-charge-speed", 2.0)));
            windChargeCooldowns.put(playerId, System.currentTimeMillis()
                    + Math.max(0, getConfig().getLong("effects.feather.wind-charge-cooldown-seconds", 5)) * 1000L);
            player.playSound(player.getLocation(), "entity.wind_charge.throw", 1.0f, 1.0f);
        } else if (charges > 0 && cooldownUntil > System.currentTimeMillis()) {
            player.sendMessage(mini.deserialize("<gray>Your Feather wind charge is recharging."));
        }
    }

    private void triggerBoundSpark(Player player, String action, org.bukkit.event.Cancellable event) {
        String[] configured = controls.getOrDefault(player.getUniqueId(), new String[]{"none", "none"});
        for (int i = 0; i < configured.length; i++) {
            if (configured[i].equalsIgnoreCase(action)) {
                event.setCancelled(true);
                spark(player, i == 0 ? "primary" : "secondary");
                return;
            }
        }
    }

    public void quit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        stopInvisibilitySpark(player);
        clearHasteStack(player);
        clearSpeedStack(player);
        updateFrostModifiers(player, false);
        invisiblePlayers.remove(player.getUniqueId());
        fireGlidingPlayers.remove(player.getUniqueId());
        oceanAir.remove(player.getUniqueId());
        oceanLastActive.remove(player.getUniqueId());
        recentDamage.remove(player.getUniqueId());
        clearWindChargeVisuals(player.getUniqueId());
        windChargeCooldowns.remove(player.getUniqueId());
        enderCurses.remove(player.getUniqueId());
        regenFoodWindow.remove(player.getUniqueId());
        cooldowns.keySet().removeIf(key -> key.startsWith(player.getUniqueId() + ":"));
        sparkEnds.keySet().removeIf(key -> key.startsWith(player.getUniqueId() + ":"));
        hitCounters.keySet().removeIf(key -> key.startsWith(player.getUniqueId() + ":"));
        hitPairs.keySet().removeIf(key -> key.startsWith(player.getUniqueId() + ":") || key.endsWith(":" + player.getUniqueId()));
    }

    private void clearSpeedStack(Player player) {
        UUID id = player.getUniqueId();
        org.bukkit.attribute.AttributeModifier modifier = speedModifiers.remove(id);
        var attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute != null && modifier != null) attribute.removeModifier(modifier);
        speedStacks.remove(id);
    }

    private void clearHasteStack(Player player) {
        UUID id = player.getUniqueId();
        org.bukkit.attribute.AttributeModifier modifier = hasteModifiers.remove(id);
        var attribute = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attribute != null && modifier != null) attribute.removeModifier(modifier);
        hasteStacks.remove(id);
        updateHasteBreakSpeed(player, false);
        clearHasteSpark(player);
    }

    public void join(PlayerJoinEvent e) {
        Player joining = e.getPlayer();
        slots(joining.getUniqueId());
        if (!joining.hasPlayedBefore() && getConfig().getBoolean("settings.roll-first-join", false)) {
            List<String> rollEffects = EFFECTS.stream().filter(effect -> !effect.equals("earth") && !effect.equals("ender")).toList();
            if (!rollEffects.isEmpty()) {
                String selected = rollEffects.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(rollEffects.size()));
                Bukkit.getScheduler().runTaskLater(this, () -> rollFirstJoin(joining, selected), 40L);
            }
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(joining)) continue;
            if (invisiblePlayers.contains(online.getUniqueId()) && !trustManager.getTrusted(online.getUniqueId()).contains(joining.getUniqueId())) joining.hidePlayer(this, online);
            if (invisiblePlayers.contains(joining.getUniqueId()) && !trustManager.getTrusted(joining.getUniqueId()).contains(online.getUniqueId())) online.hidePlayer(this, joining);
        }
        String url=getConfig().getString("settings.resource-pack.url","");
        if(getConfig().getBoolean("settings.resource-pack.enabled") && !url.isBlank()) {
            String hash=getConfig().getString("settings.resource-pack.sha1","");
            if(hash.matches("(?i)[0-9a-f]{40}")) e.getPlayer().setResourcePack(url, java.util.HexFormat.of().parseHex(hash), getConfig().getString("settings.resource-pack.prompt","Infuse resource pack"), false);
        }
    }
    public List<String> completeCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("resetcooldown")) return args.length == 1 ? List.of("primary", "secondary") : List.of();
        if (command.getName().equalsIgnoreCase("resetcraft")) return args.length == 1 ? java.util.stream.Stream.concat(EFFECTS.stream(), java.util.stream.Stream.of("all")).toList() : List.of();
        if (command.getName().equalsIgnoreCase("mapregion")) return args.length == 1 ? List.of("add", "delete", "list", "clear") : args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("delete")) && sender instanceof Player player && regionManager != null ? regionManager.list().stream().map(value -> value.substring(value.indexOf('|') + 1)).toList() : List.of();
        if (command.getName().equalsIgnoreCase("conf")) return args.length == 1 ? List.of("get", "set", "list") : args.length == 2 && args[0].equalsIgnoreCase("list") ? List.of("show", "add", "remove", "clear") : List.of();
        if (command.getName().equalsIgnoreCase("item")) return args.length == 1 ? EFFECTS.stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList() : List.of("true", "false");
        if (command.getName().equalsIgnoreCase("trust")) return args.length == 1 ? List.of("add", "remove", "list", "clear") : args.length == 2 ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList() : List.of();
        if (command.getName().equalsIgnoreCase("control")) return args.length == 1 ? List.of("primary", "secondary") : args.length == 2 ? List.of("swap", "drop", "sneak", "sneak-drop", "sneak-swap", "none") : List.of();
        if (command.getName().equalsIgnoreCase("use")) return args.length == 1 ? List.of("primary", "secondary", "p", "s") : List.of();
        if (command.getName().equalsIgnoreCase("infuse")) {
            if (args.length == 1) return List.of("gui", "give", "clear", "spark", "reload", "add", "remove", "get", "history").stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
            if (args.length == 3 && args[0].equalsIgnoreCase("give")) return EFFECTS;
        }
        return List.of();
    }
}