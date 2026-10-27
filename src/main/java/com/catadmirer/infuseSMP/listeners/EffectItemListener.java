package com.catadmirer.infuseSMP.listeners;

import com.catadmirer.infuseSMP.Infuse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

/** Routes effect item interactions, projectiles, inventory actions, and XP events. */
public final class EffectItemListener implements Listener {
    private final Infuse plugin;

    public EffectItemListener(Infuse plugin) { this.plugin = plugin; }

    @EventHandler public void onFireSnowball(PlayerInteractEvent event) { plugin.convertFireSnowballs(event); }
    @EventHandler public void onInvisibilityProjectile(ProjectileHitEvent event) { plugin.invisibilityRangedHit(event); }
    @EventHandler public void onTridentHit(ProjectileHitEvent event) { plugin.infuseTridentEffects(event); }
    @EventHandler public void onEffectConsume(PlayerItemConsumeEvent event) { plugin.onEffectPotionConsume(event); }
    @EventHandler public void onExperienceGain(PlayerExpChangeEvent event) { plugin.onExperienceGain(event); }
    @EventHandler public void onInventoryClick(InventoryClickEvent event) { plugin.inventoryClick(event); }
}
