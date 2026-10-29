package com.chloe.chloemanager.model;

import net.md_5.bungee.api.ChatColor;

import java.util.Random;

/**
 * MODEL — BossDifficulty
 *
 * The five difficulty tiers a boss can spawn with. Each tier defines the
 * money reward, stat multipliers (health / damage / speed), the relative
 * spawn weight, and how many Essence of Life items the boss can drop.
 *
 * Spawn weights sum to 100, so NIGHTMARE (weight 5) ≈ 5% spawn chance.
 */
public enum BossDifficulty {

    //              displayName,  reward,     baseHealth, damageMult, speedMult, armor, maxHitCapPct, weight, minEss, maxEss, dropChance, color
    EASY     ("Easy",       10_000.0,    400.0,      1.8,       1.05,      10.0,  0.15,          30,     1,      2,      0.30,       ChatColor.GREEN),
    NORMAL   ("Normal",     20_000.0,    800.0,      2.5,       1.10,      14.0,  0.10,          25,     1,      3,      0.50,       ChatColor.YELLOW),
    MEDIUM   ("Medium",     50_000.0,  1_500.0,      3.5,       1.15,      18.0,  0.08,          20,     2,      4,      0.50,       ChatColor.GOLD),
    HARD     ("Hard",       70_000.0,  3_000.0,      4.5,       1.20,      22.0,  0.05,          20,     3,      5,      0.65,       ChatColor.RED),
    NIGHTMARE("Nightmare", 150_000.0,  6_000.0,      6.0,       1.25,      25.0,  0.03,           5,     4,      5,      0.95,       ChatColor.DARK_RED);

    private final String    displayName;
    private final double    reward;
    private final double    baseHealth;
    private final double    damageMultiplier;
    private final double    speedMultiplier;
    private final double    armor;
    private final double    maxDamageCapPercent; // Max % of max HP boss can take in a single hit
    private final int       spawnWeight;
    private final int       minEssence;
    private final int       maxEssence;
    private final double    essenceDropChance;   // 0.0 – 1.0
    private final ChatColor color;

    BossDifficulty(String displayName, double reward,
                   double baseHealth, double damageMultiplier, double speedMultiplier,
                   double armor, double maxDamageCapPercent,
                   int spawnWeight, int minEssence, int maxEssence,
                   double essenceDropChance, ChatColor color) {
        this.displayName         = displayName;
        this.reward              = reward;
        this.baseHealth          = baseHealth;
        this.damageMultiplier    = damageMultiplier;
        this.speedMultiplier     = speedMultiplier;
        this.armor               = armor;
        this.maxDamageCapPercent = maxDamageCapPercent;
        this.spawnWeight         = spawnWeight;
        this.minEssence          = minEssence;
        this.maxEssence          = maxEssence;
        this.essenceDropChance   = essenceDropChance;
        this.color               = color;
    }

    public String    getDisplayName()         { return displayName; }
    public double    getReward()              { return reward; }
    public double    getBaseHealth()          { return baseHealth; }
    public double    getDamageMultiplier()    { return damageMultiplier; }
    public double    getSpeedMultiplier()     { return speedMultiplier; }
    public double    getArmor()               { return armor; }
    public double    getMaxDamageCapPercent() { return maxDamageCapPercent; }
    public int       getMinEssence()          { return minEssence; }
    public int       getMaxEssence()          { return maxEssence; }
    public double    getEssenceDropChance()   { return essenceDropChance; }
    public ChatColor getColor()               { return color; }

    /**
     * Weighted random selection. NIGHTMARE ≈ 5% when weights sum to 100.
     */
    public static BossDifficulty roll(Random random) {
        int total = 0;
        for (BossDifficulty d : values()) total += d.spawnWeight;
        int roll = random.nextInt(total);
        int cumulative = 0;
        for (BossDifficulty d : values()) {
            cumulative += d.spawnWeight;
            if (roll < cumulative) return d;
        }
        return EASY;
    }
}
