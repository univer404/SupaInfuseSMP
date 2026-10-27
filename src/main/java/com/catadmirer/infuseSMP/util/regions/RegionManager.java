package com.catadmirer.infuseSMP.util.regions;

import org.bukkit.Location;

import java.util.List;

/** Optional integration boundary for regions where Infuse abilities are disabled. */
public interface RegionManager {
    boolean isDisabled(Location location);
    boolean add(String worldName, String regionId);
    boolean remove(String worldName, String regionId);
    List<String> list();
    void clear();
    String bounds(String worldName, String regionId);
}
