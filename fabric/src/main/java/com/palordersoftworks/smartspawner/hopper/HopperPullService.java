package com.palordersoftworks.smartspawner.hopper;

import com.palordersoftworks.smartspawner.SmartSpawnerRuntime;
import com.palordersoftworks.smartspawner.data.FabricSpawnerData;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Pulls from SmartSpawner virtual storage into a hopper directly below the spawner (Paper parity).
 */
public final class HopperPullService {

    private final SmartSpawnerRuntime runtime;

    public HopperPullService(SmartSpawnerRuntime runtime) {
        this.runtime = runtime;
    }

    public void tick() {
        if (!runtime.getModConfig().hopperEnabled()) {
            return;
        }
        int maxStacks = runtime.getModConfig().hopperStacksPerTransfer();
        for (FabricSpawnerData s : runtime.getSpawnerManager().all()) {
            ServerWorld world = s.getWorld();
            if (!world.shouldTickBlockAt(s.getPos())) {
                continue;
            }
            BlockPos hopperPos = s.getPos().down();
            if (!world.getBlockState(hopperPos).isOf(Blocks.HOPPER)) {
                continue;
            }
            if (!(world.getBlockEntity(hopperPos) instanceof HopperBlockEntity hopper)) {
                continue;
            }
            transferUpTo(s, hopper, maxStacks);
        }
    }

    private void transferUpTo(FabricSpawnerData spawner, HopperBlockEntity hopper, int maxStacks) {
        if (!spawner.getInventoryLock().tryLock()) {
            return;
        }
        try {
            int movedStacks = 0;
            List<ItemStack> view = spawner.virtualInventory().asDisplayList();
            for (ItemStack template : view) {
                if (template.isEmpty() || movedStacks >= maxStacks) {
                    break;
                }
                int inserted = tryInsertAll(hopper, template);
                if (inserted > 0) {
                    spawner.virtualInventory().removeAmountMatching(template, inserted);
                    movedStacks++;
                    if (runtime.getYamlStore() != null) {
                        runtime.getYamlStore().markDirty(spawner.getSpawnerId());
                    }
                }
            }
        } finally {
            spawner.getInventoryLock().unlock();
        }
    }

    private static int tryInsertAll(Inventory hopper, ItemStack stack) {
        int remaining = stack.getCount();
        ItemStack toMove = stack.copy();
        toMove.setCount(remaining);
        for (int i = 0; i < hopper.size() && !toMove.isEmpty(); i++) {
            ItemStack slot = hopper.getStack(i);
            if (slot.isEmpty()) {
                hopper.setStack(i, toMove.copy());
                return remaining;
            }
            if (ItemStack.areItemsAndComponentsEqual(slot, toMove) && slot.getCount() < slot.getMaxCount()) {
                int space = slot.getMaxCount() - slot.getCount();
                int move = Math.min(space, toMove.getCount());
                slot.increment(move);
                hopper.setStack(i, slot);
                toMove.decrement(move);
            }
        }
        return remaining - toMove.getCount();
    }
}
