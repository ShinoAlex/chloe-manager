package com.chloe.chloemanager.view;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * VIEW — EconomyView
 *
 * All economy-related player-facing messages live here.
 * Controllers call these methods; they never build raw strings themselves.
 */
public class EconomyView {

    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,##0.00");
    private static final String PREFIX = ChatColor.GOLD + "[Economy] " + ChatColor.RESET;

    // ── Generic helpers ──────────────────────────────────────────────────────

    public void sendInfo(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + ChatColor.AQUA + message);
    }

    public void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + ChatColor.GREEN + message);
    }

    public void sendError(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + ChatColor.RED + message);
    }

    public void sendWarning(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + ChatColor.YELLOW + message);
    }

    // ── Balance ──────────────────────────────────────────────────────────────

    /**
     * Shows a player's own balance.
     */
    public void sendOwnBalance(CommandSender sender, double balance) {
        sender.sendMessage(PREFIX
                + ChatColor.AQUA + "Your balance: "
                + ChatColor.GOLD + "$" + fmt(balance));
    }

    /**
     * Shows another player's balance.
     */
    public void sendOtherBalance(CommandSender sender, String targetName, double balance) {
        sender.sendMessage(PREFIX
                + ChatColor.AQUA + targetName + "'s balance: "
                + ChatColor.GOLD + "$" + fmt(balance));
    }

    // ── Sell ─────────────────────────────────────────────────────────────────

    /**
     * Confirms a successful GUI sell.
     *
     * @param earned Total money earned in this transaction.
     * @param newBalance Balance after the transaction.
     */
    public void sendSellSuccess(CommandSender sender, double earned, double newBalance) {
        sender.sendMessage(PREFIX
                + ChatColor.GREEN + "Sold! You earned "
                + ChatColor.GOLD + "$" + fmt(earned)
                + ChatColor.GREEN + ". Balance: "
                + ChatColor.GOLD + "$" + fmt(newBalance));
    }

    /**
     * Result of /sell all: shows items sold, total earned, and new balance.
     */
    public void sendSellAllResult(Player player, int itemsSold, double totalEarned, double newBalance) {
        if (itemsSold == 0) {
            sendWarning(player, "No sellable items found in your inventory.");
            return;
        }
        player.sendMessage(PREFIX
                + ChatColor.GREEN + "Sold " + ChatColor.YELLOW + itemsSold
                + ChatColor.GREEN + " item(s) for "
                + ChatColor.GOLD + "$" + fmt(totalEarned)
                + ChatColor.GREEN + ". Balance: "
                + ChatColor.GOLD + "$" + fmt(newBalance));
    }

    /** Tells the sender that no item was placed in the sell slot. */
    public void sendNothingToSell(CommandSender sender) {
        sendWarning(sender, "Put an item in the sell slot first.");
    }

    /** Tells the sender the item is not on the price list. */
    public void sendItemNotSellable(CommandSender sender) {
        sendError(sender, "That item is not sellable.");
    }

    // ── Pay ──────────────────────────────────────────────────────────────────

    /**
     * Confirms a successful payment to the sender.
     */
    public void sendPaySuccess(CommandSender sender, String recipientName, double amount, double newBalance) {
        sender.sendMessage(PREFIX
                + ChatColor.GREEN + "Paid "
                + ChatColor.YELLOW + recipientName
                + ChatColor.GREEN + " "
                + ChatColor.GOLD + "$" + fmt(amount)
                + ChatColor.GREEN + ". Your balance: "
                + ChatColor.GOLD + "$" + fmt(newBalance));
    }

    /**
     * Notifies the recipient that they received money.
     */
    public void sendPayReceived(Player recipient, String senderName, double amount, double newBalance) {
        recipient.sendMessage(PREFIX
                + ChatColor.GOLD + senderName
                + ChatColor.GREEN + " paid you "
                + ChatColor.GOLD + "$" + fmt(amount)
                + ChatColor.GREEN + ". Your balance: "
                + ChatColor.GOLD + "$" + fmt(newBalance));
    }

    /** Tells the sender they don't have enough money. */
    public void sendInsufficientFunds(CommandSender sender, double balance) {
        sendError(sender, "Insufficient funds. Your balance: $" + fmt(balance));
    }

    // ── Baltop ───────────────────────────────────────────────────────────────

    /**
     * Renders the top-N richest players leaderboard.
     *
     * @param entries List of (UUID, balance) pairs, pre-sorted descending.
     * @param resolver A function to resolve UUID to display name.
     */
    public void sendBaltop(CommandSender sender,
                           List<Map.Entry<UUID, Double>> entries,
                           java.util.function.Function<UUID, String> resolver) {
        sender.sendMessage(ChatColor.GOLD + "━━━━━ " + ChatColor.YELLOW + "Top Balances"
                + ChatColor.GOLD + " ━━━━━");
        if (entries.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "  No data yet.");
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<UUID, Double> entry = entries.get(i);
            String name = resolver.apply(entry.getKey());
            String rank = rankColor(i + 1) + "#" + (i + 1);
            sender.sendMessage("  " + rank + ChatColor.RESET + " " + ChatColor.WHITE + name
                    + ChatColor.GRAY + " — "
                    + ChatColor.GOLD + "$" + fmt(entry.getValue()));
        }
        sender.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━");
    }

    // ── Misc errors ──────────────────────────────────────────────────────────

    public void sendMustBePlayer(CommandSender sender) {
        sendError(sender, "Only players can use this command.");
    }

    public void sendInvalidAmount(CommandSender sender) {
        sendError(sender, "Invalid amount. Please enter a positive number.");
    }

    public void sendPlayerNotFound(CommandSender sender, String name) {
        sendError(sender, "Player '" + name + "' is not online.");
    }

    public void sendCannotPaySelf(CommandSender sender) {
        sendError(sender, "You cannot pay yourself.");
    }

    public void sendBalUsage(CommandSender sender) {
        sendError(sender, "Usage: /balance [player]");
    }

    public void sendPayUsage(CommandSender sender) {
        sendError(sender, "Usage: /pay <player> <amount>");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String fmt(double value) {
        return MONEY_FMT.format(value);
    }

    private ChatColor rankColor(int rank) {
        return switch (rank) {
            case 1  -> ChatColor.GOLD;
            case 2  -> ChatColor.GRAY;
            case 3  -> ChatColor.RED;
            default -> ChatColor.WHITE;
        };
    }
}
