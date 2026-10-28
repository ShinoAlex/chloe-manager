package com.chloe.chloemanager.view;

import com.chloe.chloemanager.model.BossDifficulty;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;

/**
 * VIEW — BossView
 *
 * All boss-related player-facing messages: spawn broadcasts, kill rewards,
 * death-by-boss notices, and trade feedback.
 */
public class BossView {

    private static final DecimalFormat FMT = new DecimalFormat("#,##0.00");
    private static final String PREFIX =
            ChatColor.DARK_PURPLE + "\u2605 " + ChatColor.LIGHT_PURPLE + "Boss"
                    + ChatColor.DARK_PURPLE + " \u2605" + ChatColor.RESET + " ";

    // ── Generic ──────────────────────────────────────────────────────────────

    public void sendMustBePlayer(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.RED + "Only players can use this.");
    }

    // ── Spawn broadcast (to all online players) ───────────────────────────────

    public void broadcastSpawn(BossDifficulty diff, String mobName, String worldName,
                               int x, int y, int z, boolean netherBonus) {
        String coords = x + ", " + y + ", " + z;
        String bonus  = netherBonus
                ? ChatColor.GOLD + " (Nether \u00d71.5 reward!)" : "";

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(
                    diff.getColor() + "" + ChatColor.BOLD + "\u2620 BOSS SPAWNED \u2620",
                    diff.getColor() + diff.getDisplayName() + ChatColor.GRAY
                            + " " + mobName + " \u00b7 " + worldName,
                    10, 70, 20);
            p.sendMessage(PREFIX + diff.getColor() + "" + ChatColor.BOLD
                    + "\u2620 A " + diff.getDisplayName() + " Boss has spawned! \u2620");
            p.sendMessage(ChatColor.GRAY + "  Mob: " + ChatColor.WHITE + mobName
                    + ChatColor.GRAY + "  |  World: " + ChatColor.WHITE + worldName
                    + ChatColor.GRAY + "  |  " + ChatColor.WHITE + coords + bonus);
        }
    }

    // ── Kill reward (to killer) ──────────────────────────────────────────────

    public void sendBossSlainKiller(Player killer, BossDifficulty diff, double reward,
                                     boolean netherBonus) {
        killer.sendMessage(PREFIX + ChatColor.GOLD + "" + ChatColor.BOLD
                + "\u2620 BOSS SLAIN! \u2620");
        killer.sendMessage(ChatColor.GREEN + "You killed a "
                + diff.getColor() + diff.getDisplayName()
                + ChatColor.GREEN + " Boss!");
        killer.sendMessage(ChatColor.GOLD + "Reward: $" + FMT.format(reward)
                + (netherBonus ? ChatColor.GOLD + " (Nether \u00d71.5!)" : ""));
    }

    // ── Kill broadcast (to all) ──────────────────────────────────────────────

    public void broadcastBossSlain(String killerName, BossDifficulty diff) {
        Bukkit.broadcastMessage(PREFIX + ChatColor.GOLD + "" + ChatColor.BOLD
                + "\u2620 " + killerName + " has slain a "
                + diff.getColor() + diff.getDisplayName()
                + ChatColor.GOLD + "" + ChatColor.BOLD + " Boss! \u2620");
    }

    // ── Player killed by boss ─────────────────────────────────────────────────

    public String deathByBossMessage(String victimName) {
        return ChatColor.DARK_RED + "\u2620 " + victimName
                + " was slain by a Boss! \u2620";
    }

    public void sendSonicBoomHit(Player target) {
        target.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD
                + "\u2620 SONIC BOOM! \u2620 " + ChatColor.RED
                + "The Nightmare Boss unleashed a sonic boom!");
    }

    // ── Trade ────────────────────────────────────────────────────────────────

    public void sendTradeSuccess(Player player) {
        player.sendMessage(PREFIX + ChatColor.GREEN
                + "Trade complete! You received the "
                + ChatColor.GOLD + "Pickaxe of the Boss Slayer"
                + ChatColor.GREEN + "!");
    }

    public void sendNotEnoughEssence(Player player, int count) {
        player.sendMessage(PREFIX + ChatColor.RED
                + "You need " + ChatColor.GOLD + "128 Essence of Life"
                + ChatColor.RED + " to trade. You have " + ChatColor.WHITE
                + count + ChatColor.RED + ".");
    }

    public void sendInventoryFull(Player player) {
        player.sendMessage(PREFIX + ChatColor.RED
                + "Your inventory is full! Make some space first.");
    }
}
