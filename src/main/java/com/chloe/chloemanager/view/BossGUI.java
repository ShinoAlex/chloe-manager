package com.chloe.chloemanager.view;

import com.chloe.chloemanager.model.BossModel;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * VIEW — BossGUI
 *
 * A clean 27-slot (3-row) trade GUI:
 *
 *   Slot  4  \u2014 Player's current Essence of Life count
 *   Slot 11  \u2014 Essence of Life  (need 64)
 *   Slot 13  \u2014 Arrow indicator (\u2192)
 *   Slot 15  \u2014 Boss Slayer Pickaxe  (reward)
 *   Slot 22  \u2014 Confirm Trade button
 *
 * All other slots are purple-stained glass filler.
 */
public class BossGUI {

    public static final String TITLE =
            ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "\u2605 Boss Trade \u2605";

    public static final int SLOT_ESSENCE_COUNT = 4;
    public static final int SLOT_ESSENCE        = 11;
    public static final int SLOT_ARROW          = 13;
    public static final int SLOT_PICKAXE        = 15;
    public static final int SLOT_TRADE          = 22;

    private final BossModel model;

    public BossGUI(BossModel model) {
        this.model = model;
    }

    /**
     * Builds the trade inventory.
     *
     * @param essenceCount How many Essence of Life the player currently owns.
     */
    public Inventory build(int essenceCount) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        // ── Filler everywhere ───────────────────────────────────────────────
        ItemStack filler = makeFiller(Material.PURPLE_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // ── Player's essence count ──────────────────────────────────────────
        inv.setItem(SLOT_ESSENCE_COUNT, makeEssenceCount(essenceCount));

        // ── Trade layout ─────────────────────────────────────────────────────
        inv.setItem(SLOT_ESSENCE, makeEssenceDisplay());
        inv.setItem(SLOT_ARROW,   makeArrow());
        inv.setItem(SLOT_PICKAXE, makePickaxeDisplay());

        // ── Trade button ─────────────────────────────────────────────────────
        inv.setItem(SLOT_TRADE, makeTradeButton(essenceCount >= 128));

        return inv;
    }

    // ── Item factories ─────────────────────────────────────────────────────────

    private ItemStack makeEssenceCount(int count) {
        ItemStack item = new ItemStack(Material.RED_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD
                + "Your Essence of Life");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.WHITE + "" + count + ChatColor.GRAY
                        + " / 128 needed to trade",
                "",
                ChatColor.DARK_GRAY + "Kill bosses to collect more!"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeEssenceDisplay() {
        ItemStack item = new ItemStack(Material.RED_DYE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD
                + "Essence of Life");
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Required: " + ChatColor.GOLD + "x128 (2 stacks)"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeArrow() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "\u2192");
        meta.setLore(java.util.List.of(
                ChatColor.GRAY + "Trade 64 Essence for a legendary pickaxe"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makePickaxeDisplay() {
        // Use the real pickaxe item as the display (clicks are cancelled)
        return model.createBossPickaxe();
    }

    private ItemStack makeTradeButton(boolean canTrade) {
        ItemStack item = new ItemStack(canTrade
                ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (canTrade) {
            meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD
                    + "\u2714 Confirm Trade");
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Exchange " + ChatColor.GOLD + "128 Essence of Life",
                    ChatColor.GRAY + "for a " + ChatColor.GOLD + "Boss Slayer Pickaxe",
                    "",
                    ChatColor.GREEN + "Click to trade!"
            ));
        } else {
            meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD
                    + "\u2716 Not Enough Essence");
            meta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "You need " + ChatColor.GOLD + "128 Essence of Life"
                    + ChatColor.GRAY + " to trade.",
                    "",
                    ChatColor.RED + "Kill more bosses!"
            ));
        }
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
}
