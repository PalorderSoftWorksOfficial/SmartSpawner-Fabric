package com.palordersoftworks.smartspawner.hooks.economy.shops.providers.economyshopgui;

import com.palordersoftworks.smartspawner.SmartSpawner;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import me.gypopo.economyshopgui.api.events.ShopItemsLoadEvent;

public class ESGUICompatibilityHandler implements Listener {
    private final SmartSpawner plugin;

    public ESGUICompatibilityHandler(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onESGUIShopItemsLoad(ShopItemsLoadEvent event) {
        plugin.getItemPriceManager().reloadShopIntegration();
        plugin.getSpawnerSettingsConfig().reload();
        plugin.getSpawnerManager().reloadSpawnerDrops();
    }
}