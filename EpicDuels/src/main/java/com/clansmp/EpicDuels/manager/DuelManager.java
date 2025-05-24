package com.clansmp.EpicDuels.manager;

//import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import com.clansmp.EpicDuels.EpicDuels;
import com.clansmp.EpicDuels.object.Duel;
import com.clansmp.EpicDuels.object.DuelChallenge;
import com.clansmp.EpicDuels.object.PlayerState;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;

public class DuelManager {

    private final EpicDuels plugin;
    private final ArenaManager arenaManager;

    private final Map<UUID, Duel> activeDuels;
    private final Map<Player, DuelChallenge> outgoingChallenges;
    private final Map<Player, DuelChallenge> incomingChallenges;

    private final Map<Player, DuelChallenge> guiChallenges; // Map to store challenges in GUI setup phase (Challenger -> DuelChallenge)

    private final Set<UUID> playersForcedToSpawnAtGlobal; // For players who logged out during a non-keep-inv duel
    private final Map<UUID, UUID> playersDiedInDuelAndAwaitingRespawn; // PlayerUUID -> DuelUUID for natural death
    private final Set<UUID> playersInCountdown; // Track players in countdown

    public DuelManager(EpicDuels plugin, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.activeDuels = new ConcurrentHashMap<>();
        this.outgoingChallenges = new HashMap<>();
        this.incomingChallenges = new HashMap<>();
        this.guiChallenges = new HashMap<>();
        this.playersForcedToSpawnAtGlobal = new HashSet<>();
        this.playersDiedInDuelAndAwaitingRespawn = new ConcurrentHashMap<>();
        this.playersInCountdown = new HashSet<>(); // Initialize the countdown set
    }

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
        // Since activeDuels stores by player UUID, we need to iterate over values to find by Duel ID.
        // This is not the most efficient for large numbers of active duels, but suitable for typical use.
        for (Duel duel : activeDuels.values()) {
            if (duel.getDuelId().equals(duelId)) {
                return duel;
            }
        }
        return null; // Not found
    }

    public DuelChallenge getPendingChallengeByPlayer(Player player) {
        return incomingChallenges.get(player);
    }

    public DuelChallenge getOutgoingChallengeByPlayer(Player player) {
        return outgoingChallenges.get(player);
    }

    public void removeDuel(UUID duelId, String source) { // Added 'source' parameter
        Duel duelToRemove = null;
        // Search for the duel by ID in the active duels map (values)
        for (Duel duel : activeDuels.values()) {
            if (duel.getDuelId().equals(duelId)) {
                duelToRemove = duel;
                break;
            }
        }
        if (duelToRemove != null) {
            // Remove by both player UUIDs since it's a map keyed by player UUID
            activeDuels.remove(duelToRemove.getChallengerUUID());
            activeDuels.remove(duelToRemove.getChallengedUUID());

            // Also remove from your playersDiedInDuelAndAwaitingRespawn if it's there.
            playersDiedInDuelAndAwaitingRespawn.remove(duelToRemove.getChallengerUUID());
            playersDiedInDuelAndAwaitingRespawn.remove(duelToRemove.getChallengedUUID());
            // --- MODIFICATION START ---
            // Set duel inactive and release arena here, as this is the final cleanup point.
            duelToRemove.setActive(false);
            arenaManager.releaseArena(duelToRemove.getArenaNumber());
            // Updated log message to include the source
            plugin.getLogger().info("Duel " + duelId + " fully cleaned up by " + source + " and arena " + duelToRemove.getArenaNumber() + " released.");
            // --- MODIFICATION END ---
        } else {
             plugin.getLogger().warning("Attempted to remove non-existent duel with ID: " + duelId + " from source: " + source);
        }
    }



    // --- Player State Management Methods ---

    public void preparePlayerForDuel(Player player, Duel duel) {
        if (player == null || !player.isOnline()) return;

        // Reset health/food/effects for fairness, but do NOT clear inventory
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

        // Inventory is *not* cleared here, players duel with their current gear.
        plugin.getLogger().info("Prepared " + player.getName() + " for duel.");
    }

    public void restorePlayerState(Player player, Duel duel) {
        if (player == null || !player.isOnline()) {
            plugin.getLogger().warning("Attempted to restore state for offline or null player.");
            return;
        }

        PlayerState originalState = null;
        if (duel != null) {
            if (player.getUniqueId().equals(duel.getChallengerUUID())) {
                originalState = duel.getChallengerOriginalState();
            } else if (player.getUniqueId().equals(duel.getChallengedUUID())) {
                originalState = duel.getChallengedOriginalState();
            }
        }

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

        // We specifically remove setting XP/Level here because PlayerRespawnEvent will handle it
        // directly from playerDuelXPSave.
        // player.setLevel(originalState.getLevel());
        // player.setExp(originalState.getExp());
        // player.setTotalExperience(originalState.getTotalExperience());

        player.setGameMode(originalState.getGameMode());

        player.getInventory().clear();
        player.getInventory().setContents(originalState.getInventoryContents());
        player.getInventory().setHelmet(originalState.getHelmet());
        player.getInventory().setChestplate(originalState.getChestplate());
        player.getInventory().setLeggings(originalState.getLeggings());
        player.getInventory().setBoots(originalState.getBoots());

        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        originalState.getPotionEffects().forEach(player::addPotionEffect);

        player.setFlying(originalState.isFlying());
        player.setAllowFlight(originalState.canFly());

        player.setFireTicks(0);
        player.setFallDistance(0);
        plugin.getLogger().info("Restored state for player " + player.getName());
    }


    // --- Duel Lifecycle Methods ---

    public void startChallenge(Player challenger, Player challenged, boolean keepInventory) {
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

        removeGuiChallenge(challenger); // Remove from GUI tracking as challenge is now formal

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
            // Note: arenaManager.releaseArena(arenaNumber) is removed here as arenaNumber is -1 and it's not a valid arena to release.
            cancelChallenge(challenge);
            return;
        }

        // Player states are saved HERE, BEFORE preparing them for the duel!
        Duel newDuel = new Duel(challenger, challenged, arenaNumber, keepInventory);
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

        // Announce the start of the duel
        challenger.sendMessage(Component.text("Your duel with " + challenged.getName() + " has begun!").color(NamedTextColor.GREEN));
        challenged.sendMessage(Component.text("Your duel with " + challenger.getName() + " has begun!").color(NamedTextColor.GREEN));

        // Add players to countdown set to prevent movement
        addPlayerToCountdown(challenger.getUniqueId());
        addPlayerToCountdown(challenged.getUniqueId());

        // Start countdown
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
                    // Remove players from countdown set after it finishes
                    removePlayerFromCountdown(challenger.getUniqueId());
                    removePlayerFromCountdown(challenged.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void cancelChallenge(DuelChallenge challenge) {
        if (challenge == null) return;

        challenge.cancelTimeout();

        Player challenger = challenge.getChallenger();
        Player challenged = challenge.getChallenged();

        outgoingChallenges.remove(challenger);
        incomingChallenges.remove(challenged);

        removeGuiChallenge(challenger); // Ensure GUI-related challenge is also cleared

        if (challenger != null && challenger.isOnline()) {
            challenger.sendMessage(Component.text("Your duel challenge to " + (challenged != null ? challenged.getName() : "an unknown player") + " has been cancelled.").color(NamedTextColor.RED));
        }
        if (challenged != null && challenged.isOnline()) {
            challenged.sendMessage(Component.text("The duel challenge from " + (challenger != null ? challenger.getName() : "an unknown player") + " has been cancelled.").color(NamedTextColor.RED));
        }
        plugin.getLogger().info("Duel challenge between " + (challenger != null ? challenger.getName() : "N/A") + " and " + (challenged != null ? challenged.getName() : "N/A") + " cancelled.");
    }

    public void endDuel(Player winner, Player loser, Duel duel) {
        if (duel == null) return;

        // Clean up duel state (these were already commented out, good!)
        //activeDuels.remove(duel.getChallengerUUID());
        //activeDuels.remove(duel.getChallengedUUID());
        // --- MODIFICATION START ---
        // Removed duel.setActive(false) from here; it's now in removeDuel.
        // Removed arenaManager.releaseArena() and its corresponding log from here; it's now in removeDuel.
        // --- MODIFICATION END ---

        // --- Handle Winner ---
        if (winner != null && winner.isOnline()) {
            winner.sendMessage(Component.text("Congratulations! You won the duel!").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

            if (!duel.isKeepInventory()) { // Keep Inventory OFF -> give time for loot collection
                winner.sendMessage(Component.text("You have 30 seconds to collect the loot from " + (loser != null ? loser.getName() : "your opponent") + ".").color(NamedTextColor.GREEN));
                winner.sendMessage(Component.text("You will be teleported to the global spawn in 30 seconds.").color(NamedTextColor.GRAY));

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (winner.isOnline()) {
                            Location spawnLoc = arenaManager.getSpawnLocation();
                            if (spawnLoc != null) {
                                winner.teleport(spawnLoc);
                                winner.sendMessage(Component.text("Time's up! You have been teleported to the global spawn location.").color(NamedTextColor.AQUA));
                            } else {
                                winner.sendMessage(Component.text("Global spawn location not set, spawning at world spawn.").color(NamedTextColor.RED));
                            }
                        }
                    }
                }.runTaskLater(plugin, 30 * 20L); // 30 seconds * 20 ticks/second

            } else { // Keep Inventory ON -> give 5 seconds to bask in glory
                winner.sendMessage(Component.text("You will be teleported to the global spawn in 5 seconds...").color(NamedTextColor.AQUA));
                new BukkitRunnable() {
                    int countdown = 5;
                    @Override
                    public void run() {
                        if (winner.isOnline()) {
                            if (countdown > 0) {
                                winner.sendActionBar(Component.text("Teleporting in " + countdown + "...").color(NamedTextColor.YELLOW));
                                winner.playSound(winner.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                                countdown--;
                            } else {
                                Location spawnLoc = arenaManager.getSpawnLocation();
                                if (spawnLoc != null) {
                                    winner.teleport(spawnLoc);
                                    winner.sendMessage(Component.text("You have been teleported to the global spawn location.").color(NamedTextColor.AQUA));
                                } else {
                                    winner.sendMessage(Component.text("Global spawn location not set, spawning at world spawn.").color(NamedTextColor.RED));
                                }
                                winner.sendActionBar(Component.text("Teleported!").color(NamedTextColor.GREEN));
                                cancel();
                            }
                        } else {
                            cancel(); // Cancel if winner goes offline
                        }
                    }
                }.runTaskTimer(plugin, 0L, 20L); // 5 seconds * 20 ticks/second
            }
            // Always ensure winner is in survival mode and flight off if not an admin
            winner.setGameMode(GameMode.SURVIVAL);
            winner.setFlying(false);
            winner.setAllowFlight(false);}
        // handle loser
        {
        if (loser != null) {
            if (loser.isOnline()) {
                if (loser.getHealth() <= 0) { // Loser died naturally in the duel
                    plugin.getLogger().info("Loser " + loser.getName() + " died in duel, marking for respawn handling.");
                    addPlayerDiedInDuel(loser.getUniqueId(), duel.getDuelId());
                } else { // Loser forfeited or disconnected while still alive
                    // ... (restore state for forfeiture, teleport) ...
                    plugin.getLogger().info("Loser " + loser.getName() + " forfeited. Fully cleaning up duel: " + duel.getDuelId());
                    // Old: removeDuel(duel.getDuelId());
                    removeDuel(duel.getDuelId(), "endDuel_Forfeiture"); // Updated call
                }
            } else {
                // Loser is offline. Their state won't be restored, and teleport is not applicable.
                plugin.getLogger().info("Loser " + loser.getName() + " was offline during duel end. No state restoration/teleport.");
                // If the loser is offline and the duel needs to be cleaned up because the other player finished,
                // you might still want to call removeDuel here, or rely on PlayerQuitEvent if that fires earlier.
                // For now, let's keep it consistent with the onPlayerQuit logic for offline players.
                // If this branch is reached, and the duel isn't cleaned up by the PlayerQuitEvent,
                // it might linger. For now, rely on PlayerQuitEvent to handle offline players.
            }
        }
        plugin.getLogger().info("Duel " + duel.getDuelId() + " between " +
                (winner != null ? winner.getName() : "N/A") + " and " +
                (loser != null ? loser.getName() : "N/A") + " has ended (logically).");
    }

        plugin.getLogger().info("Duel " + duel.getDuelId() + " between " +
                (winner != null ? winner.getName() : "N/A") + " and " +
                (loser != null ? loser.getName() : "N/A") + " has ended (logically).");
    }
}