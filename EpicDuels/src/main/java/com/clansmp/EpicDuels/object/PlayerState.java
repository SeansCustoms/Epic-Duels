package com.clansmp.EpicDuels.object;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType; // Import PotionEffectType

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.ArrayList; // Added for explicit ArrayList copy

@SuppressWarnings("unused")
public class PlayerState {

    private final UUID playerUUID;
    private final double health;
    private final int foodLevel;
    private final float saturation;
    private final float exp;
    private final int level;
    private final int totalExperience;
    private final GameMode gameMode;
    private final ItemStack[] inventoryContents;
    private final ItemStack helmet;
    private final ItemStack chestplate;
    private final ItemStack leggings;
    private final ItemStack boots;
    private final Collection<PotionEffect> potionEffects; // This should be final
    private final boolean isFlying;
    private final boolean canFly;

    // Constructor for saving ALL player state (e.g., on logout for keep-inv duels)
    public PlayerState(Player player) {
        this.playerUUID = player.getUniqueId();
        this.health = player.getHealth();
        this.foodLevel = player.getFoodLevel();
        this.saturation = player.getSaturation();
        this.exp = player.getExp();
        this.level = player.getLevel();
        this.totalExperience = player.getTotalExperience();
        this.gameMode = player.getGameMode();
        this.inventoryContents = player.getInventory().getContents().clone();
        this.helmet = player.getInventory().getHelmet() != null ? player.getInventory().getHelmet().clone() : null;
        this.chestplate = player.getInventory().getChestplate() != null ? player.getInventory().getChestplate().clone() : null;
        this.leggings = player.getInventory().getLeggings() != null ? player.getInventory().getLeggings().clone() : null;
        this.boots = player.getInventory().getBoots() != null ? player.getInventory().getBoots().clone() : null;
        // CORRECTED: Deep copy PotionEffects
        this.potionEffects = player.getActivePotionEffects().stream()
                               .map(effect -> new PotionEffect(effect.getType(), effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon()))
                               .collect(Collectors.toCollection(ArrayList::new)); // Collect to a modifiable list type

        this.isFlying = player.isFlying();
        this.canFly = player.getAllowFlight();
    }

    // NEW Constructor for saving ONLY non-inventory state (e.g., at duel start)
    public PlayerState(Player player, boolean saveInventory) {
        this.playerUUID = player.getUniqueId();
        this.health = player.getHealth();
        this.foodLevel = player.getFoodLevel();
        this.saturation = player.getSaturation();
        this.exp = player.getExp();
        this.level = player.getLevel();
        this.totalExperience = player.getTotalExperience();
        this.gameMode = player.getGameMode();

        if (saveInventory) {
            this.inventoryContents = player.getInventory().getContents().clone();
            this.helmet = player.getInventory().getHelmet() != null ? player.getInventory().getHelmet().clone() : null;
            this.chestplate = player.getInventory().getChestplate() != null ? player.getInventory().getChestplate().clone() : null;
            this.leggings = player.getInventory().getLeggings() != null ? player.getInventory().getLeggings().clone() : null;
            this.boots = player.getInventory().getBoots() != null ? player.getInventory().getBoots().clone() : null;
        } else {
            this.inventoryContents = null;
            this.helmet = null;
            this.chestplate = null;
            this.leggings = null;
            this.boots = null;
        }

        // CORRECTED: Deep copy PotionEffects
        this.potionEffects = player.getActivePotionEffects().stream()
                               .map(effect -> new PotionEffect(effect.getType(), effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon()))
                               .collect(Collectors.toCollection(ArrayList::new)); // Collect to a modifiable list type

        this.isFlying = player.isFlying();
        this.canFly = player.getAllowFlight();
    }

    // ... (rest of the getters are unchanged) ...

    public UUID getPlayerUUID() { return playerUUID; }
    public double getHealth() { return health; }
    public int getFoodLevel() { return foodLevel; }
    public float getSaturation() { return saturation; }
    public float getExp() { return exp; }
    public int getLevel() { return level; }
    public int getTotalExperience() { return totalExperience; }
    public GameMode getGameMode() { return gameMode; }
    // Ensure this returns a new collection or an unmodifiable one if you don't want external modification
    public Collection<PotionEffect> getPotionEffects() { return new ArrayList<>(potionEffects); } // Return a copy to prevent external modification
    public boolean isFlying() { return isFlying; }
    public boolean canFly() { return canFly; }

    public ItemStack[] getInventoryContents() { return inventoryContents; }
    public ItemStack getHelmet() { return helmet; }
    public ItemStack getChestplate() { return chestplate; }
    public ItemStack getLeggings() { return leggings; }
    public ItemStack getBoots() { return boots; }
    
    public boolean hasInventorySaved() {
        return inventoryContents != null;
    }
}