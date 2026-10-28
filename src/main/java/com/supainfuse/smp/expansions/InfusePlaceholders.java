package com.supainfuse.smp.expansions;

import com.supainfuse.smp.Infuse;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Optional PlaceholderAPI expansion for the placeholders shipped by the original Skript. */
public final class InfusePlaceholders extends PlaceholderExpansion {
    private final Infuse plugin;

    public InfusePlaceholders(Infuse plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "infuse";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SupaInfuseSMP";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        return plugin.placeholderValue(player, params);
    }
}
