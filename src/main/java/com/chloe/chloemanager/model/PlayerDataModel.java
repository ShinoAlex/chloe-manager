package com.chloe.chloemanager.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MODEL — PlayerDataModel
 *
 * Holds and manages player-related data.
 * This layer knows nothing about commands, messages, or Bukkit senders.
 * It only deals with pure data operations.
 *
 * In a real plugin you would swap the in-memory map for a database or
 * flat-file persistence layer here.
 */
public class PlayerDataModel {

    /** Maps a player's UUID to a simple greeting counter. */
    private final Map<UUID, Integer> greetCounts = new HashMap<>();

    /**
     * Increments and returns the number of times this player has been greeted.
     *
     * @param uuid The player's unique ID.
     * @return The updated greeting count.
     */
    public int incrementGreetCount(UUID uuid) {
        int count = greetCounts.getOrDefault(uuid, 0) + 1;
        greetCounts.put(uuid, count);
        return count;
    }

    /**
     * Returns how many times this player has been greeted (0 if never).
     *
     * @param uuid The player's unique ID.
     * @return Current greet count.
     */
    public int getGreetCount(UUID uuid) {
        return greetCounts.getOrDefault(uuid, 0);
    }

    /**
     * Resets the greet count for a player.
     *
     * @param uuid The player's unique ID.
     */
    public void resetGreetCount(UUID uuid) {
        greetCounts.remove(uuid);
    }
}
