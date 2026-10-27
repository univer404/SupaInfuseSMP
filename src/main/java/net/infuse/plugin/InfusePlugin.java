package net.infuse.plugin;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
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

public final class InfusePlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    static final List<String> EFFECTS = List.of("earth","emerald","ender","feather","fire","frost","haste","heart","invisibility","ocean","regeneration","speed","strength","thunder");
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Map<UUID, String[]> equipped = new HashMap<>();
    private final Map<UUID, boolean[]> augmented = new HashMap<>();
    private final Map<UUID, String[]> controls = new HashMap<>();
    private final Map<UUID, Set<UUID>> trusted = new HashMap<>();
    private final Map<UUID, Integer> selectionMode = new HashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, Integer> hitCounters = new HashMap<>();
    private final Map<UUID, Long> trustActionCooldowns = new HashMap<>();
    private final Set<UUID> invisiblePlayers = new HashSet<>();
    private NamespacedKey effectKey;
    private InfuseGui gui;
    private InfuseRecipeBookGui recipeGui;
    private BukkitTask passiveTask;

    @Override public void onEnable() {
        saveDefaultConfig();
        saveResource("gui.yml", false);
        saveResource("messages.yml", false);
        effectKey = new NamespacedKey(this, "effect");
        gui = new InfuseGui(this);
        recipeGui = new InfuseRecipeBookGui(this);
        loadData();
        Objects.requireNonNull(getCommand("infuse")).setExecutor(this);
        Objects.requireNonNull(getCommand("infuse")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("trust")).setExecutor(this);
        Objects.requireNonNull(getCommand("trust")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new InfuseCraftingGui(this), this);
        getServer().getPluginManager().registerEvents(recipeGui, this);
        passiveTask = Bukkit.getScheduler().runTaskTimer(this, this::applyPassives, 20L, 20L);
        getLogger().info("Infuse initialized. " + EFFECTS.size() + " effect identifiers registered.");
    }

    @Override public void onDisable() {
        if (passiveTask != null) passiveTask.cancel();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player hidden : Bukkit.getOnlinePlayers()) viewer.showPlayer(this, hidden);
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
                Set<UUID> list = new HashSet<>();
                for (String friend : data.getStringList(id + ".trusted")) try { list.add(UUID.fromString(friend)); } catch (IllegalArgumentException ignored) {}
                trusted.put(uuid, list);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveData() {
        getConfig().set("players", null);
        Set<UUID> playerIds = new HashSet<>(equipped.keySet());
        playerIds.addAll(trusted.keySet());
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
            getConfig().set(path + ".trusted", trusted.getOrDefault(id, Set.of()).stream().map(UUID::toString).toList());
        }
        saveConfig();
    }

    net.kyori.adventure.text.minimessage.MiniMessage getMiniMessage() { return mini; }

    private String message(String key) {
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(), "messages.yml"));
        return cfg.getString("prefix", "<#A855F7>Infuse » ") + cfg.getString(key, "<red>Missing message: " + key);
    }
    private void tell(CommandSender sender, String key) { sender.sendMessage(mini.deserialize(message(key))); }
    private String clean(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }
    String[] slots(UUID id) { return equipped.computeIfAbsent(id, ignored -> new String[]{"", ""}); }
    private boolean disabled(Player p) { return getConfig().getStringList("settings.disabled-worlds").contains(p.getWorld().getName()); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("trust")) return trustCommand(sender, args);
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
            reloadConfig(); saveDefaultConfig(); gui.reload(); recipeGui.reload(); sender.sendMessage(mini.deserialize(message("reload"))); return true;
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
            if (args[1].equalsIgnoreCase("primary") || args[1].equalsIgnoreCase("both")) s[0] = "";
            if (args[1].equalsIgnoreCase("secondary") || args[1].equalsIgnoreCase("both")) s[1] = "";
            saveData(); p.sendMessage(mini.deserialize(message("effect-cleared"))); return true;
        }
        if (args[0].equalsIgnoreCase("spark")) {
            if (!(sender instanceof Player p)) { tell(sender, "player-only"); return true; }
            spark(p); return true;
        }
        sender.sendMessage("Usage: /infuse [gui|give|clear|spark|reload]");
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
            sender.sendMessage(mini.deserialize("<yellow>No saved change history is available yet for " + target.getName() + "."));
            return true;
        }
        if (action.equals("remove")) {
            if (slot == null || slot.equals("both")) {
                current[0] = ""; current[1] = ""; aug[0] = false; aug[1] = false;
            } else if (slot.equals("primary") || slot.equals("p")) {
                current[0] = ""; aug[0] = false;
            } else if (slot.equals("secondary") || slot.equals("s")) {
                current[1] = ""; aug[1] = false;
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
        Set<UUID> list = trusted.computeIfAbsent(p.getUniqueId(), ignored -> new HashSet<>());
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
                    Set<UUID> current = trusted.computeIfAbsent(ownerId, ignored -> new HashSet<>());
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
                    trusted.computeIfAbsent(ownerId, ignored -> new HashSet<>()).remove(targetId);
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
    ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material); ItemMeta meta=item.getItemMeta();
        meta.displayName(mini.deserialize(name)); item.setItemMeta(meta); return item;
    }
    ItemStack createEffectItem(String effect, boolean augmented) {
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
    String title(String effect) { return effect.substring(0,1).toUpperCase(Locale.ROOT)+effect.substring(1); }
    String display(String effect) { return effect == null || effect.isBlank() ? "None" : title(effect); }

    @EventHandler public void inventoryClick(InventoryClickEvent e) {
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
            String effect=encoded.split(":")[0]; String[] current=slots(p.getUniqueId());
            int target = selectionMode.getOrDefault(p.getUniqueId(), 0);
            if (!getConfig().getBoolean("settings.allow-same-effects", false) && effect.equals(current[1 - target])) {
                p.sendMessage(mini.deserialize("<red>You cannot equip the same effect in both slots."));
                return;
            }
            if (getConfig().getBoolean("settings.lock-effects", false)) {
                List<String> primaryOnly = getConfig().getStringList("settings.primary-effects");
                List<String> secondaryOnly = getConfig().getStringList("settings.secondary-effects");
                if (target == 0 && secondaryOnly.contains(effect) && !primaryOnly.contains(effect)) {
                    p.sendMessage(mini.deserialize("<red>That effect is locked to the secondary slot."));
                    return;
                }
                if (target == 1 && primaryOnly.contains(effect) && !secondaryOnly.contains(effect)) {
                    p.sendMessage(mini.deserialize("<red>That effect is locked to the primary slot."));
                    return;
                }
            }
            current[target]=effect;
            augmented.computeIfAbsent(p.getUniqueId(), ignored -> new boolean[]{false, false})[target] = encoded.endsWith(":augmented");
            saveData(); p.sendMessage(mini.deserialize("<green>Equipped <white>"+title(effect)+"<green> in "+(target==0?"primary":"secondary")+" slot."));
            openMain(p);
        }
    }

    @EventHandler public void interact(PlayerInteractEvent e) {
        if(e.getItem()==null || !e.getAction().isRightClick()) return;
        String encoded=e.getItem().getItemMeta().getPersistentDataContainer().get(effectKey,PersistentDataType.STRING);
        if(encoded==null) return;
        e.setCancelled(true); spark(e.getPlayer());
    }
    private void spark(Player p) {
        spark(p, null);
    }
    private void spark(Player p, String requestedSlot) {
        String[] s=slots(p.getUniqueId());
        String effect = requestedSlot == null ? (!s[1].isBlank()?s[1]:s[0]) : s[requestedSlot.equalsIgnoreCase("secondary") ? 1 : 0];
        if(effect.isBlank()) { p.sendMessage(mini.deserialize("<red>You have no Infuse effect equipped.")); return; }
        if(disabled(p)) return;
        long now=System.currentTimeMillis(); String key=p.getUniqueId()+":"+effect;
        long until=cooldowns.getOrDefault(key,0L);
        if(until>now) { p.sendMessage(mini.deserialize("<red>That Spark is on cooldown for "+((until-now+999)/1000)+"s.")); return; }
        long cooldownSeconds = Math.max(0, getConfig().getLong("effects." + effect + ".cooldown-seconds", 60));
        int durationTicks = (int) Math.max(1, getConfig().getLong("effects." + effect + ".spark-seconds", 15) * 20L);
        cooldowns.put(key, now + cooldownSeconds * 1000L);
        p.playSound(p.getLocation(), getConfig().getString("settings.use-sound", "custom.activate"), 1.0f, 1.0f);
        switch (effect) {
            case "speed" -> {
                org.bukkit.util.Vector direction = p.getLocation().getDirection().setY(0).normalize().multiply(getConfig().getDouble("effects.speed.dash-power", 2.6));
                p.setVelocity(direction.setY(Math.max(0.15, p.getVelocity().getY())));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, 1, true, false, true));
            }
            case "feather" -> {
                p.setVelocity(p.getVelocity().setY(Math.max(1.1, p.getVelocity().getY())));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, durationTicks, 0, true, false, true));
            }
            case "haste" -> p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, durationTicks, 1, true, false, true));
            case "strength" -> p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, durationTicks, 1, true, false, true));
            case "regeneration" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, durationTicks, 1, true, false, true));
                healTrustedNearby(p, 8, 0.2);
            }
            case "heart" -> {
                var attribute = p.getAttribute(Attribute.MAX_HEALTH);
                if (attribute != null) attribute.setBaseValue(40.0);
                p.setHealth(Math.min(40.0, p.getHealth() + Math.max(2.0, p.getMaxHealth() * getConfig().getDouble("effects.heart.spark-heal-percentage", 0.2))));
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (!p.isOnline()) return;
                    var currentAttribute = p.getAttribute(Attribute.MAX_HEALTH);
                    double restoredMax = Arrays.asList(slots(p.getUniqueId())).contains("heart") ? 30.0 : 20.0;
                    if (currentAttribute != null) currentAttribute.setBaseValue(restoredMax);
                    if (p.getHealth() > restoredMax) p.setHealth(restoredMax);
                }, durationTicks);
            }
            case "invisibility" -> activateInvisibility(p, durationTicks);
            case "fire" -> {
                p.setFireTicks(0);
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, durationTicks, 0, true, false, true));
                for (org.bukkit.entity.Entity entity : p.getNearbyEntities(6, 4, 6)) {
                    if (!(entity instanceof org.bukkit.entity.LivingEntity living) || living.equals(p)) continue;
                    if (entity instanceof Player target && trusted.getOrDefault(p.getUniqueId(), Set.of()).contains(target.getUniqueId())) continue;
                    living.setFireTicks(Math.max(living.getFireTicks(), getConfig().getInt("effects.fire.ignite-ticks", 100)));
                    living.setVelocity(living.getVelocity().setY(Math.max(0.7, living.getVelocity().getY())));
                }
            }
            case "thunder" -> {
                int strikes = 0;
                for (org.bukkit.entity.Entity entity : p.getNearbyEntities(20, 10, 20)) {
                    if (!(entity instanceof org.bukkit.entity.LivingEntity living) || living.equals(p)) continue;
                    if (entity instanceof Player target && trusted.getOrDefault(p.getUniqueId(), Set.of()).contains(target.getUniqueId())) continue;
                    p.getWorld().strikeLightning(living.getLocation());
                    if (++strikes >= getConfig().getInt("effects.thunder.lightning-chain-max", 10)) break;
                }
            }
            case "ocean" -> {
                p.setRemainingAir(p.getMaximumAir());
                p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, durationTicks, 0, true, false, true));
                for (org.bukkit.entity.Entity entity : p.getNearbyEntities(6, 5, 6)) {
                    if (!(entity instanceof Player target) || target.equals(p)) continue;
                    if (trusted.getOrDefault(p.getUniqueId(), Set.of()).contains(target.getUniqueId())) continue;
                    org.bukkit.util.Vector pull = p.getLocation().toVector().subtract(target.getLocation().toVector()).normalize().multiply(0.8);
                    target.setVelocity(target.getVelocity().add(pull));
                }
            }
            case "frost" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, durationTicks, 1, true, false, true));
                for (org.bukkit.entity.Entity entity : p.getNearbyEntities(8, 5, 8)) {
                    if (!(entity instanceof org.bukkit.entity.LivingEntity living) || living.equals(p)) continue;
                    if (entity instanceof Player target && trusted.getOrDefault(p.getUniqueId(), Set.of()).contains(target.getUniqueId())) continue;
                    living.setFreezeTicks(Math.max(living.getFreezeTicks(), getConfig().getInt("effects.frost.freeze-ticks", 600)));
                    living.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, durationTicks, 1, true, false, true));
                }
            }
            case "emerald" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, durationTicks, 4, true, false, true));
                p.giveExp(getConfig().getInt("effects.emerald.spark-xp-gain", 2));
            }
            case "ender" -> {
                org.bukkit.block.Block targetBlock = p.getTargetBlockExact(15);
                if (targetBlock != null && targetBlock.getType().isSolid()) {
                    org.bukkit.Location destination = targetBlock.getLocation().add(0.5, 1, 0.5);
                    if (destination.getWorld() == p.getWorld() && destination.distanceSquared(p.getLocation()) <= 225) p.teleport(destination);
                }
            }
            case "earth" -> p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, durationTicks, 1, true, false, true));
        }
        p.sendMessage(mini.deserialize("<#A855F7>"+title(effect)+" Spark activated."));
    }

    private void healTrustedNearby(Player caster, double radius, double percentage) {
        double amount = Math.max(1.0, caster.getMaxHealth() * percentage);
        caster.setHealth(Math.min(caster.getMaxHealth(), caster.getHealth() + amount));
        for (Player target : caster.getWorld().getPlayers()) {
            if (target.equals(caster) || !trusted.getOrDefault(caster.getUniqueId(), Set.of()).contains(target.getUniqueId())) continue;
            if (target.getLocation().distanceSquared(caster.getLocation()) > radius * radius) continue;
            target.setHealth(Math.min(target.getMaxHealth(), target.getHealth() + amount));
        }
    }

    private void activateInvisibility(Player player, int durationTicks) {
        invisiblePlayers.add(player.getUniqueId());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(player) || trusted.getOrDefault(player.getUniqueId(), Set.of()).contains(viewer.getUniqueId())) {
                viewer.showPlayer(this, player);
            } else {
                viewer.hidePlayer(this, player);
            }
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, durationTicks, 0, true, false, true));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            invisiblePlayers.remove(player.getUniqueId());
            for (Player viewer : Bukkit.getOnlinePlayers()) viewer.showPlayer(this, player);
        }, durationTicks);
    }

    private void applyPassives() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String[] activeSlots = slots(player.getUniqueId());
            if (disabled(player)) continue;
            Set<String> active = new HashSet<>(Arrays.asList(activeSlots));
            active.remove("");
            if (active.contains("speed")) player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, true, false, true));
            if (active.contains("haste")) player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 0, true, false, true));
            if (active.contains("strength")) player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, true, false, true));
            if (active.contains("regeneration")) player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, true, false, true));
            if (active.contains("fire")) player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40, 0, true, false, true));
            if (active.contains("emerald")) player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 40, 2, true, false, true));
            if (active.contains("ocean")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 40, 0, true, false, true));
                if (player.isInWater()) player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 40, 0, true, false, true));
            }
            if (active.contains("frost") && (player.getLocation().getBlock().getType().name().contains("ICE") ||
                    player.getLocation().subtract(0, 1, 0).getBlock().getType().name().contains("ICE") ||
                    player.getLocation().subtract(0, 0, 0).getBlock().getType().name().contains("SNOW"))) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, true, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, true, false, true));
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
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, true, false, true));
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    if (viewer.equals(player) || trusted.getOrDefault(player.getUniqueId(), Set.of()).contains(viewer.getUniqueId())) viewer.showPlayer(this, player);
                    else viewer.hidePlayer(this, player);
                }
            } else if (invisiblePlayers.remove(player.getUniqueId())) {
                for (Player viewer : Bukkit.getOnlinePlayers()) viewer.showPlayer(this, player);
            }
            if (active.contains("ender")) {
                for (Player other : player.getWorld().getPlayers()) {
                    if (other.equals(player) || trusted.getOrDefault(player.getUniqueId(), Set.of()).contains(other.getUniqueId())) continue;
                    if (other.getLocation().distanceSquared(player.getLocation()) <= 64) {
                        other.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, true, false, true));
                    }
                }
            }
        }
    }

    @EventHandler public void environmentalDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        String[] active = slots(player.getUniqueId());
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL && Arrays.asList(active).contains("feather")) {
            event.setCancelled(true);
        }
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.DROWNING && Arrays.asList(active).contains("ocean")) {
            event.setCancelled(true);
        }
    }
    @EventHandler public void damage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof org.bukkit.entity.LivingEntity target)) return;
        if (target instanceof Player victim && trusted.getOrDefault(attacker.getUniqueId(), Set.of()).contains(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        String[] activeSlots = slots(attacker.getUniqueId());
        Set<String> active = new HashSet<>(Arrays.asList(activeSlots));
        active.remove("");
        if (active.contains("strength")) {
            double multiplier = target instanceof Player ? 1.2 : getConfig().getDouble("effects.strength.mob-damage-multiplier", 2.0);
            event.setDamage(event.getDamage() * multiplier);
        }
        if (active.contains("fire") && countHit(attacker, "fire") % getConfig().getInt("effects.fire.trigger-hits", 5) == 0) {
            target.setFireTicks(Math.max(target.getFireTicks(), getConfig().getInt("effects.fire.ignite-ticks", 100)));
        }
        if (active.contains("thunder")) {
            int threshold = getConfig().getInt("effects.thunder.trigger-hits", 10);
            if (attacker.getWorld().hasStorm() && attacker.getWorld().isThundering()) threshold = Math.max(1, threshold / 2);
            if (countHit(attacker, "thunder") % threshold == 0) {
                attacker.getWorld().strikeLightningEffect(target.getLocation());
                if (getConfig().getBoolean("settings.allow-true-damage", true)) event.setDamage(event.getDamage() + getConfig().getDouble("effects.thunder.true-damage", 1.0));
            }
        }
        if (active.contains("frost") && countHit(attacker, "frost") % getConfig().getInt("effects.frost.trigger-hits", 10) == 0) {
            target.setFreezeTicks(Math.max(target.getFreezeTicks(), getConfig().getInt("effects.frost.freeze-ticks", 600)));
            target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 200, 1, true, false, true));
        }
        if (active.contains("invisibility") && countHit(attacker, "invisibility") % getConfig().getInt("effects.invisibility.trigger-hits", 10) == 0) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 0, true, false, true));
        }
        if (active.contains("regeneration")) attacker.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 1, true, false, true));
        if (active.contains("emerald") && target instanceof Player) {
            attacker.giveExp(getConfig().getInt("effects.emerald.xp-gain-per-hit", 1));
        }
    }

    private int countHit(Player player, String effect) {
        String key = player.getUniqueId() + ":" + effect;
        int count = hitCounters.getOrDefault(key, 0) + 1;
        hitCounters.put(key, count);
        return count;
    }
    @EventHandler public void dropControl(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        String action = player.isSneaking() ? "sneak-drop" : "drop";
        triggerBoundSpark(player, action, event);
    }

    @EventHandler public void swapControl(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        String action = player.isSneaking() ? "sneak-swap" : "swap";
        triggerBoundSpark(player, action, event);
    }

    @EventHandler public void sneakControl(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        triggerBoundSpark(event.getPlayer(), "sneak", event);
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

    @EventHandler public void join(PlayerJoinEvent e) {
        Player joining = e.getPlayer();
        slots(joining.getUniqueId());
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (invisiblePlayers.contains(online.getUniqueId()) && !trusted.getOrDefault(online.getUniqueId(), Set.of()).contains(joining.getUniqueId())) joining.hidePlayer(this, online);
            if (invisiblePlayers.contains(joining.getUniqueId()) && !trusted.getOrDefault(joining.getUniqueId(), Set.of()).contains(online.getUniqueId())) online.hidePlayer(this, joining);
        }
        String url=getConfig().getString("settings.resource-pack.url","");
        if(getConfig().getBoolean("settings.resource-pack.enabled") && !url.isBlank()) {
            String hash=getConfig().getString("settings.resource-pack.sha1","");
            if(hash.matches("(?i)[0-9a-f]{40}")) e.getPlayer().setResourcePack(url, java.util.HexFormat.of().parseHex(hash), getConfig().getString("settings.resource-pack.prompt","Infuse resource pack"), false);
        }
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
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