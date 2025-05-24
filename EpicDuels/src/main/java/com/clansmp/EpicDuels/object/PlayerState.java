package com.clansmp.EpicDuels.object;

import org.bukkit.GameMode;
//import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.Collection;
import java.util.UUID;

public class PlayerState {

    private final UUID playerUUID;
    private final double health;
    private final int foodLevel;
    private final float saturation;
    private final float exp;
    private final int level;
    private final int totalExperience; // <--- This field should be here
    private final GameMode gameMode;
    private final ItemStack[] inventoryContents;
    private final ItemStack helmet;
    private final ItemStack chestplate;
    private final ItemStack leggings;
    private final ItemStack boots;
    private final Collection<PotionEffect> potionEffects;
    private final boolean isFlying;
    private final boolean canFly;

    public PlayerState(Player player) {
        this.playerUUID = player.getUniqueId();
        this.health = player.getHealth();
        this.foodLevel = player.getFoodLevel();
        this.saturation = player.getSaturation();
        this.exp = player.getExp();
        this.level = player.getLevel();
        this.totalExperience = player.getTotalExperience(); // <--- This is where it's saved
        this.gameMode = player.getGameMode();
        this.inventoryContents = player.getInventory().getContents().clone();
        this.helmet = player.getInventory().getHelmet() != null ? player.getInventory().getHelmet().clone() : null;
        this.chestplate = player.getInventory().getChestplate() != null ? player.getInventory().getChestplate().clone() : null;
        this.leggings = player.getInventory().getLeggings() != null ? player.getInventory().getLeggings().clone() : null;
        this.boots = player.getInventory().getBoots() != null ? player.getInventory().getBoots().clone() : null;
        this.potionEffects = player.getActivePotionEffects();
        this.isFlying = player.isFlying();
        this.canFly = player.getAllowFlight();
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public double getHealth() {
        return health;
    }

    public int getFoodLevel() {
        return foodLevel;
    }

    public float getSaturation() {
        return saturation;
    }

    public float getExp() {
        return exp;
    }

    public int getLevel() {
        return level;
    }

    public int getTotalExperience() { // GET XP LEVEL
        return totalExperience;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public ItemStack[] getInventoryContents() {
        return inventoryContents;
    }

    public ItemStack getHelmet() {
        return helmet;
    }

    public ItemStack getChestplate() {
        return chestplate;
    }

    public ItemStack getLeggings() {
        return leggings;
    }

    public ItemStack getBoots() {
        return boots;
    }

    public Collection<PotionEffect> getPotionEffects() {
        return potionEffects;
    }

    public boolean isFlying() {
        return isFlying;
    }

    public boolean canFly() {
        return canFly;
    }
}