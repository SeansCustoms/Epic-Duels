package com.clansmp.EpicDuels.commands;

import com.clansmp.EpicDuels.EpicDuels;
import com.clansmp.EpicDuels.manager.ArenaManager;
import com.clansmp.EpicDuels.object.Arena; // Import Arena object
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration; // For bold text
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

import org.bukkit.Location;

public class ArenaCommands implements CommandExecutor {

    private final EpicDuels plugin;
    private final ArenaManager arenaManager;

    public ArenaCommands(EpicDuels plugin, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be run by a player.").color(NamedTextColor.RED));
            return true;
        }

        // Base permission check for /duelarenas
        if (!player.hasPermission("epicduels.arena.base")) {
            player.sendMessage(Component.text("You do not have permission to use arena commands.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list":
                handleListCommand(player);
                break;
            case "release":
                handleReleaseCommand(player);
                break;
            case "remove":
                handleRemoveCommand(player, args);
                break;
            default:
                sendHelpMessage(player);
                break;
        }

        return true;
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(Component.text("--- EpicDuels Arena Commands ---").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("/duelarenas list - List all configured arenas.").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/duelarenas release - Force release all stuck arenas.").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/duelarenas remove <arenaNumber|all> - Remove a specific arena or all arenas.").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("-------------------------------").color(NamedTextColor.GOLD));
    }

    private void handleListCommand(Player player) {
        if (!player.hasPermission("epicduels.arena.list")) {
            player.sendMessage(Component.text("You do not have permission to list arenas.").color(NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("--- EpicDuels Arenas ---").color(NamedTextColor.GOLD));
        
        Map<Integer, Arena> arenas = arenaManager.getArenas();

        if (arenas.isEmpty()) {
            player.sendMessage(Component.text("No arenas are currently configured.").color(NamedTextColor.GRAY));
            return;
        }

        arenas.forEach((arenaNum, arena) -> {
            String status = arena.isOccupied() ? "Occupied" : "Available";
            String configured = arena.isFullyConfigured() ? "Configured" : "INCOMPLETE";

            Location loc1 = arena.getLocation(1);
            Location loc2 = arena.getLocation(2);

            Component message = Component.text("Arena " + arenaNum + ": ")
                                .append(Component.text(configured).color(arena.isFullyConfigured() ? NamedTextColor.GREEN : NamedTextColor.RED).decorate(TextDecoration.BOLD))
                                .append(Component.text(" | Status: "))
                                .append(Component.text(status).color(arena.isOccupied() ? NamedTextColor.YELLOW : NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                                .append(Component.text("\n  Pos1: " + formatLocation(loc1)))
                                .append(Component.text("\n  Pos2: " + formatLocation(loc2)));
            player.sendMessage(message);
        });

        player.sendMessage(Component.text("------------------------").color(NamedTextColor.GOLD));
    }

    private String formatLocation(Location loc) {
        if (loc == null) return "N/A";
        return String.format("World: %s, X: %.1f, Y: %.1f, Z: %.1f",
                loc.getWorld() != null ? loc.getWorld().getName() : "Unknown",
                loc.getX(), loc.getY(), loc.getZ());
    }

    private void handleReleaseCommand(Player player) {
        if (!player.hasPermission("epicduels.arena.set")) {
            player.sendMessage(Component.text("You do not have permission to release arenas.").color(NamedTextColor.RED));
            return;
        }

        arenaManager.forceReleaseAllArenas();
        player.sendMessage(Component.text("All duel arenas have been force released.").color(NamedTextColor.GREEN));
        plugin.getLogger().info(player.getName() + " force released all duel arenas.");
    }

    private void handleRemoveCommand(Player player, String[] args) {
        if (!player.hasPermission("epicduels.arena.set")) {
            player.sendMessage(Component.text("You do not have permission to remove arenas.").color(NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /duelarenas remove <arenaNumber|all>").color(NamedTextColor.RED));
            return;
        }

        String target = args[1].toLowerCase();

        if (target.equals("all")) {
            arenaManager.removeAllArenas();
            player.sendMessage(Component.text("All duel arenas have been removed.").color(NamedTextColor.GREEN));
            plugin.getLogger().info(player.getName() + " removed all duel arenas.");
        } else {
            try {
                int arenaNumber = Integer.parseInt(target);
                if (arenaManager.removeArena(arenaNumber)) {
                    player.sendMessage(Component.text("Arena " + arenaNumber + " has been removed.").color(NamedTextColor.GREEN));
                    plugin.getLogger().info(player.getName() + " removed arena " + arenaNumber + ".");
                } else {
                    player.sendMessage(Component.text("Arena " + arenaNumber + " not found or could not be removed.").color(NamedTextColor.RED));
                }
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Invalid arena number. Usage: /duelarenas remove <arenaNumber|all>").color(NamedTextColor.RED));
            }
        }
    }
}