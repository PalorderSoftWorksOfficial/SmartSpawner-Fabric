package com.palordersoftworks.smartspawner.spawner.gui.storage.utils;

import org.bukkit.inventory.ItemStack;
import java.util.List;

public record ItemMoveResult(int amountMoved, List<ItemStack> movedItems) {

}
