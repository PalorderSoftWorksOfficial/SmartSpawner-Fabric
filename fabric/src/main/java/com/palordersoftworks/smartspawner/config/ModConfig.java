package com.palordersoftworks.smartspawner.config;

import com.palordersoftworks.smartspawner.economy.PriceSourceMode;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@SuppressWarnings("unchecked")
public final class ModConfig {

    private final Map<String, Object> root;

    private ModConfig(Map<String, Object> root) {
        this.root = root;
    }

    public static ModConfig defaults() {
        return new ModConfig(Map.of());
    }

    public static ModConfig load(Path file) throws IOException {
        if (!Files.exists(file)) {
            return defaults();
        }
        Yaml yaml = new Yaml();
        Object o = yaml.load(Files.readString(file));
        if (o instanceof Map<?, ?> m) {
            return new ModConfig((Map<String, Object>) m);
        }
        return defaults();
    }

    public boolean debug() {
        return bool(root, "debug", false);
    }

    public int minMobs() {
        return intAt(pathDefaultSpawner(), "min_mobs", 1);
    }

    public int maxMobs() {
        return intAt(pathDefaultSpawner(), "max_mobs", 4);
    }

    public int range() {
        return intAt(pathDefaultSpawner(), "range", 16);
    }

    public long delayTicks() {
        String raw = strAt(pathDefaultSpawner(), "delay", "25s");
        return com.palordersoftworks.smartspawner.util.TimeParser.parseTicks(raw, 500);
    }

    public int maxStoragePages() {
        return intAt(pathDefaultSpawner(), "max_storage_pages", 1);
    }

    public int maxStoredExp() {
        return intAt(pathDefaultSpawner(), "max_stored_exp", 1000);
    }

    public int maxStackSize() {
        return intAt(pathDefaultSpawner(), "max_stack_size", 10000);
    }

    public boolean protectSpawnerExplosions() {
        Map<String, Object> sp = mapAt(root, "spawner_properties");
        Map<String, Object> def = mapAt(sp, "default");
        return bool(def, "protect_from_explosions", true);
    }

    public boolean spawnerBreakEnabled() {
        Map<String, Object> br = mapAt(root, "spawner_break");
        return bool(br, "enabled", true);
    }

    public boolean customEconomyEnabled() {
        Map<String, Object> ce = mapAt(root, "custom_economy");
        return bool(ce, "enabled", true);
    }

    public PriceSourceMode priceSourceMode() {
        Map<String, Object> ce = mapAt(root, "custom_economy");
        return PriceSourceMode.fromConfig(strAt(ce, "price_source_mode", "SHOP_PRIORITY"));
    }

    public boolean customPricesEnabled() {
        Map<String, Object> ce = mapAt(root, "custom_economy");
        Map<String, Object> cp = mapAt(ce, "custom_prices");
        return bool(cp, "enabled", true);
    }

    public String priceFileName() {
        Map<String, Object> ce = mapAt(root, "custom_economy");
        Map<String, Object> cp = mapAt(ce, "custom_prices");
        return strAt(cp, "price_file_name", "item_prices.yml");
    }

    public double defaultSellPrice() {
        Map<String, Object> ce = mapAt(root, "custom_economy");
        Map<String, Object> cp = mapAt(ce, "custom_prices");
        Object v = cp.get("default_price");
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return 1.0;
    }

    public boolean hopperEnabled() {
        return bool(mapAt(root, "hopper"), "enabled", false);
    }

    public long hopperCheckDelayTicks() {
        String raw = strAt(mapAt(root, "hopper"), "check_delay", "3s");
        return com.palordersoftworks.smartspawner.util.TimeParser.parseTicks(raw, 60);
    }

    public int hopperStacksPerTransfer() {
        int v = intAt(mapAt(root, "hopper"), "stack_per_transfer", 5);
        return Math.min(5, Math.max(1, v));
    }

    public boolean hologramEnabledInFile() {
        return bool(mapAt(root, "hologram"), "enabled", false);
    }

    public double hologramOffsetX() {
        Map<String, Object> h = mapAt(root, "hologram");
        Object v = h.get("offset_x");
        if (v instanceof Number n) return n.doubleValue();
        return 0.5;
    }

    public double hologramOffsetY() {
        Map<String, Object> h = mapAt(root, "hologram");
        Object v = h.get("offset_y");
        if (v instanceof Number n) return n.doubleValue();
        return 1.6;
    }

    public double hologramOffsetZ() {
        Map<String, Object> h = mapAt(root, "hologram");
        Object v = h.get("offset_z");
        if (v instanceof Number n) return n.doubleValue();
        return 0.5;
    }

    private Map<String, Object> pathDefaultSpawner() {
        Map<String, Object> sp = mapAt(root, "spawner_properties");
        return mapAt(sp, "default");
    }

    private static Map<String, Object> mapAt(Map<String, Object> m, String key) {
        if (m == null) return Map.of();
        Object v = m.get(key);
        if (v instanceof Map<?, ?> mm) {
            return (Map<String, Object>) mm;
        }
        return Map.of();
    }

    private static boolean bool(Map<String, Object> m, String key, boolean d) {
        if (m == null) return d;
        Object v = m.get(key);
        if (v instanceof Boolean b) return b;
        return d;
    }

    private static int intAt(Map<String, Object> m, String key, int d) {
        if (m == null) return d;
        Object v = m.get(key);
        if (v instanceof Number n) return n.intValue();
        return d;
    }

    private static String strAt(Map<String, Object> m, String key, String d) {
        if (m == null) return d;
        Object v = m.get(key);
        if (v instanceof String s) return s;
        return d;
    }
}
