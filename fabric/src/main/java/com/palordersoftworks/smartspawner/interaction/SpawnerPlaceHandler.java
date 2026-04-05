package com.palordersoftworks.smartspawner.interaction;

import com.palordersoftworks.smartspawner.SmartSpawnerFabric;
import com.palordersoftworks.smartspawner.SmartSpawnerRuntime;
import com.palordersoftworks.smartspawner.data.FabricSpawnerData;
import com.palordersoftworks.smartspawner.data.FabricSpawnerManager;
import com.palordersoftworks.smartspawner.item.SpawnerItemHelper;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers SmartSpawner data when a player places a smart/item spawner from our items.
 * Vanilla-marked spawners are left unmanaged so vanilla spawn logic runs.
 */
public final class SpawnerPlaceHandler {

    private static final ConcurrentHashMap<UUID, ItemStack> PENDING = new ConcurrentHashMap<>();

    private SpawnerPlaceHandler() {
    }

    /** Called from mixin at start of {@link net.minecraft.server.network.ServerPlayerInteractionManager#interactBlock}. */
    public static void noteSpawnerInteract(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isOf(Items.SPAWNER)) {
            PENDING.put(player.getUuid(), stack.copy());
        }
    }

    /** Called from mixin at return of {@code interactBlock} (Fabric has no stable place event on this version). */
    public static void onInteractBlockAfter(ServerPlayerEntity player, ServerWorld world, BlockHitResult hit, ActionResult result) {
        if (!result.isAccepted()) {
            return;
        }
        BlockPos placed = hit.getBlockPos().offset(hit.getSide());
        if (!world.getBlockState(placed).isOf(Blocks.SPAWNER)) {
            return;
        }
        ItemStack snapshot = PENDING.remove(player.getUuid());
        if (snapshot == null || !snapshot.isOf(Items.SPAWNER)) {
            return;
        }
        finishSmartPlacement(world, player, placed, snapshot);
    }

    private static void finishSmartPlacement(ServerWorld sw, ServerPlayerEntity sp, BlockPos pos, ItemStack snapshot) {
        SmartSpawnerRuntime rt = SmartSpawnerRuntime.getOrNull();
        if (rt == null) {
            return;
        }
        if (SpawnerItemHelper.isVanillaSpawnerItem(snapshot)) {
            return;
        }

        Optional<EntityType<?>> typeOpt = SpawnerItemHelper.readSpawnedEntityType(snapshot);
        if (typeOpt.isEmpty()) {
            return;
        }
        EntityType<?> entityType = typeOpt.get();
        Optional<String> itemMatOpt = SpawnerItemHelper.readItemSpawnerMaterial(snapshot);

        int stackSize = computeStackSize(sp, snapshot);
        if (!consumeExtraSpawnerItems(sp, snapshot, stackSize)) {
            return;
        }

        FabricSpawnerManager mgr = rt.getSpawnerManager();
        FabricSpawnerData existing = mgr.getByBlock(sw, pos);

        String entityName = entityTypeToBukkitName(entityType);
        String itemMat = itemMatOpt.orElse(null);

        if (existing != null) {
            mergeOntoExisting(sp, existing, entityName, itemMat, stackSize, rt);
            return;
        }

        String id = mgr.nextId();
        FabricSpawnerData data = new FabricSpawnerData(rt, id, sw, pos, entityName, itemMat);
        data.initDefaultsFromConfig();
        data.setStackSize(stackSize);
        data.recalculateStackDerived();
        mgr.register(data);
        if (rt.getYamlStore() != null) {
            rt.getYamlStore().markDirty(id);
        }
        SmartSpawnerFabric.LOGGER.info("Registered SmartSpawner {} at {} ({})", id, pos, entityName);
    }

    private static void mergeOntoExisting(
            ServerPlayerEntity player,
            FabricSpawnerData existing,
            String entityName,
            String itemMat,
            int stackSize,
            SmartSpawnerRuntime rt) {
        boolean sameMob = itemMat != null
                ? existing.isItemSpawner() && itemMat.equalsIgnoreCase(existing.getItemSpawnerMaterial())
                : !existing.isItemSpawner() && existing.getEntityTypeName().equalsIgnoreCase(entityName);
        if (sameMob) {
            int next = Math.min(existing.getStackSize() + stackSize, existing.getMaxStackSize());
            existing.setStackSize(next);
            existing.recalculateStackDerived();
        }
        if (rt.getYamlStore() != null) {
            rt.getYamlStore().markDirty(existing.getSpawnerId());
        }
    }

    private static int computeStackSize(ServerPlayerEntity player, ItemStack template) {
        SmartSpawnerRuntime rt = SmartSpawnerRuntime.getOrNull();
        int maxStack = rt != null ? rt.getModConfig().maxStackSize() : 10000;
        if (player.isSneaking()) {
            return Math.min(template.getCount(), maxStack);
        }
        return 1;
    }

    private static boolean consumeExtraSpawnerItems(ServerPlayerEntity player, ItemStack template, int stackSize) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        if (stackSize <= 1) {
            return true;
        }
        int need = stackSize - 1;
        for (int i = 0; i < player.getInventory().size() && need > 0; i++) {
            ItemStack slot = player.getInventory().getStack(i);
            if (slot.isEmpty() || !ItemStack.areItemsAndComponentsEqual(template, slot)) {
                continue;
            }
            int take = Math.min(need, slot.getCount());
            slot.decrement(take);
            need -= take;
        }
        return need == 0;
    }

    private static String entityTypeToBukkitName(EntityType<?> type) {
        return Registries.ENTITY_TYPE.getId(type).getPath().toUpperCase(Locale.ROOT).replace('.', '_');
    }
}
