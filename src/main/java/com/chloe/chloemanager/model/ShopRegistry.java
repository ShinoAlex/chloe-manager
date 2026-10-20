package com.chloe.chloemanager.model;

import org.bukkit.Material;

import java.util.*;

/**
 * MODEL — ShopRegistry
 *
 * Defines every item available for purchase in the shop, its buy price,
 * and which category it belongs to.
 *
 * To add new items: add an entry inside the relevant registerXxx() method.
 * To add a new category: create a new ShopCategory enum value, add a
 * registerXxx() call in the constructor, and add an icon/label in ShopGUI.
 */
public class ShopRegistry {

    // -------------------------------------------------------------------------
    // Category enum
    // -------------------------------------------------------------------------

    public enum ShopCategory {
        BLOCKS       ("⬛ Blocks",        Material.STONE),
        WOOD         ("🌲 Wood",           Material.OAK_LOG),
        WOOL         ("🐑 Wool",           Material.WHITE_WOOL),
        SPECIAL      ("⭐ Special Items",  Material.DIAMOND);

        private final String displayName;
        private final Material icon;

        ShopCategory(String displayName, Material icon) {
            this.displayName = displayName;
            this.icon        = icon;
        }

        public String getDisplayName() { return displayName; }
        public Material getIcon()      { return icon; }
    }

    // -------------------------------------------------------------------------
    // ShopEntry — one purchasable item
    // -------------------------------------------------------------------------

    public record ShopEntry(Material material, double buyPrice, int stackSize) {
        /** Display-friendly name: OAK_LOG → "Oak Log" */
        public String displayName() {
            String raw = material.name().replace('_', ' ').toLowerCase();
            StringBuilder sb = new StringBuilder();
            for (String word : raw.split(" ")) {
                if (!word.isEmpty())
                    sb.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1)).append(' ');
            }
            return sb.toString().trim();
        }
    }

    // -------------------------------------------------------------------------
    // Internal storage
    // -------------------------------------------------------------------------

    /** Ordered list of entries per category. */
    private final Map<ShopCategory, List<ShopEntry>> catalog = new LinkedHashMap<>();

    public ShopRegistry() {
        for (ShopCategory cat : ShopCategory.values()) {
            catalog.put(cat, new ArrayList<>());
        }
        registerBlocks();
        registerWood();
        registerWool();
        registerSpecial();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Returns all entries for a given category (unmodifiable). */
    public List<ShopEntry> getEntries(ShopCategory category) {
        return Collections.unmodifiableList(catalog.get(category));
    }

    /** Returns all categories that have at least one entry. */
    public List<ShopCategory> getCategories() {
        return Arrays.stream(ShopCategory.values())
                .filter(c -> !catalog.get(c).isEmpty())
                .toList();
    }

    // -------------------------------------------------------------------------
    // Price tables — edit freely
    // -------------------------------------------------------------------------

    private void add(ShopCategory cat, Material mat, double price) {
        add(cat, mat, price, 1);
    }

    private void add(ShopCategory cat, Material mat, double price, int stackSize) {
        catalog.get(cat).add(new ShopEntry(mat, price, stackSize));
    }

    private void registerBlocks() {
        ShopCategory c = ShopCategory.BLOCKS;

        // ── Normal blocks (1.5×–2× sell price) ──────────────────────────────
        add(c, Material.STONE,              3.0);     // sell 1.5 ×2
        add(c, Material.COBBLESTONE,        1.0);     // sell 0.5 ×2
        add(c, Material.GRANITE,            3.6);     // sell 1.8 ×2
        add(c, Material.DIORITE,            3.6);     // sell 1.8 ×2
        add(c, Material.ANDESITE,           3.6);     // sell 1.8 ×2
        add(c, Material.SAND,               1.0);     // sell 0.5 ×2
        add(c, Material.RED_SAND,           1.0);     // sell 0.5 ×2
        add(c, Material.GRAVEL,             1.0);     // sell 0.5 ×2
        add(c, Material.CLAY,               6.0);     // sell 3.0 ×2
        add(c, Material.DIRT,              1.0);     // ~sell 0.5 ×2
        add(c, Material.GRASS_BLOCK,       2.0);     // ~sell 1.0 ×2
        add(c, Material.OBSIDIAN,          20.0);    // sell 10.0 ×2
        add(c, Material.CRYING_OBSIDIAN,   30.0);     // sell 15.0 ×2
        add(c, Material.NETHERRACK,        1.0);      // sell 0.5 ×2
        add(c, Material.SOUL_SAND,         4.0);      // sell 2.0 ×2
        add(c, Material.SOUL_SOIL,         4.0);      // sell 2.0 ×2
        add(c, Material.BASALT,            2.0);      // sell 1.0 ×2
        add(c, Material.BLACKSTONE,        3.0);      // sell 1.5 ×2
        add(c, Material.END_STONE,         6.0);      // sell 3.0 ×2
        add(c, Material.GLOWSTONE,         80.0);     // sell 40.0 ×2

        // ── Decorative blocks (3×–4× sell price) ────────────────────────────
        add(c, Material.ICE,                8.0);     // ~sell 2.0 ×4
        add(c, Material.PACKED_ICE,         16.0);     // ~sell 4.0 ×4
        add(c, Material.BLUE_ICE,           24.0);     // ~sell 6.0 ×4
        add(c, Material.BRICKS,             16.0);     // ~sell 4.0 ×4
        add(c, Material.NETHER_BRICKS,      16.0);     // ~sell 4.0 ×4
        add(c, Material.QUARTZ_BLOCK,       24.0);     // quartz sell 2.0 ×4/block = 8 ×3
        add(c, Material.SEA_LANTERN,        20.0);     // ~sell 5.0 ×4
        add(c, Material.MAGMA_BLOCK,        16.0);     // ~sell 4.0 ×4
    }

    private void registerWood() {
        ShopCategory c = ShopCategory.WOOD;

        // ── Normal wood (1.5×–2× sell price) ────────────────────────────────
        add(c, Material.OAK_LOG,         2.0);     // sell 1.0 ×2
        add(c, Material.OAK_PLANKS,      0.5);      // sell ~0.25 ×2
        add(c, Material.OAK_SLAB,        0.3);
        add(c, Material.OAK_STAIRS,      1.0);
        add(c, Material.SPRUCE_LOG,      2.0);     // sell 1.0 ×2
        add(c, Material.SPRUCE_PLANKS,   0.5);
        add(c, Material.SPRUCE_SLAB,    0.3);
        add(c, Material.SPRUCE_STAIRS,   1.0);
        add(c, Material.BIRCH_LOG,       2.0);     // sell 1.0 ×2
        add(c, Material.BIRCH_PLANKS,    0.5);
        add(c, Material.BIRCH_SLAB,     0.3);
        add(c, Material.BIRCH_STAIRS,    1.0);
        add(c, Material.JUNGLE_LOG,      3.0);     // sell 1.5 ×2
        add(c, Material.JUNGLE_PLANKS,   0.75);
        add(c, Material.JUNGLE_SLAB,     0.4);
        add(c, Material.JUNGLE_STAIRS,   1.5);
        add(c, Material.ACACIA_LOG,      2.0);     // sell 1.0 ×2
        add(c, Material.ACACIA_PLANKS,   0.5);
        add(c, Material.ACACIA_SLAB,    0.3);
        add(c, Material.ACACIA_STAIRS,   1.0);
        add(c, Material.DARK_OAK_LOG,    3.0);     // sell 1.5 ×2
        add(c, Material.DARK_OAK_PLANKS, 0.75);
        add(c, Material.DARK_OAK_SLAB,   0.4);
        add(c, Material.DARK_OAK_STAIRS, 1.5);
        add(c, Material.MANGROVE_LOG,    2.0);     // sell 1.0 ×2
        add(c, Material.MANGROVE_PLANKS, 0.5);
        add(c, Material.BAMBOO,          0.4);      // sell 0.2 ×2
        add(c, Material.BAMBOO_BLOCK,    2.0);     // sell 1.0 ×2

        // ── Decorative wood (3×–4× sell price) ──────────────────────────────
        add(c, Material.CHERRY_LOG,      6.0);     // sell 1.5 ×4
        add(c, Material.CHERRY_PLANKS,   1.5);
    }

    private void registerWool() {
        ShopCategory c = ShopCategory.WOOL;
        // Wool has no direct sell price — reasonable fixed prices
        add(c, Material.WHITE_WOOL,      10.0);
        add(c, Material.ORANGE_WOOL,     10.0);
        add(c, Material.MAGENTA_WOOL,    10.0);
        add(c, Material.LIGHT_BLUE_WOOL, 10.0);
        add(c, Material.YELLOW_WOOL,     10.0);
        add(c, Material.LIME_WOOL,       10.0);
        add(c, Material.PINK_WOOL,       10.0);
        add(c, Material.GRAY_WOOL,       10.0);
        add(c, Material.LIGHT_GRAY_WOOL, 10.0);
        add(c, Material.CYAN_WOOL,       10.0);
        add(c, Material.PURPLE_WOOL,     10.0);
        add(c, Material.BLUE_WOOL,       10.0);
        add(c, Material.BROWN_WOOL,      10.0);
        add(c, Material.GREEN_WOOL,      10.0);
        add(c, Material.RED_WOOL,        10.0);
        add(c, Material.BLACK_WOOL,      10.0);
        // Carpets
        add(c, Material.WHITE_CARPET,    5.0);
        add(c, Material.ORANGE_CARPET,   5.0);
        add(c, Material.MAGENTA_CARPET,  5.0);
        add(c, Material.LIGHT_BLUE_CARPET, 5.0);
        add(c, Material.YELLOW_CARPET,   5.0);
        add(c, Material.LIME_CARPET,     5.0);
        add(c, Material.PINK_CARPET,     5.0);
        add(c, Material.GRAY_CARPET,     5.0);
        add(c, Material.LIGHT_GRAY_CARPET, 5.0);
        add(c, Material.CYAN_CARPET,     5.0);
        add(c, Material.PURPLE_CARPET,   5.0);
        add(c, Material.BLUE_CARPET,     5.0);
        add(c, Material.BROWN_CARPET,    5.0);
        add(c, Material.GREEN_CARPET,    5.0);
        add(c, Material.RED_CARPET,      5.0);
        add(c, Material.BLACK_CARPET,    5.0);
    }

    private void registerSpecial() {
        ShopCategory c = ShopCategory.SPECIAL;
        // Buy price = 2× sell price where applicable
        add(c, Material.DIAMOND,             500.0);   // sell 150.0 ×2
        add(c, Material.EMERALD,             200.0);   // sell 50.0 ×2
        //add(c, Material.NETHERITE_INGOT,    1000.0);
        add(c, Material.WITHER_SKELETON_SKULL, 10000.0);
        add(c, Material.TOTEM_OF_UNDYING,   2500.0);
        //add(c, Material.ELYTRA,             2000.0);
        //add(c, Material.NETHER_STAR,        2500.0);
        //add(c, Material.BEACON,             5000.0);
        //add(c, Material.SHULKER_SHELL,       200.0);
       //add(c, Material.DRAGON_EGG,        10000.0);
        add(c, Material.ENCHANTED_GOLDEN_APPLE, 50000.0);
        add(c, Material.EXPERIENCE_BOTTLE,    1500.0);
        //add(c, Material.END_CRYSTAL,          150.0);
        //add(c, Material.TRIDENT,              600.0);
    }
}
