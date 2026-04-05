package com.palordersoftworks.smartspawner;

import com.palordersoftworks.smartspawner.command.SsCommands;
import com.palordersoftworks.smartspawner.config.ModConfig;
import com.palordersoftworks.smartspawner.data.FabricSpawnerManager;
import com.palordersoftworks.smartspawner.economy.FabricItemPrices;
import com.palordersoftworks.smartspawner.hologram.SpawnerHologramManager;
import com.palordersoftworks.smartspawner.hopper.HopperPullService;
import com.palordersoftworks.smartspawner.interaction.SpawnerInteractions;
import com.palordersoftworks.smartspawner.loot.LootTableRegistry;
import com.palordersoftworks.smartspawner.near.NearHighlightManager;
import com.palordersoftworks.smartspawner.storage.YamlSpawnerStore;
import com.palordersoftworks.smartspawner.tick.SpawnerGameplayLoop;
import lombok.Getter;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-side singleton: config, storage, spawner registry, tick loop, and interactions.
 */
public final class SmartSpawnerRuntime {

    private static volatile SmartSpawnerRuntime INSTANCE;

    @Getter
    private final Path configDir;
    @Getter
    private ModConfig modConfig = ModConfig.defaults();
    @Getter
    private LootTableRegistry lootRegistry = new LootTableRegistry();
    @Getter
    private final FabricItemPrices itemPrices = new FabricItemPrices();
    @Getter
    @Nullable
    private YamlSpawnerStore yamlStore;
    @Getter
    private FabricSpawnerManager spawnerManager = new FabricSpawnerManager(this);
    @Getter
    private SpawnerGameplayLoop gameplayLoop = new SpawnerGameplayLoop(this);
    @Getter
    private final HopperPullService hopperPullService = new HopperPullService(this);
    @Getter
    private final NearHighlightManager nearHighlightManager = new NearHighlightManager();
    @Getter
    private final SpawnerHologramManager spawnerHologramManager;
    @Getter
    @Nullable
    private MinecraftServer server;

    private volatile boolean hologramsEnabled;

    private SmartSpawnerRuntime(Path configDir) {
        this.configDir = configDir;
        this.spawnerHologramManager = new SpawnerHologramManager(this);
    }

    public static void bootstrap() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("smartspawner");
        INSTANCE = new SmartSpawnerRuntime(dir);
        INSTANCE.registerLifecycle();
        SpawnerInteractions.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, env) -> SsCommands.register(dispatcher, ctx));
    }

    public static SmartSpawnerRuntime get() {
        if (INSTANCE == null) {
            throw new IllegalStateException("SmartSpawner not bootstrapped");
        }
        return INSTANCE;
    }

    public static @Nullable SmartSpawnerRuntime getOrNull() {
        return INSTANCE;
    }

    public boolean isHologramsEnabled() {
        return hologramsEnabled;
    }

    public void setHologramsEnabled(boolean hologramsEnabled) {
        this.hologramsEnabled = hologramsEnabled;
    }

    private void registerLifecycle() {
        ServerLifecycleEvents.SERVER_STARTING.register(s -> {
            this.server = s;
            try {
                Files.createDirectories(configDir);
                ConfigBootstrap.copyDefaultsIfMissing();
                this.modConfig = ModConfig.load(configDir.resolve("config.yml"));
                this.hologramsEnabled = modConfig.hologramEnabledInFile();
                this.itemPrices.reload(configDir, this.modConfig);
                this.lootRegistry = LootTableRegistry.load(
                        configDir.resolve("spawners_settings.yml"),
                        configDir.resolve("item_spawners_settings.yml"));
                this.yamlStore = new YamlSpawnerStore(this, configDir.resolve("spawners_data.yml"));
                this.yamlStore.loadAllIntoManager();
                this.gameplayLoop.start();
            } catch (Exception e) {
                SmartSpawnerFabric.LOGGER.error("SmartSpawner failed to start", e);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            this.gameplayLoop.stop();
            this.spawnerHologramManager.removeAll();
            if (yamlStore != null) {
                yamlStore.flushBlocking();
            }
            this.server = null;
        });
    }

    public void reloadFromDisk() throws Exception {
        this.modConfig = ModConfig.load(configDir.resolve("config.yml"));
        this.hologramsEnabled = modConfig.hologramEnabledInFile();
        this.itemPrices.reload(configDir, this.modConfig);
        this.lootRegistry = LootTableRegistry.load(
                configDir.resolve("spawners_settings.yml"),
                configDir.resolve("item_spawners_settings.yml"));
        spawnerManager.reloadLootConfigs();
        this.spawnerHologramManager.refreshAll();
    }

    public boolean debug() {
        return modConfig != null && modConfig.debug();
    }

    public void tickHopperAndVisuals(MinecraftServer server, int tickCounter) {
        long hopEvery = Math.max(1, modConfig.hopperCheckDelayTicks());
        if (tickCounter % hopEvery == 0) {
            hopperPullService.tick();
        }
        spawnerHologramManager.tick();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            nearHighlightManager.tickPlayer(p);
        }
    }
}
