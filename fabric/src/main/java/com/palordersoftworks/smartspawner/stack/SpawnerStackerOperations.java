package com.palordersoftworks.smartspawner.stack;

import com.palordersoftworks.smartspawner.SmartSpawnerRuntime;
import com.palordersoftworks.smartspawner.data.FabricSpawnerData;
import com.palordersoftworks.smartspawner.item.SpawnerItemHelper;
import com.palordersoftworks.smartspawner.util.EntityCatalog;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;

import java.util.Optional;

/**
 * Stack / de-stack logic shared by hand-use and stacker GUI (Paper parity, simplified).
 */
public final class SpawnerStackerOperations {

    private SpawnerStackerOperations() {
    }

    public static boolean handStackMatches(FabricSpawnerData target, ItemStack hand) {
        if (hand.isEmpty() || !hand.isOf(Items.SPAWNER)) {
            return false;
        }
        if (SpawnerItemHelper.isVanillaSpawnerItem(hand)) {
            return false;
        }
        boolean handItem = SpawnerItemHelper.readItemSpawnerMaterial(hand).isPresent();
        if (handItem != target.isItemSpawner()) {
            return false;
        }
        if (target.isItemSpawner()) {
            Optional<String> hm = SpawnerItemHelper.readItemSpawnerMaterial(hand);
            return hm.isPresent() && hm.get().equalsIgnoreCase(target.getItemSpawnerMaterial());
        }
        Optional<EntityType<?>> ht = SpawnerItemHelper.readSpawnedEntityType(hand);
        if (ht.isEmpty()) {
            return false;
        }
        EntityType<?> want;
        try {
            want = EntityCatalog.parseMobType(target.getEntityTypeName());
        } catch (IllegalArgumentException e) {
            return false;
        }
        return ht.get() == want;
    }

    public static ItemStack templateOne(FabricSpawnerData d) {
        if (d.isItemSpawner()) {
            return SpawnerItemHelper.createItemSpawner(d.getItemSpawnerMaterial(), 1);
        }
        try {
            EntityType<?> et = EntityCatalog.parseMobType(d.getEntityTypeName());
            return SpawnerItemHelper.createSmartSpawner(et, 1);
        } catch (IllegalArgumentException e) {
            return ItemStack.EMPTY;
        }
    }

    public static int countMatchingInInventory(ServerPlayerEntity player, FabricSpawnerData d) {
        ItemStack template = templateOne(d);
        if (template.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (!s.isEmpty() && ItemStack.areItemsAndComponentsEqual(template, s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    public static int spaceForTemplate(ServerPlayerEntity player, FabricSpawnerData d) {
        ItemStack template = templateOne(d);
        if (template.isEmpty()) {
            return 0;
        }
        int space = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isEmpty()) {
                space += template.getMaxCount();
            } else if (ItemStack.areItemsAndComponentsEqual(template, s)) {
                space += template.getMaxCount() - s.getCount();
            }
        }
        return space;
    }

    public static void removeFromInventory(ServerPlayerEntity player, FabricSpawnerData d, int amount) {
        if (amount <= 0) {
            return;
        }
        ItemStack template = templateOne(d);
        if (template.isEmpty()) {
            return;
        }
        int left = amount;
        for (int i = 0; i < player.getInventory().size() && left > 0; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isEmpty() || !ItemStack.areItemsAndComponentsEqual(template, s)) {
                continue;
            }
            int take = Math.min(left, s.getCount());
            s.decrement(take);
            left -= take;
        }
    }

    public static void giveTemplateItems(ServerPlayerEntity player, FabricSpawnerData d, int amount) {
        if (amount <= 0) {
            return;
        }
        ItemStack template = templateOne(d);
        if (template.isEmpty()) {
            return;
        }
        int left = amount;
        while (left > 0) {
            int n = Math.min(left, template.getMaxCount());
            ItemStack give = template.copy();
            give.setCount(n);
            player.getInventory().offerOrDrop(give);
            left -= n;
        }
    }

    public static boolean tryIncreaseStack(ServerPlayerEntity player, FabricSpawnerData d, int requested) {
        int current = d.getStackSize();
        int max = d.getMaxStackSize();
        int spaceLeft = max - current;
        if (spaceLeft <= 0) {
            player.sendMessage(net.minecraft.text.Text.literal("Stack is already at max (" + max + ")."), false);
            return false;
        }
        int want = Math.min(requested, spaceLeft);
        int have = countMatchingInInventory(player, d);
        if (have < want) {
            player.sendMessage(net.minecraft.text.Text.literal("Not enough matching spawners in inventory."), false);
            return false;
        }
        removeFromInventory(player, d, want);
        d.setStackSize(current + want);
        d.recalculateStackDerived();
        markDirty(d);
        ding(player);
        return true;
    }

    public static boolean tryDecreaseStack(ServerPlayerEntity player, FabricSpawnerData d, int requestedRemove) {
        int current = d.getStackSize();
        if (current <= 1) {
            player.sendMessage(net.minecraft.text.Text.literal("Cannot reduce stack below 1."), false);
            return false;
        }
        int maxRemove = current - 1;
        int actual = Math.min(requestedRemove, maxRemove);
        int cap = spaceForTemplate(player, d);
        if (cap <= 0) {
            player.sendMessage(net.minecraft.text.Text.literal("Inventory full — make room for spawner items."), false);
            return false;
        }
        actual = Math.min(actual, cap);
        if (actual <= 0) {
            return false;
        }
        d.setStackSize(current - actual);
        d.recalculateStackDerived();
        giveTemplateItems(player, d, actual);
        markDirty(d);
        ding(player);
        return true;
    }

    public static boolean tryAddAllFromInventory(ServerPlayerEntity player, FabricSpawnerData d) {
        int spaceLeft = d.getMaxStackSize() - d.getStackSize();
        if (spaceLeft <= 0) {
            player.sendMessage(net.minecraft.text.Text.literal("Stack full."), false);
            return false;
        }
        int have = countMatchingInInventory(player, d);
        if (have == 0) {
            player.sendMessage(net.minecraft.text.Text.literal("No matching spawners in inventory."), false);
            return false;
        }
        return tryIncreaseStack(player, d, Math.min(spaceLeft, have));
    }

    public static boolean tryRemoveAllToInventory(ServerPlayerEntity player, FabricSpawnerData d) {
        int current = d.getStackSize();
        if (current <= 1) {
            player.sendMessage(net.minecraft.text.Text.literal("Cannot reduce stack below 1."), false);
            return false;
        }
        return tryDecreaseStack(player, d, current - 1);
    }

    /** Add from main hand up to stack cap; decrements the held stack. */
    public static boolean tryStackFromMainHand(ServerPlayerEntity player, FabricSpawnerData d) {
        ItemStack hand = player.getMainHandStack();
        if (!handStackMatches(d, hand)) {
            return false;
        }
        int current = d.getStackSize();
        int max = d.getMaxStackSize();
        int spaceLeft = max - current;
        if (spaceLeft <= 0) {
            return false;
        }
        int add = Math.min(hand.getCount(), spaceLeft);
        if (add <= 0) {
            return false;
        }
        hand.decrement(add);
        d.setStackSize(current + add);
        d.recalculateStackDerived();
        markDirty(d);
        ding(player);
        return true;
    }

    private static void markDirty(FabricSpawnerData d) {
        SmartSpawnerRuntime rt = SmartSpawnerRuntime.getOrNull();
        if (rt != null && rt.getYamlStore() != null) {
            rt.getYamlStore().markDirty(d.getSpawnerId());
        }
    }

    private static void ding(ServerPlayerEntity player) {
        player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
    }

    public static Optional<EntityType<?>> entityTypeFromData(FabricSpawnerData d) {
        if (d.isItemSpawner()) {
            return Optional.of(EntityType.ITEM);
        }
        try {
            return Optional.of(EntityCatalog.parseMobType(d.getEntityTypeName()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
