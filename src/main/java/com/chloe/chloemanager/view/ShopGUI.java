package com.chloe.chloemanager.view;

import com.chloe.chloemanager.model.ShopRegistry;
import com.chloe.chloemanager.model.ShopRegistry.ShopCategory;
import com.chloe.chloemanager.model.ShopRegistry.ShopEntry;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

/**
 * VIEW — ShopGUI
 *
 * Builds two types of inventory:
 *
 *  1. Main menu  — 6 rows, shows one button per category + decorative filler.
 *  2. Category page — 6 rows, shows up to ITEMS_PER_PAGE items + nav controls.
 *
 * ── Category page layout (54 slots, rows 0-5) ──────────────────────────────
 *   Rows 0-4 (slots 0-44):  item grid — 45 slots, but only ITEMS_PER_PAGE used
 *   Row 5 (slots 45-53):
 *     45  — ◀ Prev page  (gray pane when no prev)
 *     46  — filler
 *     47  — filler
 *     48  — 🏠 Back to categories
 *     49  — filler (balance display item)
 *     50  — filler
 *     51  — filler
 *     52  — filler
 *     53  — ▶ Next page  (gray pane when no next)
 */
public class ShopGUI {

    // ── Slot constants ───────────────────────────────────────────────────────
    public static final int ITEMS_PER_PAGE = 45;   // rows 0-4, all 45 slots
    public static final int SLOT_PREV      = 45;
    public static final int SLOT_BACK      = 48;
    public static final int SLOT_BALANCE   = 49;
    public static final int SLOT_NEXT      = 53;

    // Title markers — used in click handler to identify which GUI is open
    public static final String TITLE_MAIN   = ChatColor.DARK_GRAY + "✦ " + ChatColor.GOLD + "Shop" + ChatColor.DARK_GRAY + " ✦";
    public static final String TITLE_MARKER = "§8⬛ §6";   // prefix for category pages

    private static final DecimalFormat FMT = new DecimalFormat("#,##0.00");

    private final ShopRegistry registry;

    public ShopGUI(ShopRegistry registry) {
        this.registry = registry;
    }

    // =========================================================================
    // Main category menu
    // =========================================================================

    /**
     * Builds the category selection menu.
     * Categories are placed in the center rows; rest is decorative filler.
     */
    public Inventory buildMainMenu() {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_MAIN);

        // Fill everything with dark pane first
        ItemStack filler = makeFiller(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        List<ShopCategory> cats = registry.getCategories();

        // Place category buttons — centred in a row starting at slot 20
        int[] slots = categorySlots(cats.size());
        for (int i = 0; i < cats.size(); i++) {
            inv.setItem(slots[i], makeCategoryButton(cats.get(i)));
        }

        return inv;
    }

    // =========================================================================
    // Category item page
    // =========================================================================

    /**
     * Builds a single page of items for the given category.
     *
     * @param category  The category to display.
     * @param page      0-based page index.
     * @param balance   Player's current balance (shown in nav row).
     */
    public Inventory buildCategoryPage(ShopCategory category, int page, double balance) {
        String title = TITLE_MARKER + category.getDisplayName();
        Inventory inv = Bukkit.createInventory(null, 54, title);

        List<ShopEntry> entries = registry.getEntries(category);
        int totalPages = totalPages(entries.size());
        int start      = page * ITEMS_PER_PAGE;
        int end        = Math.min(start + ITEMS_PER_PAGE, entries.size());

        // ── Item grid ────────────────────────────────────────────────────────
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, makeShopItem(entries.get(i)));
        }

        // ── Navigation row (row 5) ───────────────────────────────────────────
        ItemStack navFiller = makeFiller(Material.GRAY_STAINED_GLASS_PANE);
        for (int s = 45; s < 54; s++) inv.setItem(s, navFiller);

        // Prev page
        if (page > 0) {
            inv.setItem(SLOT_PREV, makeNavButton(Material.ARROW,
                    ChatColor.YELLOW + "◀ Previous Page",
                    List.of(ChatColor.GRAY + "Page " + page + " of " + totalPages)));
        } else {
            inv.setItem(SLOT_PREV, makeFiller(Material.GRAY_STAINED_GLASS_PANE));
        }

        // Back to categories
        inv.setItem(SLOT_BACK, makeNavButton(Material.BARRIER,
                ChatColor.RED + "⬅ Back to Categories",
                List.of(ChatColor.GRAY + "Return to the main shop menu")));

        // Balance display
        inv.setItem(SLOT_BALANCE, makeBalanceDisplay(balance));

        // Next page
        if (page < totalPages - 1) {
            inv.setItem(SLOT_NEXT, makeNavButton(Material.ARROW,
                    ChatColor.YELLOW + "Next Page ▶",
                    List.of(ChatColor.GRAY + "Page " + (page + 2) + " of " + totalPages)));
        } else {
            inv.setItem(SLOT_NEXT, makeFiller(Material.GRAY_STAINED_GLASS_PANE));
        }

        return inv;
    }

    /**
     * Refreshes only the balance display item in an open category page.
     * Call this after every purchase so the player sees their updated balance.
     */
    public void refreshBalance(Inventory inv, double balance) {
        // Safety guard — only update if this is a 54-slot shop inventory
        if (inv.getSize() < SLOT_BALANCE + 1) return;
        inv.setItem(SLOT_BALANCE, makeBalanceDisplay(balance));
    }

    // =========================================================================
    // Item factories
    // =========================================================================

    /** Builds the clickable item icon shown in a category page. */
    public ItemStack makeShopItem(ShopEntry entry) {
        ItemStack item = new ItemStack(entry.material());
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + entry.displayName());

        // Special (non-item) entries get a different, cleaner lore
        if (entry.customAction() != null) {
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Price: " + ChatColor.GOLD + "$" + FMT.format(entry.buyPrice()),
                    "",
                    ChatColor.GREEN + "Click " + ChatColor.GRAY + "to purchase"
            ));
        } else {
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Buy price: " + ChatColor.GOLD + "$" + FMT.format(entry.buyPrice()),
                    ChatColor.GRAY + "You receive: " + ChatColor.WHITE + "x" + entry.stackSize(),
                    "",
                    ChatColor.GREEN + "Left-click " + ChatColor.GRAY + "to purchase",
                    ChatColor.AQUA  + "Right-click " + ChatColor.GRAY + "to buy x64"
            ));
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeCategoryButton(ShopCategory category) {
        ItemStack item = new ItemStack(category.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + category.getDisplayName());
        int count = registry.getEntries(category).size();
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Items available: " + ChatColor.WHITE + count,
                "",
                ChatColor.GREEN + "Click to browse!"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeNavButton(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeBalanceDisplay(double balance) {
        ItemStack item = new ItemStack(Material.SUNFLOWER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Your Balance");
        meta.setLore(List.of(
                ChatColor.GOLD + "$" + FMT.format(balance)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeFiller(Material mat) {
        ItemStack pane = new ItemStack(mat);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    public int totalPages(int entryCount) {
        return Math.max(1, (int) Math.ceil((double) entryCount / ITEMS_PER_PAGE));
    }

    /** Returns which slot each category button occupies to centre them. */
    private int[] categorySlots(int count) {
        // Target row 2 (slots 18-26) and centre the buttons
        int[] slots = new int[count];
        // Always start from a reasonable centre point
        int[] presets = switch (count) {
            case 1  -> new int[]{22};
            case 2  -> new int[]{20, 24};
            case 3  -> new int[]{20, 22, 24};
            case 4  -> new int[]{19, 21, 23, 25};
            default -> {
                // Spread across rows 1-3
                int[] s = new int[count];
                for (int i = 0; i < count; i++) s[i] = 10 + i * 2;
                yield s;
            }
        };
        return presets;
    }
}
