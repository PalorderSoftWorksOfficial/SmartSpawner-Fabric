package com.palordersoftworks.smartspawner.economy;

import com.palordersoftworks.smartspawner.SmartSpawnerRuntime;
import com.palordersoftworks.smartspawner.data.FabricSpawnerData;
import com.palordersoftworks.smartspawner.integration.EconomyCraftBridge;
import com.palordersoftworks.smartspawner.serialization.PaperItemFormat.ItemStackKey;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FabricSpawnerSellService {

    private FabricSpawnerSellService() {
    }

    /** Floor total payout for UI; 0 if selling disabled or empty. */
    public static long estimatePayoutFloor(FabricSpawnerData spawner, SmartSpawnerRuntime rt) {
        if (rt == null || !rt.getModConfig().customEconomyEnabled()) {
            return 0L;
        }
        double total = 0.0;
        for (var e : spawner.virtualInventory().getConsolidatedItems().entrySet()) {
            total += SpawnerSellPricing.unitPrice(spawner, e.getKey().template(), rt) * (double) e.getValue();
        }
        return (long) Math.floor(total);
    }

    public static void sellAll(ServerPlayerEntity player, FabricSpawnerData spawner) {
        SmartSpawnerRuntime rt = SmartSpawnerRuntime.getOrNull();
        if (rt == null) {
            return;
        }

        if (!spawner.startSelling()) {
            player.sendMessage(Text.literal("A sell is already in progress for this spawner."), false);
            return;
        }

        try {
            if (!rt.getModConfig().customEconomyEnabled()) {
                player.sendMessage(Text.literal("Spawner selling is disabled in config."), false);
                return;
            }
            if (!EconomyCraftBridge.isAvailable()) {
                player.sendMessage(Text.literal("Install the EconomyCraft mod to sell spawner loot."), false);
                return;
            }
            if (spawner.virtualInventory().getUsedSlots() == 0) {
                player.sendMessage(Text.literal("There is nothing to sell."), false);
                return;
            }

            Map<ItemStackKey, Long> snap = spawner.virtualInventory().getConsolidatedItems();
            double totalValue = 0.0;
            long totalItems = 0;
            List<ItemStack> toRemove = new ArrayList<>();

            for (Map.Entry<ItemStackKey, Long> e : snap.entrySet()) {
                ItemStack template = e.getKey().template();
                long amount = e.getValue();
                totalItems += amount;
                double unit = SpawnerSellPricing.unitPrice(spawner, template, rt);
                totalValue += unit * (double) amount;

                int max = template.getMaxCount();
                long remaining = amount;
                while (remaining > 0) {
                    int n = (int) Math.min(remaining, max);
                    ItemStack copy = template.copy();
                    copy.setCount(n);
                    toRemove.add(copy);
                    remaining -= n;
                }
            }

            if (totalValue <= 0.0 || toRemove.isEmpty()) {
                player.sendMessage(Text.literal("Nothing here has a sell price."), false);
                return;
            }

            long payout = (long) Math.floor(totalValue);
            if (payout < 1L) {
                player.sendMessage(Text.literal("The total value is too small to credit."), false);
                return;
            }

            MinecraftServer server = rt.getServer();
            if (server == null) {
                return;
            }

            try {
                if (EconomyCraftBridge.tryRecordDailySell(server, player.getUuid(), payout)) {
                    long rem = EconomyCraftBridge.getDailySellRemaining(server, player.getUuid());
                    player.sendMessage(Text.literal(
                            "Daily sell limit reached. You can sell up to " + EconomyCraftBridge.formatMoney(rem) + " more today."), false);
                    return;
                }
                EconomyCraftBridge.addMoney(server, player.getUuid(), payout);
            } catch (ReflectiveOperationException ex) {
                player.sendMessage(Text.literal("Could not credit your balance (economy error)."), false);
                return;
            }

            spawner.getInventoryLock().lock();
            try {
                for (ItemStack stack : toRemove) {
                    spawner.virtualInventory().removeExact(stack);
                }
                spawner.updateCapacityFlag();
            } finally {
                spawner.getInventoryLock().unlock();
            }

            if (rt.getYamlStore() != null) {
                rt.getYamlStore().markDirty(spawner.getSpawnerId());
            }

            player.sendMessage(Text.literal(
                    "Sold " + totalItems + " items for " + EconomyCraftBridge.formatMoney(payout) + "."), false);
        } finally {
            spawner.stopSelling();
        }
    }
}
