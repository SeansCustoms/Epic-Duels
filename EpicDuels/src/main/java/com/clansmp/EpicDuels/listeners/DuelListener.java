package com.clansmp.EpicDuels.listeners;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent; // NEW Import
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import com.clansmp.EpicDuels.EpicDuels;
import com.clansmp.EpicDuels.manager.ArenaManager;
import com.clansmp.EpicDuels.manager.DuelManager;
import com.clansmp.EpicDuels.object.Duel;
import com.clansmp.EpicDuels.object.DuelChallenge;
import com.clansmp.EpicDuels.object.PlayerState; // NEW Import

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

    // Temporary storage for XP of dying players in duels (Still used for respawn sync)
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
                // When players are NOT in a duel:
                boolean pvpAllowedOutsideDuel = plugin.getConfig().getBoolean("pvp-outside-duel", true); // Get config setting, default to true.
                if (!pvpAllowedOutsideDuel) {
                    event.setCancelled(true);
                    // damager.sendActionBar(Component.text("PvP is currently disabled outside of duels.").color(NamedTextColor.RED));
                } else {
                    event.setCancelled(false);
                }
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
                
                event.setDroppedExp(0); //set for safety
                event.setKeepInventory(true);
                event.setKeepLevel(true);

                // Save XP to map (in case Bukkit's method fails)
                playerDuelXPSave.put(loser.getUniqueId(), loser.getTotalExperience());
                plugin.getLogger().info("Saved " + playerDuelXPSave.get(loser.getUniqueId()) + " XP for " + loser.getName() + " for keep-inv duel.");

            } else { // Keep Inventory OFF
                event.setKeepInventory(false); // Player loses items and XP
                event.setKeepLevel(false);
            }

            // end the duel (logically) - the loser's respawn event will trigger the final removeDuel cleanup.
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

        // --- 1. Handle loser ---
        // If the player quit *immediately* after dying in a duel, their respawn is handled by onPlayerRespawn.
        if (duelManager.getDuelIdForDiedPlayer(playerUUID) != null) {
            plugin.getLogger().info("Loser " + player.getName() + " quit, but they just died in a duel. Skipping PlayerQuitEvent duel handling, onPlayerRespawn will handle.");
            playerDuelXPSave.remove(playerUUID); // Clean up temp XP map if present
            return;
        }

        // --- 2. Handle active duel participants: Determine if Winner or Forfeiting Loser ---
        Duel duel = duelManager.getDuelByPlayer(player);
        
        if (duel != null && duel.isActive()) {
            Player opponent = duel.getOpponent(player);

            // Check if the *opponent* has already died in this duel
            if (opponent != null && duelManager.getDuelIdForDiedPlayer(opponent.getUniqueId()) != null) {
                // Scenario: Opponent has died, so 'player' (the quitter) is the winner.
                // They are logging out after the duel is essentially over.
                plugin.getLogger().info("Winner " + player.getName() + " quit after opponent " + opponent.getName() + " died. Formalizing win and cleaning up.");

                // Immediately restore winner's state for a clean exit
                // (Health, food, effects, game mode, flight should be restored. Inventory is never affected for the winner).
                player.setHealth(Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue());
                player.setFoodLevel(20);
                player.setSaturation(20);
                player.setFireTicks(0);
                player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
                player.setGameMode(GameMode.SURVIVAL); // Ensure survival mode
                player.setFlying(false);
                player.setAllowFlight(false);

                // Teleport them to global spawn immediately (or let next join handle if they log out before this executes)
                Location globalSpawn = arenaManager.getSpawnLocation();
                if (globalSpawn != null) {
                    player.teleport(globalSpawn);
                    player.sendMessage(Component.text("You logged out after winning your duel. Your state is safe and you have been returned to spawn.").color(NamedTextColor.AQUA));
                } else {
                    player.sendMessage(Component.text("You logged out after winning your duel. Global spawn not set, returning to world spawn.").color(NamedTextColor.RED));
                }

                // Ensure duel is properly ended with player as winner
                duelManager.endDuel(player, opponent, duel); // Pass player as winner, opponent as loser
                duelManager.removeDuel(duel.getDuelId(), "onPlayerQuit_WinnerLogout"); // Explicitly remove duel

                return; // Handled as winner.
            }
            // Scenario: Opponent is still alive (or their death hasn't been processed yet),
            // so 'player' (the quitter) is effectively forfeiting.
            else {
                plugin.getLogger().info(player.getName() + " logged out during active duel with " + (opponent != null ? opponent.getName() : "an unknown opponent") + ". Forfeiting duel.");

                // Apply forfeiture consequences to the quitting player
                if (!duel.isKeepInventory()) { // Non-keep-inventory forfeit
                    player.setHealth(0.0); // Kill the forfeiting player
                    duelManager.addPlayerToGlobalSpawnQueue(player.getUniqueId()); // Mark for forced respawn on next login
                } else { // Keep-inventory forfeit
                    PlayerState loggedOutState = new PlayerState(player);
                    duelManager.addLoggedOutKeepInvPlayerState(player.getUniqueId(), loggedOutState);
                }

                // Inform the opponent (if online) and formalize the duel end
                if (opponent != null && opponent.isOnline()) {
                    opponent.sendMessage(Component.text(player.getName()).color(NamedTextColor.YELLOW)
                            .append(Component.text(" logged out during the duel! You win by forfeit.").color(NamedTextColor.GREEN)));
                    duelManager.endDuel(opponent, player, duel); // Opponent wins, player loses by forfeit
                } else {
                    // Both players offline or opponent was never online (edge case).
                    // Duel simply collapses without a clear winner/loser through endDuel.
                    duelManager.endDuel(null, null, duel);
                }
                duelManager.removeDuel(duel.getDuelId(), "onPlayerQuit_Forfeit"); // Explicitly remove duel here
                return; // Duel outcome settled.
            }
        }

        DuelChallenge challenge = null; 
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
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // --- Handling Disconnected Winners ---
        if (duelManager.isDisconnectedWinner(playerUUID)) {
            Location spawnLoc = arenaManager.getSpawnLocation();

            if (spawnLoc != null) {
                // Teleport the player to the spawn location with a slight delay
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    player.teleport(spawnLoc);
                    player.sendMessage(Component.text("Welcome back! You were returned to spawn after winning your last duel.").color(NamedTextColor.GREEN));
                    plugin.getLogger().info("Teleported disconnected winner " + player.getName() + " back to spawn on rejoin.");
                }, 5L); // 5L means 5 ticks (0.25 seconds)
            } else {
                player.sendMessage(Component.text("Welcome back! Could not find a return spawn location.").color(NamedTextColor.YELLOW));
                plugin.getLogger().warning("Disconnected winner " + player.getName() + " could not be teleported to spawn, no spawn location set in ArenaManager.");
            }
            // Remove them from the disconnected winners list so they aren't teleported again
            duelManager.removeDisconnectedWinner(playerUUID);

            // Also, reset their general player state on join just in case
            if (player.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            } else {
                // Fallback if for some reason the attribute is null (shouldn't happen for players)
                player.setHealth(20.0);
            }
            player.setFoodLevel(20);
            player.setFireTicks(0);
            player.setNoDamageTicks(0);
            player.setGameMode(GameMode.SURVIVAL); // Assuming SURVIVAL is your default
            return; // IMPORTANT: If handled as disconnected winner, stop further join processing
        }

        // --- Handling Logged-Out Keep-Inventory Players (if not already handled as winner) ---
        // This is for players who logged out during a keep-inventory duel without it ending (e.g., server restart)
        PlayerState savedKeepInvState = duelManager.getLoggedOutKeepInvPlayerState(playerUUID);
        if (savedKeepInvState != null) {
            plugin.getLogger().info("Restoring full state for " + player.getName() + " from logged-out keep-inv duel.");
            
            // Teleport to spawn first, then restore state to ensure they are out of arena
            Location spawnLoc = arenaManager.getSpawnLocation();
            if (spawnLoc != null) {
                player.teleport(spawnLoc);
                player.sendMessage(Component.text("Welcome back! Your state from a previous duel has been restored.").color(NamedTextColor.AQUA));
            } else {
                player.sendMessage(Component.text("Welcome back! Your state from a previous duel has been restored, but global spawn not set.").color(NamedTextColor.RED));
            }
            
            duelManager.restoreFullPlayerState(player, savedKeepInvState);
            duelManager.removeLoggedOutKeepInvPlayerState(playerUUID); // Remove after restoration
            Duel duel = duelManager.getDuelByPlayer(player);
            if(duel != null && duel.isActive()) {
                 duelManager.removeDuel(duel.getDuelId(), "PlayerJoin_KeepInvLogout");
            }
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
                    // Restore XP from our temporary map
                    if (playerDuelXPSave.containsKey(playerUUID)) {
                        int savedXP = playerDuelXPSave.get(playerUUID);
                        player.setTotalExperience(savedXP);
                        plugin.getLogger().info("Restored " + savedXP + " XP for " + player.getName() + " on respawn (keep-inv duel).");
                        playerDuelXPSave.remove(playerUUID);
                    } else {
                        plugin.getLogger().warning("No saved XP found for " + player.getName() + " on respawn, despite dying in a keep-inv duel. Using Bukkit's kept level.");
                    }
                    
                    // Only restore non-inventory state here, inventory was kept by Bukkit.
                    duelManager.restorePlayerState(player, duel); 
                    player.sendMessage(Component.text("Your general state has been restored.").color(NamedTextColor.GREEN));
                } else { // Keep Inventory OFF (natural death in duel)
                    player.sendMessage(Component.text("You lost your items in the duel!").color(NamedTextColor.RED));
                    // Ensure inventory is cleared - Bukkit should do this, but for safety:
                    player.getInventory().clear();
                    player.getInventory().setHelmet(null);
                    player.getInventory().setChestplate(null);
                    player.getInventory().setLeggings(null);
                    player.getInventory().setBoots(null);
                    
                    // Restore general state for non-keep-inv duels
                    duelManager.restorePlayerState(player, duel);
                }

                player.setGameMode(GameMode.SURVIVAL);
                player.setFlying(false);
                player.setAllowFlight(false);

                // This is the *only* place removeDuel should be called for natural death.
                duelManager.removeDuel(duelId, "onPlayerRespawn_NaturalDeath");
            } else {
                plugin.getLogger().warning("Could not find duel object for player " + player.getName() + " after death in duel with ID: " + duelId + " during respawn. This indicates a prior unexpected cleanup.");
            }
            duelManager.removePlayerDiedInDuel(playerUUID); // Clear the death flag
            return;
        }

        // This block handles players who were killed/forfeited by logging out of a non-keep-inv duel
        if (duelManager.isPlayerInGlobalSpawnQueue(playerUUID)) {
            Location globalSpawn = arenaManager.getSpawnLocation();
            if (globalSpawn != null) {
                event.setRespawnLocation(globalSpawn); // Set respawn location to global spawn
                player.sendMessage(Component.text("You have respawned at the global spawn location after disconnecting during a duel.").color(NamedTextColor.AQUA));
            } else {
                player.sendMessage(Component.text("Global spawn location not set, spawning at world spawn.").color(NamedTextColor.RED));
            }
            // Also ensure health/food/effects are reset after respawn, as duelManager.restorePlayerState does this.
            // For players coming from globalSpawnQueue, ensure they are alive and clean.
            player.setHealth(Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue());
            player.setFoodLevel(20);
            player.setSaturation(20);
            player.setFireTicks(0);
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

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