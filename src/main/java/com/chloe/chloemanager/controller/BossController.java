package com.chloe.chloemanager.controller;

import com.chloe.chloemanager.ChloeManagerPlugin;
import com.chloe.chloemanager.model.BossDifficulty;
import com.chloe.chloemanager.model.BossModel;
import com.chloe.chloemanager.model.BossModel.BossInstance;
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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
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
 */
public class BossController implements CommandExecutor, Listener {

    private static final DecimalFormat FMT = new DecimalFormat("#,##0.00");

    // ── Tunable constants ────────────────────────────────────────────────────
    private static final long   SPAWN_INITIAL_DELAY  = 2400L;   // 2 min  (first boss)
    private static final long   SPAWN_INTERVAL_TICKS = 18000L;  // 15 min (after that)
    private static final long   SKILL_INTERVAL_TICKS = 100L;    // 5 sec
    private static final int    SPAWN_RADIUS         = 3_000;
    private static final double  SKILL_RANGE          = 25.0;
    private static final double  MAX_HEALTH_CAP       = 50000.0;
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
        EntityType.ZOGLIN,
        EntityType.DROWNED
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
        openTradeGUI(player);
        return true;
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
        EntityType mobType;
        boolean isNetherMob;
        if (inNether) {
            mobType    = NETHER_MOBS[random.nextInt(NETHER_MOBS.length)];
            isNetherMob = true;
        } else {
            mobType    = OVERWORLD_MOBS[random.nextInt(OVERWORLD_MOBS.length)];
            isNetherMob = false;
        }

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

        // Spawn the entity
        LivingEntity boss;
        try {
            Entity entity = world.spawnEntity(loc, mobType);
            if (!(entity instanceof LivingEntity)) {
                entity.remove();
                return;
            }
            boss = (LivingEntity) entity;
        } catch (Exception e) {
            return;  // mob couldn't spawn here — try next cycle
        }

        // Tag, scale, and name the boss
        bossModel.tagBoss(boss, difficulty);
        scaleAttributes(boss, difficulty);
        setBossName(boss, difficulty, mobType);

        // Track it
        bossModel.addBoss(boss.getUniqueId(),
                new BossInstance(boss.getUniqueId(), difficulty));

        // Broadcast
        String worldName = inNether ? "Nether" : "Overworld";
        boolean netherBonus = inNether && isNetherMob;
        view.broadcastSpawn(difficulty, formatMobName(mobType), worldName,
                x, y, z, netherBonus);
    }

    private void scaleAttributes(LivingEntity boss, BossDifficulty diff) {
        AttributeInstance maxHealth = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            double newMax = Math.min(
                    maxHealth.getBaseValue() * diff.getHealthMultiplier(),
                    MAX_HEALTH_CAP);
            maxHealth.setBaseValue(newMax);
            try {
                boss.setHealth(newMax);
            } catch (Exception ignored) { }
        }
        AttributeInstance attack = boss.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attack != null) {
            attack.setBaseValue(attack.getBaseValue() * diff.getDamageMultiplier());
        }
        AttributeInstance speed = boss.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * diff.getSpeedMultiplier());
        }
    }

    private void setBossName(LivingEntity boss, BossDifficulty diff, EntityType type) {
        String mobName = formatMobName(type);
        boss.setCustomName(diff.getColor() + "" + ChatColor.BOLD
                + "\u2620 " + diff.getDisplayName() + " Boss \u2620 "
                + ChatColor.RESET + ChatColor.GRAY + mobName);
        boss.setCustomNameVisible(true);
        boss.setGlowing(true);
        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(true);
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

            useSkill(boss, bi);
        }
    }

    private void useSkill(LivingEntity boss, BossInstance bi) {
        long now = System.currentTimeMillis();
        BossDifficulty diff = bi.getDifficulty();

        Player target = findNearestPlayer(boss, SKILL_RANGE);
        if (target == null) return;

        switch (diff) {
            // ── EASY: 3 skills ──────────────────────────────────────────────────
            case EASY -> {
                // Skill 1: Weakness I on target (12s CD)
                if (now - bi.getLastSkillTime() > 12_000) {
                    target.addPotionEffect(
                            new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
                    bi.setLastSkillTime(now);
                }
                // Skill 2: Self-Regeneration I (25s CD) — keeps the boss alive longer
                if (now - bi.getLastSkillTime2() > 25_000) {
                    boss.addPotionEffect(
                            new PotionEffect(PotionEffectType.REGENERATION, 100, 0));
                    bi.setLastSkillTime2(now);
                }
                // Skill 3: Knockback + damage (10s CD)
                if (now - bi.getLastSkillTime3() > 10_000) {
                    knockbackTarget(boss, target, 4.0, 1.3);
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
                // Skill 3: Summon 1 minion (35s CD)
                if (now - bi.getLastSkillTime3() > 35_000) {
                    summonMinions(boss, 1);
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
                // Skill 1: Summon 2 minions (25s CD)
                if (now - bi.getLastSkillTime() > 25_000) {
                    summonMinions(boss, 2);
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
        EntityType type = boss.getType();
        for (int i = 0; i < 2; i++) {
            Location loc = boss.getLocation().add(
                    random.nextDouble() * 4 - 2, 0, random.nextDouble() * 4 - 2);
            try {
                Entity minion = boss.getWorld().spawnEntity(loc, type);
                if (minion instanceof LivingEntity m) {
                    m.setCustomName(ChatColor.RED + "Boss Minion");
                    m.setCustomNameVisible(true);
                    m.setRemoveWhenFarAway(true);
                }
            } catch (Exception ignored) { }
        }
        boss.getWorld().playSound(boss.getLocation(),
                Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.5f);
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

    // ── Skill helpers ──────────────────────────────────────────────────────────

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
        target.damage(8.0, boss);
        boss.getWorld().playSound(target.getLocation(),
                Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.0f);
    }

    // =========================================================================
    // Boss death — rewards & essence drop
    // =========================================================================

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!bossModel.isBoss(entity)) return;

        BossDifficulty diff = bossModel.getBossDifficulty(entity);
        bossModel.removeBoss(entity.getUniqueId());
        if (diff == null) return;

        Player killer = entity.getKiller();
        if (killer == null) return;  // no player credit → no rewards

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
    // Player death by boss — keep inventory, no money loss
    // =========================================================================

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

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
