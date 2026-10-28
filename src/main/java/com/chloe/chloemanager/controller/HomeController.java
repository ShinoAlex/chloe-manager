package com.chloe.chloemanager.controller;

import com.chloe.chloemanager.ChloeManagerPlugin;
import com.chloe.chloemanager.model.EconomyModel;
import com.chloe.chloemanager.model.HomeModel;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CONTROLLER — HomeController
 *
 * Commands:
 *   /sethome [1|2|3] — sets the player's home at the given slot (free)
 *   /home    [1|2|3] — teleports to home for $1000, with a 5-second warmup
 *
 * Players start with slot 1.  Slots 2 and 3 are purchased from /shop.
 *
 * Warmup:
 *  - Player sees a countdown title on screen.
 *  - If the player takes ANY damage during the warmup, the teleport is cancelled.
 *  - This prevents PvP escape.
 */
public class HomeController implements CommandExecutor, Listener, TabCompleter {

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
    /** Which home index each warming-up player is heading to. */
    private final Map<UUID, Integer> warmupHomeIndex = new HashMap<>();

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
            case "sethome" -> handleSetHome(player, args);
            case "home"    -> handleHome(player, args);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                       String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();

        int max = homeModel.getMaxHomes(player.getUniqueId());
        List<String> suggestions = new ArrayList<>();
        for (int i = 1; i <= max; i++) {
            String s = String.valueOf(i);
            if (s.startsWith(args[0])) suggestions.add(s);
        }
        return suggestions;
    }

    // ── /sethome ────────────────────────────────────────────────────────────

    private void handleSetHome(Player player, String[] args) {
        int index = parseHomeIndex(player, args);
        if (index < 0) return;

        UUID uuid = player.getUniqueId();
        if (!homeModel.isSlotUnlocked(uuid, index)) {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "\u00d4 nh\u00e0 #" + index
                    + " ch\u01b0a m\u1edf! Mua t\u1ea1i /shop (Homes).");
            return;
        }

        Location loc = player.getLocation();
        homeModel.setHome(uuid, index, loc);

        player.sendMessage(PREFIX + ChatColor.GREEN + "\u0110\u00e3 \u0111\u1eb7t nh\u00e0 #"
                + index + " t\u1ea1i "
                + ChatColor.YELLOW + loc.getWorld().getName()
                + ChatColor.WHITE + " (" + (int) loc.getX() + ", "
                + (int) loc.getY() + ", " + (int) loc.getZ() + ")"
                + ChatColor.GREEN + ".");
        player.sendMessage(PREFIX + ChatColor.GRAY
                + "D\u00f9ng " + ChatColor.AQUA + "/home " + index
                + ChatColor.GRAY + " \u0111\u1ec3 d\u1ecbch chuy\u1ec3n v\u1ec1 (ph\u00ed $"
                + FMT.format(TP_COST) + ").");
    }

    // ── /home ────────────────────────────────────────────────────────────────

    private void handleHome(Player player, String[] args) {
        int index = parseHomeIndex(player, args);
        if (index < 0) return;

        UUID uuid = player.getUniqueId();

        // Already warming up?
        if (warmupTimers.containsKey(uuid)) {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "B\u1ea1n \u0111ang ch\u1edd d\u1ecbch chuy\u1ec3n r\u1ed3i!");
            return;
        }

        // Slot unlocked?
        if (!homeModel.isSlotUnlocked(uuid, index)) {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "\u00d4 nh\u00e0 #" + index
                    + " ch\u01b0a m\u1edf! Mua t\u1ea1i /shop (Homes).");
            return;
        }

        // Has a home set?
        if (!homeModel.hasHome(uuid, index)) {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "B\u1ea1n ch\u01b0a \u0111\u1eb7t nh\u00e0 #" + index
                    + ". D\u00f9ng /sethome " + index + " tr\u01b0\u1edbc.");
            return;
        }

        // Can afford it?
        double balance = economy.getBalance(uuid);
        if (balance < TP_COST) {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "Kh\u00f4ng \u0111\u1ee7 ti\u1ec1n! C\u1ea7n $" + FMT.format(TP_COST)
                    + ", b\u1ea1n c\u00f3 $" + FMT.format(balance) + ".");
            return;
        }

        // Start warmup
        warmupTimers.put(uuid, WARMUP_SECONDS);
        warmupCancelled.put(uuid, false);
        warmupHomeIndex.put(uuid, index);
        player.sendMessage(PREFIX + ChatColor.YELLOW
                + "\u0110ang d\u1ecbch chuy\u1ec3n v\u1ec1 nh\u00e0 #" + index + "... "
                + ChatColor.RED + "KH\u00d4NG DI CHUY\u1ec2N!"
                + ChatColor.YELLOW + " (" + WARMUP_SECONDS + " gi\u00e2y)");

        tickWarmup(player);
    }

    // =========================================================================
    // Warmup logic — ticks every second
    // =========================================================================

    private void tickWarmup(Player player) {
        UUID uuid = player.getUniqueId();

        if (!warmupTimers.containsKey(uuid)) return;

        if (warmupCancelled.getOrDefault(uuid, true)) {
            warmupTimers.remove(uuid);
            warmupCancelled.remove(uuid);
            warmupHomeIndex.remove(uuid);
            player.sendMessage(PREFIX + ChatColor.RED
                    + "D\u1ecbch chuy\u1ec3n b\u1ecb h\u1ee7y \u2014 b\u1ea1n \u0111\u00e3 nh\u1eadn s\u00e1t th\u01b0\u01a1ng!");
            player.sendTitle("", "", 0, 0, 0);
            return;
        }

        int secondsLeft = warmupTimers.get(uuid);

        if (secondsLeft > 0) {
            int index = warmupHomeIndex.getOrDefault(uuid, 1);
            player.sendTitle(
                    ChatColor.YELLOW + "D\u1ecbch chuy\u1ec3n # " + index + " sau " + secondsLeft + "s",
                    ChatColor.RED + "Kh\u00f4ng nh\u1eadn s\u00e1t th\u01b0\u01a1ng!",
                    0, 20, 10);
            warmupTimers.put(uuid, secondsLeft - 1);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        warmupTimers.remove(uuid);
                        warmupCancelled.remove(uuid);
                        warmupHomeIndex.remove(uuid);
                        return;
                    }
                    tickWarmup(player);
                }
            }.runTaskLater(plugin, 20L);
        } else {
            warmupTimers.remove(uuid);
            warmupCancelled.remove(uuid);
            executeTeleport(player);
        }
    }

    private void executeTeleport(Player player) {
        UUID uuid = player.getUniqueId();
        int index = warmupHomeIndex.remove(uuid);
        if (index <= 0) index = 1;

        if (!economy.withdraw(uuid, TP_COST)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Giao d\u1ecbch th\u1ea5t b\u1ea1i.");
            return;
        }

        Location home = homeModel.getHome(uuid, index);
        if (home == null) {
            economy.deposit(uuid, TP_COST);
            player.sendMessage(PREFIX + ChatColor.RED + "Kh\u00f4ng t\u00ecm th\u1ea5y nh\u00e0 #" + index + ".");
            return;
        }

        player.teleport(home);
        player.sendMessage(PREFIX + ChatColor.GREEN + "\u0110\u00e3 v\u1ec1 nh\u00e0 #" + index
                + "! Ph\u00ed: " + ChatColor.GOLD + "$" + FMT.format(TP_COST)
                + ChatColor.GREEN + ". S\u1ed1 d\u01b0: "
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

    // ── Helper ───────────────────────────────────────────────────────────────

    /** Parses the optional home index argument. Returns -1 on error. */
    private int parseHomeIndex(Player player, String[] args) {
        if (args.length == 0) return 1;  // default to home #1
        try {
            int i = Integer.parseInt(args[0]);
            if (i < 1 || i > HomeModel.MAX_HOMES_CAP) {
                player.sendMessage(PREFIX + ChatColor.RED
                        + "S\u1ed1 nh\u00e0 ph\u1ea3i t\u1eeb 1 \u0111\u1ebfn "
                        + HomeModel.MAX_HOMES_CAP + ".");
                return -1;
            }
            return i;
        } catch (NumberFormatException e) {
            player.sendMessage(PREFIX + ChatColor.RED + "S\u1ed1 nh\u00e0 kh\u00f4ng h\u1ee3p l\u1ec7.");
            return -1;
        }
    }
}
