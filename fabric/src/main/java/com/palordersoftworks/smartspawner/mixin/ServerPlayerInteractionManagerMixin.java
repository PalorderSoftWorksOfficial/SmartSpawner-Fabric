package com.palordersoftworks.smartspawner.mixin;

import com.palordersoftworks.smartspawner.interaction.SpawnerPlaceHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerMixin {

    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void smartspawner$captureSpawnerItem(
            ServerPlayerEntity player,
            World world,
            ItemStack stack,
            Hand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<ActionResult> cir) {
        if (!world.isClient()) {
            SpawnerPlaceHandler.noteSpawnerInteract(player, stack);
        }
    }

    @Inject(method = "interactBlock", at = @At("RETURN"))
    private void smartspawner$afterInteractBlock(
            ServerPlayerEntity player,
            World world,
            ItemStack stack,
            Hand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<ActionResult> cir) {
        if (!world.isClient() && world instanceof ServerWorld sw) {
            SpawnerPlaceHandler.onInteractBlockAfter(player, sw, hit, cir.getReturnValue());
        }
    }
}
