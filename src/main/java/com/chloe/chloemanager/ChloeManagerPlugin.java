package com.chloe.chloemanager;

import com.chloe.chloemanager.controller.AhController;
import com.chloe.chloemanager.controller.BankController;
import com.chloe.chloemanager.controller.EconomyController;
import com.chloe.chloemanager.controller.HelloController;
import com.chloe.chloemanager.controller.KillRewardController;
import com.chloe.chloemanager.controller.ShopController;
import com.chloe.chloemanager.controller.TaixiuController;
import com.chloe.chloemanager.model.AhModel;
import com.chloe.chloemanager.model.BankModel;
import com.chloe.chloemanager.model.EconomyModel;
import com.chloe.chloemanager.model.PlayerDataModel;
import com.chloe.chloemanager.model.SellPriceRegistry;
import com.chloe.chloemanager.model.ShopRegistry;
import com.chloe.chloemanager.model.TaixiuLog;
import com.chloe.chloemanager.model.TaixiuModel;
import com.chloe.chloemanager.view.AhGUI;
import com.chloe.chloemanager.view.BankGUI;
import com.chloe.chloemanager.view.EconomyView;
import com.chloe.chloemanager.view.PlayerView;
import com.chloe.chloemanager.view.SellGUI;
import com.chloe.chloemanager.view.ShopGUI;
import com.chloe.chloemanager.view.TaixiuView;
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

    // Shop feature
    private ShopRegistry   shopRegistry;
    private ShopGUI        shopGUI;
    private ShopController shopController;

    // Tài Xỉu feature
    private TaixiuModel      taixiuModel;
    private TaixiuLog        taixiuLog;
    private TaixiuView       taixiuView;
    private TaixiuController taixiuController;

    // Black Market (/ah)
    private AhModel      ahModel;
    private AhGUI        ahGUI;
    private AhController ahController;

    // Kill bounty
    private KillRewardController killRewardController;

    // Bank feature
    private BankModel      bankModel;
    private BankGUI        bankGUI;
    private BankController bankController;

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

        // ── Shop feature ─────────────────────────────────────────────────
        shopRegistry   = new ShopRegistry();
        shopGUI        = new ShopGUI(shopRegistry);
        shopController = new ShopController(economyModel, shopRegistry, economyView, shopGUI);

        getCommand("shop").setExecutor(shopController);
        getServer().getPluginManager().registerEvents(shopController, this);

        // ── Tài Xỉu feature ──────────────────────────────────────────────
        taixiuModel      = new TaixiuModel();
        taixiuLog        = new TaixiuLog(this);
        taixiuView       = new TaixiuView();
        taixiuController = new TaixiuController(this, economyModel, taixiuModel, taixiuView, taixiuLog);

        getCommand("taixiu").setExecutor(taixiuController);
        getCommand("taixiu").setTabCompleter(taixiuController);
        getServer().getPluginManager().registerEvents(taixiuController, this);

        taixiuController.startLoop();

        // ── Black Market (/ah) ────────────────────────────────────────────
        ahModel      = new AhModel(this);
        ahGUI        = new AhGUI();
        ahController = new AhController(this, ahModel, economyModel, ahGUI);

        getCommand("ah").setExecutor(ahController);
        getCommand("ah").setTabCompleter(ahController);
        getServer().getPluginManager().registerEvents(ahController, this);
        ahController.startExpiryTask();

        // ── Kill bounty ────────────────────────────────────────────────────
        killRewardController = new KillRewardController(economyModel);
        getServer().getPluginManager().registerEvents(killRewardController, this);

        // ── Bank feature ──────────────────────────────────────────────────
        bankModel      = new BankModel(this);
        bankGUI        = new BankGUI(bankModel);
        bankController = new BankController(this, bankModel, economyModel, bankGUI);

        getCommand("bank").setExecutor(bankController);
        getCommand("bank").setTabCompleter(bankController);

        getCommand("deposit").setExecutor(bankController);
        getCommand("deposit").setTabCompleter(bankController);

        getCommand("withdraw").setExecutor(bankController);
        getCommand("withdraw").setTabCompleter(bankController);

        getServer().getPluginManager().registerEvents(bankController, this);
        bankController.startInterestTask();

        getLogger().info("ChloeManager enabled!");
    }

    @Override
    public void onDisable() {
        if (taixiuController != null) taixiuController.stopLoop();
        if (ahController     != null) ahController.stopExpiryTask();
        if (bankController   != null) bankController.stopInterestTask();
        if (ahModel          != null) ahModel.save();
        if (economyModel     != null) economyModel.save();
        getLogger().info("ChloeManager disabled.");
        instance = null;
    }

    /** Static accessor — use sparingly; prefer constructor injection. */
    public static ChloeManagerPlugin getInstance() {
        return instance;
    }
}
