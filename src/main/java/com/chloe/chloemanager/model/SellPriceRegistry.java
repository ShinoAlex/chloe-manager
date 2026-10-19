package com.chloe.chloemanager.model;

import org.bukkit.Material;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * MODEL — SellPriceRegistry
 *
 * A central registry of every item that can be sold and its price per unit.
 *
 * Rules:
 *  - Only raw resources are sellable (ores, logs, crops, fish, etc.).
 *  - Tools, armour, food (except fish), and crafted goods are NOT listed here.
 *  - Adding a new sellable item later only requires a new entry in this class.
 */
public class SellPriceRegistry {

    private final Map<Material, Double> prices = new EnumMap<>(Material.class);

    public SellPriceRegistry() {
        registerAll();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the sell price per item, or -1.0 if the material is not sellable.
     */
    public double getPrice(Material material) {
        return prices.getOrDefault(material, -1.0);
    }

    /** Returns true if this material can be sold. */
    public boolean isSellable(Material material) {
        return prices.containsKey(material);
    }

    /** Returns an unmodifiable view of the full price map. */
    public Map<Material, Double> getPrices() {
        return Collections.unmodifiableMap(prices);
    }

    // -------------------------------------------------------------------------
    // Price table  — update / extend here freely
    // -------------------------------------------------------------------------

    private void registerAll() {

        // ── Logs ────────────────────────────────────────────────────────────
        prices.put(Material.OAK_LOG,       1.0);
        prices.put(Material.SPRUCE_LOG,    1.0);
        prices.put(Material.BIRCH_LOG,     1.0);
        prices.put(Material.JUNGLE_LOG,    1.5);
        prices.put(Material.ACACIA_LOG,    1.0);
        prices.put(Material.DARK_OAK_LOG,  1.5);
        prices.put(Material.MANGROVE_LOG,  1.0);
        prices.put(Material.CHERRY_LOG,    1.5);
        prices.put(Material.BAMBOO_BLOCK,  1.0);
        prices.put(Material.BAMBOO,        0.2);

        // ── Stone & minerals ────────────────────────────────────────────────
        prices.put(Material.COBBLESTONE,   0.5);
        prices.put(Material.STONE,         1.5);
        prices.put(Material.GRANITE,       1.8);
        prices.put(Material.DIORITE,       1.8);
        prices.put(Material.ANDESITE,      1.8);
        prices.put(Material.GRAVEL,        0.5);
        prices.put(Material.SAND,          0.5);
        prices.put(Material.RED_SAND,      0.5);
        prices.put(Material.FLINT,         1.0);
        prices.put(Material.CLAY_BALL,     1.0);
        prices.put(Material.CLAY,          3.0);

        // ── Ores & ingots ────────────────────────────────────────────────
        prices.put(Material.COAL,          5.0);
        prices.put(Material.COAL_ORE,      4.0);
        prices.put(Material.DEEPSLATE_COAL_ORE, 5.0);
        prices.put(Material.RAW_IRON,      3.0);
        prices.put(Material.IRON_INGOT,    5.0);
        prices.put(Material.IRON_ORE,      4.0);
        prices.put(Material.DEEPSLATE_IRON_ORE, 7.0);
        prices.put(Material.RAW_COPPER,    3.0);
        prices.put(Material.COPPER_INGOT,  6.0);
        prices.put(Material.COPPER_ORE,    4.0);
        prices.put(Material.DEEPSLATE_COPPER_ORE, 4.0);
        prices.put(Material.RAW_GOLD,      10.0);
        prices.put(Material.GOLD_INGOT,    15.0);
        prices.put(Material.GOLD_ORE,      10.0);
        prices.put(Material.DEEPSLATE_GOLD_ORE, 10.0);
        prices.put(Material.REDSTONE,      2.0);
        prices.put(Material.REDSTONE_ORE,  1.0);
        prices.put(Material.DEEPSLATE_REDSTONE_ORE, 1.0);
        prices.put(Material.LAPIS_LAZULI,  3.0);
        prices.put(Material.LAPIS_ORE,     1.0);
        prices.put(Material.DEEPSLATE_LAPIS_ORE, 1.0);
        prices.put(Material.DIAMOND,       150.0);
        prices.put(Material.DIAMOND_ORE,   120.0);
        prices.put(Material.DEEPSLATE_DIAMOND_ORE, 140.0);
        prices.put(Material.EMERALD,       50.0);
        prices.put(Material.EMERALD_ORE,   100.0);
        prices.put(Material.DEEPSLATE_EMERALD_ORE, 120.0);
        prices.put(Material.NETHERITE_SCRAP, 1000.0);
        prices.put(Material.NETHERITE_INGOT, 5000.0);
        prices.put(Material.ANCIENT_DEBRIS, 800.0);
        prices.put(Material.QUARTZ,        2.0);
        prices.put(Material.NETHER_QUARTZ_ORE, 4.0);
        prices.put(Material.GLOWSTONE_DUST, 8.0);
        prices.put(Material.GLOWSTONE,     40.0);
        prices.put(Material.NETHER_GOLD_ORE, 10.0);
        prices.put(Material.IRON_BLOCK,    45.0);
        prices.put(Material.COAL_BLOCK,    45.0);
        prices.put(Material.GOLD_BLOCK,    90.0);
        prices.put(Material.DIAMOND_BLOCK, 1350.0);

        // ── Crops & farm produce ─────────────────────────────────────────
        prices.put(Material.WHEAT,         2.0);
        prices.put(Material.WHEAT_SEEDS,   0.5);
        prices.put(Material.CARROT,        2.0);
        prices.put(Material.POTATO,        2.0);
        prices.put(Material.BEETROOT,      2.5);
        prices.put(Material.BEETROOT_SEEDS, 1.0);
        prices.put(Material.MELON_SLICE,   1.5);
        prices.put(Material.MELON,         8.0);
        prices.put(Material.PUMPKIN,       8.0);
        prices.put(Material.SUGAR_CANE,    0.2);
        prices.put(Material.CACTUS,        1.5);
        prices.put(Material.SWEET_BERRIES, 2.0);
        prices.put(Material.GLOW_BERRIES,  3.0);
        prices.put(Material.COCOA_BEANS,   3.0);
        prices.put(Material.INK_SAC,       3.0);
        prices.put(Material.GLOW_INK_SAC,  8.0);

        // ── Animal drops ─────────────────────────────────────────────────
        prices.put(Material.LEATHER,       4.0);
        prices.put(Material.FEATHER,       1.5);
        prices.put(Material.BONE,          1.0);
        prices.put(Material.BONE_MEAL,     1.0);
        prices.put(Material.STRING,        1.5);
        prices.put(Material.SPIDER_EYE,    1.0);
        prices.put(Material.GUNPOWDER,     2.0);
        prices.put(Material.SLIME_BALL,    10.0);
        prices.put(Material.BLAZE_ROD,     30.0);
        prices.put(Material.BLAZE_POWDER,  10.0);
        prices.put(Material.MAGMA_CREAM,   10.0);
        prices.put(Material.GHAST_TEAR,    50.0);
        prices.put(Material.ENDER_PEARL,   10.0);
        prices.put(Material.EGG,           1.0);
        prices.put(Material.HONEYCOMB,     10.0);
        prices.put(Material.HONEY_BOTTLE,  10.0);
        prices.put(Material.PHANTOM_MEMBRANE, 10.0);
        prices.put(Material.RABBIT_HIDE,   4.0);
        prices.put(Material.RABBIT_FOOT,   100.0);
        prices.put(Material.TURTLE_EGG,    50.0);
        prices.put(Material.SCUTE,         20.0);
        prices.put(Material.PRISMARINE_SHARD, 50.0);
        prices.put(Material.PRISMARINE_CRYSTALS, 50.0);

        // ── Fish ─────────────────────────────────────────────────────────
        prices.put(Material.COD,           10.0);
        prices.put(Material.SALMON,        10.0);
        prices.put(Material.TROPICAL_FISH, 40.0);
        prices.put(Material.PUFFERFISH,    15.0);

        // ── Nether & End resources ────────────────────────────────────────
        prices.put(Material.NETHER_BRICK,  1.0);
        prices.put(Material.NETHER_WART,   2.0);
        prices.put(Material.SOUL_SAND,     2.0);
        prices.put(Material.SOUL_SOIL,     2.0);
        prices.put(Material.NETHERRACK,    0.5);
        prices.put(Material.BASALT,        1.0);
        prices.put(Material.BLACKSTONE,    1.5);
        prices.put(Material.CRYING_OBSIDIAN, 15.0);
        prices.put(Material.OBSIDIAN,      10.0);
        prices.put(Material.END_STONE,     3.0);
        prices.put(Material.SHULKER_SHELL, 80.0);

    }
}
