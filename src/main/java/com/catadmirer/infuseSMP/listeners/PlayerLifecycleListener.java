package com.supainfuse.smp.listeners;

import com.supainfuse.smp.Infuse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Handles player join, quit, and death lifecycle events. */
public final class PlayerLifecycleListener implements Listener {
    private final Infuse plugin;

    public PlayerLifecycleListener(Infuse plugin) { this.plugin = plugin; }

    @EventHandler public void onJoin(PlayerJoinEvent event) { plugin.join(event); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { plugin.quit(event); }
    @EventHandler public void onDeath(PlayerDeathEvent event) { plugin.onPlayerDeath(event); }
}
