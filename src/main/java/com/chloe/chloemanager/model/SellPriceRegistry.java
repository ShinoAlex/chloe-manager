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
        prices.put(Material.OAK_LOG,       2.0);
        prices.put(Material.SPRUCE_LOG,    2.0);
        prices.put(Material.BIRCH_LOG,     2.0);
        prices.put(Material.JUNGLE_LOG,    2.5);
        prices.put(Material.ACACIA_LOG,    2.0);
        prices.put(Material.DARK_OAK_LOG,  2.5);
        prices.put(Material.MANGROVE_LOG,  3.0);
        prices.put(Material.CHERRY_LOG,    3.5);
        prices.put(Material.BAMBOO_BLOCK,  1.0);
        prices.put(Material.BAMBOO,        0.5);

        // ── Stone & minerals ────────────────────────────────────────────────
        prices.put(Material.COBBLESTONE,   0.5);
        prices.put(Material.STONE,         0.5);
        prices.put(Material.GRANITE,       0.8);
        prices.put(Material.DIORITE,       0.8);
        prices.put(Material.ANDESITE,      0.8);
        prices.put(Material.GRAVEL,        0.5);
        prices.put(Material.SAND,          0.5);
        prices.put(Material.RED_SAND,      0.8);
        prices.put(Material.FLINT,         1.0);
        prices.put(Material.CLAY_BALL,     1.0);
        prices.put(Material.CLAY,          3.0);

        // ── Ores & ingots ────────────────────────────────────────────────
        prices.put(Material.COAL,          5.0);
        prices.put(Material.COAL_ORE,      4.0);
        prices.put(Material.DEEPSLATE_COAL_ORE, 5.0);
        prices.put(Material.RAW_IRON,      10.0);
        prices.put(Material.IRON_INGOT,    15.0);
        prices.put(Material.IRON_ORE,      8.0);
        prices.put(Material.DEEPSLATE_IRON_ORE, 10.0);
        prices.put(Material.RAW_COPPER,    6.0);
        prices.put(Material.COPPER_INGOT,  8.0);
        prices.put(Material.COPPER_ORE,    5.0);
        prices.put(Material.DEEPSLATE_COPPER_ORE, 6.0);
        prices.put(Material.RAW_GOLD,      20.0);
        prices.put(Material.GOLD_INGOT,    30.0);
        prices.put(Material.GOLD_ORE,      18.0);
        prices.put(Material.DEEPSLATE_GOLD_ORE, 22.0);
        prices.put(Material.REDSTONE,      5.0);
        prices.put(Material.REDSTONE_ORE,  8.0);
        prices.put(Material.DEEPSLATE_REDSTONE_ORE, 10.0);
        prices.put(Material.LAPIS_LAZULI,  8.0);
        prices.put(Material.LAPIS_ORE,     12.0);
        prices.put(Material.DEEPSLATE_LAPIS_ORE, 15.0);
        prices.put(Material.DIAMOND,       150.0);
        prices.put(Material.DIAMOND_ORE,   120.0);
        prices.put(Material.DEEPSLATE_DIAMOND_ORE, 140.0);
        prices.put(Material.EMERALD,       200.0);
        prices.put(Material.EMERALD_ORE,   160.0);
        prices.put(Material.DEEPSLATE_EMERALD_ORE, 180.0);
        prices.put(Material.NETHERITE_SCRAP, 300.0);
        prices.put(Material.NETHERITE_INGOT, 500.0);
        prices.put(Material.ANCIENT_DEBRIS, 350.0);
        prices.put(Material.QUARTZ,        5.0);
        prices.put(Material.NETHER_QUARTZ_ORE, 8.0);
        prices.put(Material.GLOWSTONE_DUST, 8.0);
        prices.put(Material.GLOWSTONE,     25.0);
        prices.put(Material.NETHER_GOLD_ORE, 12.0);

        // ── Crops & farm produce ─────────────────────────────────────────
        prices.put(Material.WHEAT,         2.0);
        prices.put(Material.WHEAT_SEEDS,   1.0);
        prices.put(Material.CARROT,        2.0);
        prices.put(Material.POTATO,        2.0);
        prices.put(Material.BEETROOT,      2.5);
        prices.put(Material.BEETROOT_SEEDS, 1.0);
        prices.put(Material.MELON_SLICE,   1.5);
        prices.put(Material.MELON,         10.0);
        prices.put(Material.PUMPKIN,       8.0);
        prices.put(Material.SUGAR_CANE,    1.5);
        prices.put(Material.CACTUS,        1.5);
        prices.put(Material.SWEET_BERRIES, 2.0);
        prices.put(Material.GLOW_BERRIES,  3.0);
        prices.put(Material.COCOA_BEANS,   3.0);
        prices.put(Material.INK_SAC,       3.0);
        prices.put(Material.GLOW_INK_SAC,  8.0);

        // ── Animal drops ─────────────────────────────────────────────────
        prices.put(Material.LEATHER,       5.0);
        prices.put(Material.FEATHER,       1.5);
        prices.put(Material.BONE,          2.0);
        prices.put(Material.BONE_MEAL,     1.0);
        prices.put(Material.STRING,        1.5);
        prices.put(Material.SPIDER_EYE,    4.0);
        prices.put(Material.GUNPOWDER,     6.0);
        prices.put(Material.SLIME_BALL,    5.0);
        prices.put(Material.BLAZE_ROD,     20.0);
        prices.put(Material.BLAZE_POWDER,  12.0);
        prices.put(Material.MAGMA_CREAM,   10.0);
        prices.put(Material.GHAST_TEAR,    30.0);
        prices.put(Material.ENDER_PEARL,   25.0);
        prices.put(Material.EGG,           1.0);
        prices.put(Material.HONEYCOMB,     5.0);
        prices.put(Material.HONEY_BOTTLE,  8.0);
        prices.put(Material.PHANTOM_MEMBRANE, 20.0);
        prices.put(Material.RABBIT_HIDE,   3.0);
        prices.put(Material.RABBIT_FOOT,   15.0);
        prices.put(Material.TURTLE_EGG,    25.0);
        prices.put(Material.SCUTE,         20.0);
        prices.put(Material.PRISMARINE_SHARD, 8.0);
        prices.put(Material.PRISMARINE_CRYSTALS, 10.0);

        // ── Fish ─────────────────────────────────────────────────────────
        prices.put(Material.COD,           5.0);
        prices.put(Material.SALMON,        8.0);
        prices.put(Material.TROPICAL_FISH, 12.0);
        prices.put(Material.PUFFERFISH,    10.0);

        // ── Nether & End resources ────────────────────────────────────────
        prices.put(Material.NETHER_BRICK,  3.0);
        prices.put(Material.NETHER_WART,   5.0);
        prices.put(Material.SOUL_SAND,     2.0);
        prices.put(Material.SOUL_SOIL,     2.0);
        prices.put(Material.NETHERRACK,    0.5);
        prices.put(Material.BASALT,        1.0);
        prices.put(Material.BLACKSTONE,    1.5);
        prices.put(Material.CRYING_OBSIDIAN, 15.0);
        prices.put(Material.OBSIDIAN,      10.0);
        prices.put(Material.CHORUS_FRUIT,  5.0);
        prices.put(Material.CHORUS_FLOWER, 8.0);
        prices.put(Material.PURPUR_BLOCK,  5.0);
        prices.put(Material.END_STONE,     3.0);
        prices.put(Material.SHULKER_SHELL, 100.0);

        // ── Dyes & misc ──────────────────────────────────────────────────
        prices.put(Material.WHITE_DYE,     2.0);
        prices.put(Material.ORANGE_DYE,    2.0);
        prices.put(Material.MAGENTA_DYE,   2.0);
        prices.put(Material.LIGHT_BLUE_DYE, 2.0);
        prices.put(Material.YELLOW_DYE,    2.0);
        prices.put(Material.LIME_DYE,      2.0);
        prices.put(Material.PINK_DYE,      2.0);
        prices.put(Material.GRAY_DYE,      2.0);
        prices.put(Material.LIGHT_GRAY_DYE, 2.0);
        prices.put(Material.CYAN_DYE,      2.0);
        prices.put(Material.PURPLE_DYE,    2.0);
        prices.put(Material.BLUE_DYE,      2.0);
        prices.put(Material.BROWN_DYE,     2.0);
        prices.put(Material.GREEN_DYE,     2.0);
        prices.put(Material.RED_DYE,       2.0);
        prices.put(Material.BLACK_DYE,     2.0);

        // ── Wool & dyed materials ─────────────────────────────────────────
        prices.put(Material.WHITE_WOOL,    3.0);
        prices.put(Material.ORANGE_WOOL,   3.0);
        prices.put(Material.MAGENTA_WOOL,  3.0);
        prices.put(Material.LIGHT_BLUE_WOOL, 3.0);
        prices.put(Material.YELLOW_WOOL,   3.0);
        prices.put(Material.LIME_WOOL,     3.0);
        prices.put(Material.PINK_WOOL,     3.0);
        prices.put(Material.GRAY_WOOL,     3.0);
        prices.put(Material.LIGHT_GRAY_WOOL, 3.0);
        prices.put(Material.CYAN_WOOL,     3.0);
        prices.put(Material.PURPLE_WOOL,   3.0);
        prices.put(Material.BLUE_WOOL,     3.0);
        prices.put(Material.BROWN_WOOL,    3.0);
        prices.put(Material.GREEN_WOOL,    3.0);
        prices.put(Material.RED_WOOL,      3.0);
        prices.put(Material.BLACK_WOOL,    3.0);
    }
}
