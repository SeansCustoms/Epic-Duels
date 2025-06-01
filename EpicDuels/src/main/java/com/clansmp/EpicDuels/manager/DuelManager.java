package com.clansmp.EpicDuels.manager;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import com.clansmp.EpicDuels.EpicDuels;
import com.clansmp.EpicDuels.object.Duel;
import com.clansmp.EpicDuels.object.DuelChallenge;
import com.clansmp.EpicDuels.object.PlayerState; // Ensure this is imported

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.potion.PotionEffect; // Import for PotionEffect

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;

@SuppressWarnings("unused")
public class DuelManager {

    private final EpicDuels plugin;
    private final ArenaManager arenaManager;

    private final Map<UUID, Duel> activeDuels;
    private final Map<Player, DuelChallenge> outgoingChallenges;
    private final Map<Player, DuelChallenge> incomingChallenges;

    private final Map<Player, DuelChallenge> guiChallenges;

    private final Set<UUID> playersForcedToSpawnAtGlobal;
    private final Map<UUID, UUID> playersDiedInDuelAndAwaitingRespawn;
    private final Set<UUID> playersInCountdown;
    private final Set<UUID> disconnectedWinners;

    // NEW: Map to store player state for keep-inventory duels when players log out
    private final Map<UUID, PlayerState> loggedOutKeepInvPlayers;

    public DuelManager(EpicDuels plugin, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.activeDuels = new ConcurrentHashMap<>();
        this.outgoingChallenges = new HashMap<>();
        this.incomingChallenges = new HashMap<>();
        this.guiChallenges = new HashMap<>();
        this.playersForcedToSpawnAtGlobal = new HashSet<>();
        this.playersDiedInDuelAndAwaitingRespawn = new ConcurrentHashMap<>();
        this.playersInCountdown = new HashSet<>();
        this.loggedOutKeepInvPlayers = new ConcurrentHashMap<>(); // Initialize
        this.disconnectedWinners = ConcurrentHashMap.newKeySet();
    }

    // --- NEW: Logout/Login state methods for KeepInv Duels ---
    public void addLoggedOutKeepInvPlayerState(UUID playerUUID, PlayerState state) {
        loggedOutKeepInvPlayers.put(playerUUID, state);
    }

    public PlayerState getLoggedOutKeepInvPlayerState(UUID playerUUID) {
        return loggedOutKeepInvPlayers.get(playerUUID);
    }

    public void removeLoggedOutKeepInvPlayerState(UUID playerUUID) {
        loggedOutKeepInvPlayers.remove(playerUUID);
    }
    // --- END NEW ---

    // --- Player Respawn Queue Methods (for logout death) ---
    public void addPlayerToGlobalSpawnQueue(UUID playerUUID) {
        playersForcedToSpawnAtGlobal.add(playerUUID);
    }

    public boolean isPlayerInGlobalSpawnQueue(UUID playerUUID) {
        return playersForcedToSpawnAtGlobal.contains(playerUUID);
    }

    public void removePlayerFromGlobalSpawnQueue(UUID playerUUID) {
        playersForcedToSpawnAtGlobal.remove(playerUUID);
    }

    // --- Natural Death Respawn Handling ---
    public void addPlayerDiedInDuel(UUID playerUUID, UUID duelUUID) {
        playersDiedInDuelAndAwaitingRespawn.put(playerUUID, duelUUID);
    }

    public UUID getDuelIdForDiedPlayer(UUID playerUUID) {
        return playersDiedInDuelAndAwaitingRespawn.get(playerUUID);
    }

    public void removePlayerDiedInDuel(UUID playerUUID) {
        playersDiedInDuelAndAwaitingRespawn.remove(playerUUID);
    }

    // --- Countdown Movement Lock Methods ---
    public boolean isPlayerInCountdown(UUID playerUUID) {
        return playersInCountdown.contains(playerUUID);
    }

    public void addPlayerToCountdown(UUID playerUUID) {
        playersInCountdown.add(playerUUID);
    }

    public void removePlayerFromCountdown(UUID playerUUID) {
        playersInCountdown.remove(playerUUID);
    }

    // --- GUI Challenge Management ---
    public void addGuiChallenge(Player challenger, DuelChallenge challenge) {
        guiChallenges.put(challenger, challenge);
    }

    public DuelChallenge getGuiChallenge(Player challenger) {
        return guiChallenges.get(challenger);
    }

    public void removeGuiChallenge(Player challenger) {
        guiChallenges.remove(challenger);
    }

    // --- Duel Challenge & Retrieval Methods ---

    public Duel getDuelByPlayer(Player player) {
        return activeDuels.get(player.getUniqueId());
    }

    public Duel getDuelById(UUID duelId) {
        for (Duel duel : activeDuels.values()) {
            if (duel.getDuelId().equals(duelId)) {
                return duel;
            }
        }
        return null;
    }

    public DuelChallenge getPendingChallengeByPlayer(Player player) {
        return incomingChallenges.get(player);
    }

    public DuelChallenge getOutgoingChallengeByPlayer(Player player) {
        return outgoingChallenges.get(player);
    }

    public void removeDuel(UUID duelId, String source) {
        Duel duelToRemove = null;
        for (Duel duel : activeDuels.values()) {
            if (duel.getDuelId().equals(duelId)) {
                duelToRemove = duel;
                break;
            }
        }
        if (duelToRemove != null) {
            activeDuels.remove(duelToRemove.getChallengerUUID());
            activeDuels.remove(duelToRemove.getChallengedUUID());

            playersDiedInDuelAndAwaitingRespawn.remove(duelToRemove.getChallengerUUID());
            playersDiedInDuelAndAwaitingRespawn.remove(duelToRemove.getChallengedUUID());
            
            // Ensure no player state is lingering if they logged out previously
            removeLoggedOutKeepInvPlayerState(duelToRemove.getChallengerUUID());
            removeLoggedOutKeepInvPlayerState(duelToRemove.getChallengedUUID());

            duelToRemove.setActive(false);
            arenaManager.releaseArena(duelToRemove.getArenaNumber());
            plugin.getLogger().info("Duel " + duelId + " fully cleaned up by " + source + " and arena " + duelToRemove.getArenaNumber() + " released.");
        } else {
            plugin.getLogger().warning("Attempted to remove non-existent duel with ID: " + duelId + " from source: " + source);
        }
    }

    // --- Player State Management Methods ---

    public void preparePlayerForDuel(Player player, Duel duel) {
        if (player == null || !player.isOnline()) return;

        // Reset health/food/effects for fairness, but do NOT clear inventory here.
        // Inventory is handled by the duel's keepInventory setting or logout logic.
        if (player.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        } else {
            player.setHealth(20.0);
        }
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setExhaustion(0);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setGameMode(GameMode.SURVIVAL);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

        plugin.getLogger().info("Prepared " + player.getName() + " for duel.");
    }

    // This method is used to restore a player's *general* state (health, food, gamemode, effects, flight)
    // Inventory is handled separately by PlayerDeathEvent (Bukkit) or PlayerJoinEvent (for logout)
    // If you're reading this, I hope these notes are helpful xD
    public void restorePlayerState(Player player, Duel duel) {
        if (player == null || !player.isOnline()) {
            plugin.getLogger().warning("Attempted to restore state for offline or null player.");
            return;
        }

        PlayerState originalState = null;
        if (duel != null) { // Duel object is passed if coming from respawn or forfeit
            if (player.getUniqueId().equals(duel.getChallengerUUID())) {
                originalState = duel.getChallengerOriginalState();
            } else if (player.getUniqueId().equals(duel.getChallengedUUID())) {
                originalState = duel.getChallengedOriginalState();
            }
        }
        
        // If no duel context (e.g., from a command or general cleanup) and no original state,
        // revert to default survival.
        if (originalState == null) {
            plugin.getLogger().warning("No original state found for player " + player.getName() + " to restore. Setting to default survival.");
            player.setGameMode(GameMode.SURVIVAL);
            player.setFlying(false);
            player.setAllowFlight(false);
            return;
        }

        player.setHealth(originalState.getHealth());
        player.setFoodLevel(originalState.getFoodLevel());
        player.setSaturation(originalState.getSaturation());

        player.setGameMode(originalState.getGameMode());

        // Clear all active potion effects before applying original ones
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        originalState.getPotionEffects().forEach(player::addPotionEffect);

        player.setFlying(originalState.isFlying());
        player.setAllowFlight(originalState.canFly());

        player.setFireTicks(0);
        player.setFallDistance(0);
        plugin.getLogger().info("Restored general state for player " + player.getName());
    }
    
    // 1.2.8: Method for restoring FULL player state including inventory/XP (used for logged-out keep-inv players)
    public void restoreFullPlayerState(Player player, PlayerState savedState) {
        if (player == null || !player.isOnline() || savedState == null) {
            plugin.getLogger().warning("Attempted to restore full state for null player or state.");
            return;
        }

        player.setHealth(savedState.getHealth());
        player.setFoodLevel(savedState.getFoodLevel());
        player.setSaturation(savedState.getSaturation());
        
        player.setTotalExperience(savedState.getTotalExperience()); // Restore total XP
        player.setLevel(savedState.getLevel());
        player.setExp(savedState.getExp());

        player.setGameMode(savedState.getGameMode());

        // Clear existing inventory and restore saved one
        player.getInventory().clear();
        if (savedState.hasInventorySaved()) { // Check if inventory was actually saved
            player.getInventory().setContents(savedState.getInventoryContents());
            player.getInventory().setHelmet(savedState.getHelmet());
            player.getInventory().setChestplate(savedState.getChestplate());
            player.getInventory().setLeggings(savedState.getLeggings());
            player.getInventory().setBoots(savedState.getBoots());
        }

        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        savedState.getPotionEffects().forEach(player::addPotionEffect);

        player.setFlying(savedState.isFlying());
        player.setAllowFlight(savedState.canFly());

        player.setFireTicks(0);
        player.setFallDistance(0);
        plugin.getLogger().info("Restored full state (including inventory) for player " + player.getName());
    }


    // --- Duel Lifecycle Methods ---

    public void startChallenge(Player challenger, Player challenged, boolean keepInventory) {
        // ... (unchanged)
        DuelChallenge existingOutgoing = getOutgoingChallengeByPlayer(challenger);
        if (existingOutgoing != null) {
            cancelChallenge(existingOutgoing);
        }
        DuelChallenge existingIncoming = getPendingChallengeByPlayer(challenged);
        if (existingIncoming != null) {
            cancelChallenge(existingIncoming);
        }

        DuelChallenge challenge = new DuelChallenge(challenger, challenged, plugin);
        challenge.setKeepInventory(keepInventory);
        outgoingChallenges.put(challenger, challenge);
        incomingChallenges.put(challenged, challenge);

        removeGuiChallenge(challenger);

        challenger.sendMessage(Component.text("You challenged " + challenged.getName() + " to a duel (Keep Inventory: " + (keepInventory ? "YES" : "NO") + "). Waiting for their response...").color(NamedTextColor.GOLD));
        challenged.sendMessage(Component.text(challenger.getName() + " has challenged you to a duel (Keep Inventory: " + (keepInventory ? "YES" : "NO") + ")! Type /duel accept to accept.").color(NamedTextColor.GOLD));
        challenger.playSound(challenged.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        challenge.startTimeout();
    }

    public void handleAcceptCommand(Player player) {
        DuelChallenge challenge = incomingChallenges.get(player);

        if (challenge == null) {
            player.sendMessage(Component.text("You have no pending duel challenges to accept.").color(NamedTextColor.RED));
            return;
        }

        challenge.cancelTimeout();

        Player challenger = challenge.getChallenger();
        Player challenged = challenge.getChallenged();
        boolean keepInventory = challenge.isKeepInventory();

        if (challenger == null || !challenger.isOnline()) {
            player.sendMessage(Component.text(Objects.requireNonNull(challenger).getName() + " is no longer online.").color(NamedTextColor.RED));
            cancelChallenge(challenge);
            return;
        }

        player.sendMessage(Component.text("You accepted the duel challenge from " + challenger.getName() + "!").color(NamedTextColor.GREEN));
        challenger.sendMessage(Component.text(challenged.getName() + " has accepted your duel challenge!").color(NamedTextColor.GREEN));

        int arenaNumber = arenaManager.findAvailableArena();
        if (arenaNumber == -1) {
            player.sendMessage(Component.text("No available arenas at the moment. Please try again later.").color(NamedTextColor.RED));
            challenger.sendMessage(Component.text("No available arenas at the moment. Please try again later.").color(NamedTextColor.RED));
            cancelChallenge(challenge);
            return;
        }

        // Duel object is now created with the new PlayerState saving (no inventory)
        Duel newDuel = new Duel(challenger, challenged, arenaNumber, keepInventory); // Uses new constructor
        newDuel.setActive(true);

        // Prepare players for the duel (reset health/food/effects, but keep inventory)
        preparePlayerForDuel(challenger, newDuel);
        preparePlayerForDuel(challenged, newDuel);

        Location pos1 = arenaManager.getArenaLocation(arenaNumber, 1);
        Location pos2 = arenaManager.getArenaLocation(arenaNumber, 2);

        if (pos1 == null || pos2 == null) {
            player.sendMessage(Component.text("Arena " + arenaNumber + " is not fully configured!").color(NamedTextColor.RED));
            challenger.sendMessage(Component.text("Arena " + arenaNumber + " is not fully configured!").color(NamedTextColor.RED));
            arenaManager.releaseArena(arenaNumber);
            cancelChallenge(challenge);
            return;
        }

        challenger.teleport(pos1);
        challenged.teleport(pos2);

        activeDuels.put(challenger.getUniqueId(), newDuel);
        activeDuels.put(challenged.getUniqueId(), newDuel);

        outgoingChallenges.remove(challenger);
        incomingChallenges.remove(challenged);

        challenger.sendMessage(Component.text("Your duel with " + challenged.getName() + " has begun!").color(NamedTextColor.GREEN));
        challenged.sendMessage(Component.text("Your duel with " + challenger.getName() + " has begun!").color(NamedTextColor.GREEN));

        addPlayerToCountdown(challenger.getUniqueId());
        addPlayerToCountdown(challenged.getUniqueId());

        new BukkitRunnable() {
            int countdown = 5;
            @Override
            public void run() {
                if (countdown > 0) {
                    Component message = Component.text("Duel starting in ").color(NamedTextColor.YELLOW)
                                             .append(Component.text(countdown).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                                             .append(Component.text("...").color(NamedTextColor.YELLOW));
                    if (challenger.isOnline() && activeDuels.containsKey(challenger.getUniqueId())) {
                        challenger.sendActionBar(message);
                        challenger.playSound(challenger.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                    }
                    if (challenged.isOnline() && activeDuels.containsKey(challenged.getUniqueId())) {
                        challenged.sendActionBar(message);
                        challenged.playSound(challenged.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                    }
                    countdown--;
                } else {
                    Component message = Component.text("FIGHT!").color(NamedTextColor.RED).decorate(TextDecoration.BOLD);
                    if (challenger.isOnline() && activeDuels.containsKey(challenger.getUniqueId())) {
                        challenger.sendActionBar(message);
                        challenger.playSound(challenger.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                    }
                    if (challenged.isOnline() && activeDuels.containsKey(challenged.getUniqueId())) {
                        challenged.sendActionBar(message);
                        challenged.playSound(challenged.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                    }
                    removePlayerFromCountdown(challenger.getUniqueId());
                    removePlayerFromCountdown(challenged.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void cancelChallenge(DuelChallenge challenge) {
        // ... (unchanged)
        if (challenge == null) return;

        challenge.cancelTimeout();

        Player challenger = challenge.getChallenger();
        Player challenged = challenge.getChallenged();

        outgoingChallenges.remove(challenger);
        incomingChallenges.remove(challenged);

        removeGuiChallenge(challenger);

        if (challenger != null && challenger.isOnline()) {
            challenger.sendMessage(Component.text("Your duel challenge to " + (challenged != null ? challenged.getName() : "an unknown player") + " has been cancelled.").color(NamedTextColor.RED));
        }
        if (challenged != null && challenged.isOnline()) {
            challenged.sendMessage(Component.text("The duel challenge from " + (challenger != null ? challenger.getName() : "an unknown player") + " has been cancelled.").color(NamedTextColor.RED));
        }
        plugin.getLogger().info("Duel challenge between " + (challenger != null ? challenger.getName() : "N/A") + " and " + (challenged != null ? challenged.getName() : "N/A") + " cancelled.");
    }
    public boolean isDisconnectedWinner(UUID uuid) {
        return disconnectedWinners.contains(uuid);
    }
    public void removeDisconnectedWinner(UUID uuid) {
        disconnectedWinners.remove(uuid);
    }

    public void endDuel(Player winner, Player loser, Duel duel) {
        if (duel == null) {
            plugin.getLogger().warning("endDuel called with null duel object.");
            return;
        }

        // --- Handle Winner ---
        if (winner != null) { // We check if winner is null here for safety
            if (winner.isOnline()) { // Only process online winners for the teleport countdown
                winner.sendMessage(Component.text("Congratulations! You won the duel!").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

                // Restore general player state for winner (health, food, etc.).
                restorePlayerState(winner, duel);
                winner.setGameMode(GameMode.SURVIVAL);
                winner.setFlying(false);
                winner.setAllowFlight(false);

                long delayTicks = duel.isKeepInventory() ? 5 * 20L : 30 * 20L;

                Component initialMessage = duel.isKeepInventory() ?
                    Component.text("You will be teleported to the global spawn in 5 seconds...").color(NamedTextColor.AQUA) :
                    Component.text("You have 30 seconds to collect the loot from " + (loser != null ? loser.getName() : "your opponent") + ".").color(NamedTextColor.GREEN)
                             .append(Component.text("\nYou will be teleported to the global spawn after this time.").color(NamedTextColor.GRAY));
                winner.sendMessage(initialMessage);

                new BukkitRunnable() {
                    int countdown = (int) (delayTicks / 20L);
                    @Override
                    public void run() {
                        // --- MODIFIED: Add logic here for winner disconnecting during countdown ---
                        if (!winner.isOnline()) {
                            // Winner logged out during countdown.
                            // Mark them so they get teleported to spawn on next login.
                            disconnectedWinners.add(winner.getUniqueId());
                            plugin.getLogger().info("Winner " + winner.getName() + " disconnected during teleport countdown. Marking for spawn return on rejoin.");
                            cancel(); // Stop this specific task
                            return;
                        }

                        if (countdown > 0) {
                            Component message = Component.text("Teleporting in " + countdown + "...").color(NamedTextColor.YELLOW);
                            winner.sendActionBar(message);
                            winner.playSound(winner.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                            countdown--;
                        } else {
                            Location spawnLoc = arenaManager.getSpawnLocation();
                            if (spawnLoc != null) {
                                winner.teleport(spawnLoc);
                                winner.sendMessage(Component.text("Time's up! You have been teleported to the global spawn location.").color(NamedTextColor.AQUA));
                            } else {
                                winner.sendMessage(Component.text("Global spawn location not set, spawning at world spawn.").color(NamedTextColor.RED));
                            }
                            winner.sendActionBar(Component.text("Teleported!").color(NamedTextColor.GREEN));
                            cancel();
                        }
                    }
                }.runTaskTimer(plugin, 0L, 20L);

            } else {
                // Winner is OFFLINE at the moment endDuel is called (e.g., opponent quit, making this player the winner)
                // Mark them to be teleported to spawn on their next login.
                disconnectedWinners.add(winner.getUniqueId());
                plugin.getLogger().info("Winner " + winner.getName() + " was offline when duel ended. Marking for spawn return on rejoin.");
            }
        }


        // --- Handle Loser ---
        if (loser != null) {
            if (loser.isOnline()) {
                if (loser.getHealth() <= 0) { // Loser died naturally in the duel
                    plugin.getLogger().info("Loser " + loser.getName() + " died in duel, marking for respawn handling. Duel ID: " + duel.getDuelId());
                    addPlayerDiedInDuel(loser.getUniqueId(), duel.getDuelId());
                } else { // Loser forfeited by command or disconnected while still alive
                    plugin.getLogger().info("Loser " + loser.getName() + " forfeited. Restoring state and cleaning up duel: " + duel.getDuelId());
                    restorePlayerState(loser, duel);
                    Location spawnLoc = arenaManager.getSpawnLocation();
                    if (spawnLoc != null) {
                        loser.teleport(spawnLoc);
                        loser.sendMessage(Component.text("You forfeited the duel and have been returned to spawn.").color(NamedTextColor.RED));
                    } else {
                        loser.sendMessage(Component.text("You forfeited the duel. Global spawn not set, returning to world spawn.").color(NamedTextColor.RED));
                    }
                    removeDuel(duel.getDuelId(), "endDuel_Forfeiture_OnlineLoser");
                }
            } else { // Loser is offline
                plugin.getLogger().info("Loser " + loser.getName() + " was offline during duel end. No immediate state restoration/teleport.");
                // If this loser was a keep-inventory player, their state should already be in `loggedOutKeepInvPlayers`
                // if they logged out during the duel. No explicit action needed here for them.
            }
        }

        // --- Final Duel Cleanup (Ensures duel is always removed) ---
        if (getDuelById(duel.getDuelId()) != null) {
            plugin.getLogger().info("Finalizing duel cleanup for " + duel.getDuelId() + " from endDuel method.");
            removeDuel(duel.getDuelId(), "endDuel_FinalCleanup");
        } else {
            plugin.getLogger().info("Duel " + duel.getDuelId() + " already removed during endDuel execution.");
        }
    }
}