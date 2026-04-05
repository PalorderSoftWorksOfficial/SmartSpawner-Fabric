package com.palordersoftworks.smartspawner.spawner.gui.stacker;

import com.palordersoftworks.smartspawner.spawner.gui.SpawnerHolder;
import com.palordersoftworks.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class SpawnerStackerHolder implements InventoryHolder, SpawnerHolder {
    private final SpawnerData spawnerData;

    public SpawnerStackerHolder(SpawnerData spawnerData) {
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