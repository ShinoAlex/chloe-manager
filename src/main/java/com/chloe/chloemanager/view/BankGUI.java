package com.chloe.chloemanager.view;

import com.chloe.chloemanager.model.BankModel;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.Arrays;

/**
 * VIEW — BankGUI
 *
 * A compact 1-row (9 slot) GUI showing:
 *   Slot 1: Bank balance
 *   Slot 3: Wallet balance (on-hand money)
 *   Slot 5: Interest rate + next payout countdown
 *   Slot 7: Last interest received
 *
 * Filler panes everywhere else.
 */
public class BankGUI {

    public static final String TITLE = ChatColor.DARK_GREEN + "🏦 " + ChatColor.GREEN + "Bank" + ChatColor.DARK_GREEN + " 🏦";

    private static final int SLOT_BANK_BAL    = 1;
    private static final int SLOT_WALLET      = 3;
    private static final int SLOT_INTEREST    = 5;
    private static final int SLOT_LAST_INTEREST = 7;

    private static final DecimalFormat FMT = new DecimalFormat("#,##0.00");

    private final BankModel model;

    public BankGUI(BankModel model) {
        this.model = model;
    }

    public void open(Player player, double walletBalance) {
        player.openInventory(build(walletBalance, player.getUniqueId()));
    }

    public Inventory build(double walletBalance, java.util.UUID playerUuid) {
        Inventory inv = Bukkit.createInventory(null, 9, TITLE);

        ItemStack pane = makeFiller();
        for (int i = 0; i < 9; i++) inv.setItem(i, pane);

        // ── Bank balance ─────────────────────────────────────────────────
        inv.setItem(SLOT_BANK_BAL, makeInfoItem(Material.GOLD_BLOCK,
                ChatColor.GOLD + "Số dư trong Bank",
                Arrays.asList(
                        ChatColor.WHITE + "$" + FMT.format(model.getBalance(playerUuid)),
                        "",
                        ChatColor.GRAY + "/deposit <số tiền>",
                        ChatColor.GRAY + "/withdraw <số tiền>"
                )));

        // ── Wallet balance ────────────────────────────────────────────────
        inv.setItem(SLOT_WALLET, makeInfoItem(Material.SUNFLOWER,
                ChatColor.YELLOW + "Tiền mặt (ví)",
                Arrays.asList(
                        ChatColor.WHITE + "$" + FMT.format(walletBalance),
                        "",
                        ChatColor.GRAY + "Tiền sẵn sàng dùng"
                )));

        // ── Interest info ─────────────────────────────────────────────────
        long daysLeft = model.getDaysUntilPayout(playerUuid);
        inv.setItem(SLOT_INTEREST, makeInfoItem(Material.EXPERIENCE_BOTTLE,
                ChatColor.LIGHT_PURPLE + "Lãi suất",
                Arrays.asList(
                        ChatColor.AQUA + "1.5% mỗi 50 ngày MC",
                        "",
                        ChatColor.GRAY + "Ngày còn lại: " + ChatColor.WHITE + daysLeft + " ngày",
                        ChatColor.GRAY + "(1 ngày MC = 20 phút thực)"
                )));

        // ── Last interest ─────────────────────────────────────────────────
        double lastInt = model.getLastInterest(playerUuid);
        inv.setItem(SLOT_LAST_INTEREST, makeInfoItem(Material.EMERALD,
                ChatColor.GREEN + "Lãi gần nhất",
                Arrays.asList(
                        lastInt > 0
                            ? ChatColor.WHITE + "+$" + FMT.format(lastInt)
                            : ChatColor.GRAY + "Chưa nhận lãi lần nào",
                        "",
                        ChatColor.GRAY + "Tự động cộng khi đủ 30 ngày"
                )));

        return inv;
    }

    // ── Factories ─────────────────────────────────────────────────────────────

    private ItemStack makeInfoItem(Material mat, String name, java.util.List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeFiller() {
        ItemStack pane = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta  meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}
