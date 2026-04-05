package com.palordersoftworks.smartspawner;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SmartSpawnerFabric implements ModInitializer {

    public static final String MOD_ID = "smartspawner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        SmartSpawnerRuntime.bootstrap();
    }
}
