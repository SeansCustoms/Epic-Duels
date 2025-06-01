package com.clansmp.EpicDuels;

import com.clansmp.EpicDuels.commands.ArenaCommands;
import com.clansmp.EpicDuels.commands.ReloadCommand;
import com.clansmp.EpicDuels.listeners.DuelListener;
import com.clansmp.EpicDuels.manager.ArenaManager;
import com.clansmp.EpicDuels.manager.DuelManager;
import com.clansmp.EpicDuels.object.DuelChallenge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.Objects;

public final class EpicDuels extends JavaPlugin implements CommandExecutor {

    private ArenaManager arenaManager;
    private DuelManager duelManager;

    @Override
    public void onEnable() {
        getLogger().info("EpicDuels has been enabled!");

        saveDefaultConfig();

        // Initialize managers
        this.arenaManager = new ArenaManager(this);
        this.duelManager = new DuelManager(this, arenaManager);
        
        //Read config setting for PVP outside of duel
        if (!getConfig().contains("pvp-outside-duel")) {
            getConfig().set("pvp-outside-duel", true); // Default to TRUE (PvP IS allowed outside duels)
            saveConfig(); // Save the updated config to disk
        }

        // Register this class as the CommandExecutor for /duel
        getCommand("duel").setExecutor(this);
        //Register arena commands
        getCommand("duelarenas").setExecutor(new ArenaCommands(this, arenaManager));
        // Register config reload command
        Objects.requireNonNull(getCommand("duelreload")).setExecutor(new ReloadCommand(this, arenaManager));
        // Register listeners
        getServer().getPluginManager().registerEvents(new DuelListener(this, duelManager, arenaManager), this);
    }

    @Override
    public void onDisable() {
        getLogger().info("EpicDuels has been disabled!");
        if (arenaManager != null) {
            arenaManager.saveArenas();
        }
    }

    // --- CommandExecutor Implementation for /duel ---
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /duel <player | accept | deny | setspawn | setarena>").color(NamedTextColor.YELLOW));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "accept":
                duelManager.handleAcceptCommand(player);
                break;
            case "deny":
                DuelChallenge challengeToDeny = duelManager.getPendingChallengeByPlayer(player);
                if (challengeToDeny != null) {
                    duelManager.cancelChallenge(challengeToDeny);
                    player.sendMessage(Component.text("You denied the duel challenge from " + challengeToDeny.getChallenger().getName() + ".").color(NamedTextColor.RED));
                } else {
                    player.sendMessage(Component.text("You have no pending duel challenges to deny.").color(NamedTextColor.RED));
                }
                break;
            case "setspawn":
                if (!player.hasPermission("epicduels.admin")) {
                    player.sendMessage(Component.text("You don't have permission to set the global spawn location.").color(NamedTextColor.RED));
                    return true;
                }
                arenaManager.setSpawnLocation(player.getLocation());
                player.sendMessage(Component.text("Global duel spawn location set to your current location.").color(NamedTextColor.GREEN));
                break;
            case "setarena":
                if (!player.hasPermission("epicduels.admin")) {
                    player.sendMessage(Component.text("You don't have permission to set arena locations.").color(NamedTextColor.RED));
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /duel setarena <arenaNumber> <pos1|pos2>").color(NamedTextColor.YELLOW));
                    return true;
                }
                try {
                    int arenaNumber = Integer.parseInt(args[1]);
                    int position = args[2].equalsIgnoreCase("pos1") ? 1 : args[2].equalsIgnoreCase("pos2") ? 2 : -1;
                    if (position == -1) {
                        player.sendMessage(Component.text("Invalid position. Use 'pos1' or 'pos2'.").color(NamedTextColor.RED));
                        return true;
                    }
                    arenaManager.setArenaLocation(arenaNumber, position, player.getLocation());
                    player.sendMessage(Component.text("Arena " + arenaNumber + " position " + position + " set.").color(NamedTextColor.GREEN));
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Invalid arena number.").color(NamedTextColor.RED));
                }
                break;
            default:
                // Assume the argument is a player name for a duel challenge
                Player targetPlayer = Bukkit.getPlayer(args[0]);
                if (targetPlayer == null || !targetPlayer.isOnline()) {
                    player.sendMessage(Component.text("Player '" + args[0] + "' not found or is offline.").color(NamedTextColor.RED));
                    return true;
                }
                if (player.equals(targetPlayer)) {
                    player.sendMessage(Component.text("You cannot duel yourself.").color(NamedTextColor.RED));
                    return true;
                }
                if (duelManager.getDuelByPlayer(player) != null || duelManager.getDuelByPlayer(targetPlayer) != null) {
                    player.sendMessage(Component.text("One of the players is already in a duel.").color(NamedTextColor.RED));
                    return true;
                }
                if (duelManager.getOutgoingChallengeByPlayer(player) != null) {
                    player.sendMessage(Component.text("You already have an outgoing duel challenge.").color(NamedTextColor.RED));
                    return true;
                }
                if (duelManager.getPendingChallengeByPlayer(targetPlayer) != null) {
                    player.sendMessage(Component.text(targetPlayer.getName() + " already has a pending duel challenge.").color(NamedTextColor.RED));
                    return true;
                }

                // If all checks pass, open the duel options GUI
                openDuelOptionsGUI(player, targetPlayer);
                break;
        }
        return true;
    }

    // --- GUI Methods ---

    private void openDuelOptionsGUI(Player challenger, Player challenged) {
        Inventory gui = Bukkit.createInventory(null, 9, Component.text("Keep Inventory On/Off?").color(NamedTextColor.DARK_GREEN).decorate(TextDecoration.BOLD));

        // Create the "Keep Inventory ON" item (Lime Wool)
        ItemStack keepInventoryOnItem = createGuiItem(Material.LIME_WOOL,
                Component.text("Keep Inventory: ").color(NamedTextColor.GOLD).append(Component.text("ON").color(NamedTextColor.GREEN)),
                Component.text("Inventory is kept on death.").color(NamedTextColor.GRAY));
        gui.setItem(2, keepInventoryOnItem); // Slot 2

        // Create the "Keep Inventory OFF" item (Red Wool)
        ItemStack keepInventoryOffItem = createGuiItem(Material.RED_WOOL,
                Component.text("Keep Inventory: ").color(NamedTextColor.GOLD).append(Component.text("OFF").color(NamedTextColor.RED)),
                Component.text("Inventory is dropped on death (lootable).").color(NamedTextColor.GRAY));
        gui.setItem(4, keepInventoryOffItem); // Slot 4

        // Create the "Cancel Duel" item (Barrier)
        ItemStack cancelDuelItem = createGuiItem(Material.BARRIER,
                Component.text("Cancel Duel").color(NamedTextColor.RED).decorate(TextDecoration.BOLD),
                Component.text("Click to cancel duel challenge.").color(NamedTextColor.YELLOW));
        gui.setItem(6, cancelDuelItem); // Slot 6

        // Store the challenge details temporarily for the GUI setup phase
        // The DuelChallenge object itself will hold the challenger and challenged players
        DuelChallenge challenge = new DuelChallenge(challenger, challenged, this);
        duelManager.addGuiChallenge(challenger, challenge); // Store this in DuelManager for listener retrieval

        challenger.openInventory(gui);
        challenger.sendMessage(Component.text("Opening duel options GUI for " + challenged.getName() + "...").color(NamedTextColor.AQUA));
    }

    private ItemStack createGuiItem(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(Collections.singletonList(Component.text().append(lore).build()));
            item.setItemMeta(meta);
        }
        return item;
    }

    // --- Manager Getters ---

    public DuelManager getDuelManager() {
        return duelManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }
}