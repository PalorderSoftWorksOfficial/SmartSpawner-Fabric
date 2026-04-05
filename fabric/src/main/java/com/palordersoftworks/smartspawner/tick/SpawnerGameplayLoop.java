package com.palordersoftworks.smartspawner.tick;

import com.palordersoftworks.smartspawner.SmartSpawnerRuntime;
import com.palordersoftworks.smartspawner.data.FabricSpawnerData;
import com.palordersoftworks.smartspawner.loot.EntityLootProfile;
import com.palordersoftworks.smartspawner.loot.LootRoller;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SpawnerGameplayLoop {

    private static final int RANGE_CHECK_INTERVAL = 20;
    private static final int SAVE_INTERVAL = 6000;

    private final SmartSpawnerRuntime runtime;
    private final Random random = new Random();
    private final ExecutorService async = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SmartSpawner-range");
        t.setDaemon(true);
        return t;
    });

    private int tickCounter;
    private boolean started;

    public SpawnerGameplayLoop(SmartSpawnerRuntime runtime) {
        this.runtime = runtime;
    }

    public void start() {
        if (started) return;
        started = true;
        ServerTickEvents.END_SERVER_TICK.register(this::onEndTick);
    }

    public void stop() {
        async.shutdownNow();
        started = false;
    }

    private void onEndTick(MinecraftServer server) {
        tickCounter++;
        runtime.tickHopperAndVisuals(server, tickCounter);
        if (tickCounter % SAVE_INTERVAL == 0 && runtime.getYamlStore() != null) {
            runtime.getYamlStore().flushAsync();
        }
        if (tickCounter % RANGE_CHECK_INTERVAL != 0) {
            return;
        }

        List<FabricSpawnerData> spawners = runtime.getSpawnerManager().all();
        if (spawners.isEmpty()) return;

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        double[][] pdat = new double[players.size()][5];
        int pi = 0;
        for (ServerPlayerEntity p : players) {
            if (!p.isAlive() || p.isSpectator()) {
                continue;
            }
            ServerWorld sw = p.getEntityWorld();
            pdat[pi][0] = sw.getRegistryKey().getValue().hashCode();
            pdat[pi][1] = p.getX();
            pdat[pi][2] = p.getY();
            pdat[pi][3] = p.getZ();
            pdat[pi][4] = 1.0;
            pi++;
        }
        final int playerCount = pi;

        async.execute(() -> {
            boolean[] near = new boolean[spawners.size()];
            for (int i = 0; i < spawners.size(); i++) {
                FabricSpawnerData s = spawners.get(i);
                double r = s.getSpawnerRange();
                double rsq = r * r;
                int dw = s.getWorld().getRegistryKey().getValue().hashCode();
                Vec3d c = s.getPos().toCenterPos();
                boolean found = false;
                for (int j = 0; j < playerCount; j++) {
                    if ((int) pdat[j][0] != dw) continue;
                    double dx = pdat[j][1] - c.x;
                    double dy = pdat[j][2] - c.y;
                    double dz = pdat[j][3] - c.z;
                    if (dx * dx + dy * dy + dz * dz <= rsq) {
                        found = true;
                        break;
                    }
                }
                near[i] = found;
            }
            server.execute(() -> applyRangeAndLoot(spawners, near));
        });
    }

    private void applyRangeAndLoot(List<FabricSpawnerData> spawners, boolean[] near) {
        for (int i = 0; i < spawners.size(); i++) {
            FabricSpawnerData s = spawners.get(i);
            if (!s.getWorld().isChunkLoaded(s.getPos())) continue;
            boolean shouldStop = !near[i];
            boolean prev = s.getSpawnerStop().get();
            if (prev != shouldStop) {
                s.getSpawnerStop().set(shouldStop);
                if (!shouldStop && s.isSpawnerActive()) {
                    s.setLastSpawnTimeMillis(System.currentTimeMillis());
                }
            } else if (s.isSpawnerActive() && !s.getSpawnerStop().get()) {
                trySpawnLoot(s);
            }
        }
    }

    private void trySpawnLoot(FabricSpawnerData s) {
        long delay = s.getCachedSpawnDelayMillis() > 0 ? s.getCachedSpawnDelayMillis() : (s.getSpawnDelayTicks() + 20L) * 50L;
        if (System.currentTimeMillis() - s.getLastSpawnTimeMillis() < delay) {
            return;
        }
        if (s.isSelling()) {
            return;
        }
        if (!s.getLootGenerationLock().tryLock()) {
            return;
        }
        try {
            if (!s.getDataLock().tryLock()) {
                return;
            }
            int minMobs = 1;
            int maxMobs = 1;
            try {
                if (s.isSelling()) {
                    return;
                }
                int used = s.virtualInventory().getUsedSlots();
                int maxSlots = s.getMaxSpawnerLootSlots();
                int exp = s.getSpawnerExp();
                int maxExp = s.getMaxStoredExp();
                minMobs = s.getMinMobs();
                maxMobs = s.getMaxMobs();
                if (used >= maxSlots && exp >= maxExp) {
                    s.setAtCapacity(true);
                    return;
                }
                if (System.currentTimeMillis() - s.getLastSpawnTimeMillis() < delay) {
                    return;
                }
                if (!s.isSpawnerActive() || s.getSpawnerStop().get()) {
                    return;
                }
            } finally {
                s.getDataLock().unlock();
            }

            EntityLootProfile prof = s.lootProfile();
            LootRoller.LootBatch batch = LootRoller.roll(prof, minMobs, maxMobs, random);

            if (!s.getDataLock().tryLock()) {
                return;
            }
            try {
                if (s.isSelling()) {
                    return;
                }
                if (!s.isSpawnerActive() || s.getSpawnerStop().get()) {
                    return;
                }
                int newExp = s.getSpawnerExp();
                if (batch.experience() > 0 && newExp < s.getMaxStoredExp()) {
                    newExp = Math.min(newExp + batch.experience(), s.getMaxStoredExp());
                    s.setSpawnerExp(newExp);
                }
                if (!batch.items().isEmpty() && s.virtualInventory().getUsedSlots() < s.getMaxSpawnerLootSlots()) {
                    s.virtualInventory().addItems(batch.items());
                }
                s.setLastSpawnTimeMillis(System.currentTimeMillis());
                s.updateCapacityFlag();
            } finally {
                s.getDataLock().unlock();
            }
            if (runtime.getYamlStore() != null) {
                runtime.getYamlStore().markDirty(s.getSpawnerId());
            }
        } finally {
            s.getLootGenerationLock().unlock();
        }
    }
}
