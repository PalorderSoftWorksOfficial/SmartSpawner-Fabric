package com.palordersoftworks.smartspawner.data;

import com.palordersoftworks.smartspawner.SmartSpawnerRuntime;
import com.palordersoftworks.smartspawner.loot.EntityLootProfile;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

@Getter
public final class FabricSpawnerData {

    private final SmartSpawnerRuntime runtime;
    private final String spawnerId;
    private final ServerWorld world;
    private final BlockPos pos;

    @Getter
    private final ReentrantLock inventoryLock = new ReentrantLock();
    @Getter
    private final ReentrantLock lootGenerationLock = new ReentrantLock();
    @Getter
    private final ReentrantLock dataLock = new ReentrantLock();
    private final AtomicBoolean selling = new AtomicBoolean(false);
    private final AtomicBoolean spawnerStop = new AtomicBoolean(true);

    @Setter
    private String entityTypeName;
    /** For item spawners (Paper EntityType.ITEM) */
    @Setter
    private String itemSpawnerMaterial;

    @Setter
    private int spawnerExp;
    @Setter
    private boolean spawnerActive = true;
    @Setter
    private int spawnerRange;
    @Setter
    private long spawnDelayTicks;
    @Setter
    private int maxSpawnerLootSlots;
    @Setter
    private int maxStoredExp;
    @Setter
    private int minMobs;
    @Setter
    private int maxMobs;
    @Setter
    private int stackSize = 1;
    @Setter
    private int maxStackSize;
    @Setter
    private long lastSpawnTimeMillis = System.currentTimeMillis();
    @Setter
    private boolean atCapacity;
    @Setter
    private long cachedSpawnDelayMillis = -1;

    private VirtualInventoryMc virtualInventory;

    public FabricSpawnerData(SmartSpawnerRuntime runtime, String id, ServerWorld world, BlockPos pos,
                             String entityTypeName, String itemSpawnerMaterial) {
        this.runtime = runtime;
        this.spawnerId = id;
        this.world = world;
        this.pos = pos.toImmutable();
        this.entityTypeName = entityTypeName;
        this.itemSpawnerMaterial = itemSpawnerMaterial;
    }

    /** New spawner: config defaults + empty virtual inventory. */
    public void initDefaultsFromConfig() {
        reloadDefaultsFromConfig();
        recalculateStackDerived();
        this.virtualInventory = new VirtualInventoryMc(maxSpawnerLootSlots);
    }

    public void reloadDefaultsFromConfig() {
        var c = runtime.getModConfig();
        this.spawnerRange = c.range();
        this.spawnDelayTicks = c.delayTicks();
        this.maxStackSize = c.maxStackSize();
        int basePages = c.maxStoragePages();
        int baseExp = c.maxStoredExp();
        this.minMobs = c.minMobs();
        this.maxMobs = c.maxMobs();
        this.baseMaxStoragePages = basePages;
        this.baseMaxStoredExp = baseExp;
        this.baseMinMobs = c.minMobs();
        this.baseMaxMobs = c.maxMobs();
        this.cachedSpawnDelayMillis = (spawnDelayTicks + 20L) * 50L;
    }

    private int baseMaxStoragePages;
    private int baseMaxStoredExp;
    private int baseMinMobs;
    private int baseMaxMobs;

    public void recalculateStackDerived() {
        this.maxStoredExp = baseMaxStoredExp * stackSize;
        int maxPages = baseMaxStoragePages * stackSize;
        this.maxSpawnerLootSlots = maxPages * 45;
        this.minMobs = baseMinMobs * stackSize;
        this.maxMobs = baseMaxMobs * stackSize;
        this.spawnerExp = Math.min(this.spawnerExp, this.maxStoredExp);
        if (virtualInventory != null && virtualInventory.getMaxSlots() != maxSpawnerLootSlots) {
            virtualInventory.resize(maxSpawnerLootSlots);
        }
    }

    public VirtualInventoryMc virtualInventory() {
        if (virtualInventory == null) {
            throw new IllegalStateException("virtual inventory not initialized for " + spawnerId);
        }
        return virtualInventory;
    }

    public boolean isItemSpawner() {
        return itemSpawnerMaterial != null && !itemSpawnerMaterial.isEmpty();
    }

    public EntityLootProfile lootProfile() {
        if (isItemSpawner()) {
            return runtime.getLootRegistry().forItemMaterial(itemSpawnerMaterial);
        }
        return runtime.getLootRegistry().forEntity(entityTypeName);
    }

    public boolean isSelling() {
        return selling.get();
    }

    public boolean startSelling() {
        return selling.compareAndSet(false, true);
    }

    public void stopSelling() {
        selling.set(false);
    }

    public void addItemsFromLoot(List<ItemStack> stacks) {
        inventoryLock.lock();
        try {
            virtualInventory.addItems(stacks);
        } finally {
            inventoryLock.unlock();
        }
    }

    public void updateCapacityFlag() {
        boolean full = virtualInventory.getUsedSlots() >= maxSpawnerLootSlots && spawnerExp >= maxStoredExp;
        this.atCapacity = full;
    }

    public long millisUntilNextSpawn() {
        long delay = cachedSpawnDelayMillis > 0 ? cachedSpawnDelayMillis : (spawnDelayTicks + 20L) * 50L;
        long elapsed = System.currentTimeMillis() - lastSpawnTimeMillis;
        return Math.max(0, delay - elapsed);
    }
}
