package com.chloe.chloemanager.model;

import com.chloe.chloemanager.ChloeManagerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * MODEL — BankModel
 *
 * Stores per-player bank balances and the game-time of their last interest
 * payout.  Interest is 2% of the bank balance, paid every 30 Minecraft days
 * (30 × 24000 = 720,000 ticks of world full time).
 *
 * Persistence:
 *   bank_data.yml  — balances + last payout times
 *   bank_log.txt   — append-only log of deposits, withdrawals, and interest
 */
public class BankModel {

    private static final String DATA_FILE   = "bank_data.yml";
    private static final String LOG_FILE    = "bank_log.txt";
    private static final String PLAYERS_KEY = "players";

    /** 1 Minecraft day = 24000 ticks.  Interest cycle = 30 days. */
    public static final long TICKS_PER_DAY      = 24000L;
    public static final long INTEREST_CYCLE_DAYS = 30L;
    public static final long INTEREST_CYCLE_TICKS = INTEREST_CYCLE_DAYS * TICKS_PER_DAY;
    public static final double INTEREST_RATE = 0.02;  // 2%

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final File              dataFile;
    private       FileConfiguration cfg;

    /** balance, last payout full-time, last interest amount */
    public record BankAccount(double balance, long lastPayoutTime, double lastInterest) {}

    private final Map<UUID, Double> balances       = new HashMap<>();
    private final Map<UUID, Long>   lastPayoutTime = new HashMap<>();
    private final Map<UUID, Double> lastInterest   = new HashMap<>();

    private final File logFile;

    public BankModel(ChloeManagerPlugin plugin) {
        dataFile = new File(plugin.getDataFolder(), DATA_FILE);
        logFile  = new File(plugin.getDataFolder(), LOG_FILE);
        load();
    }

    /** Returns all UUIDs that have a bank account (for the interest scheduler). */
    public Set<UUID> getAllAccountUuids() {
        return new HashSet<>(balances.keySet());
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public double getBalance(UUID uuid) {
        return balances.getOrDefault(uuid, 0.0);
    }

    public long getLastPayoutTime(UUID uuid) {
        return lastPayoutTime.getOrDefault(uuid, -1L);
    }

    public double getLastInterest(UUID uuid) {
        return lastInterest.getOrDefault(uuid, 0.0);
    }

    public BankAccount getAccount(UUID uuid) {
        return new BankAccount(
                getBalance(uuid),
                getLastPayoutTime(uuid),
                getLastInterest(uuid)
        );
    }

    /** Deposits money into the bank.  Returns new balance. */
    public double deposit(UUID uuid, double amount) {
        double newBal = balances.merge(uuid, amount, Double::sum);
        save();
        return newBal;
    }

    /**
     * Withdraws money from the bank.
     * Returns the new balance, or -1 if insufficient funds.
     */
    public double withdraw(UUID uuid, double amount) {
        double current = getBalance(uuid);
        if (current < amount) return -1;
        balances.put(uuid, current - amount);
        save();
        return current - amount;
    }

    /**
     * Returns the world full-time used for interest tracking.
     * Uses the first world (overworld) — standard for single-world setups.
     */
    public long getWorldFullTime() {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return world != null ? world.getFullTime() : 0;
    }

    /**
     * Returns the number of full Minecraft days elapsed since the player's
     * last interest payout.  Returns 0 if they have never received interest.
     */
    public long getDaysSincePayout(UUID uuid) {
        long last = getLastPayoutTime(uuid);
        if (last < 0) return 0;
        long now = getWorldFullTime();
        return (now - last) / TICKS_PER_DAY;
    }

    /** Days remaining until the next interest payout. */
    public long getDaysUntilPayout(UUID uuid) {
        long last = getLastPayoutTime(uuid);
        if (last < 0) {
            // New account — interest cycle starts from now
            return INTEREST_CYCLE_DAYS;
        }
        long now  = getWorldFullTime();
        long elapsedTicks = now - last;
        long remainingTicks = INTEREST_CYCLE_TICKS - (elapsedTicks % INTEREST_CYCLE_TICKS);
        if (elapsedTicks >= INTEREST_CYCLE_TICKS) remainingTicks = 0;
        return remainingTicks / TICKS_PER_DAY;
    }

    /**
     * If eligible (≥ 30 days since last payout), pays interest and returns the
     * interest amount.  Returns 0 if not eligible or balance is zero.
     *
     * Also sets the last payout time to the current world full time.
     */
    public double payInterestIfEligible(UUID uuid) {
        double bal = getBalance(uuid);
        if (bal <= 0) return 0;

        long last = getLastPayoutTime(uuid);
        long now  = getWorldFullTime();

        if (last < 0) {
            // First-ever check — just record the starting time, no payout yet
            lastPayoutTime.put(uuid, now);
            save();
            return 0;
        }

        if (now - last < INTEREST_CYCLE_TICKS) return 0;

        // Eligible!  Pay 2% interest.
        double interest = Math.round(bal * INTEREST_RATE * 100.0) / 100.0;
        if (interest <= 0) return 0;

        balances.put(uuid, bal + interest);
        lastPayoutTime.put(uuid, now);
        lastInterest.put(uuid, interest);
        save();
        return interest;
    }

    // =========================================================================
    // Logging
    // =========================================================================

    public void logTransaction(UUID uuid, String playerName, String type, double amount, double newBalance) {
        String timestamp = SDF.format(new Date());
        String line = "[" + timestamp + "] " + playerName + " (" + uuid + ") "
                + type + " $" + String.format("%,.2f", amount)
                + " | Bank balance: $" + String.format("%,.2f", newBalance);
        appendLog(line);
    }

    public void logInterest(UUID uuid, String playerName, double interest, double newBalance) {
        String timestamp = SDF.format(new Date());
        long day = getWorldFullTime() / TICKS_PER_DAY;
        String line = "[" + timestamp + "] " + playerName + " (" + uuid + ") "
                + "INTEREST +$" + String.format("%,.2f", interest)
                + " (2% after 30 days, day " + day + ")"
                + " | Bank balance: $" + String.format("%,.2f", newBalance);
        appendLog(line);
    }

    private void appendLog(String line) {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(logFile, true), StandardCharsets.UTF_8))) {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            ChloeManagerPlugin.getInstance().getLogger()
                    .warning("Failed to write bank_log.txt: " + e.getMessage());
        }
    }

    // =========================================================================
    // Persistence
    // =========================================================================

    private void save() {
        cfg = new YamlConfiguration();
        for (UUID uuid : balances.keySet()) {
            String path = PLAYERS_KEY + "." + uuid;
            cfg.set(path + ".balance",         balances.get(uuid));
            cfg.set(path + ".last-payout",      lastPayoutTime.getOrDefault(uuid, -1L));
            cfg.set(path + ".last-interest",    lastInterest.getOrDefault(uuid, 0.0));
        }
        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            ChloeManagerPlugin.getInstance().getLogger()
                    .warning("Failed to save bank_data.yml: " + e.getMessage());
        }
    }

    private void load() {
        if (!dataFile.exists()) {
            cfg = new YamlConfiguration();
            return;
        }
        cfg = YamlConfiguration.loadConfiguration(dataFile);
        if (cfg.isConfigurationSection(PLAYERS_KEY)) {
            for (String key : cfg.getConfigurationSection(PLAYERS_KEY).getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String path = PLAYERS_KEY + "." + key;
                    balances.put(uuid,       cfg.getDouble(path + ".balance", 0.0));
                    lastPayoutTime.put(uuid, cfg.getLong(path + ".last-payout", -1L));
                    lastInterest.put(uuid,   cfg.getDouble(path + ".last-interest", 0.0));
                } catch (IllegalArgumentException e) {
                    ChloeManagerPlugin.getInstance().getLogger()
                            .warning("Skipping malformed UUID in bank_data.yml: " + key);
                }
            }
        }
    }
}
