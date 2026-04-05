package com.palordersoftworks.smartspawner.spawner.gui.main;

import com.palordersoftworks.smartspawner.spawner.gui.SpawnerHolder;
import com.palordersoftworks.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class SpawnerMenuHolder implements InventoryHolder, SpawnerHolder {
    private final SpawnerData spawnerData;

    public SpawnerMenuHolder(SpawnerData spawnerData) {
        this.spawnerData = spawnerData;
    }

    @Override
    public Inventory getInventory() {
        return null; // Required by interface
    }

    public SpawnerData getSpawnerData() {
        return spawnerData;
    }

}
