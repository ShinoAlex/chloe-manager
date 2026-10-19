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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * CONTROLLER — EconomyController
 *
 * Commands: /sell, /sell all, /balance, /bal, /baltop, /pay
 * GUI: sell GUI click and close handling.
 */
public class EconomyController implements CommandExecutor, TabCompleter, Listener {

    private final EconomyModel      model;
    private final SellPriceRegistry registry;
    private final EconomyView       view;
    private final SellGUI           gui;

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
        switch (command.getName().toLowerCase()) {
            case "sell"           -> handleSell(sender, args);
            case "balance", "bal" -> handleBalance(sender, args);
            case "baltop"         -> handleBaltop(sender);
            case "pay"            -> handlePay(sender, args);
        }
        return true;
    }

    // ── /sell ─────────────────────────────────────────────────────────────────

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

    private void sellAll(Player player) {
        double totalEarned = 0;
        int    itemsSold   = 0;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() == Material.AIR) continue;
            double price = registry.getPrice(stack.getType());
            if (price < 0) continue;
            totalEarned += price * stack.getAmount();
            itemsSold   += stack.getAmount();
            player.getInventory().setItem(i, null);
        }

        if (itemsSold > 0) model.deposit(player.getUniqueId(), totalEarned);
        view.sendSellAllResult(player, itemsSold, totalEarned,
                model.getBalance(player.getUniqueId()));
    }

    // ── /balance (/bal) ───────────────────────────────────────────────────────

    private void handleBalance(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) { view.sendMustBePlayer(sender); return; }
            view.sendOwnBalance(player, model.getBalance(player.getUniqueId()));
        } else if (args.length == 1) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null) {
                view.sendOtherBalance(sender, target.getName(),
                        model.getBalance(target.getUniqueId()));
            } else {
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

    // ── /baltop ───────────────────────────────────────────────────────────────

    private void handleBaltop(CommandSender sender) {
        List<Map.Entry<UUID, Double>> top = model.getTopBalances(10);
        view.sendBaltop(sender, top, uuid -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            return (op.getName() != null) ? op.getName() : uuid.toString().substring(0, 8);
        });
    }

    // ── /pay ──────────────────────────────────────────────────────────────────

    private void handlePay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player payer)) { view.sendMustBePlayer(sender); return; }
        if (args.length != 2)                  { view.sendPayUsage(sender);     return; }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            view.sendInvalidAmount(sender);
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null)                                    { view.sendPlayerNotFound(sender, args[0]); return; }
        if (target.getUniqueId().equals(payer.getUniqueId())) { view.sendCannotPaySelf(sender);           return; }

        if (!model.withdraw(payer.getUniqueId(), amount)) {
            view.sendInsufficientFunds(sender, model.getBalance(payer.getUniqueId()));
            return;
        }

        model.deposit(target.getUniqueId(), amount);
        view.sendPaySuccess(payer, target.getName(), amount, model.getBalance(payer.getUniqueId()));
        view.sendPayReceived(target, payer.getName(), amount, model.getBalance(target.getUniqueId()));
    }

    // =========================================================================
    // Tab completion
    // =========================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        return switch (command.getName().toLowerCase()) {
            case "balance" -> args.length == 1 ? onlinePlayers(args[0]) : List.of();
            case "sell"    -> args.length == 1 ? List.of("all")         : List.of();
            case "pay"     -> args.length == 1 ? onlinePlayers(args[0]) : List.of();
            default        -> List.of();
        };
    }

    private List<String> onlinePlayers(String partial) {
        String lower = partial.toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Sell GUI — inventory events
    // =========================================================================

    /** Returns true only if this inventory is our Sell GUI. */
    private boolean isSellGUI(InventoryClickEvent event) {
        return SellGUI.TITLE.equals(event.getView().getTitle());
    }

    private boolean isSellGUI(InventoryCloseEvent event) {
        return SellGUI.TITLE.equals(event.getView().getTitle());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isSellGUI(event)) return;

        int slot = event.getRawSlot();
        Inventory topInv = event.getView().getTopInventory();

        // ── Click in player's own inventory (bottom) ────────────────────────
        if (slot >= topInv.getSize()) {
            // Shift-click from player inventory → move item to sell slot
            if (event.getClick() == ClickType.SHIFT_LEFT
                    || event.getClick() == ClickType.SHIFT_RIGHT) {

                event.setCancelled(true);
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || clicked.getType() == Material.AIR) return;

                // Only place if sell slot is empty
                ItemStack current = topInv.getItem(SellGUI.SELL_SLOT);
                if (current != null && current.getType() != Material.AIR) return;

                // Move the stack to the sell slot
                topInv.setItem(SellGUI.SELL_SLOT, clicked.clone());
                event.setCurrentItem(null);

                // Refresh confirm button next tick
                Bukkit.getScheduler().runTask(
                        com.chloe.chloemanager.ChloeManagerPlugin.getInstance(),
                        () -> gui.refreshConfirmButton(topInv));
            }
            // All other bottom-inventory clicks are allowed freely
            return;
        }

        // ── Click inside the Sell GUI ────────────────────────────────────────
        if (slot == SellGUI.SELL_SLOT) {
            // Allow the click so player can place/take items naturally,
            // then refresh the confirm button after the click resolves.
            Bukkit.getScheduler().runTask(
                    com.chloe.chloemanager.ChloeManagerPlugin.getInstance(),
                    () -> {
                        if (player.getOpenInventory() != null)
                            gui.refreshConfirmButton(player.getOpenInventory().getTopInventory());
                    });
            return; // do NOT cancel
        }

        if (slot == SellGUI.CONFIRM_SLOT) {
            event.setCancelled(true);
            handleGUISell(player, topInv);
            return;
        }

        // All other GUI slots (panes) — cancel
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!isSellGUI(event)) return;

        // Return any item left in the sell slot to the player
        Inventory inv      = event.getInventory();
        ItemStack leftover = inv.getItem(SellGUI.SELL_SLOT);
        if (leftover != null && leftover.getType() != Material.AIR) {
            inv.setItem(SellGUI.SELL_SLOT, null);
            player.getInventory().addItem(leftover).values()
                    .forEach(excess -> player.getWorld().dropItemNaturally(
                            player.getLocation(), excess));
        }
    }

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
