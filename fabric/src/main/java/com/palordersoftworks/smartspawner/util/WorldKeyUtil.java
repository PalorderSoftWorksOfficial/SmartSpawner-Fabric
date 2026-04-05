package com.palordersoftworks.smartspawner.util;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public final class WorldKeyUtil {

    private WorldKeyUtil() {
    }

    /**
     * Paper saves Bukkit world names (often "world", "world_nether"). Resolve to a loaded {@link ServerWorld}.
     */
    public static ServerWorld resolveLevel(MinecraftServer server, String paperName) {
        if (paperName == null || paperName.isEmpty()) {
            return null;
        }
        if ("world".equalsIgnoreCase(paperName)) {
            return server.getOverworld();
        }
        if ("world_nether".equalsIgnoreCase(paperName)) {
            return server.getWorld(World.NETHER);
        }
        if ("world_the_end".equalsIgnoreCase(paperName)) {
            return server.getWorld(World.END);
        }
        Identifier id = Identifier.tryParse(paperName);
        if (id != null) {
            RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
            ServerWorld sw = server.getWorld(key);
            if (sw != null) {
                return sw;
            }
        }
        for (ServerWorld sw : server.getWorlds()) {
            Identifier dimId = sw.getRegistryKey().getValue();
            if (paperName.equals(dimId.toString()) || paperName.equals(dimId.getPath())) {
                return sw;
            }
        }
        return null;
    }

    public static String toPaperWorldName(ServerWorld world) {
        RegistryKey<World> dim = world.getRegistryKey();
        if (dim == World.OVERWORLD) {
            return "world";
        }
        if (dim == World.NETHER) {
            return "world_nether";
        }
        if (dim == World.END) {
            return "world_the_end";
        }
        return dim.getValue().toString();
    }
}
