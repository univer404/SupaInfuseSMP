package com.catadmirer.infuseSMP.listeners;

import com.catadmirer.infuseSMP.Infuse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

/** Routes combat, environmental damage, and invisibility targeting events. */
public final class DamageListener implements Listener {
    private final Infuse plugin;

    public DamageListener(Infuse plugin) { this.plugin = plugin; }

    @EventHandler public void onDamage(EntityDamageByEntityEvent event) { plugin.damage(event); }
    @EventHandler public void onEnvironmentalDamage(EntityDamageEvent event) { plugin.environmentalDamage(event); }
    @EventHandler public void onMobTarget(EntityTargetLivingEntityEvent event) { plugin.invisibilityMobTarget(event); }
    @EventHandler public void onPotionEffect(EntityPotionEffectEvent event) { plugin.invisibilityPotionImmunity(event); }
}
