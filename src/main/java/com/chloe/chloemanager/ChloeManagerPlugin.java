package com.chloe.chloemanager;

import com.chloe.chloemanager.controller.EconomyController;
import com.chloe.chloemanager.controller.HelloController;
import com.chloe.chloemanager.model.EconomyModel;
import com.chloe.chloemanager.model.PlayerDataModel;
import com.chloe.chloemanager.model.SellPriceRegistry;
import com.chloe.chloemanager.view.EconomyView;
import com.chloe.chloemanager.view.PlayerView;
import com.chloe.chloemanager.view.SellGUI;
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

    // ── MVC layers ────────────────────────────────────────────────────────

    // Hello feature
    private PlayerDataModel playerDataModel;
    private PlayerView      playerView;
    private HelloController helloController;

    // Economy feature
    private EconomyModel      economyModel;
    private SellPriceRegistry sellPriceRegistry;
    private EconomyView       economyView;
    private SellGUI           sellGUI;
    private EconomyController economyController;

    @Override
    public void onEnable() {
        instance = this;

        // ── Hello feature ────────────────────────────────────────────────
        playerDataModel = new PlayerDataModel();
        playerView      = new PlayerView();
        helloController = new HelloController(playerDataModel, playerView);

        getCommand("hello").setExecutor(helloController);
        getCommand("hello").setTabCompleter(helloController);

        // ── Economy feature ──────────────────────────────────────────────
        economyModel      = new EconomyModel(this);
        sellPriceRegistry = new SellPriceRegistry();
        economyView       = new EconomyView();
        sellGUI           = new SellGUI(sellPriceRegistry);
        economyController = new EconomyController(
                economyModel, sellPriceRegistry, economyView, sellGUI);

        getCommand("sell").setExecutor(economyController);
        getCommand("sell").setTabCompleter(economyController);

        getCommand("balance").setExecutor(economyController);
        getCommand("balance").setTabCompleter(economyController);

        getCommand("bal").setExecutor(economyController);
        getCommand("bal").setTabCompleter(economyController);

        getCommand("baltop").setExecutor(economyController);

        getCommand("pay").setExecutor(economyController);
        getCommand("pay").setTabCompleter(economyController);

        // Register GUI event listener
        getServer().getPluginManager().registerEvents(economyController, this);

        getLogger().info("ChloeManager enabled!");
    }

    @Override
    public void onDisable() {
        // Save all balances to disk on clean shutdown
        if (economyModel != null) {
            economyModel.save();
        }
        getLogger().info("ChloeManager disabled.");
        instance = null;
    }

    /** Static accessor — use sparingly; prefer constructor injection. */
    public static ChloeManagerPlugin getInstance() {
        return instance;
    }
}
