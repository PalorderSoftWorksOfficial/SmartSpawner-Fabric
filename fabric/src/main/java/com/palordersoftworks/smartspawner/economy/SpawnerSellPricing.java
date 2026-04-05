package com.palordersoftworks.smartspawner.economy;

import com.palordersoftworks.smartspawner.SmartSpawnerRuntime;
import com.palordersoftworks.smartspawner.config.ModConfig;
import com.palordersoftworks.smartspawner.data.FabricSpawnerData;
import com.palordersoftworks.smartspawner.loot.EntityLootProfile;
import com.palordersoftworks.smartspawner.loot.LootEntryDef;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

import java.util.Locale;

public final class SpawnerSellPricing {

    private SpawnerSellPricing() {
    }

    public static double unitPrice(FabricSpawnerData spawner, ItemStack template, SmartSpawnerRuntime rt) {
        ModConfig cfg = rt.getModConfig();
        if (!cfg.customEconomyEnabled()) {
            return 0.0;
        }
        String mat = Registries.ITEM.getId(template.getItem()).getPath().toUpperCase(Locale.ROOT);
        FabricItemPrices prices = rt.getItemPrices();
        double customExplicit = prices.explicitPrice(mat);
        double customResolved = customExplicit > 0 ? customExplicit : cfg.defaultSellPrice();
        double loot = lootTableUnitPrice(spawner.lootProfile(), template);
        PriceSourceMode mode = cfg.priceSourceMode();

        return switch (mode) {
            case CUSTOM_ONLY -> customResolved;
            case SHOP_ONLY -> loot;
            case CUSTOM_PRIORITY -> customExplicit > 0 ? customExplicit : (loot > 0 ? loot : cfg.defaultSellPrice());
            case SHOP_PRIORITY -> loot > 0 ? loot : (customExplicit > 0 ? customExplicit : cfg.defaultSellPrice());
        };
    }

    private static double lootTableUnitPrice(EntityLootProfile profile, ItemStack stack) {
        if (profile == null || profile.entries() == null) {
            return 0.0;
        }
        double best = 0.0;
        for (LootEntryDef e : profile.entries()) {
            if (!stack.isOf(e.item())) {
                continue;
            }
            if (e.item() == Items.TIPPED_ARROW && e.potionTypeName() != null) {
                PotionContentsComponent pc = stack.get(DataComponentTypes.POTION_CONTENTS);
                String pot = "WATER";
                if (pc != null && pc.potion().isPresent()) {
                    pot = pc.potion().get().getKey()
                            .map(k -> k.getValue().getPath().toUpperCase(Locale.ROOT))
                            .orElse("WATER");
                }
                if (!e.potionTypeName().equalsIgnoreCase(pot)) {
                    continue;
                }
            }
            if (e.minDamage() != null && e.maxDamage() != null && stack.isDamageable()) {
                int d = stack.getDamage();
                if (d < e.minDamage() || d > e.maxDamage()) {
                    continue;
                }
            }
            if (e.sellPrice() > best) {
                best = e.sellPrice();
            }
        }
        return best;
    }
}
