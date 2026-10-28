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

    EASY     ("Easy",       10_000.0,  50.0, 10.25, 2.95, 30, 1, 2, 0.30, ChatColor.GREEN),
    NORMAL   ("Normal",     20_000.0,  65.0, 15.0,  3.25, 25, 1, 3, 0.50, ChatColor.YELLOW),
    MEDIUM   ("Medium",     50_000.0,  85.0, 25.75, 4.55, 20, 2, 4, 0.50, ChatColor.GOLD),
    HARD     ("Hard",       70_000.0, 140.0, 35.5,  5.85, 20, 3, 5, 0.65, ChatColor.RED),
    NIGHTMARE("Nightmare", 150_000.0, 255.0, 60.0,  5.3,   5, 4, 5, 0.95, ChatColor.DARK_RED);

    private final String    displayName;
    private final double    reward;
    private final double    healthMultiplier;
    private final double    damageMultiplier;
    private final double    speedMultiplier;
    private final int       spawnWeight;
    private final int       minEssence;
    private final int       maxEssence;
    private final double    essenceDropChance;   // 0.0 – 1.0
    private final ChatColor color;

    BossDifficulty(String displayName, double reward,
                   double healthMultiplier, double damageMultiplier, double speedMultiplier,
                   int spawnWeight, int minEssence, int maxEssence,
                   double essenceDropChance, ChatColor color) {
        this.displayName       = displayName;
        this.reward            = reward;
        this.healthMultiplier  = healthMultiplier;
        this.damageMultiplier  = damageMultiplier;
        this.speedMultiplier   = speedMultiplier;
        this.spawnWeight       = spawnWeight;
        this.minEssence        = minEssence;
        this.maxEssence        = maxEssence;
        this.essenceDropChance = essenceDropChance;
        this.color             = color;
    }

    public String    getDisplayName()       { return displayName; }
    public double    getReward()            { return reward; }
    public double    getHealthMultiplier()  { return healthMultiplier; }
    public double    getDamageMultiplier()  { return damageMultiplier; }
    public double    getSpeedMultiplier()   { return speedMultiplier; }
    public int       getMinEssence()        { return minEssence; }
    public int       getMaxEssence()        { return maxEssence; }
    public double    getEssenceDropChance() { return essenceDropChance; }
    public ChatColor getColor()             { return color; }

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
