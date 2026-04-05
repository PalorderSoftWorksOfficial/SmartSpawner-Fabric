package com.palordersoftworks.smartspawner.mixin;

import com.palordersoftworks.smartspawner.SmartSpawnerRuntime;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobSpawnerBlockEntity.class)
public abstract class SpawnerBlockEntityMixin {

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void smartspawner$cancelVanillaSpawn(World world, BlockPos pos, BlockState state, MobSpawnerBlockEntity blockEntity, CallbackInfo ci) {
        if (!(world instanceof ServerWorld sl)) {
            return;
        }
        SmartSpawnerRuntime rt = SmartSpawnerRuntime.getOrNull();
        if (rt != null && rt.getSpawnerManager().manages(sl, pos)) {
            ci.cancel();
        }
    }
}
