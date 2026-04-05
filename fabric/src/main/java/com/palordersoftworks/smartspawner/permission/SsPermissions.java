package com.palordersoftworks.smartspawner.permission;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.Predicate;

/**
 * LuckPerms-compatible nodes (same strings as Paper {@code plugin.yml}). Uses Fabric Permissions API (bundled).
 */
public final class SsPermissions {

    public static final String COMMAND_USE = "smartspawner.command.use";
    public static final String COMMAND_RELOAD = "smartspawner.command.reload";
    public static final String COMMAND_GIVE = "smartspawner.command.give";
    public static final String COMMAND_LIST = "smartspawner.command.list";
    public static final String COMMAND_HOLOGRAM = "smartspawner.command.hologram";
    public static final String COMMAND_PRICES = "smartspawner.command.prices";
    public static final String COMMAND_CLEAR = "smartspawner.command.clear";
    public static final String COMMAND_NEAR = "smartspawner.command.near";

    public static final String STACK = "smartspawner.stack";
    public static final String SELL_ALL = "smartspawner.sellall";
    public static final String BREAK = "smartspawner.break";

    /** When unset, require vanilla permission level (2 ≈ gamemaster / Paper {@code default: op}). */
    public static final int DEFAULT_OP = 2;

    private SsPermissions() {
    }

    public static boolean check(ServerCommandSource source, String node, int defaultRequiredLevel) {
        return Permissions.check(source, node, defaultRequiredLevel);
    }

    public static boolean check(ServerCommandSource source, String node, boolean defaultValue) {
        return Permissions.check(source, node, defaultValue);
    }

    public static boolean check(ServerPlayerEntity player, String node, int defaultRequiredLevel) {
        return Permissions.check(player, node, defaultRequiredLevel);
    }

    public static boolean check(ServerPlayerEntity player, String node, boolean defaultValue) {
        return Permissions.check(player, node, defaultValue);
    }

    public static Predicate<ServerCommandSource> requireLevel(String node, int defaultRequiredLevel) {
        return src -> Permissions.check(src, node, defaultRequiredLevel);
    }

    public static Predicate<ServerCommandSource> requireDefaultTrue(String node) {
        return src -> Permissions.check(src, node, true);
    }
}
