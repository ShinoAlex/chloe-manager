package com.chloe.chloemanager.model;

import java.util.*;

/**
 * MODEL — TaixiuModel
 *
 * Holds all state for a single Tài Xỉu game session:
 *   - Current bets (UUID → BetEntry)
 *   - Toggle preferences (players who opted out)
 *   - Latest dice result
 *
 * No Bukkit / scheduler logic lives here — that belongs in the controller.
 */
public class TaixiuModel {

    // ── Bet side ─────────────────────────────────────────────────────────────

    public enum BetSide {
        TAI("Tài"),
        XIU("Xỉu");

        private final String display;
        BetSide(String display) { this.display = display; }
        public String getDisplay() { return display; }

        public static BetSide parse(String s) {
            return switch (s.toLowerCase().trim()) {
                case "tai", "tài", "t" -> TAI;
                case "xiu", "xỉu", "x" -> XIU;
                default -> null;
            };
        }
    }

    // ── Bet entry ─────────────────────────────────────────────────────────────

    public static class BetEntry {
        public final UUID     uuid;
        public final String   playerName;
        public final BetSide  side;
        public final double   amount;

        public BetEntry(UUID uuid, String playerName, BetSide side, double amount) {
            this.uuid       = uuid;
            this.playerName = playerName;
            this.side       = side;
            this.amount     = amount;
        }
    }

    // ── Dice result ───────────────────────────────────────────────────────────

    public static class DiceResult {
        public final int d1, d2, d3;
        public final int sum;
        public final BetSide winner;   // null if triple (house wins)
        public final boolean isTriple;

        public DiceResult(int d1, int d2, int d3) {
            this.d1     = d1;
            this.d2     = d2;
            this.d3     = d3;
            this.sum    = d1 + d2 + d3;
            this.isTriple = (d1 == d2 && d2 == d3) && (d1 == 1 || d1 == 6);
            if (isTriple) {
                this.winner = null;
            } else {
                this.winner = sum >= 11 ? BetSide.TAI : BetSide.XIU;
            }
        }

        public String diceString() {
            return "[ " + d1 + " - " + d2 + " - " + d3 + " ]";
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    /** Maps UUID → current round's bet. One bet per player per round. */
    private final Map<UUID, BetEntry> bets = new LinkedHashMap<>();

    /** Players who have opted out of taixiu messages. */
    private final Set<UUID> optedOut = new HashSet<>();

    /** Whether betting is currently locked (last 5 seconds). */
    private boolean bettingLocked = false;

    // ── Bet management ────────────────────────────────────────────────────────

    public boolean isBettingLocked() { return bettingLocked; }
    public void    setBettingLocked(boolean locked) { this.bettingLocked = locked; }

    /**
     * Places or replaces a bet for a player.
     * Returns false if betting is locked.
     */
    public boolean placeBet(UUID uuid, String playerName, BetSide side, double amount) {
        if (bettingLocked) return false;
        bets.put(uuid, new BetEntry(uuid, playerName, side, amount));
        return true;
    }

    /**
     * Adds {@code extraAmount} on top of an existing bet (same side).
     * The bet must already exist and be on the same side.
     * Returns false if betting is locked.
     */
    public boolean addToBet(UUID uuid, double extraAmount) {
        if (bettingLocked) return false;
        BetEntry existing = bets.get(uuid);
        if (existing == null) return false;
        bets.put(uuid, new BetEntry(uuid, existing.playerName, existing.side,
                existing.amount + extraAmount));
        return true;
    }

    public boolean hasBet(UUID uuid) { return bets.containsKey(uuid); }

    public Map<UUID, BetEntry> getBets() { return Collections.unmodifiableMap(bets); }

    /** Returns the entry with the highest bet amount, or null if no bets. */
    public BetEntry getBiggestBet() {
        return bets.values().stream()
                .max(Comparator.comparingDouble(e -> e.amount))
                .orElse(null);
    }

    /** Clears all bets and unlocks betting for the next round. */
    public void resetRound() {
        bets.clear();
        bettingLocked = false;
    }

    // ── Toggle ────────────────────────────────────────────────────────────────

    public boolean isOptedOut(UUID uuid) { return optedOut.contains(uuid); }

    public void setOptedOut(UUID uuid, boolean out) {
        if (out) optedOut.add(uuid);
        else     optedOut.remove(uuid);
    }

    // ── Dice roll ─────────────────────────────────────────────────────────────

    private static final Random RNG = new Random();

    public static DiceResult rollDice() {
        return new DiceResult(
                RNG.nextInt(6) + 1,
                RNG.nextInt(6) + 1,
                RNG.nextInt(6) + 1
        );
    }
}
