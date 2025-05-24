package com.clansmp.EpicDuels.object;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class Arena {

    private final int arenaNumber;
    private Location pos1;
    private Location pos2;
    private boolean occupied; // True if an active duel is using this arena

    public Arena(int arenaNumber) {
        this.arenaNumber = arenaNumber;
        this.occupied = false; // Arenas start as not occupied
    }

    public int getArenaNumber() {
        return arenaNumber;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public void setOccupied(boolean occupied) {
        this.occupied = occupied;
    }

    /**
     * Checks if both position 1 and position 2 are set for the arena.
     * @return true if both positions are set, false otherwise.
     */
    public boolean isFullyConfigured() {
        return pos1 != null && pos2 != null;
    }

    /**
     * Sets one of the arena's teleportation locations.
     * @param position 1 or 2.
     * @param location The Bukkit Location object.
     */
    public void setLocation(int position, Location location) {
        if (position == 1) {
            this.pos1 = location;
        } else if (position == 2) {
            this.pos2 = location;
        } else {
            throw new IllegalArgumentException("Invalid arena position: " + position + ". Must be 1 or 2.");
        }
    }

    /**
     * Gets one of the arena's teleportation locations.
     * @param position 1 or 2.
     * @return The Bukkit Location object or null if not set.
     */
    public Location getLocation(int position) {
        if (position == 1) {
            return pos1;
        } else if (position == 2) {
            return pos2;
        }
        return null;
    }

    /**
     * Loads arena data from the plugin's configuration file.
     * @param config The FileConfiguration instance (e.g., plugin.getConfig()).
     * @param path The base path for this arena's data (e.g., "arenas.1").
     */
    public void loadFromConfig(FileConfiguration config, String path) {
        ConfigurationSection arenaSection = config.getConfigurationSection(path);
        if (arenaSection == null) {
            return; // No data for this arena
        }

        // Load pos1
        ConfigurationSection pos1Section = arenaSection.getConfigurationSection("pos1");
        if (pos1Section != null) {
            this.pos1 = deserializeLocation(pos1Section);
        }

        // Load pos2
        ConfigurationSection pos2Section = arenaSection.getConfigurationSection("pos2");
        if (pos2Section != null) {
            this.pos2 = deserializeLocation(pos2Section);
        }

        // Load occupied status (default to false if not found or on startup)
        this.occupied = arenaSection.getBoolean("occupied", false);
    }

    /**
     * Saves arena data to the plugin's configuration file.
     * @param config The FileConfiguration instance (e.g., plugin.getConfig()).
     * @param path The base path for this arena's data (e.g., "arenas.1").
     */
    public void saveToConfig(FileConfiguration config, String path) {
        // Clear previous data for this arena path to ensure clean save
        // Commenting this out for now to avoid losing existing sections if config.set(path, null)
        // behaves unexpectedly with FileConfiguration, though it's standard for clear.
        // For simplicity, we'll just overwrite specific keys.
        // config.set(path, null); // Remove existing section to prevent stale data

        // Save pos1
        if (pos1 != null) {
            serializeLocation(config, path + ".pos1", pos1);
        } else {
            config.set(path + ".pos1", null); // Remove if no longer set
        }

        // Save pos2
        if (pos2 != null) {
            serializeLocation(config, path + ".pos2", pos2);
        } else {
            config.set(path + ".pos2", null); // Remove if no longer set
        }

        // Save occupied status
        config.set(path + ".occupied", occupied);
    }

    // --- Helper methods for Location serialization/deserialization ---

    private void serializeLocation(FileConfiguration config, String path, Location loc) {
        if (loc == null) return;
        config.set(path + ".world", loc.getWorld().getName());
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".yaw", loc.getYaw());
        config.set(path + ".pitch", loc.getPitch());
    }

    private Location deserializeLocation(ConfigurationSection section) {
        if (section == null) return null;

        String worldName = section.getString("world");
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw");
        float pitch = (float) section.getDouble("pitch");

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            // Log a warning if the world doesn't exist, but still return a location for now.
            Bukkit.getLogger().warning("World '" + worldName + "' not found for arena location. Attempting to use default world.");
            world = Bukkit.getWorlds().stream().findFirst().orElse(null); // Fallback to first loaded world
            if (world == null) {
                 Bukkit.getLogger().severe("No worlds loaded at all, cannot deserialize arena location.");
                 return null;
            }
        }
        return new Location(world, x, y, z, yaw, pitch);
    }
}