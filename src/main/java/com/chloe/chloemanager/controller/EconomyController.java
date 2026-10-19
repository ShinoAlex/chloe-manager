package com.chloe.chloemanager.controller;

import com.chloe.chloemanager.model.EconomyModel;
import com.chloe.chloemanager.model.SellPriceRegistry;
import com.chloe.chloemanager.view.EconomyView;
import com.chloe.chloemanager.view.SellGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * CONTROLLER — EconomyController
 *
 * Handles all economy commands and the sell GUI interaction.
 *
 * Commands registered:
 *   /sell            — opens the sell GUI
 *   /sell all        — auto-sells all sellable items in inventory
 *   /balance [name]  — shows balance (alias: /bal)
 *   /baltop          — top 10 richest players
 *   /pay <name> <amt>— transfers money between players
 */
public class EconomyController implements CommandExecutor, TabCompleter, Listener {

    private static final String GUI_TITLE_CONTAINS = "Sell Items";

    private final EconomyModel  model;
    private final SellPriceRegistry registry;
    private final EconomyView   view;
    private final SellGUI       gui;

    public EconomyController(EconomyModel model,
                             SellPriceRegistry registry,
                             EconomyView view,
                             SellGUI gui) {
        this.model    = model;
        this.registry = registry;
        this.view     = view;
        this.gui      = gui;
    }

    // =========================================================================
    // Command dispatch
    // =========================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "sell"    -> handleSell(sender, args);
            case "balance",
                 "bal"     -> handleBalance(sender, args);
            case "baltop"  -> handleBaltop(sender);
            case "pay"     -> handlePay(sender, args);
            default        -> { /* should not happen */ }
        }

        return true;
    }

    // ── /sell ─────────────────────────────────────────────────────────────

    private void handleSell(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            view.sendMustBePlayer(sender);
            return;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("all")) {
            sellAll(player);
        } else if (args.length == 0) {
            gui.open(player);
        } else {
            view.sendError(sender, "Usage: /sell  or  /sell all");
        }
    }

    /**
     * Iterates the player's inventory and sells every sellable stack.
     * Tools, food (non-fish), armour, etc. are skipped automatically because
     * they are simply not in the price registry.
     */
    private void sellAll(Player player) {
        double totalEarned = 0;
        int itemsSold = 0;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() == Material.AIR) continue;

            double price = registry.getPrice(stack.getType());
            if (price < 0) continue; // not sellable

            totalEarned += price * stack.getAmount();
            itemsSold   += stack.getAmount();
            player.getInventory().setItem(i, null);
        }

        if (itemsSold > 0) {
            model.deposit(player.getUniqueId(), totalEarned);
        }

        view.sendSellAllResult(player, itemsSold, totalEarned,
                model.getBalance(player.getUniqueId()));
    }

    // ── /balance (/bal) ────────────────────────────────────────────────────

    private void handleBalance(CommandSender sender, String[] args) {
        if (args.length == 0) {
            // /bal — show own balance (player only)
            if (!(sender instanceof Player player)) {
                view.sendMustBePlayer(sender);
                return;
            }
            view.sendOwnBalance(player, model.getBalance(player.getUniqueId()));

        } else if (args.length == 1) {
            // /balance <player>
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null) {
                view.sendOtherBalance(sender, target.getName(),
                        model.getBalance(target.getUniqueId()));
            } else {
                // Try offline player
                @SuppressWarnings("deprecation")
                OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
                if (offline != null && offline.hasPlayedBefore()) {
                    view.sendOtherBalance(sender,
                            offline.getName() != null ? offline.getName() : args[0],
                            model.getBalance(offline.getUniqueId()));
                } else {
                    view.sendPlayerNotFound(sender, args[0]);
                }
            }
        } else {
            view.sendBalUsage(sender);
        }
    }

    // ── /baltop ────────────────────────────────────────────────────────────

    private void handleBaltop(CommandSender sender) {
        List<Map.Entry<UUID, Double>> top = model.getTopBalances(10);
        view.sendBaltop(sender, top, uuid -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            return (op.getName() != null) ? op.getName() : uuid.toString().substring(0, 8);
        });
    }

    // ── /pay ───────────────────────────────────────────────────────────────

    private void handlePay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player payer)) {
            view.sendMustBePlayer(sender);
            return;
        }

        if (args.length != 2) {
            view.sendPayUsage(sender);
            return;
        }

        String targetName = args[0];
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            view.sendInvalidAmount(sender);
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            view.sendPlayerNotFound(sender, targetName);
            return;
        }

        if (target.getUniqueId().equals(payer.getUniqueId())) {
            view.sendCannotPaySelf(sender);
            return;
        }

        boolean success = model.withdraw(payer.getUniqueId(), amount);
        if (!success) {
            view.sendInsufficientFunds(sender, model.getBalance(payer.getUniqueId()));
            return;
        }

        model.deposit(target.getUniqueId(), amount);
        view.sendPaySuccess(payer, target.getName(), amount,
                model.getBalance(payer.getUniqueId()));
        view.sendPayReceived(target, payer.getName(), amount,
                model.getBalance(target.getUniqueId()));
    }

    // =========================================================================
    // Tab completion
    // =========================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        String cmd = command.getName().toLowerCase();

        if ((cmd.equals("balance")) && args.length == 1) {
            return onlinePlayers(args[0]);
        }

        if (cmd.equals("sell") && args.length == 1) {
            return List.of("all");
        }

        if (cmd.equals("pay") && args.length == 1) {
            return onlinePlayers(args[0]);
        }

        return List.of();
    }

    private List<String> onlinePlayers(String partial) {
        String lower = partial.toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // GUI event handling
    // =========================================================================

    /**
     * Intercepts all clicks inside the Sell GUI.
     *
     * Behaviour:
     *  - Clicks on decorative panes → cancel (no-op).
     *  - Click on CONFIRM slot with a sellable item in SELL slot → execute sale.
     *  - Clicks on the sell slot → allow vanilla item movement, then refresh button.
     *  - Any other click inside the GUI → cancel.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTitle() == null) return;
        if (!event.getView().getTitle().contains(GUI_TITLE_CONTAINS)) return;

        Inventory inv = event.getInventory();
        int slot = event.getRawSlot();

        // Click is in the top (GUI) inventory
        if (slot >= 0 && slot < inv.getSize()) {
            if (slot == SellGUI.SELL_SLOT) {
                // Allow the click but refresh the confirm button one tick later
                Bukkit.getScheduler().runTask(
                        com.chloe.chloemanager.ChloeManagerPlugin.getInstance(),
                        () -> gui.refreshConfirmButton(player.getOpenInventory().getTopInventory()));
                return; // do NOT cancel — let the player place/take the item
            }

            if (slot == SellGUI.CONFIRM_SLOT) {
                event.setCancelled(true);
                handleGUISell(player, inv);
                return;
            }

            // Everything else in the GUI (panes, etc.) → cancel
            event.setCancelled(true);
        }
        // Clicks in the player's own inventory are allowed normally
    }

    /**
     * When the GUI is closed, return any item still sitting in the sell slot.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getView().getTitle() == null) return;
        if (!event.getView().getTitle().contains(GUI_TITLE_CONTAINS)) return;

        ItemStack leftover = event.getInventory().getItem(SellGUI.SELL_SLOT);
        if (leftover != null && leftover.getType() != Material.AIR) {
            event.getInventory().setItem(SellGUI.SELL_SLOT, null);
            player.getInventory().addItem(leftover).values()
                    .forEach(excess -> player.getWorld().dropItemNaturally(player.getLocation(), excess));
        }
    }

    /** Executes the sale for the item currently in the sell slot. */
    private void handleGUISell(Player player, Inventory inv) {
        ItemStack item = inv.getItem(SellGUI.SELL_SLOT);

        if (item == null || item.getType() == Material.AIR) {
            view.sendNothingToSell(player);
            return;
        }

        double price = registry.getPrice(item.getType());
        if (price < 0) {
            view.sendItemNotSellable(player);
            return;
        }

        double earned = price * item.getAmount();
        inv.setItem(SellGUI.SELL_SLOT, null);
        model.deposit(player.getUniqueId(), earned);
        gui.refreshConfirmButton(inv);

        view.sendSellSuccess(player, earned, model.getBalance(player.getUniqueId()));
    }
}
