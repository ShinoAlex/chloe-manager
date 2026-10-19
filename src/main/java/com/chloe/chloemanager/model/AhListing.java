package com.chloe.chloemanager.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * MODEL — AhListing
 *
 * Represents a single item listing on the black market (/ah).
 *
 * Lifecycle:
 *   ACTIVE   → visible in the /ah GUI for up to 3 real days.
 *   EXPIRED  → 3 days passed without a sale → moved to seller's temp inventory.
 *   SOLD     → buyer purchased it → money transferred, item delivered.
 *   REMOVED  → seller manually removed it → moved to seller's temp inventory.
 */
public class AhListing {

    public enum Status { ACTIVE, EXPIRED, SOLD, REMOVED }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final String    id;          // UUID string, unique per listing
    private final UUID      sellerUuid;
    private final String    sellerName;
    private final ItemStack item;        // the actual item (preserves enchants, lore, etc.)
    private final int       amount;      // how many are being sold
    private final double    price;       // total price for the whole stack
    private final long      listedAt;    // System.currentTimeMillis() at listing time
    private final long      expiresAt;   // listedAt + 3 days in millis

    private Status status = Status.ACTIVE;

    private static final long THREE_DAYS_MS = 3L * 24 * 60 * 60 * 1000;

    // ── Constructor ───────────────────────────────────────────────────────────

    public AhListing(UUID sellerUuid, String sellerName,
                     ItemStack item, int amount, double price) {
        this.id          = UUID.randomUUID().toString();
        this.sellerUuid  = sellerUuid;
        this.sellerName  = sellerName;
        this.item        = item.clone();
        this.amount      = amount;
        this.price       = price;
        this.listedAt    = System.currentTimeMillis();
        this.expiresAt   = listedAt + THREE_DAYS_MS;
    }

    /** Constructor used when loading from YAML (all fields known). */
    public AhListing(String id, UUID sellerUuid, String sellerName,
                     ItemStack item, int amount, double price,
                     long listedAt, long expiresAt, Status status) {
        this.id         = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.item       = item.clone();
        this.amount     = amount;
        this.price      = price;
        this.listedAt   = listedAt;
        this.expiresAt  = expiresAt;
        this.status     = status;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String    getId()          { return id; }
    public UUID      getSellerUuid()  { return sellerUuid; }
    public String    getSellerName()  { return sellerName; }
    public ItemStack getItem()        { return item.clone(); }
    public int       getAmount()      { return amount; }
    public double    getPrice()       { return price; }
    public long      getListedAt()    { return listedAt; }
    public long      getExpiresAt()   { return expiresAt; }
    public Status    getStatus()      { return status; }
    public void      setStatus(Status s) { this.status = s; }

    /** Returns true if this listing has passed its 3-day expiry window. */
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    /** Returns remaining milliseconds, or 0 if already expired. */
    public long getRemainingMs() {
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }

    /** Human-readable remaining time string: "2d 14h 30m" */
    public String getRemainingString() {
        long ms      = getRemainingMs();
        if (ms == 0) return "Hết hạn";
        long days    = ms / (24 * 3600_000L);
        long hours   = (ms % (24 * 3600_000L)) / 3600_000L;
        long minutes = (ms % 3600_000L) / 60_000L;
        StringBuilder sb = new StringBuilder();
        if (days > 0)    sb.append(days).append("d ");
        if (hours > 0)   sb.append(hours).append("h ");
        sb.append(minutes).append("m");
        return sb.toString().trim();
    }
}
