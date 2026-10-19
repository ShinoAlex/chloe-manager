package com.chloe.chloemanager.view;

import com.chloe.chloemanager.model.TaixiuModel.BetEntry;
import com.chloe.chloemanager.model.TaixiuModel.BetSide;
import com.chloe.chloemanager.model.TaixiuModel.DiceResult;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * VIEW — TaixiuView
 *
 * All Tài Xỉu player-facing output.
 * Methods that need the phiên (round) number accept it as a parameter.
 */
public class TaixiuView {

    private static final DecimalFormat FMT    = new DecimalFormat("#,##0.00");
    private static final String PREFIX  = ChatColor.GOLD + "[Tài Xỉu] " + ChatColor.RESET;
    private static final String DIVIDER = ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

    // ── Boss bar ──────────────────────────────────────────────────────────────

    private final BossBar bossBar;

    public TaixiuView() {
        bossBar = Bukkit.createBossBar(
                ChatColor.GOLD + "Tài Xỉu — " + ChatColor.WHITE + "Đang chờ...",
                BarColor.YELLOW,
                BarStyle.SEGMENTED_20
        );
    }

    public void showBossBar(Player player) {
        if (!bossBar.getPlayers().contains(player)) bossBar.addPlayer(player);
    }

    public void hideBossBar(Player player) {
        bossBar.removePlayer(player);
    }

    /**
     * Updates the boss bar each second.
     *
     * @param secondsLeft  Seconds left in this phiên.
     * @param totalSeconds Total duration (120s).
     * @param biggestBet   Current top bet, or null.
     * @param phien        Current phiên number.
     */
    public void updateBossBar(int secondsLeft, int totalSeconds,
                              BetEntry biggestBet, int phien) {
        double progress = Math.max(0, Math.min(1.0, (double) secondsLeft / totalSeconds));
        bossBar.setProgress(progress);

        if (secondsLeft <= 5) {
            bossBar.setColor(BarColor.RED);
        } else if (secondsLeft <= 20) {
            bossBar.setColor(BarColor.YELLOW);
        } else {
            bossBar.setColor(BarColor.GREEN);
        }

        // Format: "Tài Xỉu | Phiên 42 | 119s [LOCKED] | Top: PlayerName $500.00 Tài"
        String lock = secondsLeft <= 5 ? ChatColor.RED + " [KHOÁ]" : "";
        String top  = biggestBet != null
                ? ChatColor.GRAY + " | Top: "
                  + ChatColor.YELLOW + biggestBet.playerName
                  + ChatColor.WHITE + " $" + FMT.format(biggestBet.amount)
                  + " " + sideColor(biggestBet.side) + biggestBet.side.getDisplay()
                : "";

        bossBar.setTitle(
                ChatColor.GOLD + "Tài Xỉu "
                + ChatColor.GRAY + "| "
                + ChatColor.AQUA + "Phiên " + phien + " "
                + ChatColor.GRAY + "| "
                + ChatColor.WHITE + secondsLeft + "s"
                + lock + top
        );
    }

    public void clearBossBar() {
        bossBar.setTitle(ChatColor.GOLD + "Tài Xỉu — " + ChatColor.WHITE + "Đang chờ...");
        bossBar.setProgress(0);
        bossBar.setColor(BarColor.YELLOW);
        bossBar.removeAll();
    }

    public void showBossBarToAll(Collection<? extends Player> players,
                                 Predicate<UUID> isVisible) {
        for (Player p : players) {
            if (isVisible.test(p.getUniqueId())) showBossBar(p);
        }
    }

    // ── Round broadcasts ──────────────────────────────────────────────────────

    public void broadcastRoundStart(Collection<? extends Player> players,
                                    Predicate<UUID> isVisible,
                                    int phien, int totalSeconds) {
        broadcast(PREFIX + ChatColor.GREEN + "Phiên mới đã mở có thể đặt cược!",
                players, isVisible);
    }

    public void broadcastNoBetsRestart(Collection<? extends Player> players,
                                       Predicate<UUID> isVisible) {
        broadcast(PREFIX + ChatColor.YELLOW + "Không ai đặt cược — phiên bắt đầu lại!",
                players, isVisible);
    }

    public void broadcastLocked(Collection<? extends Player> players,
                                Predicate<UUID> isVisible) {
        broadcast(PREFIX + ChatColor.RED + "⚠ Hết thời gian đặt cược! Xúc xắc chuẩn bị lăn...",
                players, isVisible);
    }

    public void broadcastResult(Collection<? extends Player> players,
                                Predicate<UUID> isVisible,
                                DiceResult result,
                                Map<UUID, BetEntry> bets,
                                BetEntry biggestBet,
                                int phien) {
        StringBuilder sb = new StringBuilder();
        sb.append(DIVIDER).append("\n");
        sb.append(PREFIX).append(ChatColor.AQUA).append("Phiên ").append(phien).append(" — ")
          .append(ChatColor.YELLOW).append("🎲 KẾT QUẢ: ")
          .append(ChatColor.WHITE).append(result.diceString())
          .append(ChatColor.GOLD).append(" Tổng: ").append(result.sum).append("\n");

        if (result.isTriple) {
            sb.append(ChatColor.DARK_RED).append("  ☠ TRIPLE! Nhà cái thắng tất cả!\n");
        } else {
            String wColor = result.winner == BetSide.TAI
                    ? ChatColor.RED.toString() : ChatColor.AQUA.toString();
            sb.append(ChatColor.GREEN).append("  Kết quả: ")
              .append(wColor).append("▶ ").append(result.winner.getDisplay()).append("\n");
        }

        if (biggestBet != null) {
            sb.append(ChatColor.GRAY).append("  Cược lớn nhất: ")
              .append(ChatColor.YELLOW).append(biggestBet.playerName)
              .append(ChatColor.WHITE).append(" — $").append(FMT.format(biggestBet.amount))
              .append(" ").append(sideColor(biggestBet.side)).append(biggestBet.side.getDisplay())
              .append("\n");
        }

        sb.append(DIVIDER);
        broadcast(sb.toString(), players, isVisible);
    }

    // ── Personal result ───────────────────────────────────────────────────────

    /**
     * @param profit  Amount won (same as bet on a normal win). 0 on loss/triple.
     */
    public void sendPersonalResult(Player player, boolean won, boolean isTriple,
                                   double betAmount, double profit, double newBalance) {
        if (isTriple) {
            player.sendMessage(PREFIX + ChatColor.DARK_RED + "Nhà cái thắng! Bạn mất "
                    + ChatColor.GOLD + "$" + FMT.format(betAmount) + ChatColor.DARK_RED + ".");
        } else if (won) {
            // Won: got back bet + profit (total = bet * 2)
            player.sendMessage(PREFIX + ChatColor.GREEN + "✔ Bạn thắng "
                    + ChatColor.GOLD + "+$" + FMT.format(profit)
                    + ChatColor.GREEN + "! Số dư: "
                    + ChatColor.GOLD + "$" + FMT.format(newBalance));
        } else {
            player.sendMessage(PREFIX + ChatColor.RED + "✘ Bạn thua "
                    + ChatColor.GOLD + "-$" + FMT.format(betAmount)
                    + ChatColor.RED + ". Số dư: "
                    + ChatColor.GOLD + "$" + FMT.format(newBalance));
        }
    }

    // ── Bet feedback ──────────────────────────────────────────────────────────

    public void sendBetConfirm(Player player, BetSide side, double amount) {
        player.sendMessage(PREFIX + ChatColor.GREEN + "Đặt cược "
                + sideColor(side) + side.getDisplay()
                + ChatColor.WHITE + " $" + FMT.format(amount)
                + ChatColor.GREEN + " thành công! Nếu thắng nhận "
                + ChatColor.GOLD + "$" + FMT.format(amount * 2) + ChatColor.GREEN + ".");
    }

    public void sendBetAdded(Player player, BetSide side, double added, double newTotal) {
        player.sendMessage(PREFIX + ChatColor.GREEN + "Cộng thêm "
                + ChatColor.GOLD + "$" + FMT.format(added)
                + ChatColor.GREEN + " vào cược "
                + sideColor(side) + side.getDisplay()
                + ChatColor.GREEN + ". Tổng cược: "
                + ChatColor.GOLD + "$" + FMT.format(newTotal)
                + ChatColor.GREEN + ". Nếu thắng nhận "
                + ChatColor.GOLD + "$" + FMT.format(newTotal * 2) + ChatColor.GREEN + ".");
    }

    public void sendAllinBroadcast(Collection<? extends Player> players,
                                   Predicate<UUID> isVisible,
                                   String playerName, BetSide side, double amount) {
        broadcast(PREFIX + ChatColor.GOLD + "★ ALL IN! "
                + ChatColor.YELLOW + playerName
                + ChatColor.WHITE + " đặt tất cả "
                + ChatColor.GOLD + "$" + FMT.format(amount)
                + ChatColor.WHITE + " vào "
                + sideColor(side) + side.getDisplay() + ChatColor.WHITE + "!",
                players, isVisible);
    }

    public void sendBettingLocked(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.RED + "Cược đã bị khoá! Còn ≤5 giây.");
    }

    public void sendCannotSwitchSide(Player player, BetSide currentSide) {
        player.sendMessage(PREFIX + ChatColor.RED + "Không thể đổi cửa! Bạn đã cược "
                + sideColor(currentSide) + currentSide.getDisplay()
                + ChatColor.RED + ". Chỉ có thể thêm tiền vào cùng cửa.");
    }

    public void sendInsufficientFunds(CommandSender sender, double balance) {
        sender.sendMessage(PREFIX + ChatColor.RED + "Không đủ tiền! Số dư: $" + FMT.format(balance));
    }

    public void sendInvalidAmount(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.RED + "Số tiền không hợp lệ. Dùng số dương hoặc 'allin'.");
    }

    public void sendMinimumBet(CommandSender sender, double min) {
        sender.sendMessage(PREFIX + ChatColor.RED + "Cược tối thiểu là $" + FMT.format(min) + ".");
    }

    public void sendMaximumBet(CommandSender sender, double max) {
        sender.sendMessage(PREFIX + ChatColor.RED + "Cược tối đa là $" + FMT.format(max) + ".");
    }

    public void sendNoBalance(CommandSender sender, double min) {
        sender.sendMessage(PREFIX + ChatColor.RED
                + "Bạn cần ít nhất $" + FMT.format(min) + " để đặt cược.");
    }

    // ── Toggle ────────────────────────────────────────────────────────────────

    public void sendToggleOn(Player player) {
        player.sendMessage(PREFIX + ChatColor.GREEN + "Tài Xỉu đã được bật.");
    }

    public void sendToggleOff(Player player) {
        player.sendMessage(PREFIX + ChatColor.GRAY + "Tài Xỉu đã tắt. Dùng /taixiu toggle on để bật lại.");
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    public void sendHelp(CommandSender sender, double min, double max, int phien) {
        sender.sendMessage(DIVIDER);
        sender.sendMessage(ChatColor.GOLD + "  🎲 Tài Xỉu — Phiên " + phien);
        sender.sendMessage(DIVIDER);
        sender.sendMessage(ChatColor.YELLOW + "  /taixiu"
                + ChatColor.GRAY + " — Hiện menu này");
        sender.sendMessage(ChatColor.YELLOW + "  /taixiu bet <tai/xiu> <số tiền>"
                + ChatColor.GRAY + " — Đặt cược");
        sender.sendMessage(ChatColor.YELLOW + "  /taixiu bet <tai/xiu> allin"
                + ChatColor.GRAY + " — Cược tất cả (thông báo server)");
        sender.sendMessage(ChatColor.YELLOW + "  /taixiu toggle <on/off>"
                + ChatColor.GRAY + " — Bật/tắt thông báo Tài Xỉu");
        sender.sendMessage(DIVIDER);
        sender.sendMessage(ChatColor.AQUA + "  Tài " + ChatColor.GRAY + "= tổng ≥ 11  |  "
                + ChatColor.RED + "Xỉu " + ChatColor.GRAY + "= tổng ≤ 10");
        sender.sendMessage(ChatColor.GRAY + "  Min cược: "
                + ChatColor.GREEN + "$" + FMT.format(min)
                + ChatColor.GRAY + "  |  Max: "
                + ChatColor.GREEN + "$" + FMT.format(max));
        sender.sendMessage(ChatColor.GRAY + "  Thắng → nhận lại "
                + ChatColor.GOLD + "2× cược"
                + ChatColor.GRAY + "  |  Thua → mất cược");
        sender.sendMessage(ChatColor.DARK_RED + "  1-1-1 / 6-6-6 = Nhà cái thắng tất cả!");
        sender.sendMessage(DIVIDER);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void broadcast(String message, Collection<? extends Player> players,
                           Predicate<UUID> isVisible) {
        for (Player p : players) {
            if (isVisible.test(p.getUniqueId())) p.sendMessage(message);
        }
    }

    private String sideColor(BetSide side) {
        return side == BetSide.TAI ? ChatColor.RED.toString() : ChatColor.AQUA.toString();
    }
}
