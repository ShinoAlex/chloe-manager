package com.chloe.chloemanager.controller;

import com.chloe.chloemanager.ChloeManagerPlugin;
import com.chloe.chloemanager.model.BankModel;
import com.chloe.chloemanager.model.EconomyModel;
import com.chloe.chloemanager.view.BankGUI;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * CONTROLLER — BankController
 *
 * Commands:
 *   /bank            — opens the bank GUI
 *   /bank help       — shows help text
 *   /deposit <amount> — moves money from wallet into the bank
 *   /withdraw <amount> — moves money from the bank into wallet
 *
 * Interest: 2% of bank balance, paid automatically every 30 Minecraft days.
 * A background task checks every 60 seconds.
 */
public class BankController implements CommandExecutor, TabCompleter, Listener {

    private static final DecimalFormat FMT = new DecimalFormat("#,##0.00");
    private static final String PREFIX = ChatColor.DARK_GREEN + "[Bank] " + ChatColor.RESET;
    private static final int    DEPOSIT_MINIMUM = 1;

    private final ChloeManagerPlugin plugin;
    private final BankModel    bank;
    private final EconomyModel economy;
    private final BankGUI      gui;

    /** Tracks UUIDs that have ever had a bank account, for the interest task. */
    private final Set<UUID> knownAccounts = new HashSet<>();

    private org.bukkit.scheduler.BukkitTask interestTask;

    public BankController(ChloeManagerPlugin plugin, BankModel bank,
                         EconomyModel economy, BankGUI gui) {
        this.plugin  = plugin;
        this.bank    = bank;
        this.economy = economy;
        this.gui     = gui;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void startInterestTask() {
        // Load existing accounts so interest works after restart
        knownAccounts.addAll(bank.getAllAccountUuids());

        // Check every 60 seconds (1200 ticks)
        interestTask = new BukkitRunnable() {
            @Override public void run() { checkInterestForAll(); }
        }.runTaskTimer(plugin, 1200L, 1200L);
    }

    public void stopInterestTask() {
        if (interestTask != null) interestTask.cancel();
    }

    /** Checks all known bank accounts for interest eligibility. */
    private void checkInterestForAll() {
        for (UUID uuid : new HashSet<>(knownAccounts)) {
            double interest = bank.payInterestIfEligible(uuid);
            if (interest > 0) {
                Player p = Bukkit.getPlayer(uuid);
                String name = (p != null) ? p.getName() : uuid.toString().substring(0, 8);
                double newBal = bank.getBalance(uuid);

                bank.logInterest(uuid, name, interest, newBal);

                if (p != null && p.isOnline()) {
                    p.sendMessage(PREFIX + ChatColor.GREEN + "💰 Lãi suất đã được cộng! +"
                            + ChatColor.GOLD + "$" + FMT.format(interest)
                            + ChatColor.GREEN + " (2% sau 30 ngày MC)."
                            + ChatColor.GREEN + " Bank: "
                            + ChatColor.GOLD + "$" + FMT.format(newBal));
                }
            }
        }
    }

    /** Call when a player makes their first deposit so the interest task knows about them. */
    private void trackAccount(UUID uuid) {
        knownAccounts.add(uuid);
    }

    // =========================================================================
    // Commands
    // =========================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "bank"    -> handleBank(sender, args);
            case "deposit" -> handleDeposit(sender, args);
            case "withdraw"-> handleWithdraw(sender, args);
        }
        return true;
    }

    // ── /bank ────────────────────────────────────────────────────────────────

    private void handleBank(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Only players can use /bank.");
            return;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("help")) {
            sendHelp(player);
            return;
        }

        // Open the GUI
        double wallet = economy.getBalance(player.getUniqueId());
        gui.open(player, wallet);
    }

    // ── /deposit <amount> ─────────────────────────────────────────────────────

    private void handleDeposit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Only players can deposit.");
            return;
        }

        if (args.length < 1) {
            player.sendMessage(PREFIX + ChatColor.RED + "Usage: /deposit <amount>");
            return;
        }

        boolean isAll = args[0].equalsIgnoreCase("all");
        double amount;

        if (isAll) {
            amount = economy.getBalance(player.getUniqueId());
            if (amount < DEPOSIT_MINIMUM) {
                player.sendMessage(PREFIX + ChatColor.RED + "Ví của bạn rỗng.");
                return;
            }
        } else {
            try {
                amount = Double.parseDouble(args[0]);
                if (amount < DEPOSIT_MINIMUM) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                player.sendMessage(PREFIX + ChatColor.RED
                        + "Số tiền không hợp lệ. Dùng số dương hoặc 'all'.");
                return;
            }
        }

        double wallet = economy.getBalance(player.getUniqueId());
        if (wallet < amount) {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "Không đủ tiền! Ví: $" + FMT.format(wallet));
            return;
        }

        // Transfer: wallet → bank
        economy.withdraw(player.getUniqueId(), amount);
        double newBankBal = bank.deposit(player.getUniqueId(), amount);
        trackAccount(player.getUniqueId());
        bank.logTransaction(player.getUniqueId(), player.getName(),
                "DEPOSIT", amount, newBankBal);

        player.sendMessage(PREFIX + ChatColor.GREEN + "Đã gửi "
                + ChatColor.GOLD + "$" + FMT.format(amount)
                + ChatColor.GREEN + " vào bank. Bank: "
                + ChatColor.GOLD + "$" + FMT.format(newBankBal)
                + ChatColor.GREEN + " | Ví: "
                + ChatColor.GOLD + "$" + FMT.format(economy.getBalance(player.getUniqueId())));
    }

    // ── /withdraw <amount> ────────────────────────────────────────────────────

    private void handleWithdraw(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Only players can withdraw.");
            return;
        }

        if (args.length < 1) {
            player.sendMessage(PREFIX + ChatColor.RED + "Usage: /withdraw <amount>");
            return;
        }

        boolean isAll = args[0].equalsIgnoreCase("all");
        double amount;

        if (isAll) {
            amount = bank.getBalance(player.getUniqueId());
            if (amount < DEPOSIT_MINIMUM) {
                player.sendMessage(PREFIX + ChatColor.RED + "Bank của bạn rỗng.");
                return;
            }
        } else {
            try {
                amount = Double.parseDouble(args[0]);
                if (amount < DEPOSIT_MINIMUM) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                player.sendMessage(PREFIX + ChatColor.RED
                        + "Số tiền không hợp lệ. Dùng số dương hoặc 'all'.");
                return;
            }
        }

        double bankBal = bank.getBalance(player.getUniqueId());
        if (bankBal < amount) {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "Không đủ tiền! Bank: $" + FMT.format(bankBal));
            return;
        }

        // Transfer: bank → wallet
        double newBankBal = bank.withdraw(player.getUniqueId(), amount);
        if (newBankBal < 0) {
            player.sendMessage(PREFIX + ChatColor.RED + "Giao dịch thất bại.");
            return;
        }
        economy.deposit(player.getUniqueId(), amount);
        bank.logTransaction(player.getUniqueId(), player.getName(),
                "WITHDRAW", amount, newBankBal);

        player.sendMessage(PREFIX + ChatColor.GREEN + "Đã rút "
                + ChatColor.GOLD + "$" + FMT.format(amount)
                + ChatColor.GREEN + " từ bank. Bank: "
                + ChatColor.GOLD + "$" + FMT.format(newBankBal)
                + ChatColor.GREEN + " | Ví: "
                + ChatColor.GOLD + "$" + FMT.format(economy.getBalance(player.getUniqueId())));
    }

    // ── Help ─────────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        String d = ChatColor.DARK_GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
        player.sendMessage(d);
        player.sendMessage(ChatColor.GREEN + "  🏦 Bank — Hướng dẫn");
        player.sendMessage(d);
        player.sendMessage(ChatColor.YELLOW + "  /bank"
                + ChatColor.GRAY + " — Mở GUI xem thông tin bank");
        player.sendMessage(ChatColor.YELLOW + "  /bank help"
                + ChatColor.GRAY + " — Hiện menu này");
        player.sendMessage(ChatColor.YELLOW + "  /deposit <số tiền|all>"
                + ChatColor.GRAY + " — Gửi tiền vào bank");
        player.sendMessage(ChatColor.YELLOW + "  /withdraw <số tiền|all>"
                + ChatColor.GRAY + " — Rút tiền từ bank");
        player.sendMessage(d);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "  Lãi suất: 2% mỗi 30 ngày Minecraft");
        player.sendMessage(ChatColor.GRAY + "  1 ngày MC = 20 phút thực | 30 ngày = 10 giờ");
        player.sendMessage(d);
    }

    // =========================================================================
    // GUI click handler (read-only GUI)
    // =========================================================================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle() == null) return;
        if (!event.getView().getTitle().equals(BankGUI.TITLE)) return;

        // Bank GUI is purely informational — cancel everything
        event.setCancelled(true);
    }

    // =========================================================================
    // Tab completion
    // =========================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            String lower = args[0].toLowerCase();
            if (command.getName().equalsIgnoreCase("bank"))
                return List.of("help").stream().filter(s -> s.startsWith(lower)).toList();
            // deposit / withdraw
            return List.of("all", "100", "1000", "10000").stream()
                    .filter(s -> s.startsWith(lower)).toList();
        }
        return List.of();
    }
}
