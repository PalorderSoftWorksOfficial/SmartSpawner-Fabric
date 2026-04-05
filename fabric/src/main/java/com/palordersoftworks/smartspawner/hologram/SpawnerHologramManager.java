package com.palordersoftworks.smartspawner.hologram;

import com.palordersoftworks.smartspawner.SmartSpawnerRuntime;
import com.palordersoftworks.smartspawner.data.FabricSpawnerData;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple floating labels above managed spawners (armor stands). Toggled like Paper {@code /ss hologram}.
 */
public final class SpawnerHologramManager {

    private final SmartSpawnerRuntime runtime;
    /** Spawner id → hologram entity UUID */
    private final Map<String, UUID> tracked = new ConcurrentHashMap<>();
    private int tickCounter;

    public SpawnerHologramManager(SmartSpawnerRuntime runtime) {
        this.runtime = runtime;
    }

    public void removeAll() {
        MinecraftServer server = runtime.getServer();
        if (server != null) {
            for (UUID uuid : tracked.values()) {
                for (ServerWorld sw : server.getWorlds()) {
                    killEntity(sw, uuid);
                }
            }
        }
        tracked.clear();
    }

    public void refreshAll() {
        removeAll();
    }

    public void tick() {
        if (!runtime.isHologramsEnabled()) {
            if (!tracked.isEmpty()) {
                removeAll();
            }
            return;
        }
        tickCounter++;
        if (tickCounter % 40 != 0) {
            return;
        }
        for (FabricSpawnerData s : runtime.getSpawnerManager().all()) {
            ServerWorld world = s.getWorld();
            BlockPos pos = s.getPos();
            if (!world.getBlockState(pos).isOf(Blocks.SPAWNER)) {
                removeFor(s.getSpawnerId(), world);
                continue;
            }
            UUID existing = tracked.get(s.getSpawnerId());
            if (existing != null && isAlive(world, existing)) {
                updateName(world, existing, labelFor(s));
                continue;
            }
            spawn(world, s);
        }
    }

    private void spawn(ServerWorld world, FabricSpawnerData s) {
        removeFor(s.getSpawnerId(), world);
        var c = runtime.getModConfig();
        double x = s.getPos().getX() + c.hologramOffsetX();
        double y = s.getPos().getY() + c.hologramOffsetY();
        double z = s.getPos().getZ() + c.hologramOffsetZ();
        ArmorStandEntity stand = EntityType.ARMOR_STAND.create(world, SpawnReason.COMMAND);
        if (stand == null) {
            return;
        }
        stand.refreshPositionAndAngles(x, y, z, 0.0f, 0.0f);
        stand.setInvisible(true);
        byte flags = stand.getDataTracker().get(ArmorStandEntity.ARMOR_STAND_FLAGS);
        flags = (byte) (flags | ArmorStandEntity.MARKER_FLAG);
        stand.getDataTracker().set(ArmorStandEntity.ARMOR_STAND_FLAGS, flags);
        stand.setNoGravity(true);
        stand.setCustomNameVisible(true);
        stand.setCustomName(Text.literal(labelFor(s)));
        stand.setVelocity(Vec3d.ZERO);
        world.spawnEntity(stand);
        tracked.put(s.getSpawnerId(), stand.getUuid());
    }

    private static String labelFor(FabricSpawnerData s) {
        if (s.isItemSpawner()) {
            return s.getItemSpawnerMaterial() + " ×" + s.getStackSize();
        }
        return s.getEntityTypeName() + " ×" + s.getStackSize();
    }

    private void removeFor(String id, ServerWorld world) {
        UUID u = tracked.remove(id);
        if (u != null) {
            killEntity(world, u);
        }
    }

    private static void killEntity(ServerWorld world, UUID uuid) {
        Entity e = world.getEntity(uuid);
        if (e != null) {
            e.discard();
        }
    }

    private static boolean isAlive(ServerWorld world, UUID uuid) {
        Entity e = world.getEntity(uuid);
        return e != null && e.isAlive();
    }

    private static void updateName(ServerWorld world, UUID uuid, String label) {
        Entity e = world.getEntity(uuid);
        if (e instanceof ArmorStandEntity stand) {
            stand.setCustomName(Text.literal(label));
        }
    }
}
