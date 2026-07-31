package com.chloe.chloemanager.controller;

import com.chloe.chloemanager.model.PlayerDataModel;
import com.chloe.chloemanager.view.PlayerView;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CONTROLLER — HelloController
 *
 * Handles the /hello command.
 *
 * The controller:
 *  1. Validates input from the CommandSender.
 *  2. Calls the Model to update / retrieve data.
 *  3. Calls the View to send the appropriate message.
 *
 * It never builds raw strings itself — that belongs in the View.
 * It never stores data itself — that belongs in the Model.
 */
public class HelloController implements CommandExecutor, TabCompleter {

    private final PlayerDataModel model;
    private final PlayerView view;

    public HelloController(PlayerDataModel model, PlayerView view) {
        this.model = model;
        this.view  = view;
    }

    /**
     * /hello            — greets the sender (must be a player)
     * /hello <player>   — greets the named online player
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        // Case 1: no arguments — greet the sender
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                view.sendMustBePlayer(sender);
                return true;
            }
            Player player = (Player) sender;
            int count = model.incrementGreetCount(player.getUniqueId());
            view.sendGreeting(sender, player, count);
            return true;
        }

        // Case 2: one argument — greet the named player
        if (args.length == 1) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                view.sendPlayerNotFound(sender, args[0]);
                return true;
            }
            int count = model.incrementGreetCount(target.getUniqueId());
            view.sendGreeting(sender, target, count);
            return true;
        }

        // Too many arguments
        view.sendUsage(sender);
        return true;
    }

    /** Tab-completes online player names for the first argument. */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
