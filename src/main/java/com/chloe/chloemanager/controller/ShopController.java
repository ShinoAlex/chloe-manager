package com.chloe.chloemanager.controller;

import com.chloe.chloemanager.model.EconomyModel;
import com.chloe.chloemanager.model.HomeModel;
import com.chloe.chloemanager.model.ShopRegistry;
import com.chloe.chloemanager.model.ShopRegistry.ShopCategory;
import com.chloe.chloemanager.model.ShopRegistry.ShopEntry;
import com.chloe.chloemanager.view.EconomyView;
import com.chloe.chloemanager.view.ShopGUI;
import com.chloe.chloemanager.view.ShopGUI.ShopHolder;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CONTROLLER — ShopController
 *
 * Handles:
 *   /shop          — opens the main category GUI
 *   GUI navigation — category selection, pagination, back button
 *   Purchases      — left-click = buy x1 (or entry stackSize),
 *                    right-click = buy x64 (capped by stack size)
 *   Special purchases — entries with a customAction (e.g. sethome purchases)
 *                    are handled without giving an item.
 */
public class ShopController implements CommandExecutor, Listener {

    private static final DecimalFormat FMT = new DecimalFormat("#,##0.00");

    // ── Injected dependencies ────────────────────────────────────────────────
    private final EconomyModel  economyModel;
    private final ShopRegistry  registry;
    private final EconomyView   economyView;
    private final ShopGUI       gui;
    private final HomeModel      homeModel;   // for sethome purchases

    // ── Per-player GUI state ─────────────────────────────────────────────────
    /** Which category each player is currently browsing (null = main menu). */
    private final Map<UUID, ShopCategory> playerCategory = new HashMap<>();
    /** Which page each player is on within their current category. */
    private final Map<UUID, Integer>      playerPage     = new HashMap<>();

    public ShopController(EconomyModel economyModel,
                          ShopRegistry registry,
                          EconomyView economyView,
                          ShopGUI gui,
                          HomeModel homeModel) {
        this.economyModel = economyModel;
        this.registry     = registry;
        this.economyView  = economyView;
        this.gui          = gui;
        this.homeModel    = homeModel;
    }

    // =========================================================================
    // /shop command
    // =========================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            economyView.sendMustBePlayer(sender);
            return true;
        }
        openMainMenu(player);
        return true;
    }

    // =========================================================================
    // GUI click handler
    // =========================================================================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory top = event.getView().getTopInventory();
        ShopHolder holder = resolveShopHolder(top, event.getView().getTitle());
        if (holder == null) return;

        // Always cancel — shop is read-only, nothing should move
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) return;

        if (holder.isMainMenu()) {
            handleMainMenuClick(player, event.getCurrentItem());
        } else {
            ShopCategory category = holder.getCategory();
            if (category != null) {
                playerCategory.put(player.getUniqueId(), category);
            }
            handleCategoryPageClick(player, top, slot, event.getClick());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (resolveShopHolder(event.getView().getTopInventory(),
                event.getView().getTitle()) != null) {
            event.setCancelled(true);
        }
    }

    /** Prefer the inventory holder; fall back to title text if Paper rewrites it. */
    private ShopHolder resolveShopHolder(Inventory top, String title) {
        if (top != null && top.getHolder() instanceof ShopHolder holder) return holder;
        if (title == null) return null;
        String plain = ChatColor.stripColor(title);
        if (plain == null) return null;
        if (plain.contains("Chloe Shop")) return ShopHolder.main();
        for (ShopCategory cat : registry.getCategories()) {
            String name = ChatColor.stripColor(cat.getDisplayName());
            if (name != null && plain.contains(name)) return ShopHolder.category(cat);
        }
        return null;
    }

    // =========================================================================
    // Main menu logic
    // =========================================================================

    private void openMainMenu(Player player) {
        playerCategory.remove(player.getUniqueId());
        playerPage.remove(player.getUniqueId());
        player.openInventory(gui.buildMainMenu());
    }

    private void handleMainMenuClick(Player player, ItemStack clicked) {
        if (clicked == null) return;
        Material type = clicked.getType();
        if (type == Material.AIR
                || type == Material.LIME_STAINED_GLASS_PANE
                || type == Material.BLACK_STAINED_GLASS_PANE
                || type == Material.NETHER_STAR) {
            return;
        }
        for (ShopCategory cat : registry.getCategories()) {
            if (cat.getIcon() == type) {
                openCategoryPage(player, cat, 0);
                return;
            }
        }
    }

    // =========================================================================
    // Category page logic
    // =========================================================================

    private void openCategoryPage(Player player, ShopCategory category, int page) {
        playerCategory.put(player.getUniqueId(), category);
        playerPage.put(player.getUniqueId(), page);
        double balance = economyModel.getBalance(player.getUniqueId());
        player.openInventory(gui.buildCategoryPage(category, page, balance));
    }

    private void handleCategoryPageClick(Player player, Inventory inv,
                                         int slot, ClickType clickType) {
        UUID uuid     = player.getUniqueId();
        ShopCategory category = playerCategory.get(uuid);
        int page      = playerPage.getOrDefault(uuid, 0);

        if (category == null) return;

        // ── Navigation buttons ───────────────────────────────────────────────
        if (slot == ShopGUI.SLOT_BACK) {
            openMainMenu(player);
            return;
        }

        if (slot == ShopGUI.SLOT_PREV) {
            if (page > 0) openCategoryPage(player, category, page - 1);
            return;
        }

        if (slot == ShopGUI.SLOT_NEXT) {
            List<ShopEntry> entries = registry.getEntries(category);
            int totalPages = gui.totalPages(entries.size());
            if (page < totalPages - 1) openCategoryPage(player, category, page + 1);
            return;
        }

        // ── Item slots (0 – 44) ──────────────────────────────────────────────
        if (slot >= ShopGUI.ITEMS_PER_PAGE) return;  // nav row or out of bounds

        List<ShopEntry> entries = registry.getEntries(category);
        int index = page * ShopGUI.ITEMS_PER_PAGE + slot;
        if (index >= entries.size()) return;  // empty slot

        ShopEntry entry = entries.get(index);

        // For special (non-item) entries, qty is irrelevant — just purchase once
        if (entry.customAction() != null) {
            executeSpecialPurchase(player, inv, entry);
            return;
        }

        // Determine quantity: left-click = stackSize, right-click = 64
        int qty = (clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT)
                ? 64
                : entry.stackSize();

        executePurchase(player, inv, entry, qty);
    }

    // =========================================================================
    // Normal item purchase
    // =========================================================================

    private void executePurchase(Player player, Inventory inv,
                                 ShopEntry entry, int qty) {
        double totalCost = entry.buyPrice() * qty;
        double balance   = economyModel.getBalance(player.getUniqueId());

        if (balance < totalCost) {
            player.sendMessage(ChatColor.RED + "[Shop] " + ChatColor.RESET
                    + "Not enough money! You need "
                    + ChatColor.GOLD + "$" + FMT.format(totalCost)
                    + ChatColor.RESET + " but only have "
                    + ChatColor.GOLD + "$" + FMT.format(balance) + ".");
            return;
        }

        // Check inventory space
        ItemStack toGive = new ItemStack(entry.material(), qty);
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(toGive);
        if (!leftover.isEmpty()) {
            player.sendMessage(ChatColor.RED + "[Shop] " + ChatColor.RESET
                    + "Your inventory is full!");
            return;
        }

        // Deduct money
        economyModel.withdraw(player.getUniqueId(), totalCost);
        double newBalance = economyModel.getBalance(player.getUniqueId());

        // Confirm purchase in chat (concise)
        player.sendMessage(
                ChatColor.GOLD + "[Shop] " + ChatColor.GREEN + "Bought "
                + ChatColor.YELLOW + "x" + qty + " " + entry.displayName()
                + ChatColor.GREEN + " for "
                + ChatColor.GOLD + "$" + FMT.format(totalCost)
                + ChatColor.GREEN + ". Balance: "
                + ChatColor.GOLD + "$" + FMT.format(newBalance));

        // Refresh the balance display without reopening the GUI
        gui.refreshBalance(inv, newBalance);
    }

    // =========================================================================
    // Special purchase (non-item, e.g. extra home slot)
    // =========================================================================

    private void executeSpecialPurchase(Player player, Inventory inv, ShopEntry entry) {
        UUID uuid = player.getUniqueId();
        String action = entry.customAction();

        if (action == null) return;

        if (action.startsWith("sethome:")) {
            handleSetHomePurchase(player, inv, entry,
                    Integer.parseInt(action.substring("sethome:".length())));
        }
    }

    /**
     * Unlocks an additional home slot for the player.
     *
     * @param targetSlot The home slot index to unlock (2 or 3).
     */
    private void handleSetHomePurchase(Player player, Inventory inv,
                                       ShopEntry entry, int targetSlot) {
        UUID uuid = player.getUniqueId();

        // Already unlocked?
        if (homeModel.isSlotUnlocked(uuid, targetSlot)) {
            player.sendMessage(ChatColor.RED + "[Shop] " + ChatColor.RESET
                    + "You already have Home Slot #" + targetSlot + " unlocked!");
            return;
        }

        // Enforce ordering: slot 3 requires slot 2 first
        if (targetSlot == 3 && !homeModel.isSlotUnlocked(uuid, 2)) {
            player.sendMessage(ChatColor.RED + "[Shop] " + ChatColor.RESET
                    + "You must buy Home Slot #2 first!");
            return;
        }

        double balance = economyModel.getBalance(uuid);
        if (balance < entry.buyPrice()) {
            player.sendMessage(ChatColor.RED + "[Shop] " + ChatColor.RESET
                    + "Not enough money! You need "
                    + ChatColor.GOLD + "$" + FMT.format(entry.buyPrice())
                    + ChatColor.RESET + " but only have "
                    + ChatColor.GOLD + "$" + FMT.format(balance) + ".");
            return;
        }

        // Deduct money and unlock the slot
        economyModel.withdraw(uuid, entry.buyPrice());
        homeModel.unlockHomeSlot(uuid);

        double newBalance = economyModel.getBalance(uuid);
        player.sendMessage(
                ChatColor.GOLD + "[Shop] " + ChatColor.GREEN + "Unlocked "
                + ChatColor.YELLOW + entry.displayName()
                + ChatColor.GREEN + " for "
                + ChatColor.GOLD + "$" + FMT.format(entry.buyPrice())
                + ChatColor.GREEN + "! Balance: "
                + ChatColor.GOLD + "$" + FMT.format(newBalance));
        player.sendMessage(ChatColor.AQUA + "[Shop] " + ChatColor.GRAY
                + "Use " + ChatColor.AQUA + "/sethome " + targetSlot
                + ChatColor.GRAY + " to set it and "
                + ChatColor.AQUA + "/home " + targetSlot
                + ChatColor.GRAY + " to teleport there.");

        // Refresh the balance display
        gui.refreshBalance(inv, newBalance);
    }

}
