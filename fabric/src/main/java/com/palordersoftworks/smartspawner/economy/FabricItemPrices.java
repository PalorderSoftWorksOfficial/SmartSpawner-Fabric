package com.palordersoftworks.smartspawner.economy;

import com.palordersoftworks.smartspawner.config.ModConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads {@code item_prices.yml} (Bukkit {@code Material} names as keys, same as Paper).
 */
public final class FabricItemPrices {

    private final Map<String, Double> itemPrices = new HashMap<>();

    public void reload(Path configDir, ModConfig config) throws IOException {
        itemPrices.clear();
        if (!config.customPricesEnabled()) {
            return;
        }
        String name = config.priceFileName();
        Path file = configDir.resolve(name);
        if (!Files.exists(file)) {
            return;
        }
        Yaml yaml = new Yaml();
        Object root = yaml.load(Files.readString(file));
        if (!(root instanceof Map<?, ?> m)) {
            return;
        }
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            String key = String.valueOf(e.getKey()).trim().toUpperCase(Locale.ROOT);
            if (key.isEmpty() || key.startsWith("#")) continue;
            if (e.getValue() instanceof Number n) {
                itemPrices.put(key, n.doubleValue());
            }
        }
    }

    /** Explicit entry from file; 0 if absent. */
    public double explicitPrice(String bukkitMaterialUpper) {
        return itemPrices.getOrDefault(bukkitMaterialUpper, 0.0);
    }

    public List<Map.Entry<String, Double>> sortedEntries() {
        List<Map.Entry<String, Double>> list = new ArrayList<>(itemPrices.entrySet());
        list.sort(Comparator.comparing(Map.Entry::getKey));
        return list;
    }

    public int priceEntryCount() {
        return itemPrices.size();
    }
}
