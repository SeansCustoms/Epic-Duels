package com.clansmp.EpicDuels.object;

import org.bukkit.entity.Player;

import java.util.UUID;

public class Duel {

    private final UUID duelId;
    private final UUID challengerUUID;
    private final UUID challengedUUID;
    private final int arenaNumber;
    private boolean active;
    private final boolean keepInventory; // <--- NEW FIELD

    // Storing original player states
    private PlayerState challengerOriginalState;
    private PlayerState challengedOriginalState;


    public Duel(Player challenger, Player challenged, int arenaNumber, boolean keepInventory) { // <--- MODIFIED CONSTRUCTOR
        this.duelId = UUID.randomUUID();
        this.challengerUUID = challenger.getUniqueId();
        this.challengedUUID = challenged.getUniqueId();
        this.arenaNumber = arenaNumber;
        this.active = false; // Set to true when duel officially starts
        this.keepInventory = keepInventory; // <--- ASSIGN NEW FIELD

        // Save original states immediately upon Duel object creation
        this.challengerOriginalState = new PlayerState(challenger);
        this.challengedOriginalState = new PlayerState(challenged);
    }

    public UUID getDuelId() {
        return duelId;
    }

    public UUID getChallengerUUID() {
        return challengerUUID;
    }

    public UUID getChallengedUUID() {
        return challengedUUID;
    }

    public int getArenaNumber() {
        return arenaNumber;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isKeepInventory() { // <--- NEW GETTER
        return keepInventory;
    }

    public PlayerState getChallengerOriginalState() {
        return challengerOriginalState;
    }

    public PlayerState getChallengedOriginalState() {
        return challengedOriginalState;
    }

    // Helper to get opponent player object if available
    public Player getOpponent(Player player) {
        if (player.getUniqueId().equals(challengerUUID)) {
            return org.bukkit.Bukkit.getPlayer(challengedUUID);
        } else if (player.getUniqueId().equals(challengedUUID)) {
            return org.bukkit.Bukkit.getPlayer(challengerUUID);
        }
        return null;
    }
}