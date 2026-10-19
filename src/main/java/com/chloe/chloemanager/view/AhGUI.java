package com.chloe.chloemanager.view;

import com.chloe.chloemanager.model.AhListing;
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
 * VIEW — AhGUI
 *
 * Builds three inventory types:
 *
 *  1. MARKET PAGE    — 6 rows, slots 0-44 = listings, row 6 = navigation.
 *  2. TEMP INVENTORY — 6 rows, slots 0-44 = unclaimed items, row 6 = navigation.
 *  3. CONFIRM DIALOG — 3 rows, centred buttons for buy confirmation.
 *
 * Navigation row (slots 45-53):
 *   45 ◀ Prev    48 🗃 Temp Inv    49 balance    52 [page info]    53 ▶ Next
 */
public class AhGUI {

    // ── Slot constants ────────────────────────────────────────────────────────
    public static final int ITEMS_PER_PAGE  = 45;
    public static final int SLOT_PREV       = 45;
    public static final int SLOT_TEMP_INV   = 48;
    public static final int SLOT_BALANCE    = 49;
    public static final int SLOT_PAGE_INFO  = 52;
    public static final int SLOT_NEXT       = 53;

    // Confirm dialog slots
    public static final int CONFIRM_YES     = 11;
    public static final int CONFIRM_NO      = 15;
    public static final int CONFIRM_ITEM    = 13;

    // ── Title constants ───────────────────────────────────────────────────────
    public static final String TITLE_MARKET  = "§5✦ §dBlack Market §5✦";
    public static final String TITLE_TEMP    = "§5✦ §dTemp Inventory §5✦";
    public static final String TITLE_CONFIRM = "§5✦ §dXác nhận mua §5✦";

    private static final DecimalFormat FMT = new DecimalFormat("#,##0.00");

    // =========================================================================
    // Market page
    // =========================================================================

    /**
     * Builds one page of the main /ah market.
     *
     * @param listings  Full list of ACTIVE listings (all pages).
     * @param page      0-based page index.
     * @param balance   Viewer's current balance.
     * @param viewerUuid UUID of the player viewing (used to show "Your listing" hint).
     */
    public Inventory buildMarketPage(List<AhListing> listings, int page,
                                     double balance, java.util.UUID viewerUuid) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_MARKET);

        int start = page * ITEMS_PER_PAGE;
        int end   = Math.min(start + ITEMS_PER_PAGE, listings.size());

        for (int i = start; i < end; i++) {
            inv.setItem(i - start, makeListingItem(listings.get(i), viewerUuid));
        }

        buildNavRow(inv, page, totalPages(listings.size()), balance, false);
        return inv;
    }

    // =========================================================================
    // Temp inventory page
    // =========================================================================

    public Inventory buildTempInvPage(List<ItemStack> items, int page, double balance) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_TEMP);

        int start = page * ITEMS_PER_PAGE;
        int end   = Math.min(start + ITEMS_PER_PAGE, items.size());

        for (int i = start; i < end; i++) {
            ItemStack display = items.get(i).clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + display.getType().name()
                        .replace('_', ' ').toLowerCase()
                        .substring(0, 1).toUpperCase()
                        + display.getType().name().replace('_', ' ').toLowerCase().substring(1));
                meta.setLore(Arrays.asList(
                        "",
                        ChatColor.GREEN + "Left-click " + ChatColor.GRAY + "to retrieve to inventory",
                        ChatColor.GRAY + "(Drops on ground if inventory is full)"
                ));
                display.setItemMeta(meta);
            }
            inv.setItem(i - start, display);
        }

        buildNavRow(inv, page, totalPages(items.size()), balance, true);
        return inv;
    }

    // =========================================================================
    // Confirm dialog
    // =========================================================================

    /**
     * Builds a 3-row confirmation dialog for purchasing a listing.
     *
     * @param listing  The listing being purchased.
     * @param balance  Buyer's current balance.
     */
    public Inventory buildConfirmDialog(AhListing listing, double balance) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_CONFIRM);

        // Fill with dark pane
        ItemStack pane = makeFiller(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        // Centre: the item being bought
        ItemStack display = listing.getItem();
        display.setAmount(listing.getAmount());
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Người bán: " + ChatColor.YELLOW + listing.getSellerName(),
                    ChatColor.GRAY + "Số lượng:  " + ChatColor.WHITE + listing.getAmount(),
                    ChatColor.GRAY + "Giá:       " + ChatColor.GOLD + "$" + FMT.format(listing.getPrice()),
                    ChatColor.GRAY + "Số dư của bạn: " + ChatColor.GOLD + "$" + FMT.format(balance)
            ));
            display.setItemMeta(meta);
        }
        inv.setItem(CONFIRM_ITEM, display);

        // YES button
        ItemStack yes = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta yesMeta = yes.getItemMeta();
        yesMeta.setDisplayName(ChatColor.GREEN + "✔ Xác nhận mua");
        yesMeta.setLore(List.of(ChatColor.GOLD + "$" + FMT.format(listing.getPrice())));
        yes.setItemMeta(yesMeta);
        inv.setItem(CONFIRM_YES, yes);

        // NO button
        ItemStack no = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta noMeta = no.getItemMeta();
        noMeta.setDisplayName(ChatColor.RED + "✘ Huỷ");
        no.setItemMeta(noMeta);
        inv.setItem(CONFIRM_NO, no);

        return inv;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Creates the display item for a listing slot.
     * The listing ID is stored in the item's lore so the controller can retrieve it.
     */
    public ItemStack makeListingItem(AhListing listing, java.util.UUID viewerUuid) {
        ItemStack display = listing.getItem();
        display.setAmount(Math.min(listing.getAmount(), display.getMaxStackSize()));
        ItemMeta meta = display.getItemMeta();
        if (meta == null) meta = Bukkit.getItemFactory().getItemMeta(display.getType());

        boolean isOwner = listing.getSellerUuid().equals(viewerUuid);

        meta.setDisplayName((isOwner ? ChatColor.GOLD : ChatColor.YELLOW)
                + "" + ChatColor.BOLD + formatName(listing.getItem().getType()));

        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Người bán: " + ChatColor.WHITE + listing.getSellerName(),
                ChatColor.GRAY + "Số lượng:  " + ChatColor.WHITE + listing.getAmount(),
                ChatColor.GRAY + "Giá:       " + ChatColor.GOLD + "$" + FMT.format(listing.getPrice()),
                ChatColor.GRAY + "Hết hạn:   " + ChatColor.AQUA + listing.getRemainingString(),
                "",
                isOwner
                    ? ChatColor.RED + "Right-click " + ChatColor.GRAY + "to remove your listing"
                    : ChatColor.GREEN + "Left-click "  + ChatColor.GRAY + "to buy",
                // Hidden ID tag — last lore line, prefixed so we can find it
                ChatColor.BLACK + "ID:" + listing.getId()
        ));

        display.setItemMeta(meta);
        return display;
    }

    private void buildNavRow(Inventory inv, int page, int totalPages,
                              double balance, boolean isTempInv) {
        ItemStack filler = makeFiller(Material.GRAY_STAINED_GLASS_PANE);
        for (int s = 45; s < 54; s++) inv.setItem(s, filler);

        // Prev
        if (page > 0) {
            inv.setItem(SLOT_PREV, makeButton(Material.ARROW,
                    ChatColor.YELLOW + "◀ Trang trước",
                    List.of(ChatColor.GRAY + "Trang " + page + "/" + totalPages)));
        }

        // Temp inv toggle button
        if (!isTempInv) {
            inv.setItem(SLOT_TEMP_INV, makeButton(Material.CHEST,
                    ChatColor.LIGHT_PURPLE + "📦 Kho tạm của bạn",
                    List.of(ChatColor.GRAY + "Xem item hết hạn hoặc đã rút")));
        } else {
            inv.setItem(SLOT_TEMP_INV, makeButton(Material.BARRIER,
                    ChatColor.RED + "⬅ Quay lại chợ",
                    List.of(ChatColor.GRAY + "Trở về Black Market")));
        }

        // Balance
        ItemStack bal = new ItemStack(Material.SUNFLOWER);
        ItemMeta balMeta = bal.getItemMeta();
        balMeta.setDisplayName(ChatColor.GOLD + "Số dư: $" + FMT.format(balance));
        bal.setItemMeta(balMeta);
        inv.setItem(SLOT_BALANCE, bal);

        // Page info
        inv.setItem(SLOT_PAGE_INFO, makeButton(Material.BOOK,
                ChatColor.WHITE + "Trang " + (page + 1) + "/" + Math.max(1, totalPages),
                List.of(ChatColor.GRAY + (isTempInv ? "Kho tạm" : "Black Market"))));

        // Next
        if (page < totalPages - 1) {
            inv.setItem(SLOT_NEXT, makeButton(Material.ARROW,
                    ChatColor.YELLOW + "Trang sau ▶",
                    List.of(ChatColor.GRAY + "Trang " + (page + 2) + "/" + totalPages)));
        }
    }

    public int totalPages(int count) {
        return Math.max(1, (int) Math.ceil((double) count / ITEMS_PER_PAGE));
    }

    /** Refreshes the balance display in an open market/temp-inv inventory. */
    public void refreshBalance(Inventory inv, double balance) {
        if (inv.getSize() < SLOT_BALANCE + 1) return;
        ItemStack bal = new ItemStack(Material.SUNFLOWER);
        ItemMeta meta = bal.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Số dư: $" + FMT.format(balance));
        bal.setItemMeta(meta);
        inv.setItem(SLOT_BALANCE, bal);
    }

    private ItemStack makeButton(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeFiller(Material mat) {
        ItemStack pane = new ItemStack(mat);
        ItemMeta meta  = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }

    private String formatName(org.bukkit.Material material) {
        String raw = material.name().replace('_', ' ').toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!word.isEmpty())
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    /**
     * Extracts the listing ID from the hidden last lore line of a listing item.
     * Returns null if not found.
     */
    public static String extractListingId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return null;
        List<String> lore = meta.getLore();
        // Strip colour codes and find the ID: line
        for (int i = lore.size() - 1; i >= 0; i--) {
            String plain = ChatColor.stripColor(lore.get(i));
            if (plain != null && plain.startsWith("ID:")) {
                return plain.substring(3);
            }
        }
        return null;
    }
}
