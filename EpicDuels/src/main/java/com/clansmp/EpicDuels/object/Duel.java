package com.clansmp.EpicDuels.object;

import org.bukkit.entity.Player;

import java.util.UUID;

public class Duel {

    private final UUID duelId;
    private final UUID challengerUUID;
    private final UUID challengedUUID;
    private final int arenaNumber;
    private boolean active;
    private final boolean keepInventory;

    // Storing original player states (NO INVENTORY SAVED HERE)
    private PlayerState challengerOriginalState;
    private PlayerState challengedOriginalState;


    public Duel(Player challenger, Player challenged, int arenaNumber, boolean keepInventory) {
        this.duelId = UUID.randomUUID();
        this.challengerUUID = challenger.getUniqueId();
        this.challengedUUID = challenged.getUniqueId();
        this.arenaNumber = arenaNumber;
        this.active = false;
        this.keepInventory = keepInventory;

        // Save original states, but DO NOT save inventory here.
        // Inventory handling will be managed by Bukkit's death event or custom logout/login logic.
        this.challengerOriginalState = new PlayerState(challenger, false); // Pass 'false' to not save inventory
        this.challengedOriginalState = new PlayerState(challenged, false);  // Pass 'false' to not save inventory
    }

    public UUID getDuelId() { return duelId; }
    public UUID getChallengerUUID() { return challengerUUID; }
    public UUID getChallengedUUID() { return challengedUUID; }
    public int getArenaNumber() { return arenaNumber; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isKeepInventory() { return keepInventory; }
    public PlayerState getChallengerOriginalState() { return challengerOriginalState; }
    public PlayerState getChallengedOriginalState() { return challengedOriginalState; }

    public Player getOpponent(Player player) {
        if (player.getUniqueId().equals(challengerUUID)) {
            return org.bukkit.Bukkit.getPlayer(challengedUUID);
        } else if (player.getUniqueId().equals(challengedUUID)) {
            return org.bukkit.Bukkit.getPlayer(challengerUUID);
        }
        return null;
    }
}