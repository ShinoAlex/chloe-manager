package com.chloe.chloemanager.controller;

import com.chloe.chloemanager.ChloeManagerPlugin;
import com.chloe.chloemanager.model.BossDifficulty;
import com.chloe.chloemanager.model.BossModel;
import com.chloe.chloemanager.model.BossModel.BossInstance;
import com.chloe.chloemanager.model.BossMobProfile;
import com.chloe.chloemanager.model.EconomyModel;
import com.chloe.chloemanager.view.BossGUI;
import com.chloe.chloemanager.view.BossView;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CONTROLLER — BossController
 *
 * A complete boss-fight system:
 *
 *  Spawning  — every few minutes (if no boss is alive) a random hostile mob
 *              (never a Creeper) spawns within 10 000 blocks of world spawn,
 *              with a random difficulty tier.  All players are notified.
 *
 *  Stats     — health, damage, and speed are scaled per difficulty.
 *
 *  Skills    — each difficulty has a periodic skill used against the nearest
 *              player.  NIGHTMARE additionally unleashes a Warden-style sonic
 *              boom every 3 minutes.
 *
 *  Rewards   — the player who kills a boss receives money (×1.5 in the Nether
 *              for Nether mobs) and a chance to drop Essence of Life (max 5).
 *
 *  Death     — players killed by a boss keep their inventory & lose no money.
 *
 *  Trade     — /boss opens a GUI to trade 128 Essence of Life (2 stacks) for a
 *              legendary pickaxe (Efficiency X, Fortune V, Unbreakable, Auto-Smelt).
 *
 *  Admin     — /boss spawn [difficulty] spawns a test boss at the player.
 */
public class BossController implements CommandExecutor, TabCompleter, Listener {

    private static final DecimalFormat FMT = new DecimalFormat("#,##0.00");

    // ── Tunable constants ────────────────────────────────────────────────────
    private static final long   SPAWN_INITIAL_DELAY  = 2400L;   // 2 min  (first boss)
    private static final long   SPAWN_INTERVAL_TICKS = 18000L;  // 15 min (after that)
    private static final long   SKILL_INTERVAL_TICKS = 100L;    // 5 sec
    private static final int    SPAWN_RADIUS         = 5_000;
    private static final double  SKILL_RANGE          = 25.0;
    private static final double  MAX_HEALTH_CAP       = 200_000_000.0;
    private static final double  MIN_SPEED_MULT       = 2.5;
    private static final double  SPEED_HARD_CAP       = 0.85;
    private static final long   SONIC_BOOM_COOLDOWN_MS = 180_000L; // 3 min
    private static final long   DESPAWN_NO_PLAYER_MS   = 600_000L; // 10 min
    private static final double DESPAWN_CHECK_RANGE    = 80.0;     // must be within 80 blocks
    private static final double NETHER_REWARD_MULTIPLIER = 1.5;
    private static final int    TRADE_COST           = 128;     // 2 stacks of Essence of Life

    // ── Mob pools ─────────────────────────────────────────────────────────────
    // Overworld: hostile mobs that make good bosses
    // Excluded: Creeper, Witch, Endermite, Silverfish, Vex, Guardian,
    //           Elder Guardian, Phantom, Slime, Creaking
    private static final EntityType[] OVERWORLD_MOBS = {
        EntityType.ZOMBIE,
        EntityType.SKELETON,
        EntityType.SPIDER,
        EntityType.CAVE_SPIDER,
        EntityType.ENDERMAN,
        EntityType.HUSK,
        EntityType.STRAY,
        EntityType.PILLAGER,
        EntityType.VINDICATOR,
        EntityType.EVOKER,
        EntityType.RAVAGER,
        EntityType.ZOGLIN
    };
    // Nether: hostile mobs that make good bosses
    // Excluded: Magma Cube, Hoglin, Zombified Piglin, Ghast
    private static final EntityType[] NETHER_MOBS = {
        EntityType.BLAZE,
        EntityType.WITHER_SKELETON,
        EntityType.PIGLIN_BRUTE
    };

    // ── Auto-smelt ore → product map ─────────────────────────────────────────
    private static final Map<Material, Material> AUTO_SMELT_MAP = new HashMap<>();
    static {
        AUTO_SMELT_MAP.put(Material.IRON_ORE,           Material.IRON_INGOT);
        AUTO_SMELT_MAP.put(Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT);
        AUTO_SMELT_MAP.put(Material.GOLD_ORE,           Material.GOLD_INGOT);
        AUTO_SMELT_MAP.put(Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT);
        AUTO_SMELT_MAP.put(Material.COPPER_ORE,         Material.COPPER_INGOT);
        AUTO_SMELT_MAP.put(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT);
        AUTO_SMELT_MAP.put(Material.ANCIENT_DEBRIS,     Material.NETHERITE_SCRAP);
    }

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final ChloeManagerPlugin plugin;
    private final BossModel    bossModel;
    private final EconomyModel  economy;
    private final BossView      view;
    private final BossGUI       gui;
    private final Random       random = new Random();

    // ── Boss Combat State Tracking ───────────────────────────────────────────
    // Short i-frames after a Totem pops so the next melee tick does not instantly re-kill
    private final Map<UUID, Long> totemGraceMap = new HashMap<>();
    // Boss UUID -> Timestamp until which boss cannot melee attack any player (attack cooldown)
    private final Map<UUID, Long> bossAttackCooldownMap = new HashMap<>();

    private BukkitTask spawnTask;
    private BukkitTask skillTask;

    public BossController(ChloeManagerPlugin plugin, BossModel bossModel,
                          EconomyModel economy, BossView view, BossGUI gui) {
        this.plugin   = plugin;
        this.bossModel = bossModel;
        this.economy  = economy;
        this.view     = view;
        this.gui      = gui;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void startTasks() {
        spawnTask = new BukkitRunnable() {
            @Override public void run() {
                if (!bossModel.hasActiveBoss()) spawnBoss();
            }
        }.runTaskTimer(plugin, SPAWN_INITIAL_DELAY, SPAWN_INTERVAL_TICKS);

        skillTask = new BukkitRunnable() {
            @Override public void run() { runSkills(); }
        }.runTaskTimer(plugin, SKILL_INTERVAL_TICKS, SKILL_INTERVAL_TICKS);
    }

    public void stopTasks() {
        if (spawnTask != null) spawnTask.cancel();
        if (skillTask != null) skillTask.cancel();
    }

    // =========================================================================
    // /boss command — opens the trade GUI
    // =========================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            view.sendMustBePlayer(sender);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("spawn")) {
            handleAdminSpawn(player, args);
            return true;
        }
        openTradeGUI(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!sender.hasPermission("chloemanager.boss.spawn")) return List.of();
        if (args.length == 1) {
            return prefixMatch(args[0], List.of("spawn"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            List<String> names = new ArrayList<>();
            for (BossDifficulty d : BossDifficulty.values()) names.add(d.name().toLowerCase());
            return prefixMatch(args[1], names);
        }
        return List.of();
    }

    private List<String> prefixMatch(String prefix, List<String> options) {
        String p = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase().startsWith(p)) out.add(o);
        return out;
    }

    private void handleAdminSpawn(Player player, String[] args) {
        if (!player.hasPermission("chloemanager.boss.spawn")) {
            view.sendNoPermission(player);
            return;
        }

        BossDifficulty difficulty;
        if (args.length >= 2) {
            try {
                difficulty = BossDifficulty.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                view.sendInvalidDifficulty(player);
                return;
            }
        } else {
            difficulty = BossDifficulty.roll(random);
        }

        World world = player.getWorld();
        boolean inNether = world.getEnvironment() == World.Environment.NETHER;
        EntityType[] pool = inNether ? NETHER_MOBS : OVERWORLD_MOBS;
        EntityType mobType = pool[random.nextInt(pool.length)];

        Vector dir = player.getLocation().getDirection();
        dir.setY(0);
        if (dir.lengthSquared() < 0.01) {
            dir = player.getLocation().getDirection();
        } else {
            dir.normalize();
        }
        Location loc = player.getLocation().add(dir.multiply(5));
        loc.setY(player.getLocation().getY());

        if (!spawnBossAt(loc, difficulty, mobType, inNether)) {
            view.sendAdminSpawnFailed(player);
            return;
        }
        view.sendAdminSpawned(player, difficulty, formatMobName(mobType));
    }

    private void openTradeGUI(Player player) {
        int count = countEssence(player);
        player.openInventory(gui.build(count));
    }

    // =========================================================================
    // GUI click handler
    // =========================================================================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (title == null || !title.equals(BossGUI.TITLE)) return;

        event.setCancelled(true);  // read-only GUI

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        if (slot == BossGUI.SLOT_TRADE) {
            handleTrade(player);
        }
    }

    // =========================================================================
    // Boss spawning
    // =========================================================================

    private void spawnBoss() {
        // Choose world: 30 % chance Nether (if one exists), else Overworld
        World nether = findWorld(World.Environment.NETHER);
        boolean inNether = nether != null && random.nextDouble() < 0.30;
        World world = inNether
                ? nether
                : findWorld(World.Environment.NORMAL);
        if (world == null) return;

        // Pick a mob type appropriate to the world
        EntityType mobType = inNether
                ? NETHER_MOBS[random.nextInt(NETHER_MOBS.length)]
                : OVERWORLD_MOBS[random.nextInt(OVERWORLD_MOBS.length)];

        // Pick difficulty
        BossDifficulty difficulty = BossDifficulty.roll(random);

        // ── Spawn near a random online player in the chosen world ─────────────
        // Fallback to world spawn if no players are in that world.
        List<Player> worldPlayers = world.getPlayers().stream()
                .filter(p -> p.getGameMode() != org.bukkit.GameMode.SPECTATOR
                          && p.getGameMode() != org.bukkit.GameMode.CREATIVE)
                .collect(Collectors.toList());

        Location anchor;
        if (!worldPlayers.isEmpty()) {
            anchor = worldPlayers.get(random.nextInt(worldPlayers.size())).getLocation();
        } else {
            // No players in the target world — use world spawn but keep within radius
            anchor = world.getSpawnLocation();
        }

        // Random offset 64–3000 blocks away from the anchor (not right on top of them)
        int minDist = 64;
        int range   = SPAWN_RADIUS - minDist;
        double angle = random.nextDouble() * 2 * Math.PI;
        double dist  = minDist + random.nextInt(range);
        int x = anchor.getBlockX() + (int)(Math.cos(angle) * dist);
        int z = anchor.getBlockZ() + (int)(Math.sin(angle) * dist);
        int y = findSafeY(world, x, z, inNether);

        Location loc = new Location(world, x + 0.5, y, z + 0.5);
        spawnBossAt(loc, difficulty, mobType, inNether);
    }

    /**
     * Spawns a tagged, scaled boss at {@code loc}. Returns false if the entity
     * could not be created.
     */
    private boolean spawnBossAt(Location loc, BossDifficulty difficulty,
                                EntityType mobType, boolean inNether) {
        World world = loc.getWorld();
        if (world == null) return false;

        LivingEntity boss;
        try {
            Entity entity = world.spawnEntity(loc, mobType);
            if (!(entity instanceof LivingEntity)) {
                entity.remove();
                return false;
            }
            boss = (LivingEntity) entity;
        } catch (Exception e) {
            return false;
        }

        BossMobProfile profile = BossMobProfile.of(mobType);
        bossModel.tagBoss(boss, difficulty);
        double intendedHp = scaleAttributes(boss, difficulty, profile);
        BossInstance instance = new BossInstance(boss.getUniqueId(), difficulty, profile);
        instance.setVirtualMaxHp(intendedHp);
        instance.setVirtualHp(intendedHp);
        setBossName(boss, instance);
        bossModel.addBoss(boss.getUniqueId(), instance);

        String worldName = inNether ? "Nether" : "Overworld";
        view.broadcastSpawn(difficulty, formatMobName(mobType), worldName,
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), inNether);
        return true;
    }

    /** @return intended virtual HP after profile multiplier */
    private double scaleAttributes(LivingEntity boss, BossDifficulty diff,
                                  BossMobProfile profile) {
        double profileHp  = profile != null ? profile.getBaseHp()    : 1.0;
        double profileDmg = profile != null ? profile.getBaseDamage() : 1.0;
        double profileSpd = profile != null ? profile.getBaseSpeed()  : 1.0;
        double intendedHp = Math.min(diff.getBaseHealth() * profileHp, MAX_HEALTH_CAP);

        // Vanilla max-health is ~1024; raise it on Paper, else keep a full bar as a display.
        AttributeInstance maxHealth = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            double vanillaMax = intendedHp;
            try {
                maxHealth.getClass().getMethod("setMaxValue", double.class)
                        .invoke(maxHealth, Math.max(intendedHp, 2048.0));
            } catch (ReflectiveOperationException ignored) {
                vanillaMax = Math.min(intendedHp, 1024.0);
            }
            maxHealth.setBaseValue(vanillaMax);
            try { boss.setHealth(vanillaMax); } catch (Exception ignored) { }
        }

        // Damage: mob base × diff multiplier × mob profile multiplier
        // Vindicator gets an extra x4 on top
        AttributeInstance attack = boss.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attack != null) {
            double baseAtk = attack.getBaseValue();
            if (baseAtk <= 0.0) baseAtk = 2.0;
            double extraMult = (profile != null && profile.hasVindicatorBonus()) ? 4.0 : 1.0;
            attack.setBaseValue(baseAtk * diff.getDamageMultiplier() * profileDmg * extraMult);
        }

        // At least 2.5× vanilla walk speed; faster mob profiles can go above that.
        AttributeInstance speed = boss.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            double spdMult = Math.max(MIN_SPEED_MULT, diff.getSpeedMultiplier())
                    * Math.max(1.0, profileSpd);
            double target = speed.getBaseValue() * spdMult;
            speed.setBaseValue(Math.min(target, SPEED_HARD_CAP));
        }

        AttributeInstance follow = boss.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(Math.max(follow.getBaseValue(), 40.0));

        // Armor
        AttributeInstance armor = boss.getAttribute(Attribute.GENERIC_ARMOR);
        if (armor != null) armor.setBaseValue(diff.getArmor());

        AttributeInstance toughness = boss.getAttribute(Attribute.GENERIC_ARMOR_TOUGHNESS);
        if (toughness != null) toughness.setBaseValue(Math.max(4.0, diff.getArmor() / 2.0));

        // Knockback resistance
        AttributeInstance kbRes = boss.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (kbRes != null) kbRes.setBaseValue(0.75);

        // Enchanted bow for Skeleton / Pillager
        if (profile != null && profile.hasEnchantedBow() && boss.getEquipment() != null) {
            int powerLevel = profile.getBowPowerMin()
                    + random.nextInt(profile.getBowPowerMax() - profile.getBowPowerMin() + 1);
            boolean isPillager = boss.getType() == EntityType.PILLAGER;
            ItemStack bow = new ItemStack(isPillager ? Material.CROSSBOW : Material.BOW);
            bow.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, powerLevel); // Power
            bow.addUnsafeEnchantment(Enchantment.ARROW_FIRE, 1);            // Flame
            boss.getEquipment().setItemInMainHand(bow);
            boss.getEquipment().setItemInMainHandDropChance(0.0f);          // no drop
        }
        return intendedHp;
    }

    private void setBossName(LivingEntity boss, BossInstance bi) {
        BossDifficulty diff = bi.getDifficulty();
        String mobName = formatMobName(boss.getType());
        double current = Math.max(0, boss.getHealth());
        AttributeInstance maxAttr = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double max = maxAttr != null ? maxAttr.getValue() : bi.getVirtualMaxHp();
        if (max <= 0) max = bi.getVirtualMaxHp();
        bi.setVirtualHp(current);
        if (bi.getVirtualMaxHp() <= 0) bi.setVirtualMaxHp(max);
        String hp = String.format("%,d", (long) Math.ceil(current))
                + "/" + String.format("%,d", (long) Math.ceil(max));
        boss.setCustomName(diff.getColor() + "" + ChatColor.BOLD
                + "\u2620 " + diff.getDisplayName() + " Boss \u2620 "
                + ChatColor.RESET + ChatColor.GRAY + mobName
                + ChatColor.RED + " \u2764 " + ChatColor.WHITE + hp);
        boss.setCustomNameVisible(true);
        boss.setGlowing(true);
        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(true);
    }

    private void refreshBossHpDisplay(LivingEntity boss) {
        BossInstance bi = bossModel.getBoss(boss.getUniqueId());
        if (bi == null || !boss.isValid() || boss.isDead()) return;
        setBossName(boss, bi);
    }

    // =========================================================================
    // Skill loop
    // =========================================================================

    private void runSkills() {
        Iterator<Map.Entry<UUID, BossInstance>> it =
                bossModel.getActiveBosses().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BossInstance> entry = it.next();
            BossInstance bi = entry.getValue();

            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity == null || !entity.isValid() || entity.isDead()
                    || !(entity instanceof LivingEntity boss)) {
                it.remove();
                continue;
            }

            // ── Despawn check ──────────────────────────────────────────────────
            boolean playerNearby = findNearestPlayer(boss, DESPAWN_CHECK_RANGE) != null;
            if (playerNearby) {
                bi.setLastPlayerNearbyTime(System.currentTimeMillis());
            } else {
                long idleMs = System.currentTimeMillis() - bi.getLastPlayerNearbyTime();
                if (idleMs >= DESPAWN_NO_PLAYER_MS) {
                    // Notify all players and remove the boss
                    Bukkit.broadcastMessage(
                            net.md_5.bungee.api.ChatColor.DARK_PURPLE + "\u2620 "
                            + bi.getDifficulty().getColor()
                            + bi.getDifficulty().getDisplayName()
                            + net.md_5.bungee.api.ChatColor.GRAY
                            + " Boss despawned — no one came to fight it.");
                    boss.remove();
                    it.remove();
                    continue;
                }
                // Warn players at 1-minute remaining
                long warnAt = DESPAWN_NO_PLAYER_MS - 60_000L;
                if (idleMs >= warnAt && idleMs < warnAt + (SKILL_INTERVAL_TICKS * 50L)) {
                    Bukkit.broadcastMessage(
                            net.md_5.bungee.api.ChatColor.RED + "\u26a0 The "
                            + bi.getDifficulty().getColor()
                            + bi.getDifficulty().getDisplayName()
                            + net.md_5.bungee.api.ChatColor.RED
                            + " Boss will despawn in 1 minute if nobody engages it!");
                }
            }

            // ── Water & Lava Resistance ─────────────────────────────────────────
            handleFluidResistance(boss);

            // ── Anti-Stuck & Wall Breach ─────────────────────────────────────────
            handleAntiStuck(boss, bi);

            useSkill(boss, bi);
        }
    }

    private void useSkill(LivingEntity boss, BossInstance bi) {
        long now = System.currentTimeMillis();
        BossDifficulty diff = bi.getDifficulty();

        Player target = findNearestPlayer(boss, SKILL_RANGE);
        if (target == null) return;

        // Ranged pressure: any boss shoots when the player is not in melee
        double distSq = target.getLocation().distanceSquared(boss.getLocation());
        if (distSq > 36.0 && now - bi.getLastRangedShotTime() > 2_400L) {
            bossRangedShot(boss, target, diff);
            bi.setLastRangedShotTime(now);
        }

        switch (diff) {
            // ── EASY: No regen/weakness — only knockback-based skills ───────────
            case EASY -> {
                // Skill 1: Knockback + damage (8s CD)
                if (now - bi.getLastSkillTime() > 8_000) {
                    knockbackTarget(boss, target, 6.0, 1.5);
                    bi.setLastSkillTime(now);
                }
                // Skill 2: Area ground slam — pushes everyone nearby back (15s CD)
                if (now - bi.getLastSkillTime2() > 15_000) {
                    for (Player nearby : boss.getWorld().getPlayers()) {
                        if (nearby.getLocation().distanceSquared(boss.getLocation()) <= 64.0) {
                            knockbackTarget(boss, nearby, 4.0, 1.2);
                        }
                    }
                    boss.getWorld().playSound(boss.getLocation(),
                            Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.6f);
                    bi.setLastSkillTime2(now);
                }
                // Skill 3: Charge — leap toward player (12s CD)
                if (now - bi.getLastSkillTime3() > 12_000) {
                    leapAttack(boss, target);
                    bi.setLastSkillTime3(now);
                }
            }
            // ── NORMAL: 3 skills ────────────────────────────────────────────────
            case NORMAL -> {
                // Skill 1: Slowness I on target (10s CD)
                if (now - bi.getLastSkillTime() > 10_000) {
                    target.addPotionEffect(
                            new PotionEffect(PotionEffectType.SLOW, 100, 0));
                    bi.setLastSkillTime(now);
                }
                // Skill 2: Leap toward player (8s CD)
                if (now - bi.getLastSkillTime2() > 8_000) {
                    leapAttack(boss, target);
                    bi.setLastSkillTime2(now);
                }
                // Skill 3: Blindness + Slowness burst (30s CD)
                if (now - bi.getLastSkillTime3() > 30_000) {
                    for (Player nearby : boss.getWorld().getPlayers()) {
                        if (nearby.getLocation().distanceSquared(boss.getLocation()) <= 100.0) {
                            nearby.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                            nearby.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1));
                        }
                    }
                    boss.getWorld().playSound(boss.getLocation(),
                            Sound.ENTITY_WITHER_AMBIENT, 0.8f, 0.6f);
                    bi.setLastSkillTime3(now);
                }
            }
            // ── MEDIUM: 3 skills ────────────────────────────────────────────────
            case MEDIUM -> {
                // Skill 1: Ignite target (8s CD)
                if (now - bi.getLastSkillTime() > 8_000) {
                    target.setFireTicks(60);  // 3 seconds
                    bi.setLastSkillTime(now);
                }
                // Skill 2: Poison I on target (12s CD)
                if (now - bi.getLastSkillTime2() > 12_000) {
                    target.addPotionEffect(
                            new PotionEffect(PotionEffectType.POISON, 100, 0));
                    bi.setLastSkillTime2(now);
                }
                // Skill 3: Fireball projectile (15s CD)
                if (now - bi.getLastSkillTime3() > 15_000) {
                    shootFireball(boss, target);
                    bi.setLastSkillTime3(now);
                }
            }
            // ── HARD: 3 skills ──────────────────────────────────────────────────
            case HARD -> {
                // Skill 1: Summon 5-10 minions (25s CD) or scaled by player count
                if (now - bi.getLastSkillTime() > 25_000) {
                    int nearbyPlayers = countNearbyPlayers(boss, SKILL_RANGE);
                    int base = 5 + random.nextInt(6); // 5 to 10 minions
                    int minionCount = Math.min(15, Math.max(base, nearbyPlayers * 3)); // up to 15 max
                    summonMinions(boss, minionCount);
                    bi.setLastSkillTime(now);
                }
                // Skill 2: Wither II on target (15s CD)
                if (now - bi.getLastSkillTime2() > 15_000) {
                    target.addPotionEffect(
                            new PotionEffect(PotionEffectType.WITHER, 100, 1));
                    bi.setLastSkillTime2(now);
                }
                // Skill 3: Lightning strike (20s CD)
                if (now - bi.getLastSkillTime3() > 20_000) {
                    strikeLightningSkill(boss, target);
                    bi.setLastSkillTime3(now);
                }
            }
            // ── NIGHTMARE: 2 skills (Poison II + Sonic Boom) ─────────────────────
            case NIGHTMARE -> {
                if (now - bi.getLastSkillTime() > 10_000) {
                    target.addPotionEffect(
                            new PotionEffect(PotionEffectType.POISON, 100, 1));
                    bi.setLastSkillTime(now);
                }
                if (now - bi.getLastSonicBoomTime() > SONIC_BOOM_COOLDOWN_MS) {
                    sonicBoom(boss, target);
                    bi.setLastSonicBoomTime(now);
                }
            }
        }

        // ── Per-mob signature skill (fires for every difficulty) ──────────────
        useMobSkill(boss, bi, target);
    }

    private void sonicBoom(LivingEntity boss, Player target) {
        // Sound
        boss.getWorld().playSound(boss.getLocation(),
                Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 1.0f);

        // Particles along the line from boss to target
        Location from = boss.getEyeLocation();
        Location to   = target.getEyeLocation();
        Vector  dir   = to.toVector().subtract(from.toVector());
        double  dist  = dir.length();
        if (dist > 0.1) {
            dir.normalize();
            for (double d = 0; d < dist; d += 1.0) {
                Location p = from.clone().add(dir.clone().multiply(d));
                try {
                    boss.getWorld().spawnParticle(Particle.SONIC_BOOM, p, 1);
                } catch (Exception ignored) { }
            }
        }

        // Damage (significant — ignores nothing but hits hard)
        target.damage(30.0, boss);
        view.sendSonicBoomHit(target);
    }

    private void summonMinions(LivingEntity boss, int count) {
        // Only HARD and NIGHTMARE difficulty bosses spawn minions
        BossInstance bi = bossModel.getBoss(boss.getUniqueId());
        if (bi == null || !bi.getDifficulty().spawnMinions()) return;

        EntityType type = boss.getType();
        for (int i = 0; i < count; i++) {
            Location loc = boss.getLocation().add(
                    random.nextDouble() * 4 - 2, 0, random.nextDouble() * 4 - 2);
            try {
                Entity minion = boss.getWorld().spawnEntity(loc, type);
                if (minion instanceof LivingEntity m) {
                    m.setCustomName(ChatColor.DARK_GRAY + "[" + ChatColor.RED + "Minion" + ChatColor.DARK_GRAY + "]");
                    m.setCustomNameVisible(true);
                    m.setRemoveWhenFarAway(true);
                    m.setGlowing(false);

                    // Tag as minion via PDC
                    bossModel.tagMinion(m);
                    // Register with boss so they die when boss dies
                    bi.addMinion(m.getUniqueId());

                    // Baby form where possible
                    if (m instanceof org.bukkit.entity.Ageable ageable) {
                        ageable.setBaby();
                    } else if (m instanceof org.bukkit.entity.Zombie zombie) {
                        zombie.setBaby();
                    } else if (m instanceof org.bukkit.entity.Piglin piglin) {
                        piglin.setBaby();
                    } else if (m instanceof org.bukkit.entity.Zoglin zoglin) {
                        zoglin.setBaby();
                    }

                    try {
                        Attribute scaleAttr = Attribute.valueOf("GENERIC_SCALE");
                        AttributeInstance inst = m.getAttribute(scaleAttr);
                        if (inst != null) inst.setBaseValue(0.7);
                    } catch (Exception ignored) { }

                    // Weaker than boss
                    AttributeInstance mHealth = m.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                    if (mHealth != null) {
                        mHealth.setBaseValue(20.0);
                        try { m.setHealth(20.0); } catch (Exception ignored) { }
                    }
                    AttributeInstance atk = m.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
                    if (atk != null) atk.setBaseValue(3.0);

                    if (m.getEquipment() != null) m.getEquipment().clear();
                }
            } catch (Exception ignored) { }
        }
        boss.getWorld().playSound(boss.getLocation(),
                Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.6f);
    }

    private Player findNearestPlayer(LivingEntity boss, double range) {
        Player nearest = null;
        double nearestDist = range * range;
        for (Player p : boss.getWorld().getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            if (p.isDead()) continue;
            double d = p.getLocation().distanceSquared(boss.getLocation());
            if (d < nearestDist) {
                nearestDist = d;
                nearest = p;
            }
        }
        return nearest;
    }

    private int countNearbyPlayers(LivingEntity boss, double range) {
        int count = 0;
        double rangeSq = range * range;
        for (Player p : boss.getWorld().getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.getGameMode() == org.bukkit.GameMode.CREATIVE) continue;
            if (p.isDead()) continue;
            if (p.getLocation().distanceSquared(boss.getLocation()) <= rangeSq) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    // ── Skill helpers ──────────────────────────────────────────────────────────

    /**
     * Fires the per-mob signature skill. Called every skill tick after the
     * difficulty-based skills; uses the mob profile's own cooldown timer.
     */
    private void useMobSkill(LivingEntity boss, BossInstance bi, Player target) {
        BossMobProfile profile = bi.getMobProfile();
        if (profile == null) return;
        long now = System.currentTimeMillis();
        if (now - bi.getLastMobSkillTime() < profile.getSkillCooldownMs()) return;

        bi.setLastMobSkillTime(now);
        switch (profile) {
            case ZOMBIE ->
                // Hunger Bite: Hunger II for 6s
                target.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 120, 1));
            case HUSK ->
                // Desert Exhaustion: Slowness II + Mining Fatigue for 5s
                {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 1));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 100, 1));
                }
            case SKELETON ->
                // Rain of Arrows: shoot 3 arrows in a spread
                shootArrowSpread(boss, target, 3);
            case STRAY ->
                // Freezing Shot: Slowness III for 4s
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 80, 2));
            case SPIDER ->
                // Web Snare: Slowness II + jump boost negation for 4s
                {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 80, 1));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 80, 128));
                }
            case CAVE_SPIDER ->
                // Venom Fang: Poison II for 5s
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
            case ENDERMAN ->
                // Shadow Teleport: teleport the boss directly behind the player
                {
                    Location behind = target.getLocation().clone()
                            .subtract(target.getLocation().getDirection().multiply(2));
                    behind.setY(target.getLocation().getY());
                    boss.teleport(behind);
                    boss.getWorld().playSound(behind,
                            Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                }
            case PILLAGER ->
                // Suppressing Fire: 5 arrows rapid-fire
                shootArrowSpread(boss, target, 5);
            case VINDICATOR ->
                // Axe Cleave: heavy damage + knockback to all players within 5 blocks
                {
                    for (Player nearby : boss.getWorld().getPlayers()) {
                        if (nearby.getLocation().distanceSquared(boss.getLocation()) <= 25.0) {
                            knockbackTarget(boss, nearby, 10.0, 2.0);
                        }
                    }
                    boss.getWorld().playSound(boss.getLocation(),
                            Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
                }
            case EVOKER ->
                // Fang Barrage: summon a line of evoker fangs toward target
                spawnFangLine(boss, target);
            case RAVAGER ->
                // Devastating Charge: lunge at target with massive knockback
                {
                    knockbackTarget(boss, target, 15.0, 3.5);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 2));
                    boss.getWorld().playSound(boss.getLocation(),
                            Sound.ENTITY_RAVAGER_ATTACK, 1.0f, 1.0f);
                }
            case ZOGLIN ->
                // Feral Tusk: launch target high into the air
                {
                    target.setVelocity(new Vector(0, 2.2, 0));
                    target.damage(8.0, boss);
                    boss.getWorld().playSound(boss.getLocation(),
                            Sound.ENTITY_ZOGLIN_ATTACK, 1.0f, 1.0f);
                }
            case BLAZE ->
                // Blaze Barrage: 4 fireballs in a burst
                {
                    for (int i = 0; i < 4; i++) {
                        final int fi = i;
                        new BukkitRunnable() {
                            @Override public void run() {
                                if (boss.isValid() && !boss.isDead())
                                    shootFireball(boss, target);
                            }
                        }.runTaskLater(plugin, fi * 5L);
                    }
                }
            case WITHER_SKELETON ->
                // Wither Strike: Wither III for 5s + darkness
                {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 2));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
                }
            case PIGLIN_BRUTE ->
                // Brute Smash: Weakness II + Slowness I + heavy damage
                {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 1));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 80, 0));
                    target.damage(12.0, boss);
                    boss.getWorld().playSound(boss.getLocation(),
                            Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 1.0f, 1.0f);
                }
        }
    }

    /** Knocks the target away from the boss and deals damage. */
    private void knockbackTarget(LivingEntity boss, Player target,
                                 double damage, double strength) {
        Vector kb = target.getLocation().subtract(boss.getLocation()).toVector();
        kb.setY(0.6);
        if (kb.lengthSquared() > 0.01) kb.normalize().multiply(strength);
        target.setVelocity(kb);
        target.damage(damage, boss);
        boss.getWorld().playSound(target.getLocation(),
                Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.2f);
    }

    /** Boss leaps toward the player. */
    private void leapAttack(LivingEntity boss, Player target) {
        Vector leap = target.getLocation().subtract(boss.getLocation()).toVector();
        if (leap.lengthSquared() < 0.01) return;
        leap.normalize();
        leap.setY(0.6);
        leap.multiply(1.2);
        boss.setVelocity(leap);
        boss.getWorld().playSound(boss.getLocation(),
                Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.8f);
    }

    /** Periodic ranged shot so melee bosses still pressure from a distance. */
    private void bossRangedShot(LivingEntity boss, Player target, BossDifficulty diff) {
        if (boss.getType() == EntityType.BLAZE) {
            shootFireball(boss, target);
            return;
        }
        Vector dir = target.getEyeLocation().toVector().subtract(boss.getEyeLocation().toVector());
        if (dir.lengthSquared() < 0.01) return;
        dir.normalize();
        try {
            Arrow arrow = boss.launchProjectile(Arrow.class, dir.multiply(2.5));
            arrow.setShooter(boss);
            arrow.setDamage(rangedArrowDamage(diff));
            arrow.setKnockbackStrength(1);
            arrow.setFireTicks(80);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setCritical(diff == BossDifficulty.HARD || diff == BossDifficulty.NIGHTMARE);
        } catch (Exception ignored) {
            return;
        }
        boss.getWorld().playSound(boss.getLocation(),
                Sound.ENTITY_SKELETON_SHOOT, 1.2f, 0.7f);
    }

    private double rangedArrowDamage(BossDifficulty diff) {
        return switch (diff) {
            case EASY -> 10.0;
            case NORMAL -> 14.0;
            case MEDIUM -> 16.0;
            case HARD -> 20.0;
            case NIGHTMARE -> 26.0;
        };
    }

    /** Max hearts-worth of damage a boss hit may deal after armor (20 HP player). */
    private double maxFinalDamageOnPlayer(BossDifficulty diff) {
        if (diff == null) return 10.0;
        return switch (diff) {
            case EASY -> 8.0;        // 4 hearts
            case NORMAL -> 10.0;     // 5 hearts
            case MEDIUM -> 12.0;     // 6 hearts
            case HARD -> 14.0;       // 7 hearts
            case NIGHTMARE -> 16.0;  // 8 hearts
        };
    }

    /** Shoots count arrows in a spread toward and around the target. */
    private void shootArrowSpread(LivingEntity boss, Player target, int count) {
        Vector base = target.getEyeLocation().subtract(boss.getEyeLocation()).toVector();
        if (base.lengthSquared() < 0.01) return;
        base.normalize();
        BossInstance bi = bossModel.getBoss(boss.getUniqueId());
        BossDifficulty diff = bi != null ? bi.getDifficulty() : BossDifficulty.NORMAL;
        for (int i = 0; i < count; i++) {
            Vector spread = base.clone().add(new Vector(
                    (random.nextDouble() - 0.5) * 0.15,
                    (random.nextDouble() - 0.5) * 0.15,
                    (random.nextDouble() - 0.5) * 0.15)).normalize();
            final Vector finalSpread = spread;
            new BukkitRunnable() {
                @Override public void run() {
                    if (!boss.isValid() || boss.isDead()) return;
                    try {
                        Arrow arrow = boss.launchProjectile(Arrow.class, finalSpread.multiply(2.2));
                        arrow.setShooter(boss);
                        arrow.setDamage(rangedArrowDamage(diff));
                        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                        arrow.setFireTicks(40);
                    } catch (Exception ignored) { }
                }
            }.runTaskLater(plugin, i * 2L);
        }
        boss.getWorld().playSound(boss.getLocation(),
                Sound.ENTITY_SKELETON_SHOOT, 1.0f, 1.0f);
    }

    /** Spawns a line of evoker fangs from boss toward target. */
    private void spawnFangLine(LivingEntity boss, Player target) {
        Location from = boss.getLocation();
        Location to   = target.getLocation();
        Vector   dir  = to.toVector().subtract(from.toVector());
        double   dist = dir.length();
        if (dist < 0.1) return;
        dir.normalize();
        int steps = Math.min((int) dist, 8);
        for (int i = 1; i <= steps; i++) {
            final Location fangLoc = from.clone().add(dir.clone().multiply(i));
            new BukkitRunnable() {
                @Override public void run() {
                    try {
                        boss.getWorld().spawnEntity(fangLoc, EntityType.EVOKER_FANGS);
                    } catch (Exception ignored) { }
                }
            }.runTaskLater(plugin, i * 2L);
        }
        boss.getWorld().playSound(boss.getLocation(),
                Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 1.0f);
    }

    /** Shoots a small fireball toward the target. */
    private void shootFireball(LivingEntity boss, Player target) {
        try {
            Entity proj = boss.getWorld().spawnEntity(
                    boss.getEyeLocation(), EntityType.SMALL_FIREBALL);
            if (proj instanceof SmallFireball fireball) {
                Vector dir = target.getEyeLocation().subtract(
                        boss.getEyeLocation()).toVector();
                if (dir.lengthSquared() > 0.01) {
                    fireball.setVelocity(dir.normalize().multiply(1.5));
                }
                fireball.setShooter(boss);
            }
        } catch (Exception ignored) { }
        boss.getWorld().playSound(boss.getLocation(),
                Sound.ENTITY_BLAZE_SHOOT, 0.7f, 1.0f);
    }

    /** Strikes lightning at the target's location (visual + damage). */
    private void strikeLightningSkill(LivingEntity boss, Player target) {
        boss.getWorld().strikeLightningEffect(target.getLocation());
        target.damage(maxFinalDamageOnPlayer(bossModel.getBossDifficulty(boss)), boss);
        boss.getWorld().playSound(target.getLocation(),
                Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.0f);
    }

    // ── Fluid & Anti-Stuck Handlers ──────────────────────────────────────────

    /**
     * Prevents boss from being slowed down or cheesed in water/lava.
     */
    private void handleFluidResistance(LivingEntity boss) {
        Block feet = boss.getLocation().getBlock();
        Block head = boss.getEyeLocation().getBlock();
        boolean inWater = feet.getType() == Material.WATER || head.getType() == Material.WATER;
        boolean inLava  = feet.getType() == Material.LAVA  || head.getType() == Material.LAVA;

        if (inWater || inLava) {
            // Give Dolphins Grace / Speed II and boost velocity towards nearest player
            boss.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 60, 1, false, false));
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 2, false, false));

            Player nearest = findNearestPlayer(boss, 30.0);
            if (nearest != null) {
                Vector dir = nearest.getLocation().toVector().subtract(boss.getLocation().toVector());
                if (dir.lengthSquared() > 0.01) {
                    dir.normalize().multiply(0.4);
                    dir.setY(0.2); // keep head above surface
                    boss.setVelocity(boss.getVelocity().add(dir));
                }
            }
        }
    }

    /**
     * Detects if boss is stuck inside a wall or hasn't moved towards player in 10s.
     * When stuck, creates an explosive shockwave clearing nearby blocks and teleports/leaps to player.
     */
    private void handleAntiStuck(LivingEntity boss, BossInstance bi) {
        long now = System.currentTimeMillis();
        Location currentLoc = boss.getLocation();
        Block eyeBlock = boss.getEyeLocation().getBlock();
        boolean suffocatingInWall = eyeBlock.getType().isSolid();

        double distMovedSq = Math.pow(currentLoc.getX() - bi.getLastX(), 2)
                           + Math.pow(currentLoc.getZ() - bi.getLastZ(), 2);

        Player nearest = findNearestPlayer(boss, 40.0);

        if (distMovedSq > 1.0) {
            bi.setLastPos(currentLoc.getX(), currentLoc.getY(), currentLoc.getZ());
            bi.setLastMovedTime(now);
        }

        boolean stationaryTooLong = (nearest != null) && (now - bi.getLastMovedTime() > 10_000L);

        if (suffocatingInWall || stationaryTooLong) {
            // 1) Trigger a wall-clearing breach explosion around boss
            breachWallAroundBoss(boss);

            // 2) Rush or teleport close to the nearest player
            if (nearest != null) {
                Location playerLoc = nearest.getLocation();
                Location targetLoc = findSafeTeleportLocation(playerLoc, boss.getLocation(), 3.0);
                if (targetLoc != null) {
                    boss.getWorld().spawnParticle(Particle.PORTAL, boss.getLocation(), 40, 0.5, 1.0, 0.5, 0.1);
                    boss.teleport(targetLoc);
                    boss.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, targetLoc, 2);
                    boss.getWorld().playSound(targetLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.2f);
                    nearest.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "\u2620 "
                            + bi.getDifficulty().getColor() + bi.getDifficulty().getDisplayName() + " Boss "
                            + ChatColor.RED + "breached the walls and closed in on you!");
                } else {
                    // Fallback: Leap aggressively toward player
                    leapAttack(boss, nearest);
                }
            }

            // Reset tracking
            bi.setLastPos(boss.getLocation().getX(), boss.getLocation().getY(), boss.getLocation().getZ());
            bi.setLastMovedTime(now);
        }
    }

    /**
     * Creates an explosion effect that destroys obstructing soft/natural blocks in a 3x3x3 cube around boss,
     * while protecting chests, bedrock, and player builds.
     */
    private void breachWallAroundBoss(LivingEntity boss) {
        Location center = boss.getLocation();
        World world = center.getWorld();
        if (world == null) return;

        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
        world.spawnParticle(Particle.EXPLOSION_HUGE, center, 1);

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int y = cy; y <= cy + 2; y++) {
                for (int z = cz - 1; z <= cz + 1; z++) {
                    Block b = world.getBlockAt(x, y, z);
                    Material mat = b.getType();
                    // Don't break bedrock, containers, or indestructible materials
                    if (!mat.isAir() && mat != Material.BEDROCK && mat != Material.BARRIER
                            && mat != Material.CHEST && mat != Material.TRAPPED_CHEST
                            && mat != Material.BARREL && mat != Material.SHULKER_BOX) {
                        b.breakNaturally();
                    }
                }
            }
        }
    }

    // =========================================================================
    // Boss death — rewards & essence drop
    // =========================================================================

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // ── Minion death — no drops at all ──────────────────────────────────
        if (bossModel.isMinion(entity)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }

        if (!bossModel.isBoss(entity)) return;

        // ── Boss death ───────────────────────────────────────────────────────
        // Clear all natural drops — only essence and money are rewards
        event.getDrops().clear();
        event.setDroppedExp(0);

        BossDifficulty diff = bossModel.getBossDifficulty(entity);
        BossInstance bi = bossModel.removeBoss(entity.getUniqueId());
        if (diff == null) return;

        // Kill all minions that were spawned by this boss
        if (bi != null) {
            for (UUID minionId : bi.getMinionIds()) {
                Entity minion = Bukkit.getEntity(minionId);
                if (minion != null && minion.isValid() && !minion.isDead()) {
                    minion.remove();
                }
            }
        }

        Player killer = entity.getKiller();
        if (killer == null) return;  // no player → no rewards

        // Money reward
        double reward = diff.getReward();
        boolean netherBonus = entity.getWorld().getEnvironment() == World.Environment.NETHER
                && isNetherMob(entity.getType());
        if (netherBonus) reward *= NETHER_REWARD_MULTIPLIER;

        economy.deposit(killer.getUniqueId(), reward);
        view.sendBossSlainKiller(killer, diff, reward, netherBonus);
        view.broadcastBossSlain(killer.getName(), diff);

        // Essence drop (luck-based, max 5)
        dropEssence(entity.getLocation(), diff);
    }

    /** Evoker fang damage → triple for boss evoker fang events. */
    @EventHandler
    public void onEvokerFangDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof org.bukkit.entity.EvokerFangs fangs)) return;
        if (!(event.getEntity() instanceof Player)) return;
        org.bukkit.entity.LivingEntity owner = fangs.getOwner();
        if (owner == null || !bossModel.isBoss(owner)) return;
        // Triple the fang damage for boss Evoker
        event.setDamage(event.getDamage() * 3.0);
    }

    private void dropEssence(Location loc, BossDifficulty diff) {
        // Luck check — sometimes drops nothing
        if (random.nextDouble() > diff.getEssenceDropChance()) return;

        int min = diff.getMinEssence();
        int max = diff.getMaxEssence();
        int amount = min + random.nextInt(max - min + 1);
        amount = Math.min(amount, 5);  // hard cap

        if (amount > 0) {
            ItemStack essence = bossModel.createEssence(amount);
            loc.getWorld().dropItemNaturally(loc, essence);
        }
    }

    // =========================================================================
    // Boss Environmental & Explosion Immunities
    // =========================================================================

    @EventHandler
    public void onBossGeneralDamage(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof LivingEntity victim) || !bossModel.isBoss(victim)) return;

        EntityDamageEvent.DamageCause cause = event.getCause();
        // Prevent boss from dying to terrain hazards: suffocation, drowning, lava, hot floor, or breach explosions.
        // NOTE: FIRE_TICK is deliberately ALLOWED so Flame bows & Fire Aspect swords still burn the boss!
        if (cause == EntityDamageEvent.DamageCause.SUFFOCATION
                || cause == EntityDamageEvent.DamageCause.DROWNING
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            event.setCancelled(true);
            return;
        }
        scheduleBossHpRefresh(victim);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossHpChanged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || !bossModel.isBoss(victim)) return;
        scheduleBossHpRefresh(victim);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || !bossModel.isBoss(victim)) return;
        scheduleBossHpRefresh(victim);
    }

    private void scheduleBossHpRefresh(LivingEntity boss) {
        plugin.getServer().getScheduler().runTask(plugin, () -> refreshBossHpDisplay(boss));
    }

    // =========================================================================
    // Boss Combat Balance & Damage Handling
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        long now = System.currentTimeMillis();

        // 1) Player attacks Boss
        if (event.getEntity() instanceof LivingEntity victim && bossModel.isBoss(victim)) {
            // Players cannot snipe bosses — projectiles are dodged like an Enderman
            if (event.getDamager() instanceof Projectile proj
                    && proj.getShooter() instanceof Player) {
                event.setCancelled(true);
                proj.remove();
                dodgeLikeEnderman(victim);
                return;
            }

            // Elytra / trident dive-stab: only blocked once the boss is at half HP
            if (event.getDamager() instanceof Player attacker
                    && isBossAtOrBelowHalfHp(victim)
                    && isAirDiveAttack(attacker)) {
                event.setCancelled(true);
                denyAirDive(attacker, victim);
                return;
            }

            BossDifficulty diff = bossModel.getBossDifficulty(victim);
            BossInstance bi = bossModel.getBoss(victim.getUniqueId());
            if (diff != null) {
                double maxHp = (bi != null && bi.getVirtualMaxHp() > 0)
                        ? bi.getVirtualMaxHp()
                        : diff.getBaseHealth();
                AttributeInstance maxHealthAttr = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (maxHp <= 0 && maxHealthAttr != null) maxHp = maxHealthAttr.getValue();
                double maxAllowedDamage = maxHp * diff.getMaxDamageCapPercent();
                if (event.getDamage() > maxAllowedDamage) {
                    event.setDamage(maxAllowedDamage);
                }
            }
            scheduleBossHpRefresh(victim);
            return;
        }

        // 2) Boss attacks Player (Direct melee attack or projectile)
        boolean projectileHit = event.getDamager() instanceof Projectile;
        Entity damager = event.getDamager();
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }

        if (event.getEntity() instanceof Player target && damager instanceof LivingEntity boss && bossModel.isBoss(boss)) {
            Long graceUntil = totemGraceMap.get(target.getUniqueId());
            if (graceUntil != null) {
                if (now < graceUntil) {
                    event.setCancelled(true);
                    return;
                }
                totemGraceMap.remove(target.getUniqueId());
            }

            // Only melee is rate-limited — arrows must still land while the boss is swinging
            if (!projectileHit) {
                Long cdUntil = bossAttackCooldownMap.get(boss.getUniqueId());
                if (cdUntil != null && now < cdUntil) {
                    event.setCancelled(true);
                    return;
                }
                bossAttackCooldownMap.put(boss.getUniqueId(), now + 900L);
            }

            // Cap FINAL damage after armor so netherite players still feel a real boss hit
            BossDifficulty diff = bossModel.getBossDifficulty(boss);
            double maxFinal = maxFinalDamageOnPlayer(diff);
            double finalDmg = event.getFinalDamage();
            if (finalDmg > maxFinal && finalDmg > 0.0) {
                event.setDamage(event.getDamage() * (maxFinal / finalDmg));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHitBoss(ProjectileHitEvent event) {
        if (!(event.getHitEntity() instanceof LivingEntity victim)) return;
        if (!bossModel.isBoss(victim)) return;
        Projectile proj = event.getEntity();
        if (!(proj.getShooter() instanceof Player)) return;
        event.setCancelled(true);
        proj.remove();
        dodgeLikeEnderman(victim);
    }

    private boolean isBossAtOrBelowHalfHp(LivingEntity boss) {
        AttributeInstance attr = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double max = attr != null ? attr.getValue() : 20.0;
        if (max <= 0) return false;
        return boss.getHealth() <= max * 0.5;
    }

    /** Elytra glide, falling dive, or airborne trident stab. */
    private boolean isAirDiveAttack(Player player) {
        if (player.isGliding() || player.isFlying()) return true;
        if (player.getFallDistance() >= 1.5f) return true;
        if (player.getVelocity().getY() < -0.55) return true;
        ItemStack chest = player.getInventory().getChestplate();
        boolean elytra = chest != null && chest.getType() == Material.ELYTRA;
        if (elytra && !player.isOnGround() && player.getVelocity().lengthSquared() > 0.20) {
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean trident = hand != null && hand.getType() == Material.TRIDENT;
        return trident && !player.isOnGround()
                && (player.getFallDistance() >= 0.8f || player.getVelocity().lengthSquared() > 0.35);
    }

    private void denyAirDive(Player attacker, LivingEntity boss) {
        dodgeLikeEnderman(boss);
        attacker.setGliding(false);
        attacker.setFallDistance(0f);
        Vector away = attacker.getLocation().toVector().subtract(boss.getLocation().toVector());
        away.setY(0);
        if (away.lengthSquared() < 0.01) {
            away = attacker.getLocation().getDirection();
            away.setY(0);
        }
        if (away.lengthSquared() > 0.01) away.normalize().multiply(1.4);
        away.setY(0.55);
        attacker.setVelocity(away);
        attacker.getWorld().playSound(attacker.getLocation(),
                Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.5f);
        attacker.sendMessage(ChatColor.DARK_PURPLE + "\u2620 "
                + ChatColor.GRAY + "The boss slipped the dive. Fight on the ground.");
    }

    /** Teleport the boss a short random hop away, Enderman-style. */
    private void dodgeLikeEnderman(LivingEntity boss) {
        BossInstance bi = bossModel.getBoss(boss.getUniqueId());
        long now = System.currentTimeMillis();
        if (bi != null) {
            if (now - bi.getLastDodgeTime() < 350L) return;
            bi.setLastDodgeTime(now);
        }

        Location from = boss.getLocation();
        World world = from.getWorld();
        if (world == null) return;

        Location dest = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist  = 4.0 + random.nextDouble() * 8.0;
            int x = from.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = from.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            int baseY = from.getBlockY();
            for (int dy = 4; dy >= -4; dy--) {
                int y = baseY + dy;
                Block feet = world.getBlockAt(x, y, z);
                Block head = world.getBlockAt(x, y + 1, z);
                Block ground = world.getBlockAt(x, y - 1, z);
                if (feet.getType().isAir() && head.getType().isAir()
                        && ground.getType().isSolid()
                        && ground.getType() != Material.LAVA
                        && ground.getType() != Material.FIRE
                        && ground.getType() != Material.MAGMA_BLOCK) {
                    dest = new Location(world, x + 0.5, y, z + 0.5,
                            from.getYaw(), from.getPitch());
                    break;
                }
            }
            if (dest != null) break;
        }
        if (dest == null) return;

        world.playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        world.spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.25);
        boss.teleport(dest);
        world.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        world.spawnParticle(Particle.PORTAL, dest.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.25);
    }

    // =========================================================================
    // Totem of Undying — brief i-frames + blink, no usage cooldown
    // =========================================================================

    @EventHandler
    public void onEntityResurrect(EntityResurrectEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        EntityDamageEvent lastDmg = player.getLastDamageCause();
        if (lastDmg instanceof EntityDamageByEntityEvent edbe) {
            Entity damager = edbe.getDamager();
            if (damager instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Entity shooter) {
                damager = shooter;
            }

            if (damager instanceof LivingEntity boss && bossModel.isBoss(boss)) {
                long now = System.currentTimeMillis();
                totemGraceMap.put(player.getUniqueId(), now + 500L);
                player.setNoDamageTicks(10);

                Location safeLoc = findSafeTeleportLocation(player.getLocation(), boss.getLocation(), 7.0);
                if (safeLoc != null) {
                    player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 30, 0.5, 1.0, 0.5, 0.1);
                    player.teleport(safeLoc);
                    player.getWorld().spawnParticle(Particle.FLASH, safeLoc.clone().add(0, 1, 0), 1);
                    player.getWorld().playSound(safeLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                }

                player.sendMessage(ChatColor.GOLD + "\u2728 " + ChatColor.YELLOW
                        + "Your Totem triggered and teleported you to safety!");
            }
        }
    }

    /**
     * Finds a safe ground location away from the boss to avoid wall suffocation or lava/void hazards.
     */
    private Location findSafeTeleportLocation(Location playerLoc, Location bossLoc, double distance) {
        World world = playerLoc.getWorld();
        if (world == null) return null;

        // Direction from boss to player
        Vector dir = playerLoc.toVector().subtract(bossLoc.toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 0.01) {
            dir = new Vector(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
        }
        dir.normalize().multiply(distance);

        int targetX = bossLoc.getBlockX() + dir.getBlockX();
        int targetZ = bossLoc.getBlockZ() + dir.getBlockZ();
        int startY = playerLoc.getBlockY();

        // Search vertical range +/- 4 blocks for 2 air blocks above solid ground
        for (int dy = 0; dy <= 4; dy++) {
            for (int sign : new int[]{1, -1}) {
                int y = startY + (dy * sign);
                Block feet = world.getBlockAt(targetX, y, targetZ);
                Block head = world.getBlockAt(targetX, y + 1, targetZ);
                Block ground = world.getBlockAt(targetX, y - 1, targetZ);

                if (feet.getType().isAir() && head.getType().isAir()
                        && ground.getType().isSolid()
                        && ground.getType() != Material.LAVA
                        && ground.getType() != Material.FIRE
                        && ground.getType() != Material.MAGMA_BLOCK) {
                    Location loc = new Location(world, targetX + 0.5, y, targetZ + 0.5);
                    loc.setYaw(playerLoc.getYaw());
                    loc.setPitch(playerLoc.getPitch());
                    return loc;
                }
            }
        }
        return null;
    }

    // =========================================================================
    // Player death by boss — keep inventory, no money loss
    // =========================================================================

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        totemGraceMap.remove(victim.getUniqueId());

        EntityDamageEvent dmg = victim.getLastDamageCause();
        if (!(dmg instanceof EntityDamageByEntityEvent edbe)) return;

        Entity damager = edbe.getDamager();
        if (bossModel.isBoss(damager)) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setDeathMessage(view.deathByBossMessage(victim.getName()));
        }
    }

    // =========================================================================
    // Auto-Smelt — Boss Slayer Pickaxe smelts ores on break
    // =========================================================================

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!bossModel.isBossPickaxe(tool)) return;

        Block block = event.getBlock();
        Material smelted = AUTO_SMELT_MAP.get(block.getType());
        if (smelted == null) return;  // not a smeltable ore

        // Prevent the natural (raw) drops, then drop the smelted product
        event.setDropItems(false);
        block.getWorld().dropItemNaturally(
                block.getLocation(), new ItemStack(smelted, 1));
    }

    // =========================================================================
    // Trade — 128 Essence of Life (2 stacks) → 1 Boss Slayer Pickaxe
    // =========================================================================

    private void handleTrade(Player player) {
        int count = countEssence(player);
        if (count < TRADE_COST) {
            view.sendNotEnoughEssence(player, count);
            return;
        }

        // Make sure there's room for the pickaxe
        ItemStack pickaxe = bossModel.createBossPickaxe();
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(pickaxe);
        if (!leftover.isEmpty()) {
            view.sendInventoryFull(player);
            return;
        }

        removeEssence(player, TRADE_COST);
        view.sendTradeSuccess(player);

        // Refresh the GUI with the new essence count
        int newCount = countEssence(player);
        player.openInventory(gui.build(newCount));
    }

    private int countEssence(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (bossModel.isEssence(item)) count += item.getAmount();
        }
        return count;
    }

    private void removeEssence(Player player, int needed) {
        int remaining = needed;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            ItemStack item = inv.getItem(i);
            if (!bossModel.isEssence(item)) continue;
            if (item.getAmount() <= remaining) {
                remaining -= item.getAmount();
                inv.clear(i);
            } else {
                item.setAmount(item.getAmount() - remaining);
                remaining = 0;
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private World findWorld(World.Environment env) {
        return Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == env)
                .findFirst().orElse(null);
    }

    private boolean isNetherMob(EntityType type) {
        for (EntityType t : NETHER_MOBS) if (t == type) return true;
        return false;
    }

    private int findSafeY(World world, int x, int z, boolean nether) {
        if (nether) {
            // Scan downward from the ceiling for a 2-tall air pocket above solid ground
            for (int y = 100; y > 10; y--) {
                if (world.getBlockAt(x, y, z).getType().isAir()
                        && world.getBlockAt(x, y + 1, z).getType().isAir()
                        && !world.getBlockAt(x, y - 1, z).getType().isAir()) {
                    return y;
                }
            }
            return 60;
        }
        return world.getHighestBlockYAt(x, z) + 1;
    }

    private String formatMobName(EntityType type) {
        String raw = type.name().replace('_', ' ').toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }
}
