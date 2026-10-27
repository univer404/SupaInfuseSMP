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
    private final Map<UUID, Set<UUID>> trusted = new HashMap<>();
    private final Map<UUID, Integer> selectionMode = new HashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private NamespacedKey effectKey;
    private InfuseGui gui;
    private BukkitTask passiveTask;

    @Override public void onEnable() {
        saveDefaultConfig();
        saveResource("gui.yml", false);
        saveResource("messages.yml", false);
        effectKey = new NamespacedKey(this, "effect");
        gui = new InfuseGui(this);
        loadData();
        Objects.requireNonNull(getCommand("infuse")).setExecutor(this);
        Objects.requireNonNull(getCommand("infuse")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("trust")).setExecutor(this);
        Objects.requireNonNull(getCommand("trust")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new InfuseCraftingGui(this), this);
        passiveTask = Bukkit.getScheduler().runTaskTimer(this, this::applyPassives, 20L, 20L);
        getLogger().info("Infuse initialized. " + EFFECTS.size() + " effect identifiers registered.");
    }

    @Override public void onDisable() {
        if (passiveTask != null) passiveTask.cancel();
        saveData();
    }

    private void loadData() {
        var data = getConfig().getConfigurationSection("players");
        if (data == null) return;
        for (String id : data.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(id);
                equipped.put(uuid, new String[]{data.getString(id + ".primary", ""), data.getString(id + ".secondary", "")});
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
            p.sendMessage(mini.deserialize("<light_purple>Use a sneaking right-click on a brewing stand to open the Infuse crafting menu."));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            if (!(sender instanceof Player p)) { tell(sender, "player-only"); return true; }
            openMain(p); return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("infuse.admin")) { tell(sender, "no-permission"); return true; }
            reloadConfig(); saveDefaultConfig(); sender.sendMessage(mini.deserialize(message("reload"))); return true;
        }
        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("infuse.admin")) { tell(sender, "no-permission"); return true; }
            if (args.length < 3) { sender.sendMessage("/infuse give <player> <effect> [augmented]"); return true; }
            Player target = Bukkit.getPlayerExact(args[1]); String effect = clean(args[2]);
            if (target == null) { tell(sender, "not-player"); return true; }
            if (!EFFECTS.contains(effect)) { tell(sender, "unknown-effect"); return true; }
            boolean augmented = args.length > 3 && args[3].equalsIgnoreCase("augmented");
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
                list.add(target.getUniqueId()); saveData();
                p.sendMessage(mini.deserialize(message("trust-added").replace("{player}", target.getName())));
            }
            case "remove" -> {
                if (args.length < 2) { p.sendMessage("/trust remove <player>"); return true; }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]); list.remove(target.getUniqueId()); saveData();
                p.sendMessage(mini.deserialize(message("trust-removed").replace("{player}", args[1])));
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
            String effect=encoded.split(":")[0]; String[] slots=slots(p.getUniqueId());
            int target = selectionMode.getOrDefault(p.getUniqueId(), 0);
            slots[target]=effect;
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
        long seconds=getConfig().getLong("effects."+effect+".spark-seconds",15);
        cooldowns.put(key,now+seconds*1000);
        switch(effect) {
            case "speed","feather" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,100,1,true,false,true));
            case "haste" -> p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,100,1,true,false,true));
            case "strength" -> p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,100,1,true,false,true));
            case "regeneration","heart" -> p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,100,1,true,false,true));
            case "invisibility" -> p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,100,0,true,false,true));
            case "fire" -> { p.setFireTicks(0); p.getNearbyEntities(5,3,5).stream().filter(ent -> ent instanceof org.bukkit.entity.LivingEntity).map(ent -> (org.bukkit.entity.LivingEntity)ent).filter(ent -> !ent.equals(p)).forEach(ent -> ent.setFireTicks(100)); }
            case "thunder" -> p.getNearbyEntities(8,5,8).stream().filter(ent -> ent instanceof org.bukkit.entity.LivingEntity).map(ent -> (org.bukkit.entity.LivingEntity)ent).filter(ent -> !ent.equals(p)).forEach(ent -> p.getWorld().strikeLightning(ent.getLocation()));
            case "ocean" -> { p.setRemainingAir(p.getMaximumAir()); p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE,100,0,true,false,true)); }
            case "frost" -> p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,100,1,true,false,true));
            case "emerald" -> p.giveExp(10);
            case "ender" -> { org.bukkit.Location destination = p.getTargetBlockExact(24) == null ? null : p.getTargetBlockExact(24).getLocation().add(0.5, 1, 0.5); if (destination != null && destination.getWorld() == p.getWorld()) p.teleport(destination); }
            case "earth" -> p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,100,1,true,false,true));
        }
        p.sendMessage(mini.deserialize("<#A855F7>"+title(effect)+" Spark activated."));
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
            if (active.contains("invisibility")) player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, true, false, true));
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
    @EventHandler public void damage(EntityDamageByEntityEvent e) {
        if(!(e.getDamager() instanceof Player attacker)) return;
        String[] s=slots(attacker.getUniqueId());
        if(Arrays.asList(s).contains("strength")) e.setDamage(e.getDamage()*1.2);
        if(e.getEntity() instanceof Player victim && trusted.getOrDefault(attacker.getUniqueId(),Set.of()).contains(victim.getUniqueId())) e.setCancelled(true);
    }
    @EventHandler public void join(PlayerJoinEvent e) {
        slots(e.getPlayer().getUniqueId());
        String url=getConfig().getString("settings.resource-pack.url","");
        if(getConfig().getBoolean("settings.resource-pack.enabled") && !url.isBlank()) {
            String hash=getConfig().getString("settings.resource-pack.sha1","");
            if(hash.matches("(?i)[0-9a-f]{40}")) e.getPlayer().setResourcePack(url, java.util.HexFormat.of().parseHex(hash), getConfig().getString("settings.resource-pack.prompt","Infuse resource pack"), false);
        }
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if(command.getName().equalsIgnoreCase("trust")) return args.length==1?List.of("add","remove","list","clear"):args.length==2?Bukkit.getOnlinePlayers().stream().map(Player::getName).toList():List.of();
        if(args.length==1) return List.of("gui","give","clear","spark","reload","add","remove","get","swap","history").stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if(args.length==3 && args[0].equalsIgnoreCase("give")) return EFFECTS;
        return List.of();
    }
}