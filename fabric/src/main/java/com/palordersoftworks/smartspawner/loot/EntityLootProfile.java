package com.palordersoftworks.smartspawner.loot;

import java.util.List;

public record EntityLootProfile(int experience, List<LootEntryDef> entries) {
}
