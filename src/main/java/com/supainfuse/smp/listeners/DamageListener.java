package com.supainfuse.smp.listeners;

import com.supainfuse.smp.Infuse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void shareEnderCurseDamage(EntityDamageByEntityEvent event) {
        plugin.shareCursedDamage(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void trackRecentDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;
        plugin.recordDamageDealt(attacker, event.getFinalDamage());
    }
    @EventHandler public void onEnvironmentalDamage(EntityDamageEvent event) { plugin.environmentalDamage(event); }
    @EventHandler public void onMobTarget(EntityTargetLivingEntityEvent event) { plugin.invisibilityMobTarget(event); }
    @EventHandler public void onPotionEffect(EntityPotionEffectEvent event) { plugin.invisibilityPotionImmunity(event); }
}
