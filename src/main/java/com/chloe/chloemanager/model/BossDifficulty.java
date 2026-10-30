package com.chloe.chloemanager.model;

import net.md_5.bungee.api.ChatColor;

import java.util.Random;

/**
 * MODEL — BossDifficulty
 *
 * Stats per difficulty tier. Mob-profile multipliers (BossMobProfile) stack
 * on top:   finalHp = baseHealth * profile.baseHp
 *
 * Health is a virtual pool (Minecraft caps vanilla max health ~1024, so huge
 * setBaseValue calls were ignored and bosses stayed at ~20 HP).
 *   EASY 4k · NORMAL 7.5k · MEDIUM 12.5k · HARD 20k · NIGHTMARE 35k
 *
 * Damage multipliers apply on top of the mob's natural base attack:
 *   EASY x100, NORMAL x150, MEDIUM x150, HARD x200, NIGHTMARE x300
 *
 * Speed is at least 2.5× the mob's vanilla walk speed.
 */
public enum BossDifficulty {

    //             name,       reward,     baseHp,     dmgMult, spdMult, armor, maxDmgCapPct, weight, minEss, maxEss, dropChance, color
    EASY     ("Easy",      10_000.0,   4_000.0,  100.0,   2.5,    12.0,  0.02,          30,     1,      2,      0.30, ChatColor.GREEN),
    NORMAL   ("Normal",    20_000.0,   7_500.0,  150.0,   2.5,    16.0,  0.02,          25,     1,      3,      0.50, ChatColor.YELLOW),
    MEDIUM   ("Medium",    50_000.0,  12_500.0,  150.0,   2.5,    20.0,  0.02,          20,     2,      4,      0.50, ChatColor.GOLD),
    HARD     ("Hard",      70_000.0,  20_000.0,  200.0,   2.5,    24.0,  0.015,         20,     3,      5,      0.65, ChatColor.RED),
    NIGHTMARE("Nightmare",150_000.0,  35_000.0,  300.0,   2.5,    28.0,  0.01,           5,     4,      5,      0.95, ChatColor.DARK_RED);

    private final String    displayName;
    private final double    reward;
    private final double    baseHealth;
    private final double    damageMultiplier;
    private final double    speedMultiplier;
    private final double    armor;
    /** Max fraction of boss's max HP a single hit can deal (prevents 1-shot). */
    private final double    maxDamageCapPercent;
    private final int       spawnWeight;
    private final int       minEssence;
    private final int       maxEssence;
    private final double    essenceDropChance;
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

    /** Minions only spawn for HARD and NIGHTMARE. */
    public boolean spawnMinions() { return this == HARD || this == NIGHTMARE; }

    /** Weighted random selection. NIGHTMARE ≈ 5% when weights sum to 100. */
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
