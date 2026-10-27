package com.supainfuse.smp.listeners;

import com.supainfuse.smp.Infuse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/** Handles the configurable player actions used to activate Spark abilities. */
public final class SparkControlListener implements Listener {
    private final Infuse plugin;

    public SparkControlListener(Infuse plugin) { this.plugin = plugin; }

    @EventHandler public void onDrop(PlayerDropItemEvent event) { plugin.dropControl(event); }
    @EventHandler public void onHandSwap(PlayerSwapHandItemsEvent event) { plugin.swapControl(event); }
    @EventHandler public void onSneak(PlayerToggleSneakEvent event) { plugin.sneakControl(event); }
}
