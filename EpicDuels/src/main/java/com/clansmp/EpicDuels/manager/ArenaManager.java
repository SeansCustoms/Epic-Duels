package com.clansmp.EpicDuels.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import com.clansmp.EpicDuels.EpicDuels;
import com.clansmp.EpicDuels.object.Arena;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList; // Added for random arena selection
import java.util.List;     // Added for random arena selection
import java.util.Random;   // Added for random arena selection

public class ArenaManager {

    private final EpicDuels plugin;
    private final FileConfiguration arenasConfig;
    private final Map<Integer, Arena> arenas;
    private Location spawnLocation; // Global return spawn location

    public ArenaManager(EpicDuels plugin) {
        this.plugin = plugin;
        this.arenasConfig = plugin.getConfig(); // Use the main plugin config for arenas.yml
        this.arenas = new HashMap<>();
        loadArenas(); // Load arenas from arenas.yml on startup
        loadSpawnLocation(); // Load the global spawn location on startup
    }

    private void loadArenas() {
        ConfigurationSection arenaSection = arenasConfig.getConfigurationSection("arenas");
        if (arenaSection != null) {
            for (String key : arenaSection.getKeys(false)) {
                try {
                    int arenaNumber = Integer.parseInt(key);
                    Arena arena = new Arena(arenaNumber);
                    arena.loadFromConfig(arenasConfig, "arenas." + key);
                    arenas.put(arenaNumber, arena);
                    plugin.getLogger().info("Loaded arena " + arenaNumber + " from config.yml");
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid arena number in config.yml: " + key);
                }
            }
        } else {
            plugin.getLogger().info("No arenas configured in config.yml");
        }
    }

    public void saveArenas() {
        for (Arena arena : arenas.values()) {
            arena.saveToConfig(arenasConfig, "arenas." + arena.getArenaNumber());
        }
        saveConfig();
    }

    public Location getArenaLocation(int arenaNumber, int position) {
        Arena arena = arenas.get(arenaNumber);
        if (arena != null) {
            return arena.getLocation(position);
        }
        return null;
    }

    public int findAvailableArena() {
        List<Arena> availableArenas = new ArrayList<>();
        // Collect all arenas that are fully configured and not currently occupied
        for (Arena arena : arenas.values()) {
            if (arena.isFullyConfigured() && !arena.isOccupied()) {
                availableArenas.add(arena);
            }
        }

        if (availableArenas.isEmpty()) {
            // No available arenas found
            return -1;
        }

        // Randomly select one from the list of available arenas
        Random random = new Random();
        int randomIndex = random.nextInt(availableArenas.size());
        Arena chosenArena = availableArenas.get(randomIndex);

        // Mark the chosen arena as occupied and save the change to config
        chosenArena.setOccupied(true);
        saveArenas(); // Crucial: saves the occupied status to config

        plugin.getLogger().info("Randomly selected Arena " + chosenArena.getArenaNumber() + " and marked it as occupied.");
        return chosenArena.getArenaNumber();
    }

    public Arena getArena(int arenaNumber) {
        return arenas.get(arenaNumber);
    }

    public void setArenaLocation(int arenaNumber, int position, Location location) {
        Arena arena = arenas.computeIfAbsent(arenaNumber, k -> new Arena(arenaNumber));
        arena.setLocation(position, location);
        saveArenas();
        plugin.getLogger().info("Set arena " + arenaNumber + " position " + position + " to " + location);
    }

    public void releaseArena(int arenaNumber) {
        Arena arena = arenas.get(arenaNumber);
        if (arena != null) {
            arena.setOccupied(false);
            saveArenas(); // Releasing also requires saving the new status
            plugin.getLogger().info("Released arena " + arenaNumber);
        }
    }

    public Set<Integer> getAllArenaNumbers() {
        return arenas.keySet();
    }

    // --- Global Spawn Location Methods ---

    public void setSpawnLocation(Location location) {
        this.spawnLocation = location;
        saveSpawnLocation();
        plugin.getLogger().info("Global duel spawn location set to " + location);
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    private void loadSpawnLocation() {
        ConfigurationSection spawnSection = arenasConfig.getConfigurationSection("spawn");
        if (spawnSection != null) {
            String worldName = spawnSection.getString("world");
            if (worldName != null) {
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    double x = spawnSection.getDouble("x");
                    double y = spawnSection.getDouble("y");
                    double z = spawnSection.getDouble("z");
                    float yaw = (float) spawnSection.getDouble("yaw");
                    float pitch = (float) spawnSection.getDouble("pitch");
                    this.spawnLocation = new Location(world, x, y, z, yaw, pitch);
                    plugin.getLogger().info("Loaded global duel spawn location from config.");
                } else {
                    plugin.getLogger().warning("Invalid world name '" + worldName + "' in spawn location config.");
                }
            }
        } else {
            plugin.getLogger().info("No global duel spawn location configured.");
        }
    }

    private void saveSpawnLocation() {
        if (spawnLocation != null) {
            arenasConfig.set("spawn.world", spawnLocation.getWorld().getName());
            arenasConfig.set("spawn.x", spawnLocation.getX());
            arenasConfig.set("spawn.y", spawnLocation.getY());
            arenasConfig.set("spawn.z", spawnLocation.getZ());
            arenasConfig.set("spawn.yaw", spawnLocation.getYaw());
            arenasConfig.set("spawn.pitch", spawnLocation.getPitch());
            saveConfig();
        }
    }

    private void saveConfig() {
        try {
            arenasConfig.save(plugin.getDataFolder() + "/config.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save config.yml: " + e.getMessage());
        }
    }
}