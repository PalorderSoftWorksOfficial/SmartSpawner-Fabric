package com.palordersoftworks.smartspawner.near;

import com.palordersoftworks.smartspawner.SmartSpawnerRuntime;
import com.palordersoftworks.smartspawner.data.FabricSpawnerData;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highlights nearby managed spawners with particles (Paper {@code /ss near} subset).
 */
public final class NearHighlightManager {

    public static final int MAX_RADIUS = 10_000;
    public static final int DEFAULT_RADIUS = 50;

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public void startScan(ServerPlayerEntity player, int radius) {
        cancel(player.getUuid());
        SmartSpawnerRuntime rt = SmartSpawnerRuntime.getOrNull();
        if (rt == null) {
            return;
        }
        int r = Math.min(Math.max(1, radius), MAX_RADIUS);
        ServerWorld world = player.getEntityWorld();
        Vec3d origin = new Vec3d(player.getX(), player.getY(), player.getZ());
        List<BlockPos> hits = new ArrayList<>();
        for (FabricSpawnerData s : rt.getSpawnerManager().all()) {
            if (!s.getWorld().equals(world)) {
                continue;
            }
            Vec3d c = s.getPos().toCenterPos();
            if (c.squaredDistanceTo(origin) <= (double) r * r) {
                hits.add(s.getPos());
            }
        }
        sessions.put(player.getUuid(), new Session(hits, 30 * 20));
        player.sendMessage(Text.literal("SmartSpawner near: " + hits.size() + " spawner(s) in radius " + r + ". Use /ss near cancel to stop."), false);
    }

    public void cancel(UUID playerId) {
        sessions.remove(playerId);
    }

    public boolean hasActiveSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public void tickPlayer(ServerPlayerEntity player) {
        Session s = sessions.get(player.getUuid());
        if (s == null) {
            return;
        }
        s.ticksLeft--;
        if (s.ticksLeft <= 0) {
            sessions.remove(player.getUuid());
            return;
        }
        if (s.ticksLeft % 10 != 0) {
            return;
        }
        ServerWorld world = player.getEntityWorld();
        for (BlockPos pos : s.positions) {
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5;
            world.spawnParticles(player, ParticleTypes.END_ROD, false, false, x, y, z, 2, 0.2, 0.3, 0.2, 0.01);
        }
    }

    private static final class Session {
        final List<BlockPos> positions;
        int ticksLeft;

        Session(List<BlockPos> positions, int ticksLeft) {
            this.positions = positions;
            this.ticksLeft = ticksLeft;
        }
    }
}
