package com.palordersoftworks.smartspawner.storage;

import com.palordersoftworks.smartspawner.SmartSpawnerFabric;
import com.palordersoftworks.smartspawner.SmartSpawnerRuntime;
import com.palordersoftworks.smartspawner.data.FabricSpawnerData;
import com.palordersoftworks.smartspawner.data.FabricSpawnerManager;
import com.palordersoftworks.smartspawner.serialization.PaperItemFormat;
import com.palordersoftworks.smartspawner.util.WorldKeyUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unchecked")
public final class YamlSpawnerStore {

    private static final int DATA_VERSION = 3;
    private static final String DATA_VERSION_KEY = "data_version";

    private final SmartSpawnerRuntime runtime;
    private final Path file;
    private final Map<String, Object> root;
    private final Set<String> dirty = ConcurrentHashMap.newKeySet();
    private final Set<String> deleted = ConcurrentHashMap.newKeySet();
    private final Yaml yaml;

    public YamlSpawnerStore(SmartSpawnerRuntime runtime, Path file) throws IOException {
        this.runtime = runtime;
        this.file = file;
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        this.yaml = new Yaml(options);
        if (!Files.exists(file)) {
            this.root = new HashMap<>();
            root.put(DATA_VERSION_KEY, DATA_VERSION);
            root.put("spawners", new HashMap<String, Object>());
            saveWholeFile();
        } else {
            Object loaded = yaml.load(Files.readString(file));
            if (loaded instanceof Map<?, ?> m) {
                this.root = (Map<String, Object>) m;
            } else {
                this.root = new HashMap<>();
                root.put("spawners", new HashMap<String, Object>());
            }
        }
    }

    public void loadAllIntoManager() {
        MinecraftServer server = runtime.getServer();
        if (server == null) return;
        Map<String, Object> spawners = map(root, "spawners");
        FabricSpawnerManager mgr = runtime.getSpawnerManager();
        for (String id : spawners.keySet()) {
            FabricSpawnerData data = loadOne(server, id);
            if (data != null) {
                mgr.putLoaded(data);
            }
        }
    }

    private FabricSpawnerData loadOne(MinecraftServer server, String id) {
        Map<String, Object> spawners = map(root, "spawners");
        Object raw = spawners.get(id);
        if (!(raw instanceof Map<?, ?> sm)) return null;
        Map<String, Object> s = (Map<String, Object>) sm;
        String loc = str(s, "location");
        if (loc == null) return null;
        String[] lp = loc.split(",");
        if (lp.length != 4) return null;
        ServerWorld world = WorldKeyUtil.resolveLevel(server, lp[0]);
        if (world == null) return null;
        BlockPos pos = new BlockPos(Integer.parseInt(lp[1].trim()), Integer.parseInt(lp[2].trim()), Integer.parseInt(lp[3].trim()));
        String entityType = str(s, "entityType");
        if (entityType == null) return null;
        String itemMat = str(s, "itemSpawnerMaterial");
        FabricSpawnerData data = new FabricSpawnerData(runtime, id, world, pos, entityType, itemMat);
        data.initDefaultsFromConfig();

        String settings = str(s, "settings");
        if (settings != null) {
            String[] parts = settings.split(",");
            int ver = root.get(DATA_VERSION_KEY) instanceof Number n ? n.intValue() : 1;
            try {
                if (ver >= 3 && parts.length >= 13) {
                    data.setSpawnerExp(Integer.parseInt(parts[0]));
                    data.setSpawnerActive(Boolean.parseBoolean(parts[1]));
                    data.setSpawnerRange(Integer.parseInt(parts[2]));
                    data.getSpawnerStop().set(Boolean.parseBoolean(parts[3]));
                    data.setSpawnDelayTicks(Long.parseLong(parts[4]));
                    data.setMaxSpawnerLootSlots(Integer.parseInt(parts[5]));
                    data.setMaxStoredExp(Integer.parseInt(parts[6]));
                    data.setMinMobs(Integer.parseInt(parts[7]));
                    data.setMaxMobs(Integer.parseInt(parts[8]));
                    data.setMaxStackSize(Integer.parseInt(parts[10]));
                    data.setStackSize(Integer.parseInt(parts[9]));
                    data.setLastSpawnTimeMillis(Long.parseLong(parts[11]));
                    data.setAtCapacity(Boolean.parseBoolean(parts[12]));
                }
            } catch (Exception e) {
                SmartSpawnerFabric.LOGGER.warn("Bad settings for spawner {}: {}", id, e.getMessage());
            }
        }

        data.recalculateStackDerived();
        Object inv = s.get("inventory");
        if (inv instanceof List<?> list) {
            data.virtualInventory().loadFromSerializedLines((List<String>) list);
        }
        return data;
    }

    public void saveSpawner(FabricSpawnerData d) {
        Map<String, Object> spawners = map(root, "spawners");
        Map<String, Object> s = new HashMap<>();
        String worldName = WorldKeyUtil.toPaperWorldName(d.getWorld());
        s.put("location", worldName + "," + d.getPos().getX() + "," + d.getPos().getY() + "," + d.getPos().getZ());
        s.put("entityType", d.getEntityTypeName());
        s.put("itemSpawnerMaterial", d.isItemSpawner() ? d.getItemSpawnerMaterial() : null);
        String settings = String.format("%d,%b,%d,%b,%d,%d,%d,%d,%d,%d,%d,%d,%b",
                d.getSpawnerExp(),
                d.isSpawnerActive(),
                d.getSpawnerRange(),
                d.getSpawnerStop().get(),
                d.getSpawnDelayTicks(),
                d.getMaxSpawnerLootSlots(),
                d.getMaxStoredExp(),
                d.getMinMobs(),
                d.getMaxMobs(),
                d.getStackSize(),
                d.getMaxStackSize(),
                d.getLastSpawnTimeMillis(),
                d.isAtCapacity());
        s.put("settings", settings);
        s.put("inventory", PaperItemFormat.serialize(d.virtualInventory().getConsolidatedItems()));
        spawners.put(d.getSpawnerId(), s);
        root.put(DATA_VERSION_KEY, DATA_VERSION);
    }

    public void removeFromFile(String id) {
        map(root, "spawners").remove(id);
    }

    public void markDirty(String id) {
        dirty.add(id);
        deleted.remove(id);
    }

    public void markDeleted(String id) {
        deleted.add(id);
        dirty.remove(id);
    }

    public void flushAsync() {
        if (dirty.isEmpty() && deleted.isEmpty()) return;
        Set<String> d0 = new HashSet<>(dirty);
        dirty.removeAll(d0);
        Set<String> del = new HashSet<>(deleted);
        deleted.removeAll(del);
        for (String id : del) {
            removeFromFile(id);
        }
        FabricSpawnerManager mgr = runtime.getSpawnerManager();
        for (String id : d0) {
            FabricSpawnerData data = mgr.getById(id);
            if (data != null) {
                saveSpawner(data);
            }
        }
        try {
            saveWholeFile();
        } catch (IOException e) {
            SmartSpawnerFabric.LOGGER.error("Failed to save spawners_data.yml", e);
        }
    }

    public void flushBlocking() {
        flushAsync();
    }

    private void saveWholeFile() throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml.dump(root));
    }

    private static Map<String, Object> map(Map<String, Object> r, String key) {
        Object o = r.computeIfAbsent(key, k -> new HashMap<String, Object>());
        return (Map<String, Object>) o;
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof String s ? s : null;
    }
}
