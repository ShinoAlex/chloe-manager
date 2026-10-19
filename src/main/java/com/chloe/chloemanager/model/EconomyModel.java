package com.chloe.chloemanager.model;

import com.chloe.chloemanager.ChloeManagerPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * MODEL — EconomyModel
 *
 * Manages player balances with full YAML persistence.
 * All reads and writes go through this class; no other layer touches the file.
 *
 * Storage format (playerdata.yml):
 *   balances:
 *     <uuid>: <double>
 */
public class EconomyModel {

    private static final String BALANCES_KEY = "balances";

    private final File dataFile;
    private FileConfiguration dataConfig;

    /** In-memory cache so every balance lookup does not hit the disk. */
    private final Map<UUID, Double> cache = new HashMap<>();

    public EconomyModel(ChloeManagerPlugin plugin) {
        dataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        load();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the balance for a player (0.0 if they have no record).
     */
    public double getBalance(UUID uuid) {
        return cache.getOrDefault(uuid, 0.0);
    }

    /**
     * Sets a player's balance directly.  Persists immediately.
     *
     * @throws IllegalArgumentException if amount is negative.
     */
    public void setBalance(UUID uuid, double amount) {
        if (amount < 0) throw new IllegalArgumentException("Balance cannot be negative.");
        cache.put(uuid, amount);
        save();
    }

    /**
     * Adds {@code amount} to a player's balance.
     */
    public void deposit(UUID uuid, double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Deposit amount must be positive.");
        cache.merge(uuid, amount, Double::sum);
        save();
    }

    /**
     * Removes {@code amount} from a player's balance.
     *
     * @return true if successful, false if the player does not have enough funds.
     */
    public boolean withdraw(UUID uuid, double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Withdrawal amount must be positive.");
        double current = getBalance(uuid);
        if (current < amount) return false;
        cache.put(uuid, current - amount);
        save();
        return true;
    }

    /**
     * Returns the top {@code limit} players sorted by balance descending.
     * Returns a list of Map.Entry<UUID, Double>.
     */
    public List<Map.Entry<UUID, Double>> getTopBalances(int limit) {
        return cache.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(limit)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    /** Loads balances from disk into the in-memory cache. */
    public void load() {
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            // File will be created on first save — nothing to load yet
            dataConfig = new YamlConfiguration();
            return;
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        if (dataConfig.isConfigurationSection(BALANCES_KEY)) {
            for (String key : dataConfig.getConfigurationSection(BALANCES_KEY).getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    double balance = dataConfig.getDouble(BALANCES_KEY + "." + key, 0.0);
                    cache.put(uuid, balance);
                } catch (IllegalArgumentException e) {
                    ChloeManagerPlugin.getInstance().getLogger()
                            .warning("Skipping malformed UUID in playerdata.yml: " + key);
                }
            }
        }
    }

    /** Writes the current in-memory cache to disk. */
    public void save() {
        cache.forEach((uuid, balance) ->
                dataConfig.set(BALANCES_KEY + "." + uuid.toString(), balance));

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            ChloeManagerPlugin.getInstance().getLogger()
                    .log(Level.SEVERE, "Failed to save playerdata.yml", e);
        }
    }
}
