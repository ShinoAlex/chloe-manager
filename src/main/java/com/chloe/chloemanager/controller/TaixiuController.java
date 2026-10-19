package com.chloe.chloemanager.controller;

import com.chloe.chloemanager.ChloeManagerPlugin;
import com.chloe.chloemanager.model.EconomyModel;
import com.chloe.chloemanager.model.TaixiuLog;
import com.chloe.chloemanager.model.TaixiuModel;
import com.chloe.chloemanager.model.TaixiuModel.BetEntry;
import com.chloe.chloemanager.model.TaixiuModel.BetSide;
import com.chloe.chloemanager.model.TaixiuModel.DiceResult;
import com.chloe.chloemanager.view.TaixiuView;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * CONTROLLER — TaixiuController
 *
 * Game rules:
 *  - Each phiên (round) lasts 2 minutes (120 seconds).
 *  - Betting is locked at 5 seconds remaining.
 *  - Minimum bet: $100   |   Maximum bet: $1,000,000
 *  - Win → receive 2× bet back (net profit = bet amount).
 *  - Lose → lose the bet (already deducted at placement).
 *  - Triple (1-1-1 / 6-6-6) → all money goes to Nhà cái (Senj0ugahara).
 *  - Each result is logged to taixiu_log.txt with phiên number.
 */
public class TaixiuController implements CommandExecutor, TabCompleter, Listener {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final int    ROUND_SECONDS     = 120;        // 2 minutes
    private static final int    LOCK_SECONDS      = 5;
    private static final double MINIMUM_BET       = 100.0;      // $100
    private static final double MAXIMUM_BET       = 1_000_000.0; // $1,000,000
    /**
     * Payout multiplier on WIN.
     * Bet is deducted at placement (escrow). On win, we return bet × 2.
     * Example: bet $500 → on win deposit $1,000 back (net +$500 profit).
     */
    private static final double WIN_RETURN        = 2.0;
    private static final String HOUSE_NAME        = "Senj0ugahara";

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final ChloeManagerPlugin plugin;
    private final EconomyModel       economy;
    private final TaixiuModel        model;
    private final TaixiuView         view;
    private final TaixiuLog          log;

    // ── Round state ───────────────────────────────────────────────────────────
    private int            secondsLeft = ROUND_SECONDS;
    private BukkitRunnable roundTask   = null;

    public TaixiuController(ChloeManagerPlugin plugin,
                            EconomyModel economy,
                            TaixiuModel model,
                            TaixiuView view,
                            TaixiuLog log) {
        this.plugin  = plugin;
        this.economy = economy;
        this.model   = model;
        this.view    = view;
        this.log     = log;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void startLoop() {
        startNewRound();
    }

    public void stopLoop() {
        if (roundTask != null) {
            roundTask.cancel();
            roundTask = null;
        }
        view.clearBossBar();
    }

    // =========================================================================
    // Round loop
    // =========================================================================

    private void startNewRound() {
        model.resetRound();
        secondsLeft = ROUND_SECONDS;

        int phien = log.getCurrentRound();

        view.showBossBarToAll(Bukkit.getOnlinePlayers(), uuid -> !model.isOptedOut(uuid));
        view.broadcastRoundStart(Bukkit.getOnlinePlayers(), uuid -> !model.isOptedOut(uuid),
                phien, ROUND_SECONDS);
        view.updateBossBar(secondsLeft, ROUND_SECONDS, model.getBiggestBet(), phien);

        roundTask = new BukkitRunnable() {
            @Override
            public void run() {
                secondsLeft--;

                if (secondsLeft == LOCK_SECONDS && !model.isBettingLocked()) {
                    model.setBettingLocked(true);
                    view.broadcastLocked(Bukkit.getOnlinePlayers(),
                            uuid -> !model.isOptedOut(uuid));
                }

                view.updateBossBar(secondsLeft, ROUND_SECONDS,
                        model.getBiggestBet(), log.getCurrentRound());

                if (secondsLeft <= 0) {
                    cancel();
                    resolveRound();
                }
            }
        };
        roundTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void resolveRound() {
        int        phien      = log.getCurrentRound();
        DiceResult result     = TaixiuModel.rollDice();
        Map<UUID, BetEntry> bets = new HashMap<>(model.getBets()); // snapshot
        BetEntry   biggestBet = model.getBiggestBet();

        // ── No bets this round → restart without counting ─────────────────────
        if (bets.isEmpty()) {
            view.broadcastNoBetsRestart(Bukkit.getOnlinePlayers(),
                    uuid -> !model.isOptedOut(uuid));
            view.clearBossBar();

            // Reset round state and start a new one — phiên number unchanged
            model.resetRound();
            new BukkitRunnable() {
                @Override public void run() { startNewRound(); }
            }.runTaskLater(plugin, 40L); // 2-second pause
            return;
        }

        // ── Broadcast result ────────────────────────────────────────────────
        view.broadcastResult(Bukkit.getOnlinePlayers(),
                uuid -> !model.isOptedOut(uuid),
                result, bets, biggestBet, phien);

        // ── Payout ──────────────────────────────────────────────────────────
        if (result.isTriple) {
            // All bet money → house
            double totalLost = 0;
            for (BetEntry bet : bets.values()) totalLost += bet.amount;
            depositToHouse(totalLost);

            for (BetEntry bet : bets.values()) {
                Player p = Bukkit.getPlayer(bet.uuid);
                if (p != null && p.isOnline()) {
                    view.sendPersonalResult(p, false, true,
                            bet.amount, 0, economy.getBalance(bet.uuid));
                }
            }

            // Log: triple — house wins
            log.appendResult(phien, result.diceString(), result.sum,
                    "TRIPLE (Nhà cái)", null, totalLost, 0);

        } else {
            // Normal round — winners get 2× bet returned
            for (BetEntry bet : bets.values()) {
                boolean won = bet.side == result.winner;
                Player  p   = Bukkit.getPlayer(bet.uuid);

                if (won) {
                    // Return original bet + equal profit (2× total)
                    double returnAmount = bet.amount * WIN_RETURN;
                    economy.deposit(bet.uuid, returnAmount);
                    double newBal = economy.getBalance(bet.uuid);

                    if (p != null && p.isOnline()) {
                        view.sendPersonalResult(p, true, false,
                                bet.amount, bet.amount, newBal); // profit = bet amount
                    }

                    // Log each winner separately
                    log.appendResult(phien, result.diceString(), result.sum,
                            result.winner.getDisplay(),
                            bet.playerName, bet.amount, bet.amount); // profit logged

                } else {
                    // Money already withheld — just notify
                    if (p != null && p.isOnline()) {
                        view.sendPersonalResult(p, false, false,
                                bet.amount, 0, economy.getBalance(bet.uuid));
                    }
                }
            }

            // If nobody bet on the winning side, still log the result
            boolean anyWinner = bets.values().stream()
                    .anyMatch(b -> b.side == result.winner);
            if (!anyWinner) {
                log.appendResult(phien, result.diceString(), result.sum,
                        result.winner.getDisplay(), null, 0, 0);
            }
        }

        // ── Advance round counter ────────────────────────────────────────────
        log.incrementRound();

        view.clearBossBar();

        // Next phiên starts after 3 seconds
        new BukkitRunnable() {
            @Override public void run() { startNewRound(); }
        }.runTaskLater(plugin, 60L);
    }

    @SuppressWarnings("deprecation")
    private void depositToHouse(double amount) {
        if (amount <= 0) return;
        org.bukkit.OfflinePlayer house = Bukkit.getOfflinePlayer(HOUSE_NAME);
        if (house != null) economy.deposit(house.getUniqueId(), amount);
    }

    // =========================================================================
    // Commands
    // =========================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /taixiu.");
            return true;
        }

        if (args.length == 0) {
            view.sendHelp(player, MINIMUM_BET, MAXIMUM_BET, log.getCurrentRound());
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "bet"    -> handleBet(player, args);
            case "toggle" -> handleToggle(player, args);
            default       -> view.sendHelp(player, MINIMUM_BET, MAXIMUM_BET, log.getCurrentRound());
        }
        return true;
    }

    // ── /taixiu bet ───────────────────────────────────────────────────────────

    private void handleBet(Player player, String[] args) {
        if (args.length < 3) {
            view.sendHelp(player, MINIMUM_BET, MAXIMUM_BET, log.getCurrentRound());
            return;
        }

        BetSide side = BetSide.parse(args[1]);
        if (side == null) {
            player.sendMessage("§c[Tài Xỉu] Chọn 'tai' hoặc 'xiu'.");
            return;
        }

        if (model.isBettingLocked()) {
            view.sendBettingLocked(player);
            return;
        }

        double balance  = economy.getBalance(player.getUniqueId());
        boolean isAllin = args[2].equalsIgnoreCase("allin");

        double amount;
        if (isAllin) {
            if (balance < MINIMUM_BET) {
                view.sendNoBalance(player, MINIMUM_BET);
                return;
            }
            // Allin is capped at the maximum bet
            amount = Math.min(balance, MAXIMUM_BET);
        } else {
            try {
                amount = Double.parseDouble(args[2]);
                if (amount <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                view.sendInvalidAmount(player);
                return;
            }
            if (amount < MINIMUM_BET) {
                view.sendMinimumBet(player, MINIMUM_BET);
                return;
            }
            if (amount > MAXIMUM_BET) {
                view.sendMaximumBet(player, MAXIMUM_BET);
                return;
            }
        }

        // ── Handle existing bet ───────────────────────────────────────────────
        if (model.hasBet(player.getUniqueId())) {
            BetEntry prev = model.getBets().get(player.getUniqueId());

            if (prev.side != side) {
                // Cannot switch sides — betting is final
                view.sendCannotSwitchSide(player, prev.side);
                return;
            }

            // Same side → ADD to existing bet
            double newTotal = prev.amount + amount;

            // Cap combined total at MAXIMUM_BET
            if (newTotal > MAXIMUM_BET) {
                double allowed = MAXIMUM_BET - prev.amount;
                if (allowed <= 0) {
                    view.sendMaximumBet(player, MAXIMUM_BET);
                    return;
                }
                // Clamp the extra amount to what's still allowed
                amount = allowed;
                newTotal = MAXIMUM_BET;
            }

            if (balance < amount) {
                view.sendInsufficientFunds(player, balance);
                return;
            }

            economy.withdraw(player.getUniqueId(), amount);
            boolean added = model.addToBet(player.getUniqueId(), amount);
            if (!added) {
                economy.deposit(player.getUniqueId(), amount);
                view.sendBettingLocked(player);
                return;
            }

            view.sendBetAdded(player, side, amount, newTotal);
            view.updateBossBar(secondsLeft, ROUND_SECONDS,
                    model.getBiggestBet(), log.getCurrentRound());

            if (isAllin) {
                view.sendAllinBroadcast(Bukkit.getOnlinePlayers(),
                        uuid -> !model.isOptedOut(uuid),
                        player.getName(), side, newTotal);
            }
            return;
        }

        // ── Place new bet (first bet this round) ─────────────────────────────
        if (balance < amount) {
            view.sendInsufficientFunds(player, balance);
            return;
        }

        // Deduct bet immediately (held in escrow until round ends)
        economy.withdraw(player.getUniqueId(), amount);

        boolean placed = model.placeBet(player.getUniqueId(), player.getName(), side, amount);
        if (!placed) {
            economy.deposit(player.getUniqueId(), amount); // refund race condition
            view.sendBettingLocked(player);
            return;
        }

        view.sendBetConfirm(player, side, amount);
        view.updateBossBar(secondsLeft, ROUND_SECONDS,
                model.getBiggestBet(), log.getCurrentRound());

        if (isAllin) {
            view.sendAllinBroadcast(Bukkit.getOnlinePlayers(),
                    uuid -> !model.isOptedOut(uuid),
                    player.getName(), side, amount);
        }
    }

    // ── /taixiu toggle ────────────────────────────────────────────────────────

    private void handleToggle(Player player, String[] args) {
        if (args.length < 2) {
            view.sendHelp(player, MINIMUM_BET, MAXIMUM_BET, log.getCurrentRound());
            return;
        }
        switch (args[1].toLowerCase()) {
            case "off" -> {
                model.setOptedOut(player.getUniqueId(), true);
                view.hideBossBar(player);
                view.sendToggleOff(player);
            }
            case "on" -> {
                model.setOptedOut(player.getUniqueId(), false);
                view.showBossBar(player);
                view.sendToggleOn(player);
            }
            default -> view.sendHelp(player, MINIMUM_BET, MAXIMUM_BET, log.getCurrentRound());
        }
    }

    // =========================================================================
    // Player events
    // =========================================================================

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!model.isOptedOut(player.getUniqueId())) {
            view.showBossBar(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        view.hideBossBar(event.getPlayer());
        // Refund pending bet from disconnected player
        UUID uuid = event.getPlayer().getUniqueId();
        if (model.hasBet(uuid)) {
            BetEntry bet = model.getBets().get(uuid);
            economy.deposit(uuid, bet.amount);
        }
    }

    // =========================================================================
    // Tab completion
    // =========================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        return switch (args.length) {
            case 1 -> filterStart(args[0], List.of("bet", "toggle"));
            case 2 -> switch (args[0].toLowerCase()) {
                case "bet"    -> filterStart(args[1], List.of("tai", "xiu"));
                case "toggle" -> filterStart(args[1], List.of("on", "off"));
                default       -> List.of();
            };
            case 3 -> args[0].equalsIgnoreCase("bet")
                    ? filterStart(args[2], List.of("allin", "100", "500", "1000", "10000"))
                    : List.of();
            default -> List.of();
        };
    }

    private List<String> filterStart(String partial, List<String> options) {
        String lower = partial.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .toList();
    }
}
