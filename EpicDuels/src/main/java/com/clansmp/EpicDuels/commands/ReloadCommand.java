package com.clansmp.EpicDuels.commands;

import com.clansmp.EpicDuels.EpicDuels;
import com.clansmp.EpicDuels.manager.ArenaManager; // Import ArenaManager
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {

    private final EpicDuels plugin;
    private final ArenaManager arenaManager; // We need to pass ArenaManager to reload locations

    public ReloadCommand(EpicDuels plugin, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager; // Store reference to ArenaManager
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check for permission
        if (!sender.hasPermission("epicduels.arena.set")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.").color(NamedTextColor.RED));
            return true; // Command handled, but permission denied
        }

        // Check if the command is "/duelreload"
        if (command.getName().equalsIgnoreCase("duelreload")) {
            plugin.reloadConfig(); // This reloads the config.yml file from disk

            // IMPORTANT: After reloading the config, you need to tell any managers
            // that depend on config values to re-load their data.
            // ArenaManager stores arena locations and global spawn, which come from config.
            arenaManager.loadArenas(); // Assuming this method exists and reloads data.

            sender.sendMessage(Component.text("EpicDuels configuration reloaded!").color(NamedTextColor.GREEN));
            plugin.getLogger().info("EpicDuels configuration reloaded by " + sender.getName() + ".");
            return true; // Command successfully handled
        }

        return false; // Should not be reached if command is registered correctly
    }
}