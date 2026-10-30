package com.chloe.chloemanager.model;

import org.bukkit.entity.EntityType;

/**
 * MODEL — BossMobProfile
 *
 * Per-mob stat multipliers and signature-skill cooldown.
 * All multipliers are applied on top of BossDifficulty base values.
 *
 *   finalHp     = diff.baseHealth  * profile.baseHp
 *   finalDamage = mobNaturalDamage * diff.dmgMult * profile.baseDamage
 *   finalSpeed  = mobNaturalSpeed  * diff.spdMult * profile.baseSpeed
 *
 * Special flags:
 *   vindicatorExtraDamage — Vindicator deals x4 on top of everything else.
 *   rangedBowPower        — Skeleton/Pillager spawn with Power (min) to (max) bow.
 */
public enum BossMobProfile {

    // ── Overworld ────────────────────────────────────────────────────────────
    //              EntityType                  baseHp baseDmg baseSp skillCdMs  skillName            extraDmg bowMin bowMax
    ZOMBIE      (EntityType.ZOMBIE,             1.00,  1.00,  0.90,  8_000, "Hunger Bite",           false, 0, 0),
    HUSK        (EntityType.HUSK,               1.10,  1.10,  0.90,  9_000, "Desert Exhaustion",     false, 0, 0),
    SKELETON    (EntityType.SKELETON,           0.80,  1.20,  1.10,  7_000, "Rain of Arrows",        false, 7,15),
    STRAY       (EntityType.STRAY,              0.90,  1.20,  1.00,  8_000, "Freezing Shot",         false, 7,12),
    SPIDER      (EntityType.SPIDER,             1.00,  1.00,  1.30,  6_000, "Web Snare",             false, 0, 0),
    CAVE_SPIDER (EntityType.CAVE_SPIDER,        0.70,  1.10,  1.40,  6_000, "Venom Fang",            false, 0, 0),
    ENDERMAN    (EntityType.ENDERMAN,           1.30,  1.40,  1.20, 12_000, "Shadow Teleport",       false, 0, 0),
    PILLAGER    (EntityType.PILLAGER,           0.90,  1.30,  1.00,  8_000, "Suppressing Fire",      false, 7,15),
    VINDICATOR  (EntityType.VINDICATOR,         1.20,  4.00,  1.10, 10_000, "Axe Cleave",            true,  0, 0),
    EVOKER      (EntityType.EVOKER,             1.10,  1.10,  0.90, 14_000, "Fang Barrage",          false, 0, 0),
    RAVAGER     (EntityType.RAVAGER,            2.00,  2.00,  0.80, 15_000, "Devastating Charge",    false, 0, 0),
    ZOGLIN      (EntityType.ZOGLIN,             1.40,  1.50,  1.10, 11_000, "Feral Tusk",            false, 0, 0),

    // ── Nether ──────────────────────────────────────────────────────────────
    BLAZE           (EntityType.BLAZE,          1.00,  1.30,  0.90,  8_000, "Blaze Barrage",         false, 0, 0),
    WITHER_SKELETON (EntityType.WITHER_SKELETON,1.30,  1.50,  1.00, 10_000, "Wither Strike",         false, 0, 0),
    PIGLIN_BRUTE    (EntityType.PIGLIN_BRUTE,   1.40,  1.80,  1.10,  9_000, "Brute Smash",           false, 0, 0);

    private final EntityType entityType;
    private final double     baseHp;
    private final double     baseDamage;
    private final double     baseSpeed;
    private final long       skillCooldownMs;
    private final String     skillName;
    /** Vindicator: multiplies final attack damage by 4 extra on top of everything. */
    private final boolean    vindicatorExtraDamage;
    /** Bow/crossbow enchant range (Power level). 0 means no ranged weapon. */
    private final int        bowPowerMin;
    private final int        bowPowerMax;

    BossMobProfile(EntityType entityType,
                   double baseHp, double baseDamage, double baseSpeed,
                   long skillCooldownMs, String skillName,
                   boolean vindicatorExtraDamage, int bowPowerMin, int bowPowerMax) {
        this.entityType            = entityType;
        this.baseHp                = baseHp;
        this.baseDamage            = baseDamage;
        this.baseSpeed             = baseSpeed;
        this.skillCooldownMs       = skillCooldownMs;
        this.skillName             = skillName;
        this.vindicatorExtraDamage = vindicatorExtraDamage;
        this.bowPowerMin           = bowPowerMin;
        this.bowPowerMax           = bowPowerMax;
    }

    public EntityType getEntityType()          { return entityType; }
    public double     getBaseHp()              { return baseHp; }
    public double     getBaseDamage()          { return baseDamage; }
    public double     getBaseSpeed()           { return baseSpeed; }
    public long       getSkillCooldownMs()     { return skillCooldownMs; }
    public String     getSkillName()           { return skillName; }
    public boolean    hasVindicatorBonus()     { return vindicatorExtraDamage; }
    public boolean    hasEnchantedBow()        { return bowPowerMin > 0; }
    public int        getBowPowerMin()         { return bowPowerMin; }
    public int        getBowPowerMax()         { return bowPowerMax; }

    public static BossMobProfile of(EntityType type) {
        for (BossMobProfile p : values())
            if (p.entityType == type) return p;
        return null;
    }
}
