package com.palordersoftworks.smartspawner.util;

import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Mob-type names for {@code /ss give} (Bukkit-style upper snake from registry path).
 */
public final class EntityCatalog {

    private EntityCatalog() {
    }

    public static List<String> spawnerMobNamesSorted() {
        List<String> out = new ArrayList<>();
        for (Identifier id : Registries.ENTITY_TYPE.getIds()) {
            EntityType<?> t = Registries.ENTITY_TYPE.get(id);
            if (t == EntityType.PLAYER) {
                continue;
            }
            if (!t.isSummonable()) {
                continue;
            }
            out.add(id.getPath().toUpperCase(Locale.ROOT).replace('/', '_'));
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    public static EntityType<?> parseMobType(String word) throws IllegalArgumentException {
        String path = word.toLowerCase(Locale.ROOT);
        Identifier id = Identifier.of("minecraft", path);
        EntityType<?> t = Registries.ENTITY_TYPE.get(id);
        if (t == null || t == EntityType.PLAYER) {
            throw new IllegalArgumentException(word);
        }
        return t;
    }
}
