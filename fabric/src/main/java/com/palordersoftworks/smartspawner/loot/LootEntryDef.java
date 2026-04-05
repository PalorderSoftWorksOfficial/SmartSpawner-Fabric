package com.palordersoftworks.smartspawner.loot;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.Random;

public record LootEntryDef(
        Item item,
        int minAmount,
        int maxAmount,
        double chancePercent,
        Integer minDamage,
        Integer maxDamage,
        String potionTypeName,
        double sellPrice
) {

    public boolean roll(Random random) {
        return random.nextDouble(100.0) < chancePercent;
    }

    public int rollAmount(Random random) {
        if (maxAmount <= minAmount) return minAmount;
        return random.nextInt(maxAmount - minAmount + 1) + minAmount;
    }

    public ItemStack createStack(Random random, int amount) {
        ItemStack stack = new ItemStack(item, amount);
        if (minDamage != null && maxDamage != null && stack.isDamageable()) {
            int dmg = random.nextInt(maxDamage - minDamage + 1) + minDamage;
            stack.setDamage(dmg);
        }
        if (item == Items.TIPPED_ARROW && potionTypeName != null) {
            Identifier potionRl = Identifier.of("minecraft", potionTypeName.toLowerCase(Locale.ROOT));
            Registries.POTION.getEntry(potionRl).ifPresent(holder ->
                    stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(holder)));
        }
        return stack;
    }
}
