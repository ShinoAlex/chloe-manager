package com.chloe.chloemanager.model;

import com.chloe.chloemanager.ChloeManagerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MODEL — HomeModel
 *
 * Stores one home location per player.  Persists to home_data.yml.
 *
 * Format:
 *   homes:
 *     <uuid>:
 *       world: <name>
 *       x: <double>
 *       y: <double>
 *       z: <double>
 *       yaw: <float>
 *       pitch: <float>
 */
public class HomeModel {

    private static final String FILE_NAME  = "home_data.yml";
    private static final String HOMES_KEY  = "homes";

    private final File              dataFile;
    private       FileConfiguration cfg;
    private final Map<UUID, Location> homes = new HashMap<>();

    public HomeModel(ChloeManagerPlugin plugin) {
        dataFile = new File(plugin.getDataFolder(), FILE_NAME);
        load();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean hasHome(UUID uuid) {
        return homes.containsKey(uuid);
    }

    public Location getHome(UUID uuid) {
        return homes.get(uuid);
    }

    public void setHome(UUID uuid, Location loc) {
        homes.put(uuid, loc.clone());
        save();
    }

    public void removeHome(UUID uuid) {
        homes.remove(uuid);
        save();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void save() {
        cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Location> entry : homes.entrySet()) {
            String path = HOMES_KEY + "." + entry.getKey();
            Location loc = entry.getValue();
            cfg.set(path + ".world", loc.getWorld().getName());
            cfg.set(path + ".x",     loc.getX());
            cfg.set(path + ".y",     loc.getY());
            cfg.set(path + ".z",     loc.getZ());
            cfg.set(path + ".yaw",   loc.getYaw());
            cfg.set(path + ".pitch", loc.getPitch());
        }
        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            ChloeManagerPlugin.getInstance().getLogger()
                    .warning("Failed to save home_data.yml: " + e.getMessage());
        }
    }

    private void load() {
        if (!dataFile.exists()) {
            cfg = new YamlConfiguration();
            return;
        }
        cfg = YamlConfiguration.loadConfiguration(dataFile);
        if (cfg.isConfigurationSection(HOMES_KEY)) {
            for (String key : cfg.getConfigurationSection(HOMES_KEY).getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String path = HOMES_KEY + "." + key;
                    String worldName = cfg.getString(path + ".world", "world");
                    World  world  = Bukkit.getWorld(worldName);
                    if (world == null) {
                        ChloeManagerPlugin.getInstance().getLogger()
                                .warning("World not found for home of " + uuid + ": " + worldName);
                        continue;
                    }
                    Location loc = new Location(world,
                            cfg.getDouble(path + ".x"),
                            cfg.getDouble(path + ".y"),
                            cfg.getDouble(path + ".z"),
                            (float) cfg.getDouble(path + ".yaw"),
                            (float) cfg.getDouble(path + ".pitch"));
                    homes.put(uuid, loc);
                } catch (IllegalArgumentException e) {
                    ChloeManagerPlugin.getInstance().getLogger()
                            .warning("Skipping malformed home UUID: " + key);
                }
            }
        }
    }
}
