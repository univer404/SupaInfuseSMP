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
    private static final List<String> EFFECTS = List.of("earth","emerald","ender","feather","fire","frost","haste","heart","invisibility","ocean","regeneration","speed","strength","thunder");
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Map<UUID, String[]> equipped = new HashMap<>();
    private final Map<UUID, Set<UUID>> trusted = new HashMap<>();
    private final Map<UUID, Integer> selectionMode = new HashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private NamespacedKey effectKey;
    private Inventory mainMenu;
    private Inventory effectMenu;
    private BukkitTask passiveTask;

    @Override public void onEnable() {
        saveDefaultConfig();
        saveResource("gui.yml", false);
        saveResource("messages.yml", false);
        effectKey = new NamespacedKey(this, "effect");
        loadData();
        Objects.requireNonNull(getCommand("infuse")).setExecutor(this);
        Objects.requireNonNull(getCommand("infuse")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("trust")).setExecutor(this);
        Objects.requireNonNull(getCommand("trust")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
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
        for (var entry : equipped.entrySet()) {
            String path = "players." + entry.getKey();
            getConfig().set(path + ".primary", entry.getValue()[0]);
            getConfig().set(path + ".secondary", entry.getValue()[1]);
            getConfig().set(path + ".trusted", trusted.getOrDefault(entry.getKey(), Set.of()).stream().map(UUID::toString).toList());
        }
        saveConfig();
    }

    private String message(String key) {
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(), "messages.yml"));
        return cfg.getString("prefix", "<#A855F7>Infuse » ") + cfg.getString(key, "<red>Missing message: " + key);
    }
    private void tell(CommandSender sender, String key) { sender.sendMessage(mini.deserialize(message(key))); }
    private String clean(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }
    private String[] slots(UUID id) { return equipped.computeIfAbsent(id, ignored -> new String[]{"", ""}); }
    private boolean disabled(Player p) { return getConfig().getStringList("settings.disabled-worlds").contains(p.getWorld().getName()); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("trust")) return trustCommand(sender, args);
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
        var gui = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(), "gui.yml"));
        int size = Math.max(9, Math.min(54, gui.getInt("main.rows", 3) * 9));
        Inventory inv = Bukkit.createInventory(p, size, mini.deserialize(gui.getString("main.title", "<light_purple>Infuse")));
        ItemStack filler = named(Material.BLACK_STAINED_GLASS_PANE, gui.getString("main.filler-name", " "));
        for (int i=0;i<size;i++) inv.setItem(i, filler);
        inv.setItem(gui.getInt("main.primary-slot",11), named(Material.NETHER_STAR, "<#C4B5FD>Primary: " + display(slots(p.getUniqueId())[0])));
        inv.setItem(gui.getInt("main.secondary-slot",15), named(Material.AMETHYST_SHARD, "<#A855F7>Secondary: " + display(slots(p.getUniqueId())[1])));
        inv.setItem(gui.getInt("main.effects-slot",13), named(Material.ENCHANTED_BOOK, "<#C4B5FD>Browse / equip effects"));
        inv.setItem(gui.getInt("main.close-slot",22), named(Material.BARRIER, "<red>Close"));
        mainMenu = inv; p.openInventory(inv);
    }
    private void openEffects(Player p, int slot) {
        var gui = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(), "gui.yml"));
        int size = Math.max(9, Math.min(54, gui.getInt("effects.rows",6)*9));
        Inventory inv = Bukkit.createInventory(p, size, mini.deserialize(gui.getString("effects.title","<light_purple>Choose an Effect")));
        ItemStack filler = named(Material.BLACK_STAINED_GLASS_PANE, " ");
        for(int i=0;i<size;i++) inv.setItem(i,filler);
        List<Integer> positions = gui.getIntegerList("effects.slots");
        for(int i=0;i<EFFECTS.size() && i<positions.size();i++) {
            String effect=EFFECTS.get(i);
            inv.setItem(positions.get(i), createEffectItem(effect,false));
        }
        inv.setItem(gui.getInt("effects.close-slot",49), named(Material.BARRIER,"<red>Close"));
        inv.setItem(gui.getInt("effects.back-slot",45), named(Material.ARROW,"<yellow>Back"));
        p.openInventory(inv);
    }
    private ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material); ItemMeta meta=item.getItemMeta();
        meta.displayName(mini.deserialize(name)); item.setItemMeta(meta); return item;
    }
    private ItemStack createEffectItem(String effect, boolean augmented) {
        Material material = switch(effect) {
            case "earth" -> Material.MOSS_BLOCK; case "emerald" -> Material.EMERALD; case "ender" -> Material.ENDER_PEARL;
            case "feather" -> Material.FEATHER; case "fire" -> Material.BLAZE_POWDER; case "frost" -> Material.PACKED_ICE;
            case "haste" -> Material.GOLDEN_PICKAXE; case "heart" -> Material.GOLDEN_APPLE; case "invisibility" -> Material.FERMENTED_SPIDER_EYE;
            case "ocean" -> Material.HEART_OF_THE_SEA; case "regeneration" -> Material.GHAST_TEAR; case "speed" -> Material.SUGAR;
            case "strength" -> Material.BLAZE_ROD; case "thunder" -> Material.TRIDENT; default -> Material.AMETHYST_SHARD;
        };
        ItemStack item = new ItemStack(material); ItemMeta meta=item.getItemMeta();
        meta.displayName(mini.deserialize("<bold><#A855F7>" + (augmented ? "Augmented " : "") + title(effect) + " Infuse"));
        meta.lore(List.of(mini.deserialize("<gray>Right-click while held to activate Spark"), mini.deserialize("<dark_gray>Infuse effect: " + effect)));
        meta.getPersistentDataContainer().set(effectKey, PersistentDataType.STRING, effect + (augmented ? ":augmented" : ""));
        item.setItemMeta(meta); return item;
    }
    private String title(String effect) { return effect.substring(0,1).toUpperCase(Locale.ROOT)+effect.substring(1); }
    private String display(String effect) { return effect == null || effect.isBlank() ? "None" : title(effect); }

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
        String[] s=slots(p.getUniqueId());
        String effect=!s[1].isBlank()?s[1]:s[0];
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
        for(Player p:Bukkit.getOnlinePlayers()) {
            if(disabled(p)) continue;
            for(String effect:slots(p.getUniqueId())) {
                if(effect==null || effect.isBlank()) continue;
                switch(effect) {
                    case "speed" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,40,0,true,false,true));
                    case "haste" -> p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,40,0,true,false,true));
                    case "strength" -> p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,40,0,true,false,true));
                    case "regeneration" -> p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,40,0,true,false,true));
                    case "heart" -> { try { var attr=p.getAttribute(Attribute.MAX_HEALTH); if(attr!=null && attr.getBaseValue()<24) attr.setBaseValue(24); } catch(Exception ignored){} }
                    case "invisibility" -> { if(p.isSneaking()) p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,40,0,true,false,true)); }
                    case "ocean" -> { if(p.isInWater()) p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE,40,0,true,false,true)); }
                }
            }
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
        if(args.length==1) return List.of("gui","give","clear","spark","reload").stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if(args.length==3 && args[0].equalsIgnoreCase("give")) return EFFECTS;
        return List.of();
    }
}