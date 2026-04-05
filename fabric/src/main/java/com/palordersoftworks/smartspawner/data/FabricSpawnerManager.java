package com.palordersoftworks.smartspawner.data;

import com.palordersoftworks.smartspawner.SmartSpawnerRuntime;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FabricSpawnerManager {

    private final SmartSpawnerRuntime runtime;
    private final Map<String, FabricSpawnerData> byId = new ConcurrentHashMap<>();
    private final Map<Long, FabricSpawnerData> byBlock = new ConcurrentHashMap<>();

    public FabricSpawnerManager(SmartSpawnerRuntime runtime) {
        this.runtime = runtime;
    }

    private static long key(ServerWorld world, BlockPos pos) {
        return pos.asLong() ^ ((long) world.getRegistryKey().getValue().hashCode() << 32);
    }

    public FabricSpawnerData getByBlock(ServerWorld world, BlockPos pos) {
        return byBlock.get(key(world, pos));
    }

    public void register(FabricSpawnerData d) {
        byId.put(d.getSpawnerId(), d);
        byBlock.put(key(d.getWorld(), d.getPos()), d);
    }

    /** Same as {@link #register}; used when loading from disk. */
    public void putLoaded(FabricSpawnerData d) {
        register(d);
    }

    public FabricSpawnerData getById(String id) {
        return byId.get(id);
    }

    public void unregister(String id) {
        FabricSpawnerData d = byId.remove(id);
        if (d != null) {
            byBlock.remove(key(d.getWorld(), d.getPos()));
        }
    }

    public List<FabricSpawnerData> all() {
        return new ArrayList<>(byId.values());
    }

    public String nextId() {
        return "ss_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public void reloadLootConfigs() {
        for (FabricSpawnerData d : byId.values()) {
            d.reloadDefaultsFromConfig();
            d.recalculateStackDerived();
        }
    }

    public boolean manages(ServerWorld world, BlockPos pos) {
        return byBlock.containsKey(key(world, pos));
    }
}
