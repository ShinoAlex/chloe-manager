package com.chloe.chloemanager.controller;

import com.chloe.chloemanager.ChloeManagerPlugin;
import com.chloe.chloemanager.model.AhListing;
import com.chloe.chloemanager.model.AhModel;
import com.chloe.chloemanager.model.EconomyModel;
import com.chloe.chloemanager.view.AhGUI;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CONTROLLER — AhController
 *
 * Handles:
 *   /ah               → opens the market GUI (page 0)
 *   /ah sell [amount] → lists the held item for the given price
 *
 * GUI interactions:
 *   Market page:
 *     Left-click  item slot → open confirm dialog (if not own listing)
 *     Right-click item slot → remove listing (if own listing)
 *     Left-click  SLOT_TEMP_INV → open temp inventory
 *     Left-click  SLOT_PREV / SLOT_NEXT → paginate
 *
 *   Temp inventory page:
 *     Left-click  item slot → retrieve item to player inventory (drop if full)
 *     Left-click  SLOT_TEMP_INV (back button) → return to market
 *     Left-click  SLOT_PREV / SLOT_NEXT → paginate
 *
 *   Confirm dialog:
 *     CONFIRM_YES → execute purchase
 *     CONFIRM_NO  → close dialog, reopen market
 */
public class AhController implements CommandExecutor, TabCompleter, Listener {

    private static final DecimalFormat FMT = new DecimalFormat("#,##0.00");
    private static final String PREFIX = ChatColor.DARK_PURPLE + "[AH] " + ChatColor.RESET;

    // ── Per-player GUI state ──────────────────────────────────────────────────
    private final Map<UUID, Integer>    marketPage  = new HashMap<>();
    private final Map<UUID, Integer>    tempInvPage = new HashMap<>();
    /** Listing ID currently pending confirmation for each player. */
    private final Map<UUID, String>     pendingBuy  = new HashMap<>();
    /** Whether the player is viewing temp inventory (vs market). */
    private final Set<UUID>             viewingTemp = new HashSet<>();

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final ChloeManagerPlugin plugin;
    private final AhModel            ahModel;
    private final EconomyModel       economy;
    private final AhGUI              gui;

    // Hourly expiry check task
    private org.bukkit.scheduler.BukkitTask expiryTask;

    public AhController(ChloeManagerPlugin plugin, AhModel ahModel,
                        EconomyModel economy, AhGUI gui) {
        this.plugin  = plugin;
        this.ahModel = ahModel;
        this.economy = economy;
        this.gui     = gui;
    }

    public void startExpiryTask() {
        // Check expiry every 5 minutes (6000 ticks)
        expiryTask = new BukkitRunnable() {
            @Override public void run() { ahModel.processExpired(); }
        }.runTaskTimer(plugin, 6000L, 6000L);
    }

    public void stopExpiryTask() {
        if (expiryTask != null) expiryTask.cancel();
    }

    // =========================================================================
    // Commands
    // =========================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Only players can use /ah.");
            return true;
        }

        if (args.length == 0) {
            openMarket(player, 0);
            return true;
        }

        if (args[0].equalsIgnoreCase("sell")) {
            handleSell(player, args);
            return true;
        }

        player.sendMessage(PREFIX + ChatColor.YELLOW + "Usage: /ah  |  /ah sell <price> [amount]");
        return true;
    }

    // ── /ah sell <price> [amount] ─────────────────────────────────────────────

    private void handleSell(Player player, String[] args) {
        // /ah sell <price> [amount]
        if (args.length < 2) {
            player.sendMessage(PREFIX + ChatColor.RED + "Usage: /ah sell <price> [amount]");
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Bạn phải cầm item muốn bán.");
            return;
        }

        double price;
        try {
            price = Double.parseDouble(args[1]);
            if (price <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(PREFIX + ChatColor.RED + "Giá không hợp lệ.");
            return;
        }

        int maxAmount = held.getAmount();
        int amount    = maxAmount; // default: sell everything held
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount <= 0 || amount > maxAmount) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                player.sendMessage(PREFIX + ChatColor.RED
                        + "Số lượng không hợp lệ (1 – " + maxAmount + ").");
                return;
            }
        }

        // Take item from player's hand
        ItemStack toList = held.clone();
        toList.setAmount(1); // single-unit item; amount tracked in listing

        if (held.getAmount() == amount) {
            player.getInventory().setItemInMainHand(null);
        } else {
            held.setAmount(held.getAmount() - amount);
        }

        AhListing listing = new AhListing(
                player.getUniqueId(), player.getName(), toList, amount, price);
        ahModel.addListing(listing);

        player.sendMessage(PREFIX + ChatColor.GREEN + "Đã đăng "
                + ChatColor.YELLOW + "x" + amount + " " + formatName(toList.getType())
                + ChatColor.GREEN + " với giá "
                + ChatColor.GOLD + "$" + FMT.format(price)
                + ChatColor.GREEN + ". Hết hạn sau 3 ngày.");
    }

    // =========================================================================
    // GUI open helpers
    // =========================================================================

    private void openMarket(Player player, int page) {
        ahModel.processExpired();
        viewingTemp.remove(player.getUniqueId());
        marketPage.put(player.getUniqueId(), page);
        List<AhListing> listings = ahModel.getActiveListings();
        double balance = economy.getBalance(player.getUniqueId());
        player.openInventory(gui.buildMarketPage(listings, page, balance, player.getUniqueId()));
    }

    private void openTempInv(Player player, int page) {
        viewingTemp.add(player.getUniqueId());
        tempInvPage.put(player.getUniqueId(), page);
        List<ItemStack> items = new ArrayList<>(ahModel.getTempInv(player.getUniqueId()));
        double balance = economy.getBalance(player.getUniqueId());
        player.openInventory(gui.buildTempInvPage(items, page, balance));
    }

    private void openConfirm(Player player, AhListing listing) {
        pendingBuy.put(player.getUniqueId(), listing.getId());
        double balance = economy.getBalance(player.getUniqueId());
        player.openInventory(gui.buildConfirmDialog(listing, balance));
    }

    // =========================================================================
    // Inventory click handler
    // =========================================================================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (title == null) return;

        boolean isMarket  = AhGUI.TITLE_MARKET.equals(title);
        boolean isTemp    = AhGUI.TITLE_TEMP.equals(title);
        boolean isConfirm = AhGUI.TITLE_CONFIRM.equals(title);

        if (!isMarket && !isTemp && !isConfirm) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        if (isMarket)  { handleMarketClick(player, event.getInventory(), slot, event.getClick()); return; }
        if (isTemp)    { handleTempClick(player, event.getInventory(), slot); return; }
        if (isConfirm) { handleConfirmClick(player, slot); }
    }

    // ── Market click ──────────────────────────────────────────────────────────

    private void handleMarketClick(Player player, Inventory inv, int slot, ClickType click) {
        UUID uuid = player.getUniqueId();

        // Navigation row
        if (slot == AhGUI.SLOT_PREV) {
            int page = Math.max(0, marketPage.getOrDefault(uuid, 0) - 1);
            openMarket(player, page);
            return;
        }
        if (slot == AhGUI.SLOT_NEXT) {
            int page = marketPage.getOrDefault(uuid, 0) + 1;
            List<AhListing> listings = ahModel.getActiveListings();
            if (page < gui.totalPages(listings.size())) openMarket(player, page);
            return;
        }
        if (slot == AhGUI.SLOT_TEMP_INV) {
            openTempInv(player, 0);
            return;
        }
        if (slot == AhGUI.SLOT_BALANCE || slot == AhGUI.SLOT_PAGE_INFO) return;

        // Item slot
        if (slot >= AhGUI.ITEMS_PER_PAGE) return;

        ItemStack clicked = inv.getItem(slot);
        if (clicked == null || clicked.getType().isAir()) return;

        String listingId = AhGUI.extractListingId(clicked);
        if (listingId == null) return;

        AhListing listing = ahModel.getById(listingId);
        if (listing == null || listing.getStatus() != AhListing.Status.ACTIVE) {
            player.sendMessage(PREFIX + ChatColor.RED + "Listing không còn tồn tại.");
            openMarket(player, marketPage.getOrDefault(uuid, 0));
            return;
        }

        boolean isOwner = listing.getSellerUuid().equals(uuid);

        if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            if (isOwner) {
                ahModel.removeListing(listingId);
                player.sendMessage(PREFIX + ChatColor.YELLOW + "Đã rút listing. Item chuyển vào kho tạm.");
                openMarket(player, marketPage.getOrDefault(uuid, 0));
            } else {
                player.sendMessage(PREFIX + ChatColor.RED + "Bạn không thể xoá listing của người khác.");
            }
        } else {
            // Left click → buy
            if (isOwner) {
                player.sendMessage(PREFIX + ChatColor.YELLOW + "Bạn không thể mua listing của chính mình.");
                return;
            }
            openConfirm(player, listing);
        }
    }

    // ── Temp inventory click ──────────────────────────────────────────────────

    private void handleTempClick(Player player, Inventory inv, int slot) {
        UUID uuid = player.getUniqueId();

        if (slot == AhGUI.SLOT_PREV) {
            int page = Math.max(0, tempInvPage.getOrDefault(uuid, 0) - 1);
            openTempInv(player, page);
            return;
        }
        if (slot == AhGUI.SLOT_NEXT) {
            int page = tempInvPage.getOrDefault(uuid, 0) + 1;
            if (page < gui.totalPages(ahModel.getTempInv(uuid).size()))
                openTempInv(player, page);
            return;
        }
        if (slot == AhGUI.SLOT_TEMP_INV) {
            // Back to market
            openMarket(player, marketPage.getOrDefault(uuid, 0));
            return;
        }
        if (slot == AhGUI.SLOT_BALANCE || slot == AhGUI.SLOT_PAGE_INFO) return;
        if (slot >= AhGUI.ITEMS_PER_PAGE) return;

        // Calculate the real index in the model
        int page      = tempInvPage.getOrDefault(uuid, 0);
        int realIndex = page * AhGUI.ITEMS_PER_PAGE + slot;

        ItemStack claimed = ahModel.claimTempItem(uuid, realIndex);
        if (claimed == null) return;

        // Give to player or drop
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(claimed);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(excess ->
                    player.getWorld().dropItemNaturally(player.getLocation(), excess));
            player.sendMessage(PREFIX + ChatColor.YELLOW
                    + "Inventory đầy — một số item đã rơi xuống đất.");
        } else {
            player.sendMessage(PREFIX + ChatColor.GREEN + "Đã nhận item vào inventory.");
        }

        // Refresh the temp inv page (item list has changed)
        Bukkit.getScheduler().runTask(plugin, () -> openTempInv(player, 
                Math.min(page, gui.totalPages(ahModel.getTempInv(uuid).size()) - 1)));
    }

    // ── Confirm dialog click ──────────────────────────────────────────────────

    private void handleConfirmClick(Player player, int slot) {
        UUID   uuid      = player.getUniqueId();
        String listingId = pendingBuy.remove(uuid);

        if (slot == AhGUI.CONFIRM_NO || listingId == null) {
            openMarket(player, marketPage.getOrDefault(uuid, 0));
            return;
        }

        if (slot != AhGUI.CONFIRM_YES) return; // clicked elsewhere in dialog

        AhListing listing = ahModel.getById(listingId);
        if (listing == null || listing.getStatus() != AhListing.Status.ACTIVE) {
            player.sendMessage(PREFIX + ChatColor.RED + "Listing không còn tồn tại.");
            openMarket(player, marketPage.getOrDefault(uuid, 0));
            return;
        }

        double balance = economy.getBalance(uuid);
        if (balance < listing.getPrice()) {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "Không đủ tiền! Cần $" + FMT.format(listing.getPrice())
                    + ", bạn có $" + FMT.format(balance) + ".");
            openMarket(player, marketPage.getOrDefault(uuid, 0));
            return;
        }

        // Execute purchase
        economy.withdraw(uuid, listing.getPrice());
        economy.deposit(listing.getSellerUuid(), listing.getPrice());
        ahModel.markSold(listingId);

        // Give item to buyer
        ItemStack item = listing.getItem();
        item.setAmount(listing.getAmount());
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(excess ->
                    player.getWorld().dropItemNaturally(player.getLocation(), excess));
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Inventory đầy — item rơi xuống đất.");
        }

        player.sendMessage(PREFIX + ChatColor.GREEN + "Đã mua "
                + ChatColor.YELLOW + "x" + listing.getAmount() + " " + formatName(listing.getItem().getType())
                + ChatColor.GREEN + " với giá "
                + ChatColor.GOLD + "$" + FMT.format(listing.getPrice())
                + ChatColor.GREEN + ". Số dư: "
                + ChatColor.GOLD + "$" + FMT.format(economy.getBalance(uuid)));

        // Notify seller if online
        Player seller = Bukkit.getPlayer(listing.getSellerUuid());
        if (seller != null && seller.isOnline()) {
            seller.sendMessage(PREFIX + ChatColor.GREEN
                    + player.getName() + " đã mua "
                    + ChatColor.YELLOW + "x" + listing.getAmount() + " " + formatName(listing.getItem().getType())
                    + ChatColor.GREEN + " với giá "
                    + ChatColor.GOLD + "$" + FMT.format(listing.getPrice()) + ".");
        }

        openMarket(player, marketPage.getOrDefault(uuid, 0));
    }

    // =========================================================================
    // Tab completion
    // =========================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) return filterStart(args[0], List.of("sell"));
        if (args.length == 2 && args[0].equalsIgnoreCase("sell"))
            return List.of("<price>");
        if (args.length == 3 && args[0].equalsIgnoreCase("sell"))
            return List.of("<amount>");
        return List.of();
    }

    private List<String> filterStart(String partial, List<String> options) {
        String lower = partial.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }

    private String formatName(org.bukkit.Material mat) {
        String raw = mat.name().replace('_', ' ').toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String w : raw.split(" ")) {
            if (!w.isEmpty())
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
