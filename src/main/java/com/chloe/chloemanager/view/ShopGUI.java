package com.chloe.chloemanager.view;

import com.chloe.chloemanager.model.ShopRegistry;
import com.chloe.chloemanager.model.ShopRegistry.ShopCategory;
import com.chloe.chloemanager.model.ShopRegistry.ShopEntry;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

/**
 * VIEW — ShopGUI
 *
 * ── Main menu layout (54 slots, 6 rows) ───────────────────────────────────
 *
 *   Row 0 : [lime]  [lime]  [lime]  [lime]  [lime]  [lime]  [lime]  [lime]  [lime]
 *   Row 1 : [lime]  [  ]    [  ]    [  ]    [  ]    [  ]    [  ]    [  ]    [lime]
 *   Row 2 : [lime]  [  ]    CAT1    [  ]    CAT2    [  ]    CAT3    [  ]    [lime]
 *   Row 3 : [lime]  [  ]    CAT4    [  ]    CAT5    [  ]    [  ]    [  ]    [lime]
 *   Row 4 : [lime]  [  ]    [  ]    [  ]    [  ]    [  ]    [  ]    [  ]    [lime]
 *   Row 5 : [lime]  [lime]  [lime]  [lime]  [lime]  [lime]  [lime]  [lime]  [lime]
 *
 * Categories are placed in a centred 3-wide grid in the middle rows.
 * Border = lime-stained glass; inner filler = black glass.
 *
 * ── Category page layout (54 slots) ────────────────────────────────────────
 *   Rows 0-4 (45 slots): item grid
 *   Row 5 : ◀ Prev | filler | filler | ⬅ Back | Balance | filler | filler | filler | ▶ Next
 */
public class ShopGUI {

    // ── Slot constants ───────────────────────────────────────────────────────
    public static final int ITEMS_PER_PAGE = 45;
    public static final int SLOT_PREV      = 45;
    public static final int SLOT_BACK      = 48;
    public static final int SLOT_BALANCE   = 49;
    public static final int SLOT_NEXT      = 53;

    public static final String TITLE_MAIN   =
            ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "\u2605 " +
            ChatColor.GREEN + "Chloe Shop" +
            ChatColor.DARK_GREEN + "" + ChatColor.BOLD + " \u2605";
    public static final String TITLE_MARKER = "\u00a72\u00a7l\u2605 \u00a7a";

    private static final DecimalFormat FMT = new DecimalFormat("#,##0.00");

    private final ShopRegistry registry;

    public ShopGUI(ShopRegistry registry) {
        this.registry = registry;
    }

    // =========================================================================
    // Main category menu — beautiful bordered layout
    // =========================================================================

    public Inventory buildMainMenu() {
        ShopHolder holder = ShopHolder.main();
        Inventory inv = Bukkit.createInventory(holder, 54, TITLE_MAIN);
        holder.bind(inv);

        ItemStack border = makeFiller(Material.LIME_STAINED_GLASS_PANE,
                ChatColor.GREEN + "\u2014");
        ItemStack inner  = makeFiller(Material.BLACK_STAINED_GLASS_PANE, " ");

        // Fill entire inventory with inner filler first
        for (int i = 0; i < 54; i++) inv.setItem(i, inner);

        // Border: row 0 and row 5 (full rows)
        for (int i = 0;  i < 9;  i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);

        // Border: column 0 and column 8 (every row)
        for (int row = 0; row < 6; row++) {
            inv.setItem(row * 9,     border);
            inv.setItem(row * 9 + 8, border);
        }

        // Category buttons in centred positions
        List<ShopCategory> cats = registry.getCategories();
        int[] slots = categorySlots(cats.size());
        for (int i = 0; i < cats.size(); i++) {
            inv.setItem(slots[i], makeCategoryButton(cats.get(i)));
        }

        // Shop title display in middle of row 1 (slot 13)
        inv.setItem(13, makeShopTitle());

        return inv;
    }

    // =========================================================================
    // Category page
    // =========================================================================

    public Inventory buildCategoryPage(ShopCategory category, int page, double balance) {
        String title = TITLE_MARKER + category.getDisplayName();
        ShopHolder holder = ShopHolder.category(category);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.bind(inv);

        List<ShopEntry> entries = registry.getEntries(category);
        int totalPages = totalPages(entries.size());
        int start      = page * ITEMS_PER_PAGE;
        int end        = Math.min(start + ITEMS_PER_PAGE, entries.size());

        for (int i = start; i < end; i++) {
            inv.setItem(i - start, makeShopItem(entries.get(i)));
        }

        // Nav row
        ItemStack navFiller = makeFiller(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = 45; s < 54; s++) inv.setItem(s, navFiller);

        inv.setItem(SLOT_PREV, page > 0
                ? makeNavButton(Material.ARROW,
                        ChatColor.YELLOW + "\u25c4 Previous Page",
                        List.of(ChatColor.GRAY + "Page " + page + " of " + totalPages))
                : makeFiller(Material.GRAY_STAINED_GLASS_PANE, " "));

        inv.setItem(SLOT_BACK, makeNavButton(Material.BARRIER,
                ChatColor.RED + "\u2b05 Back to Categories",
                List.of(ChatColor.GRAY + "Return to the main shop menu")));

        inv.setItem(SLOT_BALANCE, makeBalanceDisplay(balance));

        inv.setItem(SLOT_NEXT, page < totalPages - 1
                ? makeNavButton(Material.ARROW,
                        ChatColor.YELLOW + "Next Page \u25ba",
                        List.of(ChatColor.GRAY + "Page " + (page + 2) + " of " + totalPages))
                : makeFiller(Material.GRAY_STAINED_GLASS_PANE, " "));

        return inv;
    }

    public void refreshBalance(Inventory inv, double balance) {
        if (inv.getSize() < SLOT_BALANCE + 1) return;
        inv.setItem(SLOT_BALANCE, makeBalanceDisplay(balance));
    }

    // =========================================================================
    // Item factories
    // =========================================================================

    public ItemStack makeShopItem(ShopEntry entry) {
        ItemStack item = new ItemStack(entry.material());
        ItemMeta meta  = item.getItemMeta();

        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + entry.displayName());

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
                    ChatColor.GREEN + "Left-click "  + ChatColor.GRAY + "to purchase",
                    ChatColor.AQUA  + "Right-click " + ChatColor.GRAY + "to buy x64"
            ));
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeShopTitle() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Welcome to the Shop!");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Browse categories below.",
                ChatColor.GRAY + "Click any category to start shopping."
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeCategoryButton(ShopCategory category) {
        ItemStack item = new ItemStack(category.getIcon());
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + category.getDisplayName());
        int count = registry.getEntries(category).size();
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Items: " + ChatColor.WHITE + count,
                "",
                ChatColor.YELLOW + "\u25ba Click to browse!"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeNavButton(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeBalanceDisplay(double balance) {
        ItemStack item = new ItemStack(Material.SUNFLOWER);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Your Balance");
        meta.setLore(List.of(ChatColor.GOLD + "$" + FMT.format(balance)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeFiller(Material mat, String name) {
        ItemStack pane = new ItemStack(mat);
        ItemMeta  meta = pane.getItemMeta();
        meta.setDisplayName(name);
        pane.setItemMeta(meta);
        return pane;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    public int totalPages(int entryCount) {
        return Math.max(1, (int) Math.ceil((double) entryCount / ITEMS_PER_PAGE));
    }

    /**
     * Places category buttons in a centred 3-column grid inside the bordered area.
     * Available inner slots (excluding border col 0 & 8, row 0, 5):
     *   Slots 10-16 (row 1 inner), 19-25 (row 2), 28-34 (row 3), 37-43 (row 4)
     * We centre across columns 1-7 using every other slot for visual spacing.
     * Up to 5 categories supported cleanly; beyond that falls back to a list.
     */
    /**
     * Marks a shop inventory so click handling does not depend on title strings.
     * Paper/Adventure often rewrite GUI titles, which used to make clicks no-op.
     */
    public static final class ShopHolder implements InventoryHolder {
        private final boolean mainMenu;
        private final ShopCategory category;
        private Inventory inventory;

        private ShopHolder(boolean mainMenu, ShopCategory category) {
            this.mainMenu  = mainMenu;
            this.category  = category;
        }

        public static ShopHolder main() {
            return new ShopHolder(true, null);
        }

        public static ShopHolder category(ShopCategory category) {
            return new ShopHolder(false, category);
        }

        public boolean isMainMenu()          { return mainMenu; }
        public ShopCategory getCategory()    { return category; }

        void bind(Inventory inventory)       { this.inventory = inventory; }

        @Override
        public Inventory getInventory()      { return inventory; }
    }

    public int[] categorySlots(int count) {
        // Grid positions: 3 per row, centred in columns 2, 4, 6 (slots +2,+4,+6 from row start)
        // Row 2 = slots 18..26  → inner: 19..25  → columns 2,4,6 = slots 20, 22, 24
        // Row 3 = slots 27..35  → inner: 28..34  → columns 2,4,6 = slots 29, 31, 33
        return switch (count) {
            case 1 -> new int[]{22};
            case 2 -> new int[]{20, 24};
            case 3 -> new int[]{20, 22, 24};
            case 4 -> new int[]{20, 24, 29, 33};
            case 5 -> new int[]{20, 22, 24, 29, 33};
            default -> {
                // 6+ : fill rows 2 and 3 left-to-right
                int[] all = {20, 22, 24, 28, 31, 33};
                int[] s   = new int[Math.min(count, all.length)];
                System.arraycopy(all, 0, s, 0, s.length);
                yield s;
            }
        };
    }
}
