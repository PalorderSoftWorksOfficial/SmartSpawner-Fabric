package com.palordersoftworks.smartspawner.loot;

import com.palordersoftworks.smartspawner.SmartSpawnerFabric;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("unchecked")
public final class LootTableRegistry {

    private final Map<String, EntityLootProfile> byEntityKey = new HashMap<>();
    private final Map<String, EntityLootProfile> byItemMaterialKey = new HashMap<>();

    public boolean hasItemSpawnerMaterial(String bukkitMaterialUpper) {
        return byItemMaterialKey.containsKey(bukkitMaterialUpper.toUpperCase(Locale.ROOT));
    }

    public List<String> itemSpawnerMaterialKeysSorted() {
        List<String> keys = new ArrayList<>(byItemMaterialKey.keySet());
        keys.sort(String::compareTo);
        return keys;
    }

    public static LootTableRegistry load(Path entitiesFile, Path itemsFile) throws IOException {
        LootTableRegistry reg = new LootTableRegistry();
        if (Files.exists(entitiesFile)) {
            reg.ingestEntities(new Yaml().load(Files.readString(entitiesFile)));
        }
        if (Files.exists(itemsFile)) {
            reg.ingestItems(new Yaml().load(Files.readString(itemsFile)));
        }
        return reg;
    }

    private void ingestEntities(Object yamlRoot) {
        if (!(yamlRoot instanceof Map<?, ?> map)) return;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!(e.getKey() instanceof String key)) continue;
            if (key.startsWith("#") || "default_material".equals(key)) continue;
            if (!(e.getValue() instanceof Map<?, ?> section)) continue;
            EntityLootProfile prof = parseEntitySection(section);
            if (prof != null) {
                byEntityKey.put(key.toUpperCase(Locale.ROOT), prof);
            }
        }
    }

    private void ingestItems(Object yamlRoot) {
        if (!(yamlRoot instanceof Map<?, ?> map)) return;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!(e.getKey() instanceof String key)) continue;
            if (key.startsWith("#") || "default_material".equals(key)) continue;
            if (!(e.getValue() instanceof Map<?, ?> section)) continue;
            EntityLootProfile prof = parseEntitySection(section);
            if (prof != null) {
                byItemMaterialKey.put(key.toUpperCase(Locale.ROOT), prof);
            }
        }
    }

    private EntityLootProfile parseEntitySection(Map<?, ?> section) {
        Object expObj = section.get("experience");
        int exp = expObj instanceof Number n ? n.intValue() : 0;
        Object loot = section.get("loot");
        List<LootEntryDef> entries = new ArrayList<>();
        if (loot instanceof Map<?, ?> lootMap) {
            for (Map.Entry<?, ?> le : lootMap.entrySet()) {
                if (!(le.getKey() instanceof String matName)) continue;
                if (!(le.getValue() instanceof Map<?, ?> def)) continue;
                LootEntryDef entry = parseLootLine(matName, def);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        return new EntityLootProfile(exp, List.copyOf(entries));
    }

    private LootEntryDef parseLootLine(String materialUpper, Map<?, ?> def) {
        Item item = itemFromBukkitName(materialUpper);
        if (item == Items.AIR) {
            return null;
        }
        Object amountObj = def.get("amount");
        String amount = amountObj != null ? String.valueOf(amountObj) : "1-1";
        int minA = 1;
        int maxA = 1;
        if (amount.contains("-")) {
            String[] p = amount.split("-");
            minA = Integer.parseInt(p[0].trim());
            maxA = Integer.parseInt(p[1].trim());
        } else {
            minA = maxA = Integer.parseInt(amount.trim());
        }
        double chance = 100.0;
        Object c = def.get("chance");
        if (c instanceof Number n) {
            chance = n.doubleValue();
        }
        Integer minD = null;
        Integer maxD = null;
        Object dur = def.get("durability");
        if (dur instanceof String ds && ds.contains("-")) {
            String[] p = ds.split("-");
            minD = Integer.parseInt(p[0].trim());
            maxD = Integer.parseInt(p[1].trim());
        }
        String potion = null;
        Object pt = def.get("potion_type");
        if (pt instanceof String s) {
            potion = s;
        }
        double price = 0;
        Object sp = def.get("sell_price");
        if (sp instanceof Number n) {
            price = n.doubleValue();
        }
        return new LootEntryDef(item, minA, maxA, chance, minD, maxD, potion, price);
    }

    private static Item itemFromBukkitName(String bukkitMaterial) {
        String path = bukkitMaterial.toLowerCase(Locale.ROOT);
        Identifier rl = Identifier.of("minecraft", path);
        return Registries.ITEM.get(rl);
    }

    public EntityLootProfile forEntity(String bukkitEntityTypeName) {
        return byEntityKey.get(bukkitEntityTypeName.toUpperCase(Locale.ROOT));
    }

    public EntityLootProfile forItemMaterial(String bukkitMaterialName) {
        return byItemMaterialKey.get(bukkitMaterialName.toUpperCase(Locale.ROOT));
    }
}
