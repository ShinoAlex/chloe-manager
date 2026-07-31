package com.chloe.chloemanager.view;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * VIEW — PlayerView
 *
 * Responsible for all player-facing output: chat messages, titles, action bars.
 * This layer knows nothing about business logic or data storage.
 * Controllers call these methods to display results to the player.
 *
 * Centralising messages here makes it easy to restyle or localise text later.
 */
public class PlayerView {

    // ----- Colour palette -----
    private static final String PREFIX      = ChatColor.GOLD + "[ChloeManager] " + ChatColor.RESET;
    private static final String COLOR_OK    = ChatColor.GREEN.toString();
    private static final String COLOR_WARN  = ChatColor.YELLOW.toString();
    private static final String COLOR_ERROR = ChatColor.RED.toString();
    private static final String COLOR_INFO  = ChatColor.AQUA.toString();

    // ----- Generic helpers -----

    public void sendInfo(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + COLOR_INFO + message);
    }

    public void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + COLOR_OK + message);
    }

    public void sendWarning(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + COLOR_WARN + message);
    }

    public void sendError(CommandSender sender, String message) {
        sender.sendMessage(PREFIX + COLOR_ERROR + message);
    }

    // ----- Domain-specific messages -----

    /**
     * Sends a personalised greeting to the target player and notifies the sender.
     *
     * @param sender     Who issued the command (may equal target).
     * @param target     The player being greeted.
     * @param greetCount How many times they have been greeted in total.
     */
    public void sendGreeting(CommandSender sender, Player target, int greetCount) {
        String greeting = ChatColor.LIGHT_PURPLE + "Hello, " + ChatColor.YELLOW
                + target.getName() + ChatColor.LIGHT_PURPLE + "! "
                + ChatColor.GRAY + "(greeted " + greetCount + " time"
                + (greetCount == 1 ? "" : "s") + ")";

        target.sendMessage(PREFIX + greeting);

        // Notify the sender only if they are a different person
        if (!sender.getName().equals(target.getName())) {
            sendSuccess(sender, "Greeted " + target.getName() + ".");
        }
    }

    /** Tells the sender the command was used incorrectly. */
    public void sendUsage(CommandSender sender) {
        sendError(sender, "Usage: /hello [player]");
    }

    /** Tells the sender the specified player is not online. */
    public void sendPlayerNotFound(CommandSender sender, String name) {
        sendError(sender, "Player '" + name + "' is not online.");
    }

    /** Tells a non-player sender they must be in-game. */
    public void sendMustBePlayer(CommandSender sender) {
        sendError(sender, "You must be a player to use this command.");
    }
}
