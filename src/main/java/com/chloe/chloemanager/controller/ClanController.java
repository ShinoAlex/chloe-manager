package com.chloe.chloemanager.controller;

import com.chloe.chloemanager.ChloeManagerPlugin;
import com.chloe.chloemanager.model.ClanModel;
import com.chloe.chloemanager.model.ClanModel.ClanData;
import com.chloe.chloemanager.model.EconomyModel;
import com.chloe.chloemanager.view.ClanView;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * CONTROLLER — ClanController
 *
 * Commands:
 *   /clan create <name>     Create a clan ($2,000)
 *   /clan add <player>      Invite a player (leader only)
 *   /clan kick <player>     Kick a member   (leader only)
 *   /clan delete            Delete the clan (leader only)
 *   /clan inventory         Open shared clan chest (any member)
 *   /clan leave             Leave the clan (non-leader members)
 *   /clan info              Show clan info
 *
 * The shared inventory is a 27-slot chest that every member can open and
 * use.  Contents are saved on close and on plugin shutdown.
 */
public class ClanController implements CommandExecutor, Listener, TabCompleter {

    private static final double CREATE_COST = 2000.0;
    private static final List<String> SUBS = Arrays.asList(
            "create", "add", "kick", "delete", "inventory", "leave", "info");

    private final ChloeManagerPlugin plugin;
    private final ClanModel    clanModel;
    private final EconomyModel economy;
    private final ClanView     view;

    public ClanController(ChloeManagerPlugin plugin, ClanModel clanModel,
                          EconomyModel economy, ClanView view) {
        this.plugin   = plugin;
        this.clanModel = clanModel;
        this.economy  = economy;
        this.view     = view;
    }

    // =========================================================================
    // Command
    // =========================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            view.sendMustBePlayer(sender);
            return true;
        }
        if (args.length == 0) { view.sendUsage(player); return true; }

        switch (args[0].toLowerCase()) {
            case "create"    -> handleCreate(player, args);
            case "add"       -> handleAdd(player, args);
            case "kick"      -> handleKick(player, args);
            case "delete"    -> handleDelete(player);
            case "inventory" -> handleInventory(player);
            case "leave"     -> handleLeave(player);
            case "info"      -> handleInfo(player);
            default          -> view.sendUsage(player);
        }
        return true;
    }

    // ── /clan create ──────────────────────────────────────────────────────────

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) { view.sendUsage(player); return; }

        if (clanModel.isInClan(player.getUniqueId())) {
            view.sendAlreadyInClan(player);
            return;
        }

        String name = args[1];
        if (!isValidName(name)) { view.sendInvalidName(player); return; }
        if (clanModel.clanExists(name)) { view.sendClanNameExists(player, name); return; }

        double balance = economy.getBalance(player.getUniqueId());
        if (balance < CREATE_COST) { view.sendNotEnoughMoney(player, CREATE_COST, balance); return; }

        economy.withdraw(player.getUniqueId(), CREATE_COST);
        clanModel.createClan(name, player.getUniqueId());
        view.sendCreated(player, name, CREATE_COST);
    }

    // ── /clan add ──────────────────────────────────────────────────────────────

    private void handleAdd(Player player, String[] args) {
        if (args.length < 2) { view.sendUsage(player); return; }

        UUID uuid = player.getUniqueId();
        String clanName = clanModel.getPlayerClan(uuid);
        if (clanName == null) { view.sendNoClan(player); return; }
        if (!clanModel.isLeader(clanName, uuid)) { view.sendNotLeader(player); return; }

        UUID target = resolvePlayer(args[1]);
        if (target == null) { view.sendPlayerNotFound(player, args[1]); return; }
        if (clanModel.isInClan(target)) { view.sendTargetInClan(player, args[1]); return; }
        if (clanModel.isMember(clanName, target)) { view.sendAlreadyMember(player, args[1]); return; }

        clanModel.addMember(clanName, target);
        view.sendAdded(player, args[1], clanModel.getClan(clanName).getName());
        Player targetOnline = Bukkit.getPlayer(target);
        if (targetOnline != null) {
            view.sendAddTarget(targetOnline,
                    clanModel.getClan(clanName).getName(), player.getName());
        }
    }

    // ── /clan kick ──────────────────────────────────────────────────────────────

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) { view.sendUsage(player); return; }

        UUID uuid = player.getUniqueId();
        String clanName = clanModel.getPlayerClan(uuid);
        if (clanName == null) { view.sendNoClan(player); return; }
        if (!clanModel.isLeader(clanName, uuid)) { view.sendNotLeader(player); return; }

        UUID target = resolvePlayer(args[1]);
        if (target == null) { view.sendPlayerNotFound(player, args[1]); return; }
        if (!clanModel.isMember(clanName, target)) { view.sendNotMember(player, args[1]); return; }
        if (target.equals(uuid)) { view.sendCannotKickLeader(player); return; }

        closeIfOpen(target);
        clanModel.kickMember(clanName, target);
        view.sendKicked(player, args[1]);
        Player targetOnline = Bukkit.getPlayer(target);
        if (targetOnline != null) {
            view.sendKickTarget(targetOnline, clanModel.getClan(clanName).getName());
        }
    }

    // ── /clan delete ────────────────────────────────────────────────────────────

    private void handleDelete(Player player) {
        UUID uuid = player.getUniqueId();
        String clanName = clanModel.getPlayerClan(uuid);
        if (clanName == null) { view.sendNoClan(player); return; }
        if (!clanModel.isLeader(clanName, uuid)) { view.sendNotLeader(player); return; }

        ClanData clan = clanModel.getClan(clanName);
        String displayName = clan != null ? clan.getName() : clanName;

        // Close inventory for any online member viewing it
        if (clan != null) {
            for (UUID m : clan.getMembers()) closeIfOpen(m);
        }

        clanModel.deleteClan(clanName);
        view.sendDeleted(player, displayName);
        view.sendClanDeletedBroadcast(displayName);
    }

    // ── /clan inventory ──────────────────────────────────────────────────────

    private void handleInventory(Player player) {
        UUID uuid = player.getUniqueId();
        String clanName = clanModel.getPlayerClan(uuid);
        if (clanName == null) { view.sendNoClan(player); return; }

        ClanData clan = clanModel.getClan(clanName);
        if (clan == null || clan.getInventory() == null) { view.sendNoClan(player); return; }

        player.openInventory(clan.getInventory());
    }

    // ── /clan leave ────────────────────────────────────────────────────────────

    private void handleLeave(Player player) {
        UUID uuid = player.getUniqueId();
        String clanName = clanModel.getPlayerClan(uuid);
        if (clanName == null) { view.sendNoClan(player); return; }
        if (clanModel.isLeader(clanName, uuid)) { view.sendLeaderCannotLeave(player); return; }

        ClanData clan = clanModel.getClan(clanName);
        String displayName = clan != null ? clan.getName() : clanName;

        closeIfOpen(uuid);
        clanModel.leaveClan(uuid);
        view.sendLeft(player, displayName);
    }

    // ── /clan info ──────────────────────────────────────────────────────────────

    private void handleInfo(Player player) {
        UUID uuid = player.getUniqueId();
        String clanName = clanModel.getPlayerClan(uuid);
        if (clanName == null) { view.sendNoClan(player); return; }
        ClanData clan = clanModel.getClan(clanName);
        if (clan == null) { view.sendNoClan(player); return; }
        view.sendInfo(player, clan);
    }

    // =========================================================================
    // Inventory close — save clan data
    // =========================================================================

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (clanModel.isClanInventory(event.getInventory())) {
            clanModel.save();
        }
    }

    // =========================================================================
    // Chat — prepend clan tag [ClanName]
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String clanName = clanModel.getPlayerClan(player.getUniqueId());
        if (clanName == null) return;

        ClanData clan = clanModel.getClan(clanName);
        if (clan == null) return;

        // Format: [ClanName] PlayerName: message
        // Use HIGHEST priority so we append to whatever format other plugins set
        String tag = net.md_5.bungee.api.ChatColor.DARK_AQUA + "["
                + net.md_5.bungee.api.ChatColor.AQUA + clan.getName()
                + net.md_5.bungee.api.ChatColor.DARK_AQUA + "] "
                + net.md_5.bungee.api.ChatColor.RESET;

        event.setFormat(tag + event.getFormat());
    }

    // =========================================================================
    // Tab completion
    // =========================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                       String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("add")
                || args[0].equalsIgnoreCase("kick"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase()))
                    names.add(p.getName());
            }
            return names;
        }
        return List.of();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Resolves a player by name — online first, then offline. */
    private UUID resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        if (off != null && off.hasPlayedBefore()) return off.getUniqueId();
        return null;
    }

    private boolean isValidName(String name) {
        if (name.length() < 3 || name.length() > 16) return false;
        for (char c : name.toCharArray()) {
            if (!(Character.isLetterOrDigit(c) || c == '_')) return false;
        }
        return true;
    }

    /** Closes the clan inventory if the given player currently has it open. */
    private void closeIfOpen(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null) return;
        Inventory open = p.getOpenInventory().getTopInventory();
        if (clanModel.isClanInventory(open)) p.closeInventory();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> result = new ArrayList<>();
        for (String s : options) {
            if (s.toLowerCase().startsWith(prefix.toLowerCase())) result.add(s);
        }
        return result;
    }
}
