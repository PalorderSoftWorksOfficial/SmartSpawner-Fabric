package com.palordersoftworks.smartspawner.item;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.TypedEntityData;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.Optional;

/**
 * Builds spawner items (smart / vanilla / item-spawner) compatible with {@link com.palordersoftworks.smartspawner.util.SpawnerEntityReader}.
 */
public final class SpawnerItemHelper {

    public static final String CUSTOM_ROOT = "SmartSpawner";
    public static final String VANILLA_KEY = "Vanilla";
    public static final String ITEM_MATERIAL_KEY = "ItemMaterial";

    private SpawnerItemHelper() {
    }

    public static ItemStack createSmartSpawner(EntityType<?> entityType, int amount) {
        ItemStack stack = new ItemStack(Items.SPAWNER, amount);
        stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, TypedEntityData.create(BlockEntityType.MOB_SPAWNER, mobSpawnerNbt(entityType)));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(prettyEntityName(entityType) + " Spawner"));
        return stack;
    }

    public static ItemStack createVanillaSpawner(EntityType<?> entityType, int amount) {
        ItemStack stack = createSmartSpawner(entityType, amount);
        NbtCompound root = new NbtCompound();
        NbtCompound ss = new NbtCompound();
        ss.putBoolean(VANILLA_KEY, true);
        root.put(CUSTOM_ROOT, ss);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Vanilla " + prettyEntityName(entityType) + " Spawner"));
        return stack;
    }

    public static ItemStack createItemSpawner(String bukkitMaterialUpper, int amount) {
        ItemStack stack = new ItemStack(Items.SPAWNER, amount);
        stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, TypedEntityData.create(BlockEntityType.MOB_SPAWNER, mobSpawnerNbt(EntityType.ITEM)));
        NbtCompound root = new NbtCompound();
        NbtCompound ss = new NbtCompound();
        ss.putString(ITEM_MATERIAL_KEY, bukkitMaterialUpper.toUpperCase(Locale.ROOT));
        root.put(CUSTOM_ROOT, ss);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Item Spawner (" + bukkitMaterialUpper + ")"));
        return stack;
    }

    public static boolean isVanillaSpawnerItem(ItemStack stack) {
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (c == null) return false;
        NbtCompound root = c.copyNbt();
        return root.getCompound(CUSTOM_ROOT)
                .map(n -> n.getBoolean(VANILLA_KEY).orElse(false))
                .orElse(false);
    }

    public static Optional<String> readItemSpawnerMaterial(ItemStack stack) {
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (c == null) return Optional.empty();
        NbtCompound root = c.copyNbt();
        return root.getCompound(CUSTOM_ROOT).flatMap(n -> n.getString(ITEM_MATERIAL_KEY));
    }

    public static Optional<EntityType<?>> readSpawnedEntityType(ItemStack stack) {
        TypedEntityData<?> bed = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
        if (bed == null || !BlockEntityType.MOB_SPAWNER.equals(bed.getType())) {
            return Optional.empty();
        }
        NbtCompound nbt = bed.copyNbtWithoutId();
        Optional<String> idOpt = readEntityIdFromSpawnerNbt(nbt);
        if (idOpt.isEmpty()) {
            return Optional.empty();
        }
        Identifier id = Identifier.tryParse(idOpt.get());
        if (id == null) {
            return Optional.empty();
        }
        return Registries.ENTITY_TYPE.getOptionalValue(id);
    }

    private static Optional<String> readEntityIdFromSpawnerNbt(NbtCompound tag) {
        Optional<NbtCompound> spawnData = tag.contains("spawn_data")
                ? tag.getCompound("spawn_data")
                : tag.contains("SpawnData") ? tag.getCompound("SpawnData") : Optional.empty();
        if (spawnData.isEmpty()) {
            return Optional.empty();
        }
        NbtCompound sd = spawnData.get();
        NbtCompound entity;
        if (sd.contains("entity")) {
            Optional<NbtCompound> ec = sd.getCompound("entity");
            if (ec.isEmpty()) return Optional.empty();
            entity = ec.get();
        } else {
            entity = sd;
        }
        return entity.contains("id") ? entity.getString("id") : Optional.empty();
    }

    private static NbtCompound mobSpawnerNbt(EntityType<?> type) {
        Identifier id = Registries.ENTITY_TYPE.getId(type);
        NbtCompound entity = new NbtCompound();
        entity.putString("id", id.toString());
        NbtCompound spawnData = new NbtCompound();
        spawnData.put("entity", entity);
        NbtCompound root = new NbtCompound();
        root.put("spawn_data", spawnData);
        return root;
    }

    private static String prettyEntityName(EntityType<?> type) {
        return Registries.ENTITY_TYPE.getId(type).getPath().replace('_', ' ');
    }
}
