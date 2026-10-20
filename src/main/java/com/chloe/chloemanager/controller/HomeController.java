package com.chloe.chloemanager.controller;

import com.chloe.chloemanager.ChloeManagerPlugin;
import com.chloe.chloemanager.model.EconomyModel;
import com.chloe.chloemanager.model.HomeModel;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CONTROLLER — HomeController
 *
 * Commands:
 *   /sethome — sets the player's home to their current location (free, 1 per player)
 *   /home    — teleports to home for $1000, with a 5-second warmup
 *
 * Warmup:
 *  - Player sees a countdown title on screen.
 *  - If the player takes ANY damage during the warmup, the teleport is cancelled.
 *  - This prevents PvP escape.
 */
public class HomeController implements CommandExecutor, Listener {

    private static final double TP_COST = 1000.0;
    private static final int    WARMUP_SECONDS = 5;
    private static final DecimalFormat FMT = new DecimalFormat("#,##0.00");
    private static final String PREFIX = ChatColor.AQUA + "[Home] " + ChatColor.RESET;

    private final ChloeManagerPlugin plugin;
    private final HomeModel    homeModel;
    private final EconomyModel economy;

    /** Players currently in warmup: UUID → remaining seconds. */
    private final Map<UUID, Integer> warmupTimers = new HashMap<>();
    /** Cancelled flags for players in warmup. */
    private final Map<UUID, Boolean> warmupCancelled = new HashMap<>();

    public HomeController(ChloeManagerPlugin plugin, HomeModel homeModel,
                         EconomyModel economy) {
        this.plugin   = plugin;
        this.homeModel = homeModel;
        this.economy  = economy;
    }

    // =========================================================================
    // Commands
    // =========================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Only players can use this.");
            return true;
        }

        String cmd = command.getName().toLowerCase();
        switch (cmd) {
            case "sethome" -> handleSetHome(player);
            case "home"    -> handleHome(player);
        }
        return true;
    }

    // ── /sethome ────────────────────────────────────────────────────────────

    private void handleSetHome(Player player) {
        Location loc = player.getLocation();
        homeModel.setHome(player.getUniqueId(), loc);

        player.sendMessage(PREFIX + ChatColor.GREEN + "Đã đặt nhà tại "
                + ChatColor.YELLOW + loc.getWorld().getName()
                + ChatColor.WHITE + " (" + (int) loc.getX() + ", "
                + (int) loc.getY() + ", " + (int) loc.getZ() + ")"
                + ChatColor.GREEN + ".");
        player.sendMessage(PREFIX + ChatColor.GRAY
                + "Dùng /home để dịch chuyển về (phí $"
                + FMT.format(TP_COST) + ").");
    }

    // ── /home ────────────────────────────────────────────────────────────────

    private void handleHome(Player player) {
        UUID uuid = player.getUniqueId();

        // Already warming up?
        if (warmupTimers.containsKey(uuid)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Bạn đang chờ dịch chuyển rồi!");
            return;
        }

        // Has a home?
        if (!homeModel.hasHome(uuid)) {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "Bạn chưa đặt nhà. Dùng /sethome trước.");
            return;
        }

        // Can afford it?
        double balance = economy.getBalance(uuid);
        if (balance < TP_COST) {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "Không đủ tiền! Cần $" + FMT.format(TP_COST)
                    + ", bạn có $" + FMT.format(balance) + ".");
            return;
        }

        // Start warmup
        warmupTimers.put(uuid, WARMUP_SECONDS);
        warmupCancelled.put(uuid, false);
        player.sendMessage(PREFIX + ChatColor.YELLOW + "Đang dịch chuyển về nhà... "
                + ChatColor.RED + "KHÔNG DI CHUYỂN!"
                + ChatColor.YELLOW + " (" + WARMUP_SECONDS + " giây)");

        tickWarmup(player);
    }

    // =========================================================================
    // Warmup logic — ticks every second
    // =========================================================================

    private void tickWarmup(Player player) {
        UUID uuid = player.getUniqueId();

        // Already done or cancelled?
        if (!warmupTimers.containsKey(uuid)) return;

        if (warmupCancelled.getOrDefault(uuid, true)) {
            warmupTimers.remove(uuid);
            warmupCancelled.remove(uuid);
            player.sendMessage(PREFIX + ChatColor.RED
                    + "Dịch chuyển bị hủy — bạn đã nhận sát thương!");
            player.sendTitle("", "", 0, 0, 0);
            return;
        }

        int secondsLeft = warmupTimers.get(uuid);

        if (secondsLeft > 0) {
            player.sendTitle(
                    ChatColor.YELLOW + "Dịch chuyển sau " + secondsLeft + "s",
                    ChatColor.RED + "Không nhận sát thương!",
                    0, 20, 10);
            warmupTimers.put(uuid, secondsLeft - 1);

            // Schedule next tick
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        warmupTimers.remove(uuid);
                        warmupCancelled.remove(uuid);
                        return;
                    }
                    tickWarmup(player);
                }
            }.runTaskLater(plugin, 20L);
        } else {
            // Warmup complete
            warmupTimers.remove(uuid);
            warmupCancelled.remove(uuid);
            executeTeleport(player);
        }
    }

    private void executeTeleport(Player player) {
        UUID uuid = player.getUniqueId();

        if (!economy.withdraw(uuid, TP_COST)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Giao dịch thất bại.");
            return;
        }

        Location home = homeModel.getHome(uuid);
        if (home == null) {
            economy.deposit(uuid, TP_COST);
            player.sendMessage(PREFIX + ChatColor.RED + "Không tìm thấy nhà.");
            return;
        }

        player.teleport(home);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Đã về nhà! Phí: "
                + ChatColor.GOLD + "$" + FMT.format(TP_COST)
                + ChatColor.GREEN + ". Số dư: "
                + ChatColor.GOLD + "$" + FMT.format(economy.getBalance(uuid)));
    }

    // =========================================================================
    // Damage listener — cancels warmup on any damage
    // =========================================================================

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        if (warmupTimers.containsKey(uuid)) {
            warmupCancelled.put(uuid, true);
        }
    }
}
