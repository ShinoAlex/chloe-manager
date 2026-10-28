package com.chloe.chloemanager.view;

import com.chloe.chloemanager.model.ClanModel.ClanData;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.Set;
import java.util.UUID;

/**
 * VIEW — ClanView
 *
 * All clan-related player-facing messages.
 */
public class ClanView {

    private static final DecimalFormat FMT = new DecimalFormat("#,##0.00");
    private static final String PREFIX =
            ChatColor.DARK_AQUA + "[Clan] " + ChatColor.RESET;

    // ── Generic ──────────────────────────────────────────────────────────────

    public void sendMustBePlayer(CommandSender s) {
        s.sendMessage(PREFIX + ChatColor.RED + "Only players can use this.");
    }

    public void sendUsage(CommandSender s) {
        s.sendMessage(PREFIX + ChatColor.AQUA + "Clan Commands:");
        s.sendMessage(ChatColor.GRAY + "  /clan create <name>  " + ChatColor.WHITE + "Create a clan ($2,000)");
        s.sendMessage(ChatColor.GRAY + "  /clan add <player>   " + ChatColor.WHITE + "Invite a player (leader)");
        s.sendMessage(ChatColor.GRAY + "  /clan kick <player>  " + ChatColor.WHITE + "Kick a member (leader)");
        s.sendMessage(ChatColor.GRAY + "  /clan delete         " + ChatColor.WHITE + "Delete your clan (leader)");
        s.sendMessage(ChatColor.GRAY + "  /clan inventory      " + ChatColor.WHITE + "Open shared clan chest");
        s.sendMessage(ChatColor.GRAY + "  /clan leave          " + ChatColor.WHITE + "Leave your clan");
        s.sendMessage(ChatColor.GRAY + "  /clan info            " + ChatColor.WHITE + "View clan info");
    }

    // ── Create ────────────────────────────────────────────────────────────────

    public void sendCreated(Player p, String clanName, double cost) {
        p.sendMessage(PREFIX + ChatColor.GREEN + "Clan \"" + ChatColor.AQUA
                + clanName + ChatColor.GREEN + "\" created for "
                + ChatColor.GOLD + "$" + FMT.format(cost) + ChatColor.GREEN + "!");
    }

    public void sendAlreadyInClan(Player p) {
        p.sendMessage(PREFIX + ChatColor.RED
                + "You are already in a clan. Leave or delete it first.");
    }

    public void sendClanNameExists(Player p, String name) {
        p.sendMessage(PREFIX + ChatColor.RED
                + "A clan named \"" + name + "\" already exists.");
    }

    public void sendNotEnoughMoney(Player p, double cost, double balance) {
        p.sendMessage(PREFIX + ChatColor.RED + "Not enough money! Need $"
                + FMT.format(cost) + ", you have $" + FMT.format(balance) + ".");
    }

    public void sendInvalidName(Player p) {
        p.sendMessage(PREFIX + ChatColor.RED
                + "Invalid clan name. Use 3-16 letters, numbers, or underscores.");
    }

    // ── Add / kick ──────────────────────────────────────────────────────────────

    public void sendNotLeader(Player p) {
        p.sendMessage(PREFIX + ChatColor.RED + "Only the clan leader can do that.");
    }

    public void sendAdded(Player p, String targetName, String clanName) {
        p.sendMessage(PREFIX + ChatColor.GREEN + "Added " + ChatColor.YELLOW
                + targetName + ChatColor.GREEN + " to clan \""
                + ChatColor.AQUA + clanName + ChatColor.GREEN + "\".");
    }

    public void sendAddTarget(Player target, String clanName, String leaderName) {
        target.sendMessage(PREFIX + ChatColor.GREEN + "You have been added to clan \""
                + ChatColor.AQUA + clanName + ChatColor.GREEN + "\" by "
                + ChatColor.YELLOW + leaderName + ChatColor.GREEN + ".");
    }

    public void sendAlreadyMember(Player p, String name) {
        p.sendMessage(PREFIX + ChatColor.RED + name + " is already in this clan.");
    }

    public void sendTargetInClan(Player p, String name) {
        p.sendMessage(PREFIX + ChatColor.RED + name + " is already in another clan.");
    }

    public void sendPlayerNotFound(Player p, String name) {
        p.sendMessage(PREFIX + ChatColor.RED + "Player \"" + name + "\" not found.");
    }

    public void sendKicked(Player p, String targetName) {
        p.sendMessage(PREFIX + ChatColor.GREEN + "Kicked " + ChatColor.YELLOW
                + targetName + ChatColor.GREEN + " from the clan.");
    }

    public void sendKickTarget(Player target, String clanName) {
        target.sendMessage(PREFIX + ChatColor.RED + "You were kicked from clan \""
                + ChatColor.AQUA + clanName + ChatColor.RED + "\".");
    }

    public void sendNotMember(Player p, String name) {
        p.sendMessage(PREFIX + ChatColor.RED + name + " is not in your clan.");
    }

    public void sendCannotKickLeader(Player p) {
        p.sendMessage(PREFIX + ChatColor.RED + "You cannot kick the clan leader.");
    }

    // ── Delete / leave ──────────────────────────────────────────────────────────

    public void sendDeleted(Player p, String clanName) {
        p.sendMessage(PREFIX + ChatColor.GREEN + "Clan \""
                + ChatColor.AQUA + clanName + ChatColor.GREEN + "\" has been deleted.");
    }

    public void sendClanDeletedBroadcast(String clanName) {
        Bukkit.broadcastMessage(PREFIX + ChatColor.GRAY + "Clan \""
                + ChatColor.AQUA + clanName + ChatColor.GRAY + "\" has been disbanded.");
    }

    public void sendLeft(Player p, String clanName) {
        p.sendMessage(PREFIX + ChatColor.GREEN + "You left clan \""
                + ChatColor.AQUA + clanName + ChatColor.GREEN + "\".");
    }

    public void sendLeaderCannotLeave(Player p) {
        p.sendMessage(PREFIX + ChatColor.RED
                + "The leader cannot leave. Use /clan delete instead.");
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    public void sendNoClan(Player p) {
        p.sendMessage(PREFIX + ChatColor.RED + "You are not in a clan.");
    }

    public void sendInfo(Player p, ClanData clan) {
        p.sendMessage(ChatColor.DARK_AQUA + "" + ChatColor.BOLD
                + "\u25c6 Clan: " + ChatColor.AQUA + clan.getName());
        String leaderName = nameOf(clan.getLeader());
        p.sendMessage(ChatColor.GRAY + "  Leader: " + ChatColor.WHITE + leaderName);
        p.sendMessage(ChatColor.GRAY + "  Members (" + clan.getMembers().size() + "):");
        for (UUID uuid : clan.getMembers()) {
            String name = nameOf(uuid);
            ChatColor color = uuid.equals(clan.getLeader())
                    ? ChatColor.GOLD : ChatColor.WHITE;
            p.sendMessage("    " + color + "\u2022 " + name);
        }
    }

    public void sendInventoryOpened(Player p, String clanName) {
        p.sendMessage(PREFIX + ChatColor.GRAY + "Opened clan chest for \""
                + ChatColor.AQUA + clanName + ChatColor.GRAY + "\".");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        return off.getName() != null ? off.getName() : uuid.toString().substring(0, 8);
    }
}
