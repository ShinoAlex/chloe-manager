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
 * Stores multiple home locations per player and how many home slots they
 * have unlocked.  Persists to home_data.yml.
 *
 * Players start with 1 home slot (index 1).  Two additional slots can be
 * purchased from the shop:
 *   • Slot 2 — $200,000   • Slot 3 — $1,000,000
 *
 * Storage format (home_data.yml):
 *   homes:
 *     <uuid>:
 *       max-homes: 1
 *       locations:
 *         1:
 *           world: <name>
 *           x: <double>
 *           y: <double>
 *           z: <double>
 *           yaw: <float>
 *           pitch: <float>
 *
 * Legacy single-home format (world/x/y/z at the root of the uuid node) is
 * automatically migrated to index 1 on first load.
 */
public class HomeModel {

    private static final String FILE_NAME  = "home_data.yml";
    private static final String HOMES_KEY  = "homes";

    public static final int DEFAULT_MAX_HOMES = 1;
    public static final int MAX_HOMES_CAP     = 3;

    private final File              dataFile;
    private       FileConfiguration cfg;

    /** uuid → (home index → Location) */
    private final Map<UUID, Map<Integer, Location>> homes = new HashMap<>();
    /** uuid → number of unlocked home slots (1–3) */
    private final Map<UUID, Integer> maxHomes = new HashMap<>();

    public HomeModel(ChloeManagerPlugin plugin) {
        dataFile = new File(plugin.getDataFolder(), FILE_NAME);
        load();
    }

    // ── Home-slot unlocking ─────────────────────────────────────────────────

    public int getMaxHomes(UUID uuid) {
        return maxHomes.getOrDefault(uuid, DEFAULT_MAX_HOMES);
    }

    /**
     * Unlocks the next home slot (e.g. 1→2 or 2→3).
     * @return true if a slot was unlocked, false if already at the cap.
     */
    public boolean unlockHomeSlot(UUID uuid) {
        int current = getMaxHomes(uuid);
        if (current >= MAX_HOMES_CAP) return false;
        maxHomes.put(uuid, current + 1);
        save();
        return true;
    }

    public boolean isSlotUnlocked(UUID uuid, int index) {
        return index >= 1 && index <= getMaxHomes(uuid);
    }

    // ── Home locations ───────────────────────────────────────────────────────

    public boolean hasHome(UUID uuid, int index) {
        Map<Integer, Location> playerHomes = homes.get(uuid);
        return playerHomes != null && playerHomes.containsKey(index);
    }

    public Location getHome(UUID uuid, int index) {
        Map<Integer, Location> playerHomes = homes.get(uuid);
        return playerHomes == null ? null : playerHomes.get(index);
    }

    public void setHome(UUID uuid, int index, Location loc) {
        homes.computeIfAbsent(uuid, k -> new HashMap<>()).put(index, loc.clone());
        save();
    }

    public void removeHome(UUID uuid, int index) {
        Map<Integer, Location> playerHomes = homes.get(uuid);
        if (playerHomes != null) {
            playerHomes.remove(index);
            save();
        }
    }

    // ── Backward-compatible single-home API (index = 1) ──────────────────────

    public boolean hasHome(UUID uuid)          { return hasHome(uuid, 1); }
    public Location getHome(UUID uuid)         { return getHome(uuid, 1); }
    public void setHome(UUID uuid, Location l) { setHome(uuid, 1, l); }
    public void removeHome(UUID uuid)          { removeHome(uuid, 1); }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void save() {
        cfg = new YamlConfiguration();
        // Save max-homes for every player that has either homes or a max record
        for (UUID uuid : maxHomes.keySet()) {
            cfg.set(HOMES_KEY + "." + uuid + ".max-homes", getMaxHomes(uuid));
        }
        for (Map.Entry<UUID, Map<Integer, Location>> entry : homes.entrySet()) {
            UUID uuid = entry.getKey();
            String base = HOMES_KEY + "." + uuid;
            // ensure max-homes is stored even if only homes map had the entry
            if (!maxHomes.containsKey(uuid)) {
                cfg.set(base + ".max-homes", DEFAULT_MAX_HOMES);
            }
            Map<Integer, Location> locs = entry.getValue();
            for (Map.Entry<Integer, Location> locEntry : locs.entrySet()) {
                String path = base + ".locations." + locEntry.getKey();
                Location loc = locEntry.getValue();
                cfg.set(path + ".world", loc.getWorld().getName());
                cfg.set(path + ".x",     loc.getX());
                cfg.set(path + ".y",     loc.getY());
                cfg.set(path + ".z",     loc.getZ());
                cfg.set(path + ".yaw",   loc.getYaw());
                cfg.set(path + ".pitch", loc.getPitch());
            }
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
        if (!cfg.isConfigurationSection(HOMES_KEY)) return;

        for (String key : cfg.getConfigurationSection(HOMES_KEY).getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String base = HOMES_KEY + "." + key;
                maxHomes.put(uuid, cfg.getInt(base + ".max-homes", DEFAULT_MAX_HOMES));

                // New format: locations section
                String locPath = base + ".locations";
                if (cfg.isConfigurationSection(locPath)) {
                    for (String idx : cfg.getConfigurationSection(locPath).getKeys(false)) {
                        try {
                            int index = Integer.parseInt(idx);
                            Location loc = readLocation(locPath + "." + idx);
                            if (loc != null) {
                                homes.computeIfAbsent(uuid, k -> new HashMap<>()).put(index, loc);
                            }
                        } catch (NumberFormatException ignored) { }
                    }
                } else if (cfg.contains(base + ".world")) {
                    // Legacy single-home format → migrate to index 1
                    Location loc = readLocation(base);
                    if (loc != null) {
                        homes.computeIfAbsent(uuid, k -> new HashMap<>()).put(1, loc);
                    }
                }
            } catch (IllegalArgumentException e) {
                ChloeManagerPlugin.getInstance().getLogger()
                        .warning("Skipping malformed home UUID: " + key);
            }
        }
    }

    private Location readLocation(String path) {
        String worldName = cfg.getString(path + ".world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            ChloeManagerPlugin.getInstance().getLogger()
                    .warning("World not found for home: " + worldName);
            return null;
        }
        return new Location(world,
                cfg.getDouble(path + ".x"),
                cfg.getDouble(path + ".y"),
                cfg.getDouble(path + ".z"),
                (float) cfg.getDouble(path + ".yaw"),
                (float) cfg.getDouble(path + ".pitch"));
    }
}
