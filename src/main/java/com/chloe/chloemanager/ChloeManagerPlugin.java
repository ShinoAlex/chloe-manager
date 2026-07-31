package com.chloe.chloemanager;

import com.chloe.chloemanager.controller.HelloController;
import com.chloe.chloemanager.model.PlayerDataModel;
import com.chloe.chloemanager.view.PlayerView;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin entry point.
 *
 * Responsibilities:
 *  - Bootstrap Models, Views, and Controllers.
 *  - Register commands and event listeners.
 *  - Provide a static accessor so other classes can reach the plugin instance.
 */
public final class ChloeManagerPlugin extends JavaPlugin {

    private static ChloeManagerPlugin instance;

    // --- MVC layers ---
    private PlayerDataModel playerDataModel;
    private PlayerView playerView;
    private HelloController helloController;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Initialise the Model
        playerDataModel = new PlayerDataModel();

        // 2. Initialise the View
        playerView = new PlayerView();

        // 3. Initialise Controllers and wire in their Model + View
        helloController = new HelloController(playerDataModel, playerView);

        // 4. Register commands
        getCommand("hello").setExecutor(helloController);
        getCommand("hello").setTabCompleter(helloController);

        getLogger().info("ChloeManager enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ChloeManager disabled.");
        instance = null;
    }

    /** Static accessor — use sparingly; prefer constructor injection. */
    public static ChloeManagerPlugin getInstance() {
        return instance;
    }
}
