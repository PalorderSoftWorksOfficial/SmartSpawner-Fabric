package com.palordersoftworks.smartspawner.interaction;

import com.palordersoftworks.smartspawner.SmartSpawnerFabric;
import com.palordersoftworks.smartspawner.SmartSpawnerRuntime;
import com.palordersoftworks.smartspawner.data.FabricSpawnerData;
import com.palordersoftworks.smartspawner.data.FabricSpawnerManager;
import com.palordersoftworks.smartspawner.gui.SpawnerMenus;
import com.palordersoftworks.smartspawner.permission.SsPermissions;
import com.palordersoftworks.smartspawner.stack.SpawnerStackService;
import com.palordersoftworks.smartspawner.util.SpawnerEntityReader;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class SpawnerInteractions {

    private SpawnerInteractions() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register(SpawnerInteractions::onUseBlock);
        PlayerBlockBreakEvents.BEFORE.register(SpawnerInteractions::onBreakBefore);
    }

    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity sp) || !(world instanceof ServerWorld sw)) {
            return ActionResult.PASS;
        }
        SmartSpawnerRuntime rt = SmartSpawnerRuntime.getOrNull();
        if (rt == null || rt.getServer() == null) {
            return ActionResult.PASS;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (!state.isOf(Blocks.SPAWNER)) {
            return ActionResult.PASS;
        }
        FabricSpawnerManager mgr = rt.getSpawnerManager();
        FabricSpawnerData data = mgr.getByBlock(sw, pos);
        if (data == null) {
            if (!(world.getBlockEntity(pos) instanceof MobSpawnerBlockEntity sbe)) {
                return ActionResult.PASS;
            }
            String entityName = SpawnerEntityReader.readBukkitEntityName(sbe, sw.getRegistryManager())
                    .orElse("PIG");
            String id = mgr.nextId();
            data = new FabricSpawnerData(rt, id, sw, pos, entityName, null);
            data.initDefaultsFromConfig();
            mgr.register(data);
            if (rt.getYamlStore() != null) {
                rt.getYamlStore().markDirty(id);
            }
            SmartSpawnerFabric.LOGGER.info("Registered SmartSpawner {} at {} ({})", id, pos, entityName);
        }
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        if (SpawnerStackService.tryHandStack(sp, data)) {
            return ActionResult.SUCCESS;
        }
        SpawnerMenus.openMain(sp, data);
        return ActionResult.SUCCESS;
    }

    private static boolean onBreakBefore(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (world.isClient() || !(world instanceof ServerWorld sw)) {
            return true;
        }
        SmartSpawnerRuntime rt = SmartSpawnerRuntime.getOrNull();
        if (rt == null) {
            return true;
        }
        FabricSpawnerData d = rt.getSpawnerManager().getByBlock(sw, pos);
        if (d != null) {
            if (player instanceof ServerPlayerEntity sp && !SsPermissions.check(sp, SsPermissions.BREAK, true)) {
                return false;
            }
            rt.getSpawnerManager().unregister(d.getSpawnerId());
        }
        return true;
    }
}
