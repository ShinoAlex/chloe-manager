package com.chloe.chloemanager.model;

import com.chloe.chloemanager.ChloeManagerPlugin;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MODEL — BossModel
 *
 * Holds the PersistentDataContainer keys used to tag boss entities and the
 * custom items (Essence of Life, Boss Slayer Pickaxe).  Also tracks the
 * currently-active bosses in memory so the skill loop can iterate them.
 *
 * Nothing here is persisted to disk — bosses are transient.  If the server
 * restarts while a boss is alive, the entity keeps its PDC tags (so it still
 * grants rewards if killed) but is no longer tracked for skills.
 */
public class BossModel {

    // ── PDC keys ────────────────────────────────────────────────────────────
    private final NamespacedKey bossKey;       // BYTE  1 = "this entity is a boss"
    private final NamespacedKey diffKey;       // STRING difficulty name
    private final NamespacedKey essenceKey;   // BYTE  1 = "this is Essence of Life"
    private final NamespacedKey pickaxeKey;    // BYTE  1 = "this is the Boss Slayer Pickaxe"

    // ── Active bosses ───────────────────────────────────────────────────────
    private final Map<UUID, BossInstance> activeBosses = new HashMap<>();

    public BossModel(ChloeManagerPlugin plugin) {
        this.bossKey    = new NamespacedKey(plugin, "is_boss");
        this.diffKey    = new NamespacedKey(plugin, "boss_difficulty");
        this.essenceKey = new NamespacedKey(plugin, "essence_of_life");
        this.pickaxeKey = new NamespacedKey(plugin, "boss_pickaxe");
    }

    // ── Key accessors ────────────────────────────────────────────────────────

    public NamespacedKey getBossKey()     { return bossKey; }
    public NamespacedKey getDiffKey()     { return diffKey; }
    public NamespacedKey getEssenceKey()  { return essenceKey; }
    public NamespacedKey getPickaxeKey()  { return pickaxeKey; }

    // ── Active-boss tracking ────────────────────────────────────────────────

    public void addBoss(UUID entityId, BossInstance instance)  { activeBosses.put(entityId, instance); }
    public BossInstance getBoss(UUID entityId)                 { return activeBosses.get(entityId); }
    public BossInstance removeBoss(UUID entityId)              { return activeBosses.remove(entityId); }
    public boolean hasActiveBoss()                             { return !activeBosses.isEmpty(); }
    public Map<UUID, BossInstance> getActiveBosses()           { return activeBosses; }

    // ── Boss tagging & detection ─────────────────────────────────────────────

    public void tagBoss(LivingEntity entity, BossDifficulty difficulty) {
        entity.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(diffKey, PersistentDataType.STRING, difficulty.name());
    }

    public boolean isBoss(Entity entity) {
        if (entity == null) return false;
        return entity.getPersistentDataContainer().has(bossKey, PersistentDataType.BYTE);
    }

    public BossDifficulty getBossDifficulty(Entity entity) {
        if (entity == null) return null;
        String name = entity.getPersistentDataContainer().get(diffKey, PersistentDataType.STRING);
        if (name == null) return null;
        try {
            return BossDifficulty.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── Essence of Life item ──────────────────────────────────────────────────

    public ItemStack createEssence(int amount) {
        ItemStack item = new ItemStack(Material.RED_DYE, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD
                + "Essence of Life");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "A rare essence dropped by bosses.",
                ChatColor.GRAY + "Collect " + ChatColor.GOLD + "64 "
                        + ChatColor.GRAY + "to trade at " + ChatColor.AQUA + "/boss",
                "",
                ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC
                        + "Pulses with otherworldly energy..."
        ));
        // Glow effect — dummy enchantment hidden from the tooltip
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(essenceKey, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    public boolean isEssence(ItemStack item) {
        if (item == null || item.getType() != Material.RED_DYE) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(essenceKey, PersistentDataType.BYTE);
    }

    // ── Boss Slayer Pickaxe item ──────────────────────────────────────────────

    public ItemStack createBossPickaxe() {
        ItemStack pick = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = pick.getItemMeta();
        if (meta == null) return pick;

        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD
                + "\u2727 Pickaxe of the Boss Slayer \u2727");
        meta.setLore(Arrays.asList(
                ChatColor.LIGHT_PURPLE + "Efficiency X",
                ChatColor.AQUA + "Fortune V",
                ChatColor.GREEN + "Unbreakable",
                ChatColor.YELLOW + "Auto-Smelt",
                "",
                ChatColor.GRAY + "Forged from 64 Essence of Life.",
                ChatColor.DARK_GRAY + "Automatically smelts mined ores."
        ));
        meta.addEnchant(Enchantment.DIG_SPEED, 10, true);            // Efficiency X
        meta.addEnchant(Enchantment.LOOT_BONUS_BLOCKS, 5, true);      // Fortune V
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(pickaxeKey, PersistentDataType.BYTE, (byte) 1);

        pick.setItemMeta(meta);
        return pick;
    }

    public boolean isBossPickaxe(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(pickaxeKey, PersistentDataType.BYTE);
    }

    // ── BossInstance ──────────────────────────────────────────────────────────

    /** Runtime data for a single active boss. */
    public static class BossInstance {
        private final UUID entityId;
        private final BossDifficulty difficulty;
        private final long spawnTime;
        private volatile long lastSkillTime;
        private volatile long lastSkillTime2;
        private volatile long lastSkillTime3;
        private volatile long lastSonicBoomTime;
        private volatile long lastPlayerNearbyTime;  // ms — resets whenever a player is in range
        private volatile double lastX;
        private volatile double lastY;
        private volatile double lastZ;
        private volatile long lastMovedTime;

        public BossInstance(UUID entityId, BossDifficulty difficulty) {
            this.entityId   = entityId;
            this.difficulty  = difficulty;
            this.spawnTime   = System.currentTimeMillis();
            this.lastSkillTime = 0;
            this.lastSkillTime2 = 0;
            this.lastSkillTime3 = 0;
            this.lastSonicBoomTime = 0;
            this.lastPlayerNearbyTime = System.currentTimeMillis(); // grace period from spawn
            this.lastMovedTime = System.currentTimeMillis();
        }

        public UUID getEntityId()            { return entityId; }
        public BossDifficulty getDifficulty() { return difficulty; }
        public long getSpawnTime()           { return spawnTime; }
        public long getLastSkillTime()       { return lastSkillTime; }
        public void setLastSkillTime(long t) { lastSkillTime = t; }
        public long getLastSkillTime2()       { return lastSkillTime2; }
        public void setLastSkillTime2(long t) { lastSkillTime2 = t; }
        public long getLastSkillTime3()       { return lastSkillTime3; }
        public void setLastSkillTime3(long t) { lastSkillTime3 = t; }
        public long getLastSonicBoomTime()       { return lastSonicBoomTime; }
        public void setLastSonicBoomTime(long t)  { lastSonicBoomTime = t; }
        public long getLastPlayerNearbyTime()          { return lastPlayerNearbyTime; }
        public void setLastPlayerNearbyTime(long t)    { lastPlayerNearbyTime = t; }
        public double getLastX()                       { return lastX; }
        public double getLastY()                       { return lastY; }
        public double getLastZ()                       { return lastZ; }
        public void setLastPos(double x, double y, double z) {
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
        }
        public long getLastMovedTime()                 { return lastMovedTime; }
        public void setLastMovedTime(long t)           { this.lastMovedTime = t; }
    }
}
