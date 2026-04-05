package com.palordersoftworks.smartspawner.util;

import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.Optional;

public final class SpawnerEntityReader {

    private SpawnerEntityReader() {
    }

    /**
     * Returns Bukkit-style entity type name (e.g. ZOMBIE) for {@code spawners_settings.yml} keys.
     */
    public static Optional<String> readBukkitEntityName(MobSpawnerBlockEntity be, DynamicRegistryManager registries) {
        NbtCompound tag = be.createComponentlessNbt(registries);
        NbtCompound spawnData = null;
        if (tag.contains("spawn_data")) {
            spawnData = tag.getCompound("spawn_data").orElse(null);
        } else if (tag.contains("SpawnData")) {
            spawnData = tag.getCompound("SpawnData").orElse(null);
        }
        if (spawnData == null) {
            return Optional.empty();
        }
        NbtCompound entity = spawnData.contains("entity")
                ? spawnData.getCompound("entity").orElse(null)
                : spawnData;
        if (entity == null || !entity.contains("id")) {
            return Optional.empty();
        }
        String idStr = entity.getString("id").orElse("");
        if (idStr.isEmpty()) {
            return Optional.empty();
        }
        Identifier rl = Identifier.tryParse(idStr);
        if (rl == null) {
            return Optional.empty();
        }
        return Optional.of(rl.getPath().toUpperCase(Locale.ROOT).replace('.', '_'));
    }
}
