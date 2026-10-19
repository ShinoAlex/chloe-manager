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
        add(c, Material.STONE,              100);
        add(c, Material.COBBLESTONE,        100.0);
        add(c, Material.GRANITE,            200.0);
        add(c, Material.DIORITE,            200.0);
        add(c, Material.ANDESITE,           200.0);
        add(c, Material.SAND,               100.0);
        add(c, Material.RED_SAND,           100.5);
        add(c, Material.GRAVEL,             100.0);
        add(c, Material.CLAY,               50.0);
        add(c, Material.DIRT,               50.0);
        add(c, Material.GRASS_BLOCK,        100.0);
        add(c, Material.OBSIDIAN,           2000.0);
        add(c, Material.CRYING_OBSIDIAN,    1500.0);
        add(c, Material.NETHERRACK,         10.0);
        add(c, Material.SOUL_SAND,          400.0);
        add(c, Material.SOUL_SOIL,          400.0);
        add(c, Material.BASALT,             20.0);
        add(c, Material.BLACKSTONE,         200.5);
        add(c, Material.END_STONE,          600.0);
        //add(c, Material.PURPUR_BLOCK,       8.0);
        add(c, Material.GLOWSTONE,          500.0);
        add(c, Material.ICE,                300.0);
        add(c, Material.PACKED_ICE,         500.0);
        add(c, Material.BLUE_ICE,           1500.0);
        add(c, Material.BRICKS,             400.0);
        add(c, Material.NETHER_BRICKS,      500.0);
        add(c, Material.QUARTZ_BLOCK,       1000.0);
        //add(c, Material.PRISMARINE,         12.0);
        add(c, Material.SEA_LANTERN,        500.0);
        add(c, Material.MAGMA_BLOCK,        300.0);
    }

    private void registerWood() {
        ShopCategory c = ShopCategory.WOOD;
        add(c, Material.OAK_LOG,        40.0);
        add(c, Material.OAK_PLANKS,     10.5);
        add(c, Material.OAK_SLAB,       10.0);
        add(c, Material.OAK_STAIRS,     20.0);
        add(c, Material.SPRUCE_LOG,     40.0);
        add(c, Material.SPRUCE_PLANKS,  10.5);
        add(c, Material.SPRUCE_SLAB,    10.0);
        add(c, Material.SPRUCE_STAIRS,  20.0);
        add(c, Material.BIRCH_LOG,      40.0);
        add(c, Material.BIRCH_PLANKS,   10.5);
        add(c, Material.BIRCH_SLAB,     10.0);
        add(c, Material.BIRCH_STAIRS,   20.0);
        add(c, Material.JUNGLE_LOG,     50.0);
        add(c, Material.JUNGLE_PLANKS,  20.0);
        add(c, Material.JUNGLE_SLAB,    10.5);
        add(c, Material.JUNGLE_STAIRS,  20.5);
        add(c, Material.ACACIA_LOG,     40.0);
        add(c, Material.ACACIA_PLANKS,  10.5);
        add(c, Material.ACACIA_SLAB,    10.0);
        add(c, Material.ACACIA_STAIRS,  20.0);
        add(c, Material.DARK_OAK_LOG,   50.0);
        add(c, Material.DARK_OAK_PLANKS, 20.0);
        add(c, Material.DARK_OAK_SLAB,  10.5);
        add(c, Material.DARK_OAK_STAIRS, 20.5);
        add(c, Material.MANGROVE_LOG,   60.0);
        add(c, Material.MANGROVE_PLANKS, 20.5);
        add(c, Material.CHERRY_LOG,     70.0);
        add(c, Material.CHERRY_PLANKS,  30.0);
        add(c, Material.BAMBOO,         10.0);
        add(c, Material.BAMBOO_BLOCK,   20.0);
    }

    private void registerWool() {
        ShopCategory c = ShopCategory.WOOL;
        add(c, Material.WHITE_WOOL,      50.0);
        add(c, Material.ORANGE_WOOL,     50.0);
        add(c, Material.MAGENTA_WOOL,    50.0);
        add(c, Material.LIGHT_BLUE_WOOL, 50.0);
        add(c, Material.YELLOW_WOOL,     50.0);
        add(c, Material.LIME_WOOL,       50.0);
        add(c, Material.PINK_WOOL,       50.0);
        add(c, Material.GRAY_WOOL,       50.0);
        add(c, Material.LIGHT_GRAY_WOOL, 50.0);
        add(c, Material.CYAN_WOOL,       50.0);
        add(c, Material.PURPLE_WOOL,     50.0);
        add(c, Material.BLUE_WOOL,       50.0);
        add(c, Material.BROWN_WOOL,      50.0);
        add(c, Material.GREEN_WOOL,      50.0);
        add(c, Material.RED_WOOL,        50.0);
        add(c, Material.BLACK_WOOL,      50.0);
        // Carpets
        add(c, Material.WHITE_CARPET,    20.0);
        add(c, Material.ORANGE_CARPET,   20.0);
        add(c, Material.MAGENTA_CARPET,  20.0);
        add(c, Material.LIGHT_BLUE_CARPET, 20.0);
        add(c, Material.YELLOW_CARPET,   20.0);
        add(c, Material.LIME_CARPET,     20.0);
        add(c, Material.PINK_CARPET,     20.0);
        add(c, Material.GRAY_CARPET,     20.0);
        add(c, Material.LIGHT_GRAY_CARPET, 20.0);
        add(c, Material.CYAN_CARPET,     20.0);
        add(c, Material.PURPLE_CARPET,   20.0);
        add(c, Material.BLUE_CARPET,     20.0);
        add(c, Material.BROWN_CARPET,    20.0);
        add(c, Material.GREEN_CARPET,    20.0);
        add(c, Material.RED_CARPET,      20.0);
        add(c, Material.BLACK_CARPET,    20.0);
    }

    private void registerSpecial() {
        ShopCategory c = ShopCategory.SPECIAL;
        add(c, Material.DIAMOND,             3000.0);
        add(c, Material.EMERALD,             700.0);
        //add(c, Material.NETHERITE_INGOT,    1000.0);
        add(c, Material.WITHER_SKELETON_SKULL, 9000.0);
        add(c, Material.TOTEM_OF_UNDYING,   1500.0);
        //add(c, Material.ELYTRA,             2000.0);
        //add(c, Material.NETHER_STAR,        2500.0);
        //add(c, Material.BEACON,             5000.0);
        //add(c, Material.SHULKER_SHELL,       200.0);
       //add(c, Material.DRAGON_EGG,        10000.0);
        add(c, Material.ENCHANTED_GOLDEN_APPLE, 50000.0);
        add(c, Material.EXPERIENCE_BOTTLE,    1000.0);
        //add(c, Material.END_CRYSTAL,          150.0);
        //add(c, Material.TRIDENT,              600.0);
    }
}
