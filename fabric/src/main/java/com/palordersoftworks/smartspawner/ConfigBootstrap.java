package com.palordersoftworks.smartspawner;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

final class ConfigBootstrap {

    private static final List<String> DEFAULTS = List.of(
            "defaults/config.yml",
            "defaults/spawners_settings.yml",
            "defaults/item_spawners_settings.yml",
            "defaults/item_prices.yml",
            "defaults/spawners_data.yml"
    );

    static void copyDefaultsIfMissing() throws Exception {
        Path root = FabricLoader.getInstance().getConfigDir().resolve("smartspawner");
        Files.createDirectories(root);
        for (String resourcePath : DEFAULTS) {
            String name = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            Path target = root.resolve(name);
            if (Files.exists(target)) {
                continue;
            }
            try (InputStream in = SmartSpawnerFabric.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    SmartSpawnerFabric.LOGGER.warn("Missing bundled default resource: {}", resourcePath);
                    continue;
                }
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                SmartSpawnerFabric.LOGGER.info("Created default {}", name);
            }
        }
    }

    private ConfigBootstrap() {
    }
}
