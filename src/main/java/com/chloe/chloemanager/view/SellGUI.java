package com.chloe.chloemanager.view;

import com.chloe.chloemanager.model.SellPriceRegistry;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

/**
 * VIEW — SellGUI
 *
 * Builds and manages the chest-inventory GUI for the /sell command.
 *
 * Layout (9 slots wide):
 *   Row 1:  [PANE][PANE][PANE][PANE][SELL SLOT][PANE][PANE][PANE][PANE]
 *   Row 2:  [PANE][PANE][PANE][PANE][CONFIRM  ][PANE][PANE][PANE][PANE]
 *
 * The player drops their item into slot 4 (centre of row 1).
 * Clicking the green confirm button (slot 13) triggers the sale.
 */
public class SellGUI {

    /** The inventory slot where the player places the item to sell. */
    public static final int SELL_SLOT = 4;

    /** The slot index of the confirm/sell button. */
    public static final int CONFIRM_SLOT = 13;

    private static final int SIZE = 18;   // 2 rows × 9
    private static final DecimalFormat FMT = new DecimalFormat("#,##0.00");

    private final SellPriceRegistry registry;

    public SellGUI(SellPriceRegistry registry) {
        this.registry = registry;
    }

    /**
     * Opens a fresh sell GUI for the given player.
     */
    public void open(Player player) {
        Inventory inv = buildInventory(null);
        player.openInventory(inv);
    }

    /**
     * Builds the inventory.  If {@code itemInSlot} is provided it is placed in
     * slot 4 so the GUI can be rebuilt after an update.
     */
    public Inventory buildInventory(ItemStack itemInSlot) {
        Inventory inv = Bukkit.createInventory(null, SIZE,
                ChatColor.DARK_GRAY + "✦ " + ChatColor.GOLD + "Sell Items" + ChatColor.DARK_GRAY + " ✦");

        // Fill decorative panes
        ItemStack pane = makePane();
        for (int i = 0; i < SIZE; i++) {
            if (i != SELL_SLOT && i != CONFIRM_SLOT) {
                inv.setItem(i, pane);
            }
        }

        // Sell slot: leave empty (or restore previous item)
        if (itemInSlot != null && itemInSlot.getType() != Material.AIR) {
            inv.setItem(SELL_SLOT, itemInSlot);
        }

        // Confirm button — price shown if item already in slot
        inv.setItem(CONFIRM_SLOT, makeConfirmButton(itemInSlot));

        return inv;
    }

    /**
     * Rebuilds the confirm button lore to reflect the item currently in the sell
     * slot.  Call this whenever the sell slot changes.
     */
    public void refreshConfirmButton(Inventory inv) {
        ItemStack inSlot = inv.getItem(SELL_SLOT);
        inv.setItem(CONFIRM_SLOT, makeConfirmButton(inSlot));
    }

    // ── Factory helpers ──────────────────────────────────────────────────────

    private ItemStack makeConfirmButton(ItemStack item) {
        boolean hasSellable = item != null && item.getType() != Material.AIR
                && registry.isSellable(item.getType());

        Material icon = hasSellable ? Material.LIME_STAINED_GLASS_PANE
                                    : Material.RED_STAINED_GLASS_PANE;
        ItemStack btn = new ItemStack(icon);
        ItemMeta meta = btn.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Confirm Sale");

        if (hasSellable) {
            double unitPrice = registry.getPrice(item.getType());
            double total = unitPrice * item.getAmount();
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Item: " + ChatColor.YELLOW + formatName(item.getType()),
                    ChatColor.GRAY + "Qty:  " + ChatColor.WHITE + item.getAmount(),
                    ChatColor.GRAY + "Price each: " + ChatColor.GOLD + "$" + FMT.format(unitPrice),
                    ChatColor.GRAY + "Total: "      + ChatColor.GOLD + "$" + FMT.format(total),
                    "",
                    ChatColor.GREEN + "Click to sell!"
            ));
        } else {
            meta.setLore(List.of(ChatColor.GRAY + "Place a sellable item in the slot above."));
        }

        btn.setItemMeta(meta);
        return btn;
    }

    private ItemStack makePane() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }

    /** Converts Material enum names like OAK_LOG into "Oak Log". */
    private String formatName(Material material) {
        String raw = material.name().replace('_', ' ').toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1))
                  .append(' ');
            }
        }
        return sb.toString().trim();
    }
}
