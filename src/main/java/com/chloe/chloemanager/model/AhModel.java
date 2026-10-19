package com.chloe.chloemanager.model;

import com.chloe.chloemanager.ChloeManagerPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * MODEL — AhModel
 *
 * Manages:
 *  1. Active listings (visible in /ah GUI).
 *  2. Per-player temporary inventories (items pending retrieval after expiry/removal).
 *  3. Persistence — everything is saved to ah_data.yml.
 *
 * Expiry check runs on every GUI open and on a scheduled task.
 */
public class AhModel {

    private static final String FILE_NAME = "ah_data.yml";

    private final File              dataFile;
    private       FileConfiguration cfg;

    /** All listings (active + expired, until fully processed). */
    private final Map<String, AhListing>      listings    = new LinkedHashMap<>();

    /** Per-player temp inventory: UUID → list of ItemStacks awaiting retrieval. */
    private final Map<UUID, List<ItemStack>>  tempInv     = new HashMap<>();

    public AhModel(ChloeManagerPlugin plugin) {
        dataFile = new File(plugin.getDataFolder(), FILE_NAME);
        load();
    }

    // =========================================================================
    // Public API — listings
    // =========================================================================

    /** Adds a new listing and saves. */
    public void addListing(AhListing listing) {
        listings.put(listing.getId(), listing);
        save();
    }

    /** Returns all currently ACTIVE listings (sorted newest-first). */
    public List<AhListing> getActiveListings() {
        return listings.values().stream()
                .filter(l -> l.getStatus() == AhListing.Status.ACTIVE)
                .sorted(Comparator.comparingLong(AhListing::getListedAt).reversed())
                .collect(Collectors.toList());
    }

    /** Returns a listing by its ID, or null. */
    public AhListing getById(String id) {
        return listings.get(id);
    }

    /**
     * Checks all active listings for expiry and moves expired ones to their
     * seller's temp inventory.  Call this periodically and on GUI open.
     */
    public void processExpired() {
        boolean changed = false;
        for (AhListing listing : listings.values()) {
            if (listing.getStatus() == AhListing.Status.ACTIVE && listing.isExpired()) {
                listing.setStatus(AhListing.Status.EXPIRED);
                addToTempInv(listing.getSellerUuid(), listing.getItem(), listing.getAmount());
                changed = true;
            }
        }
        if (changed) save();
    }

    /**
     * Marks a listing as SOLD, saves.
     * The caller is responsible for transferring money and giving the item.
     */
    public void markSold(String id) {
        AhListing l = listings.get(id);
        if (l != null) { l.setStatus(AhListing.Status.SOLD); save(); }
    }

    /**
     * Removes a listing (seller-initiated), moves item to seller's temp inv.
     */
    public void removeListing(String id) {
        AhListing l = listings.get(id);
        if (l == null) return;
        l.setStatus(AhListing.Status.REMOVED);
        addToTempInv(l.getSellerUuid(), l.getItem(), l.getAmount());
        save();
    }

    // =========================================================================
    // Public API — temp inventory
    // =========================================================================

    /** Returns all pending items for this player (unmodifiable view). */
    public List<ItemStack> getTempInv(UUID uuid) {
        return Collections.unmodifiableList(tempInv.getOrDefault(uuid, List.of()));
    }

    public boolean hasTempInv(UUID uuid) {
        List<ItemStack> inv = tempInv.get(uuid);
        return inv != null && !inv.isEmpty();
    }

    /**
     * Removes and returns the item at the given index from the player's temp inv.
     * Returns null if index is out of range.
     */
    public ItemStack claimTempItem(UUID uuid, int index) {
        List<ItemStack> inv = tempInv.get(uuid);
        if (inv == null || index < 0 || index >= inv.size()) return null;
        ItemStack item = inv.remove(index);
        if (inv.isEmpty()) tempInv.remove(uuid);
        save();
        return item;
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private void addToTempInv(UUID uuid, ItemStack item, int amount) {
        ItemStack stack = item.clone();
        stack.setAmount(amount);
        tempInv.computeIfAbsent(uuid, k -> new ArrayList<>()).add(stack);
    }

    // =========================================================================
    // Persistence
    // =========================================================================

    public void save() {
        cfg = new YamlConfiguration();

        // ── Listings ─────────────────────────────────────────────────────────
        for (AhListing l : listings.values()) {
            String path = "listings." + l.getId();
            cfg.set(path + ".seller-uuid",  l.getSellerUuid().toString());
            cfg.set(path + ".seller-name",  l.getSellerName());
            cfg.set(path + ".item",         l.getItem());
            cfg.set(path + ".amount",       l.getAmount());
            cfg.set(path + ".price",        l.getPrice());
            cfg.set(path + ".listed-at",    l.getListedAt());
            cfg.set(path + ".expires-at",   l.getExpiresAt());
            cfg.set(path + ".status",       l.getStatus().name());
        }

        // ── Temp inventories ─────────────────────────────────────────────────
        for (Map.Entry<UUID, List<ItemStack>> entry : tempInv.entrySet()) {
            String path = "temp-inv." + entry.getKey().toString();
            List<ItemStack> items = entry.getValue();
            for (int i = 0; i < items.size(); i++) {
                cfg.set(path + "." + i, items.get(i));
            }
        }

        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            ChloeManagerPlugin.getInstance().getLogger()
                    .log(Level.SEVERE, "Failed to save ah_data.yml", e);
        }
    }

    private void load() {
        if (!dataFile.exists()) {
            cfg = new YamlConfiguration();
            return;
        }
        cfg = YamlConfiguration.loadConfiguration(dataFile);

        // ── Listings ─────────────────────────────────────────────────────────
        if (cfg.isConfigurationSection("listings")) {
            for (String id : cfg.getConfigurationSection("listings").getKeys(false)) {
                String path = "listings." + id;
                try {
                    UUID      sellerUuid = UUID.fromString(cfg.getString(path + ".seller-uuid"));
                    String    sellerName = cfg.getString(path + ".seller-name", "Unknown");
                    ItemStack item       = cfg.getItemStack(path + ".item");
                    int       amount     = cfg.getInt(path + ".amount", 1);
                    double    price      = cfg.getDouble(path + ".price", 0);
                    long      listedAt   = cfg.getLong(path + ".listed-at");
                    long      expiresAt  = cfg.getLong(path + ".expires-at");
                    AhListing.Status status =
                            AhListing.Status.valueOf(cfg.getString(path + ".status", "ACTIVE"));

                    if (item == null) continue;

                    AhListing listing = new AhListing(id, sellerUuid, sellerName,
                            item, amount, price, listedAt, expiresAt, status);
                    listings.put(id, listing);
                } catch (Exception e) {
                    ChloeManagerPlugin.getInstance().getLogger()
                            .warning("Skipping malformed AH listing: " + id);
                }
            }
        }

        // ── Temp inventories ─────────────────────────────────────────────────
        if (cfg.isConfigurationSection("temp-inv")) {
            for (String uuidStr : cfg.getConfigurationSection("temp-inv").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String path = "temp-inv." + uuidStr;
                    List<ItemStack> items = new ArrayList<>();
                    if (cfg.isConfigurationSection(path)) {
                        for (String idx : cfg.getConfigurationSection(path).getKeys(false)) {
                            ItemStack item = cfg.getItemStack(path + "." + idx);
                            if (item != null) items.add(item);
                        }
                    }
                    if (!items.isEmpty()) tempInv.put(uuid, items);
                } catch (Exception e) {
                    ChloeManagerPlugin.getInstance().getLogger()
                            .warning("Skipping malformed temp-inv entry: " + uuidStr);
                }
            }
        }

        // Run expiry check immediately after loading
        processExpired();
    }
}
