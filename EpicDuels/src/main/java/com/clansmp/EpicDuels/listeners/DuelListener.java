package com.clansmp.EpicDuels.listeners;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import com.clansmp.EpicDuels.EpicDuels;
import com.clansmp.EpicDuels.manager.ArenaManager;
import com.clansmp.EpicDuels.manager.DuelManager;
import com.clansmp.EpicDuels.object.Duel;
import com.clansmp.EpicDuels.object.DuelChallenge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Objects;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DuelListener implements Listener {

    private final EpicDuels plugin;
    private final DuelManager duelManager;
    private final ArenaManager arenaManager;

    // Temporary storage for XP of dying players in duels
    private final Map<UUID, Integer> playerDuelXPSave; // Player UUID -> Total XP

    public DuelListener(EpicDuels plugin, DuelManager duelManager, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.duelManager = duelManager;
        this.arenaManager = arenaManager;
        this.playerDuelXPSave = new ConcurrentHashMap<>();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (duelManager.isPlayerInCountdown(player.getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
                event.setCancelled(true);
                player.sendActionBar(Component.text("Duel starting... do not move!").color(NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player damager && event.getEntity() instanceof Player damaged) {
            Duel duel = duelManager.getDuelByPlayer(damager);
            if (duel != null && duel.isActive() && duel.getOpponent(damager) != null && duel.getOpponent(damager).equals(damaged)) {
                if (duelManager.isPlayerInCountdown(damager.getUniqueId())) {
                    event.setCancelled(true);
                    damager.sendActionBar(Component.text("Duel hasn't started yet!").color(NamedTextColor.RED));
                } else {
                    event.setCancelled(false);
                }
            } else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Duel duel = duelManager.getDuelByPlayer(player);

        if (duel != null && duel.isActive()) {
            Player winner = duel.getOpponent(player);
            Player loser = player;

            plugin.getLogger().info(loser.getName() + " died in a duel against " + winner.getName() + ".");

            if (duel.isKeepInventory()) {
                event.getDrops().clear();
                event.setDroppedExp(0);
                event.setKeepInventory(true);
                event.setKeepLevel(true); // Explicitly keep levels too

                // Immediately save and clear XP for the loser
                playerDuelXPSave.put(loser.getUniqueId(), loser.getTotalExperience());
                plugin.getLogger().info("Saved " + playerDuelXPSave.get(loser.getUniqueId()) + " XP for " + loser.getName());

            } else {
                event.setKeepInventory(false);
                event.setKeepLevel(false);
            }

            duelManager.endDuel(winner, loser, duel);

            event.deathMessage(Component.text(loser.getName()).color(NamedTextColor.YELLOW)
                                     .append(Component.text(" was defeated by ").color(NamedTextColor.GRAY))
                                     .append(Component.text(Objects.requireNonNull(winner).getName()).color(NamedTextColor.YELLOW))
                                     .append(Component.text(" in a duel!").color(NamedTextColor.GRAY)));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (duelManager.getDuelIdForDiedPlayer(playerUUID) != null) {
            plugin.getLogger().info("Player " + player.getName() + " quit, but they just died in a duel. Skipping PlayerQuitEvent duel handling.");
            playerDuelXPSave.remove(playerUUID); // Clean up temp XP map
            return;
        }

        Duel duel = duelManager.getDuelByPlayer(player);
        DuelChallenge challenge = null;

        if (duel != null && duel.isActive()) {
            Player opponent = duel.getOpponent(player);

            if (!duel.isKeepInventory()) {
                plugin.getLogger().info(player.getName() + " logged out during a non-keep-inventory duel with " + (opponent != null ? opponent.getName() : "an unknown opponent") + ". Forfeiting duel and applying death penalty.");

                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && !item.getType().isAir()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    }
                }
                for (ItemStack armorItem : player.getInventory().getArmorContents()) {
                    if (armorItem != null && !armorItem.getType().isAir()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), armorItem);
                    }
                }
                player.getInventory().clear();

                player.setHealth(0.0);
                duelManager.addPlayerToGlobalSpawnQueue(player.getUniqueId());

                if (opponent != null && opponent.isOnline()) {
                    opponent.sendMessage(Component.text(player.getName()).color(NamedTextColor.YELLOW)
                            .append(Component.text(" logged out during the duel! You win and get their loot.").color(NamedTextColor.GREEN)));
                    duelManager.endDuel(opponent, player, duel);
                    // TEMPORARILY COMMENTED OUT removeDuel calls in onPlayerQuit for testing
                    // duelManager.removeDuel(duel.getDuelId(), "onPlayerQuit_NonKeepInv");
                } else {
                    duelManager.endDuel(null, null, duel);
                    // TEMPORARILY COMMENTED OUT removeDuel calls in onPlayerQuit for testing
                    // duelManager.removeDuel(duel.getDuelId(), "onPlayerQuit_NonKeepInv_BothOffline");
                    plugin.getLogger().info("Duel " + duel.getDuelId() + " ended due to both players logging out or opponent missing (non-keep-inv).");
                }
            } else {
                plugin.getLogger().info(player.getName() + " logged out during a keep-inventory duel with " + (opponent != null ? opponent.getName() : "an unknown opponent") + ". Ending duel cleanly.");
                if (opponent != null && opponent.isOnline()) {
                    opponent.sendMessage(Component.text(player.getName()).color(NamedTextColor.YELLOW)
                            .append(Component.text(" logged out during the duel. You win by forfeit.").color(NamedTextColor.GREEN)));
                    duelManager.endDuel(opponent, player, duel);
                    // TEMPORARILY COMMENTED OUT removeDuel calls in onPlayerQuit for testing
                    // duelManager.removeDuel(duel.getDuelId(), "onPlayerQuit_KeepInv");
                } else {
                    duelManager.endDuel(null, null, duel);
                    // TEMPORARILY COMMENTED OUT removeDuel calls in onPlayerQuit for testing
                    // duelManager.removeDuel(duel.getDuelId(), "onPlayerQuit_KeepInv_BothOffline");
                    plugin.getLogger().info("Duel " + duel.getDuelId() + " ended due to both players logging out or opponent missing (keep-inv).");
                }
            }
            return;
        }

        if (duelManager.getOutgoingChallengeByPlayer(player) != null) {
            challenge = duelManager.getOutgoingChallengeByPlayer(player);
        } else if (duelManager.getPendingChallengeByPlayer(player) != null) {
            challenge = duelManager.getPendingChallengeByPlayer(player);
        }

        if (challenge != null) {
            duelManager.cancelChallenge(challenge);
            plugin.getLogger().info("Pending duel challenge involving " + player.getName() + " cancelled due to logout.");
            Player otherPlayer = challenge.getChallenged().equals(player) ? challenge.getChallenger() : challenge.getChallenged();
            if (otherPlayer != null && otherPlayer.isOnline()) {
                otherPlayer.sendMessage(Component.text(player.getName() + " disconnected, cancelling the duel challenge.").color(NamedTextColor.RED));
            }
        }

        DuelChallenge guiChallenge = duelManager.getGuiChallenge(player);
        if (guiChallenge != null) {
            duelManager.removeGuiChallenge(player);
            plugin.getLogger().info("GUI duel setup for " + player.getName() + " cancelled due to logout.");
        }

        if (duelManager.isPlayerInCountdown(player.getUniqueId())) {
            duelManager.removePlayerFromCountdown(player.getUniqueId());
            plugin.getLogger().info("Player " + player.getName() + " removed from countdown on logout.");
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        UUID duelId = duelManager.getDuelIdForDiedPlayer(playerUUID);
        if (duelId != null) {
            Duel duel = duelManager.getDuelById(duelId);
            if (duel != null) {
                if (duel.isKeepInventory()) {
                    // Restore XP from our temporary map FIRST
                    if (playerDuelXPSave.containsKey(playerUUID)) {
                        int savedXP = playerDuelXPSave.get(playerUUID);
                        player.setTotalExperience(savedXP);
                        plugin.getLogger().info("Restored " + savedXP + " XP for " + player.getName() + " on respawn.");
                        playerDuelXPSave.remove(playerUUID);
                    } else {
                        plugin.getLogger().warning("No saved XP found for " + player.getName() + " on respawn, despite dying in a keep-inv duel.");
                    }

                    duelManager.restorePlayerState(player, duel); // Restore inventory, health, etc.
                    player.sendMessage(Component.text("Your inventory has been restored.").color(NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("You lost your items in the duel!").color(NamedTextColor.RED));
                    player.getInventory().clear();
                    player.getInventory().setHelmet(null);
                    player.getInventory().setChestplate(null);
                    player.getInventory().setLeggings(null);
                    player.getInventory().setBoots(null);
                }

                player.setGameMode(GameMode.SURVIVAL);
                player.setFlying(false);
                player.setAllowFlight(false);

                // This is the *only* place removeDuel should be called for natural death.
                duelManager.removeDuel(duelId, "onPlayerRespawn_NaturalDeath"); // Corrected call
            } else {
                plugin.getLogger().warning("Could not find duel object for player " + player.getName() + " after death in duel with ID: " + duelId + " during respawn. This indicates a prior unexpected cleanup.");
            }
            duelManager.removePlayerDiedInDuel(playerUUID);
            return;
        }

        if (duelManager.isPlayerInGlobalSpawnQueue(playerUUID)) {
            Location globalSpawn = arenaManager.getSpawnLocation();
            if (globalSpawn != null) {
                event.setRespawnLocation(globalSpawn);
                player.sendMessage(Component.text("You have respawned at the global spawn location after disconnecting during a duel.").color(NamedTextColor.AQUA));
            } else {
                player.sendMessage(Component.text("Global spawn location not set, spawning at world spawn.").color(NamedTextColor.RED));
            }
            duelManager.removePlayerFromGlobalSpawnQueue(playerUUID);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getView().title().equals(Component.text("Keep Inventory On/Off?").color(NamedTextColor.DARK_GREEN).decorate(TextDecoration.BOLD))) {
            event.setCancelled(true);

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType().isAir()) {
                return;
            }

            DuelChallenge guiChallenge = duelManager.getGuiChallenge(player);
            if (guiChallenge == null) {
                player.sendMessage(Component.text("Error: Duel challenge not found for GUI. Please try again.").color(NamedTextColor.RED));
                player.closeInventory();
                return;
            }

            if (clickedItem.getType() == Material.LIME_WOOL) {
                player.closeInventory();
                Player challengedPlayer = guiChallenge.getChallenged();
                if (challengedPlayer == null || !challengedPlayer.isOnline()) {
                    player.sendMessage(Component.text(Objects.requireNonNull(challengedPlayer).getName() + " is no longer online. Challenge cancelled.").color(NamedTextColor.RED));
                    duelManager.removeGuiChallenge(player);
                    return;
                }
                duelManager.startChallenge(player, challengedPlayer, true);

            } else if (clickedItem.getType() == Material.RED_WOOL) {
                player.closeInventory();
                Player challengedPlayer = guiChallenge.getChallenged();
                if (challengedPlayer == null || !challengedPlayer.isOnline()) {
                    player.sendMessage(Component.text(Objects.requireNonNull(challengedPlayer).getName() + " is no longer online. Challenge cancelled.").color(NamedTextColor.RED));
                    duelManager.removeGuiChallenge(player);
                    return;
                }
                duelManager.startChallenge(player, challengedPlayer, false);

            } else if (clickedItem.getType() == Material.BARRIER) {
                player.closeInventory();
                duelManager.removeGuiChallenge(player);
                player.sendMessage(Component.text("Duel challenge setup cancelled.").color(NamedTextColor.YELLOW));
            }
        }
    }
}