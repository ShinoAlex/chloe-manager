package com.chloe.chloemanager.controller;

import com.chloe.chloemanager.model.EconomyModel;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.text.DecimalFormat;

/**
 * CONTROLLER — KillRewardController
 *
 * When a player is killed by another player:
 *  - 5% of the victim's balance is transferred to the killer.
 *  - A private message is sent to ONLY the killer and the victim — no one else.
 *
 * If a player dies by any other means (fall, mobs, etc.) nothing happens.
 */
public class KillRewardController implements Listener {

    private static final double  PENALTY_PERCENT = 0.05;  // 5%
    private static final DecimalFormat FMT = new DecimalFormat("#,##0.00");
    private static final String PREFIX = ChatColor.DARK_RED + "☠ " + ChatColor.RED + "Death Bounty " + ChatColor.DARK_RED + "☠" + ChatColor.RESET + " ";

    private final EconomyModel economy;

    public KillRewardController(EconomyModel economy) {
        this.economy = economy;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        // Only trigger if killed by another player
        Player killer = victim.getKiller();
        if (killer == null) return;
        if (killer.getUniqueId().equals(victim.getUniqueId())) return; // self-kill safety

        double victimBalance = economy.getBalance(victim.getUniqueId());
        if (victimBalance <= 0) {
            // Victim has no money — nothing to take
            killer.sendMessage(PREFIX + ChatColor.GRAY + victim.getName()
                    + " không có tiền — không nhận được gì.");
            victim.sendMessage(PREFIX + ChatColor.GRAY + "Bạn không có tiền để mất.");
            return;
        }

        double penalty = victimBalance * PENALTY_PERCENT;

        // Round to 2 decimal places to avoid floating point dust
        penalty = Math.round(penalty * 100.0) / 100.0;
        if (penalty <= 0) penalty = 0.01; // minimum 1 cent if they have any money

        // Transfer: withdraw from victim, deposit to killer
        boolean success = economy.withdraw(victim.getUniqueId(), penalty);
        if (!success) return; // shouldn't happen, but guard anyway

        economy.deposit(killer.getUniqueId(), penalty);

        double killerNewBal  = economy.getBalance(killer.getUniqueId());
        double victimNewBal  = economy.getBalance(victim.getUniqueId());

        // Private message to killer only
        killer.sendMessage(PREFIX + ChatColor.GREEN + "Bạn đã giết "
                + ChatColor.YELLOW + victim.getName()
                + ChatColor.GREEN + " và nhận "
                + ChatColor.GOLD + "$" + FMT.format(penalty)
                + ChatColor.GREEN + " (5% số dư của họ)."
                + ChatColor.GREEN + " Số dư: "
                + ChatColor.GOLD + "$" + FMT.format(killerNewBal));

        // Private message to victim only
        victim.sendMessage(PREFIX + ChatColor.RED + "Bạn bị "
                + ChatColor.YELLOW + killer.getName()
                + ChatColor.RED + " giết và mất "
                + ChatColor.GOLD + "$" + FMT.format(penalty)
                + ChatColor.RED + " (5% số dư)."
                + ChatColor.RED + " Số dư: "
                + ChatColor.GOLD + "$" + FMT.format(victimNewBal));
    }
}
