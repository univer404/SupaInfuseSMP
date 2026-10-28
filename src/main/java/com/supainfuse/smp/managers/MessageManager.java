package com.supainfuse.smp.managers;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Objects;

/** Loads messages.yml once and provides consistent prefixed message strings. */
public final class MessageManager {
    private final JavaPlugin plugin;
    private final MiniMessage miniMessage;
    private YamlConfiguration messages;

    public MessageManager(JavaPlugin plugin, MiniMessage miniMessage) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.miniMessage = Objects.requireNonNull(miniMessage, "miniMessage");
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        this.messages = YamlConfiguration.loadConfiguration(file);
    }

    public String get(String key) {
        String prefix = messages.getString("prefix", "<#A855F7>Infuse » ");
        String fallback = "<red>Missing message: " + key;
        return prefix + messages.getString(key, fallback);
    }

    public net.kyori.adventure.text.Component component(String key) {
        return miniMessage.deserialize(get(key));
    }
}
