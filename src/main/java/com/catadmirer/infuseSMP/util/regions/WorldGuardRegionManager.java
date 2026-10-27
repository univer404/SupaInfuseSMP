package com.catadmirer.infuseSMP.util.regions;

import com.catadmirer.infuseSMP.Infuse;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Optional WorldGuard bridge for Infuse's disabled-region list. */
public final class WorldGuardRegionManager implements RegionManager {
    private final Infuse plugin;

    public WorldGuardRegionManager(Infuse plugin) {
        this.plugin = plugin;
    }

    public boolean isDisabled(Location location) {
        if (location == null || location.getWorld() == null || !Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return false;
        for (String mapped : plugin.getConfig().getStringList("settings.disabled-regions")) {
            String[] parts = mapped.split("\\|", 2);
            if (parts.length != 2 || !parts[0].equals(location.getWorld().getName())) continue;
            ProtectedRegion region = getRegion(location.getWorld(), parts[1]);
            if (region != null && region.contains(BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ()))) return true;
        }
        return false;
    }

    public boolean add(String worldName, String regionId) {
        World world = Bukkit.getWorld(worldName);
        if (world == null || getRegion(world, regionId) == null) return false;
        List<String> mapped = new ArrayList<>(plugin.getConfig().getStringList("settings.disabled-regions"));
        String key = worldName + "|" + regionId;
        if (!mapped.contains(key)) mapped.add(key);
        plugin.getConfig().set("settings.disabled-regions", mapped);
        plugin.saveConfig();
        return true;
    }

    public boolean remove(String worldName, String regionId) {
        List<String> mapped = new ArrayList<>(plugin.getConfig().getStringList("settings.disabled-regions"));
        boolean removed = mapped.remove(worldName + "|" + regionId);
        plugin.getConfig().set("settings.disabled-regions", mapped);
        plugin.saveConfig();
        return removed;
    }

    public List<String> list() {
        return new ArrayList<>(plugin.getConfig().getStringList("settings.disabled-regions"));
    }

    public void clear() {
        plugin.getConfig().set("settings.disabled-regions", new ArrayList<>());
        plugin.saveConfig();
    }

    private ProtectedRegion getRegion(World world, String id) {
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        return manager == null ? null : manager.getRegion(id);
    }

    public String bounds(String worldName, String regionId) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return "world unavailable";
        ProtectedRegion region = getRegion(world, regionId);
        if (region == null) return "region not found";
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        return "(" + min.x() + ", " + min.y() + ", " + min.z() + ") to (" + max.x() + ", " + max.y() + ", " + max.z() + ")";
    }
}
