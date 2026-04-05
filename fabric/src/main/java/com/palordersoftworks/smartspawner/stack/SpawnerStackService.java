package com.palordersoftworks.smartspawner.stack;

import com.palordersoftworks.smartspawner.data.FabricSpawnerData;
import com.palordersoftworks.smartspawner.permission.SsPermissions;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cooldown for hand-stacking on spawner blocks (anti-spam).
 */
public final class SpawnerStackService {

    private static final long COOLDOWN_MS = 200L;
    private static final Map<UUID, Long> LAST_HAND_STACK = new ConcurrentHashMap<>();

    private SpawnerStackService() {
    }

    public static boolean tryHandStack(ServerPlayerEntity player, FabricSpawnerData data) {
        if (!SsPermissions.check(player, SsPermissions.STACK, true)) {
            return false;
        }
        if (!SpawnerStackerOperations.handStackMatches(data, player.getMainHandStack())) {
            return false;
        }
        long now = System.currentTimeMillis();
        UUID id = player.getUuid();
        Long prev = LAST_HAND_STACK.get(id);
        if (prev != null && now - prev < COOLDOWN_MS) {
            return false;
        }
        boolean ok = SpawnerStackerOperations.tryStackFromMainHand(player, data);
        if (ok) {
            LAST_HAND_STACK.put(id, now);
        }
        return ok;
    }
}
