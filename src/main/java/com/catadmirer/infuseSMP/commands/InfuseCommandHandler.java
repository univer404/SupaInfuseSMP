package com.supainfuse.smp.commands;

import com.supainfuse.smp.Infuse;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Objects;

/** Thin Bukkit command adapter. Command behavior lives in the plugin's command service. */
public final class InfuseCommandHandler implements CommandExecutor, TabCompleter {
    private final Infuse plugin;

    public InfuseCommandHandler(Infuse plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return plugin.handleCommand(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return plugin.completeCommand(sender, command, alias, args);
    }
}
