package com.supainfuse.smp.effects;

import com.supainfuse.smp.Infuse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/** Dispatches each Spark activation to the matching effect behavior. */
public final class SparkAbilityManager {
    private final Infuse plugin;

    public SparkAbilityManager(Infuse plugin) {
        this.plugin = plugin;
    }

    public void activate(Player player, String effect, int durationTicks) {
        switch (effect) {
            case "speed" -> plugin.activateSpeedDash(player, durationTicks);
            case "feather" -> {
                player.setVelocity(player.getVelocity().setY(0));
                player.setVelocity(player.getVelocity().setY(plugin.getConfig().getDouble("effects.feather.initial-bounce-power", 2.0)));
                plugin.activateFeatherShockwave(player);
            }
            case "haste" -> plugin.activateHasteSpark(player);
            case "strength" -> player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, durationTicks, 1, true, false, true));
            case "regeneration" -> plugin.activateRegenerationSpark(player);
            case "heart" -> activateHeart(player, durationTicks);
            case "invisibility" -> plugin.activateInvisibility(player, durationTicks);
            case "fire" -> activateFire(player, durationTicks);
            case "thunder" -> plugin.activateThunderSpark(player);
            case "ocean" -> {
                player.setRemainingAir(player.getMaximumAir());
                player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, durationTicks, 0, true, false, true));
                plugin.activateOceanSpark(player);
            }
            case "frost" -> activateFrost(player, durationTicks);
            case "emerald" -> player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, durationTicks, 4, true, false, true));
            case "ender" -> activateEnder(player);
            default -> player.sendMessage("<red>That effect does not have a Spark ability.");
        }
    }

    private void activateHeart(Player player, int durationTicks) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) attribute.setBaseValue(40.0);
        double heal = plugin.recentDamageAmount(player.getUniqueId())
                * plugin.getConfig().getDouble("effects.heart.spark-heal-percentage", 0.2);
        player.setHealth(Math.min(40.0, player.getHealth() + Math.max(0.0, heal)));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            var currentAttribute = player.getAttribute(Attribute.MAX_HEALTH);
            double restoredMax = Arrays.asList(plugin.slots(player.getUniqueId())).contains("heart") ? 30.0 : 20.0;
            if (currentAttribute != null) currentAttribute.setBaseValue(restoredMax);
            if (player.getHealth() > restoredMax) player.setHealth(restoredMax);
        }, durationTicks);
    }

    private void activateFire(Player player, int durationTicks) {
        player.setFireTicks(0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, durationTicks, 0, true, false, true));
        double radius = plugin.getConfig().getDouble("effects.fire.launch-radius", 6.0);
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || living.equals(player)) continue;
            if (entity instanceof Player target && plugin.isTrusted(player.getUniqueId(), target.getUniqueId())) continue;
            living.setFireTicks(Math.max(living.getFireTicks(), plugin.getConfig().getInt("effects.fire.ignite-duration-ticks", 200)));
            living.setVelocity(living.getVelocity().setY(Math.max(
                    plugin.getConfig().getDouble("effects.fire.launch-power", 3.0), living.getVelocity().getY())));
        }
        int blockRadius = Math.max(1, (int) Math.ceil(plugin.getConfig().getDouble("effects.fire.burn-radius", 6.0)));
        org.bukkit.Location origin = player.getLocation();
        for (int x = -blockRadius; x <= blockRadius; x++) {
            for (int y = -blockRadius; y <= blockRadius; y++) {
                for (int z = -blockRadius; z <= blockRadius; z++) {
                    if (x * x + y * y + z * z > blockRadius * blockRadius) continue;
                    org.bukkit.block.Block block = origin.getWorld().getBlockAt(
                            origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z);
                    if (block.getType() == Material.WATER || block.getType() == Material.COBWEB) {
                        block.setType(Material.AIR, false);
                    } else if (block.getType() == Material.AIR &&
                            block.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid() &&
                            ThreadLocalRandom.current().nextDouble() < 0.02) {
                        block.setType(Material.FIRE, false);
                    }
                }
            }
        }
        player.getWorld().spawnParticle(org.bukkit.Particle.FLAME, player.getLocation().add(0, 1, 0), 40, 2.5, 1.5, 2.5, 0.05);
        player.playSound(player.getLocation(), "block.lava.extinguish", 1.0f, 0.8f);
    }

    private void activateFrost(Player player, int durationTicks) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, durationTicks, 1, true, false, true));
        for (Entity entity : player.getNearbyEntities(8, 5, 8)) {
            if (!(entity instanceof LivingEntity living) || living.equals(player)) continue;
            if (entity instanceof Player target && plugin.isTrusted(player.getUniqueId(), target.getUniqueId())) continue;
            living.setFreezeTicks(Math.max(living.getFreezeTicks(), plugin.getConfig().getInt("effects.frost.freeze-ticks", 600)));
            living.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, durationTicks, 1, true, false, true));
        }
    }

    private void activateEnder(Player player) {
        int maxDistance = Math.max(1, plugin.getConfig().getInt("effects.ender.teleport-max-radius", 15));
        org.bukkit.block.Block targetBlock = player.getTargetBlockExact(maxDistance);
        org.bukkit.Location destination;
        if (targetBlock != null && targetBlock.getType().isSolid()) {
            destination = targetBlock.getLocation().add(0.5, 1, 0.5);
        } else {
            destination = player.getLocation().clone().add(player.getLocation().getDirection().normalize().multiply(maxDistance));
        }
        if (destination.getWorld() == player.getWorld() &&
                destination.distanceSquared(player.getLocation()) <= maxDistance * maxDistance + 1) {
            org.bukkit.Location safeDestination = destination.clone();
            if (!safeDestination.getBlock().isPassable() || !safeDestination.clone().add(0, 1, 0).getBlock().isPassable()) {
                safeDestination.setY(safeDestination.getY() + 1);
            }
            player.teleport(safeDestination);
        }
        for (Entity entity : player.getNearbyEntities(2, 2, 2)) {
            if (entity instanceof Player target) {
                if (plugin.isTrusted(player.getUniqueId(), target.getUniqueId())) continue;
                plugin.applyEnderCurse(player, target);
                plugin.markEnderSparkCooldownReset(player.getUniqueId());
            } else if (entity instanceof LivingEntity living &&
                    living.getType() != org.bukkit.entity.EntityType.ENDER_DRAGON &&
                    living.getType() != org.bukkit.entity.EntityType.WITHER &&
                    living.getType() != org.bukkit.entity.EntityType.WARDEN) {
                living.setHealth(0);
            }
        }
    }
}
