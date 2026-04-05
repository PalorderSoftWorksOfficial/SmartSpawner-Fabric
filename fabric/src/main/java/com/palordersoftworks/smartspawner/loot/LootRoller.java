package com.palordersoftworks.smartspawner.loot;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class LootRoller {

    private LootRoller() {
    }

    public record LootBatch(List<ItemStack> items, int experience) {
    }

    public static LootBatch roll(EntityLootProfile profile, int minMobs, int maxMobs, Random random) {
        if (profile == null) {
            return new LootBatch(List.of(), 0);
        }
        int rolls = minMobs;
        if (maxMobs > minMobs) {
            rolls = random.nextInt(maxMobs - minMobs + 1) + minMobs;
        }
        List<ItemStack> out = new ArrayList<>();
        int exp = 0;
        for (int i = 0; i < rolls; i++) {
            exp += profile.experience();
            for (LootEntryDef def : profile.entries()) {
                if (!def.roll(random)) continue;
                int amt = def.rollAmount(random);
                if (amt <= 0) continue;
                ItemStack stack = def.createStack(random, amt);
                if (!stack.isEmpty()) {
                    out.add(stack);
                }
            }
        }
        return new LootBatch(out, exp);
    }
}
