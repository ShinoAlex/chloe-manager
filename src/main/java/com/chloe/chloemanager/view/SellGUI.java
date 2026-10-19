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
 * Layout (9 slots wide, 2 rows = 18 slots total):
 *   Row 1:  [PANE][PANE][PANE][PANE][SELL SLOT][PANE][PANE][PANE][PANE]
 *   Row 2:  [PANE][PANE][PANE][PANE][CONFIRM  ][PANE][PANE][PANE][PANE]
 *
 * IMPORTANT: TITLE must be an exact unique string so EconomyController's
 * click handler can distinguish it from the ShopGUI inventories.
 */
public class SellGUI {

    /** Exact inventory title — used for strict equality check in the click handler. */
    public static final String TITLE = ChatColor.DARK_AQUA + "» " + ChatColor.GOLD + "Sell Items" + ChatColor.DARK_AQUA + " «";

    /** Slot where the player places the item to sell. */
    public static final int SELL_SLOT    = 4;

    /** Slot of the confirm/sell button. */
    public static final int CONFIRM_SLOT = 13;

    private static final int SIZE = 18;  // 2 rows × 9
    private static final DecimalFormat FMT = new DecimalFormat("#,##0.00");

    private final SellPriceRegistry registry;

    public SellGUI(SellPriceRegistry registry) {
        this.registry = registry;
    }

    // -------------------------------------------------------------------------

    /** Opens a fresh sell GUI for the player. */
    public void open(Player player) {
        player.openInventory(buildInventory(null));
    }

    /** Builds the inventory, optionally pre-filling the sell slot. */
    public Inventory buildInventory(ItemStack itemInSlot) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);

        ItemStack pane = makePane();
        for (int i = 0; i < SIZE; i++) {
            if (i != SELL_SLOT && i != CONFIRM_SLOT) {
                inv.setItem(i, pane);
            }
        }

        if (itemInSlot != null && itemInSlot.getType() != Material.AIR) {
            inv.setItem(SELL_SLOT, itemInSlot);
        }

        inv.setItem(CONFIRM_SLOT, makeConfirmButton(itemInSlot));
        return inv;
    }

    /** Rebuilds the confirm button to reflect the current item in the sell slot. */
    public void refreshConfirmButton(Inventory inv) {
        ItemStack inSlot = inv.getItem(SELL_SLOT);
        inv.setItem(CONFIRM_SLOT, makeConfirmButton(inSlot));
    }

    // ── Factories ────────────────────────────────────────────────────────────

    private ItemStack makeConfirmButton(ItemStack item) {
        boolean hasSellable = item != null
                && item.getType() != Material.AIR
                && registry.isSellable(item.getType());

        Material icon = hasSellable
                ? Material.LIME_STAINED_GLASS_PANE
                : Material.RED_STAINED_GLASS_PANE;

        ItemStack btn  = new ItemStack(icon);
        ItemMeta  meta = btn.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "Confirm Sale");

        if (hasSellable) {
            double unitPrice = registry.getPrice(item.getType());
            double total     = unitPrice * item.getAmount();
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Item:  "       + ChatColor.YELLOW + formatName(item.getType()),
                    ChatColor.GRAY + "Qty:   "       + ChatColor.WHITE  + item.getAmount(),
                    ChatColor.GRAY + "Price each: "  + ChatColor.GOLD   + "$" + FMT.format(unitPrice),
                    ChatColor.GRAY + "Total: "       + ChatColor.GOLD   + "$" + FMT.format(total),
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
        ItemMeta  meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }

    private String formatName(Material material) {
        String raw = material.name().replace('_', ' ').toLowerCase();
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
