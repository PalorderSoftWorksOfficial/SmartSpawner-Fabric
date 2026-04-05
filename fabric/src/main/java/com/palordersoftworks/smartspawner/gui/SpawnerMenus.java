package com.palordersoftworks.smartspawner.gui;

import com.palordersoftworks.smartspawner.SmartSpawnerRuntime;
import com.palordersoftworks.smartspawner.data.FabricSpawnerData;
import com.palordersoftworks.smartspawner.economy.FabricSpawnerSellService;
import com.palordersoftworks.smartspawner.permission.SsPermissions;
import com.palordersoftworks.smartspawner.integration.EconomyCraftBridge;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

public final class SpawnerMenus {

    private SpawnerMenus() {
    }

    public static void openMain(ServerPlayerEntity player, FabricSpawnerData spawner) {
        SimpleInventory chest = buildMainContainer(player, spawner);
        NamedScreenHandlerFactory provider = new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("SmartSpawner");
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
                return new MainSpawnerMenu(ScreenHandlerType.GENERIC_9X3, syncId, inv, chest, spawner);
            }
        };
        player.openHandledScreen(provider);
    }

    private static SimpleInventory buildMainContainer(ServerPlayerEntity viewer, FabricSpawnerData s) {
        SimpleInventory c = new SimpleInventory(27);
        ItemStack filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        filler.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < 27; i++) {
            c.setStack(i, filler.copy());
        }
        long sec = Math.max(0, s.millisUntilNextSpawn() / 1000L);
        c.setStack(11, named(Items.CLOCK, "Next cycle: " + sec + "s"));
        c.setStack(12, named(Items.CHEST, "Storage (click)"));
        c.setStack(13, named(Items.EXPERIENCE_BOTTLE, "Stored XP: " + s.getSpawnerExp() + " / " + s.getMaxStoredExp()));
        if (SsPermissions.check(viewer, SsPermissions.STACK, true)) {
            c.setStack(14, named(Items.ANVIL, "Adjust stack (click)"));
        }
        if (SsPermissions.check(viewer, SsPermissions.SELL_ALL, true)) {
            c.setStack(21, named(Items.EMERALD, "Sell all (opens confirm)"));
        } else {
            c.setStack(21, named(Items.GRAY_STAINED_GLASS_PANE, "Selling disabled"));
        }
        c.setStack(22, named(Items.LIME_DYE, "Claim XP (click)"));
        return c;
    }

    public static void openSellConfirm(ServerPlayerEntity player, FabricSpawnerData spawner) {
        SmartSpawnerRuntime rt = SmartSpawnerRuntime.getOrNull();
        long est = rt != null ? FabricSpawnerSellService.estimatePayoutFloor(spawner, rt) : 0L;
        String line = rt != null && rt.getModConfig().customEconomyEnabled() && EconomyCraftBridge.isAvailable()
                ? "Estimated credit: " + EconomyCraftBridge.formatMoney(est)
                : (rt != null && rt.getModConfig().customEconomyEnabled()
                ? "Install EconomyCraft to sell."
                : "Selling is disabled in config.");

        SimpleInventory chest = new SimpleInventory(27);
        ItemStack filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        filler.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < 27; i++) {
            chest.setStack(i, filler.copy());
        }
        chest.setStack(13, named(Items.PAPER, line));
        chest.setStack(11, named(Items.LIME_DYE, "Confirm sell"));
        chest.setStack(15, named(Items.RED_DYE, "Cancel"));

        NamedScreenHandlerFactory provider = new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Confirm spawner sell");
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
                return new SellConfirmMenu(ScreenHandlerType.GENERIC_9X3, syncId, inv, chest, spawner);
            }
        };
        player.openHandledScreen(provider);
    }

    private static ItemStack named(net.minecraft.item.Item item, String name) {
        ItemStack st = new ItemStack(item);
        st.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return st;
    }

    public static void openStorage(ServerPlayerEntity player, FabricSpawnerData spawner) {
        List<ItemStack> display = spawner.virtualInventory().asDisplayList();
        int size = 54;
        SimpleInventory c = new SyncingSpawnerContainer(size, spawner);
        for (int i = 0; i < size; i++) {
            if (i < display.size()) {
                c.setStack(i, display.get(i).copy());
            } else {
                c.setStack(i, ItemStack.EMPTY);
            }
        }
        NamedScreenHandlerFactory provider = new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Spawner storage");
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
                return new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, inv, c, 6);
            }
        };
        player.openHandledScreen(provider);
    }

    private static void claimXp(ServerPlayerEntity player, FabricSpawnerData s) {
        int xp = s.getSpawnerExp();
        if (xp <= 0) {
            return;
        }
        s.setSpawnerExp(0);
        player.addExperience(xp);
        var rt = SmartSpawnerRuntime.getOrNull();
        if (rt != null && rt.getYamlStore() != null) {
            rt.getYamlStore().markDirty(s.getSpawnerId());
        }
        player.closeHandledScreen();
    }

    private static final class SyncingSpawnerContainer extends SimpleInventory {
        private final FabricSpawnerData spawner;

        SyncingSpawnerContainer(int size, FabricSpawnerData spawner) {
            super(size);
            this.spawner = spawner;
        }

        @Override
        public ItemStack removeStack(int slot, int amount) {
            ItemStack cur = getStack(slot);
            if (cur.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            int take = Math.min(amount, cur.getCount());
            ItemStack out = cur.split(take);
            spawner.virtualInventory().removeAmountMatching(out, take);
            var rt = SmartSpawnerRuntime.getOrNull();
            if (rt != null && rt.getYamlStore() != null) {
                rt.getYamlStore().markDirty(spawner.getSpawnerId());
            }
            markDirty();
            return out;
        }
    }

    private static final class MainSpawnerMenu extends GenericContainerScreenHandler {
        private final FabricSpawnerData spawner;

        MainSpawnerMenu(ScreenHandlerType<?> type, int syncId, PlayerInventory playerInv, Inventory data, FabricSpawnerData spawner) {
            super(type, syncId, playerInv, data, 3);
            this.spawner = spawner;
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int index) {
            if (index < 27) {
                return ItemStack.EMPTY;
            }
            return super.quickMove(player, index);
        }

        @Override
        public void onSlotClick(int slotId, int button, SlotActionType clickType, PlayerEntity player) {
            if (slotId >= 0 && slotId < 27 && clickType != SlotActionType.QUICK_CRAFT) {
                if (slotId == 12 && player instanceof ServerPlayerEntity sp) {
                    openStorage(sp, spawner);
                    return;
                }
                if (slotId == 14 && player instanceof ServerPlayerEntity sp) {
                    if (SsPermissions.check(sp, SsPermissions.STACK, true)) {
                        SpawnerStackerGui.open(sp, spawner, SpawnerStackerGui.OpenContext.PLAYER, 0);
                    }
                    return;
                }
                if (slotId == 21 && player instanceof ServerPlayerEntity sp) {
                    if (SsPermissions.check(sp, SsPermissions.SELL_ALL, true)) {
                        openSellConfirm(sp, spawner);
                    }
                    return;
                }
                if (slotId == 22 && player instanceof ServerPlayerEntity sp) {
                    claimXp(sp, spawner);
                    return;
                }
                return;
            }
            super.onSlotClick(slotId, button, clickType, player);
        }
    }

    private static final class SellConfirmMenu extends GenericContainerScreenHandler {
        private final FabricSpawnerData spawner;

        SellConfirmMenu(ScreenHandlerType<?> type, int syncId, PlayerInventory playerInv, Inventory data, FabricSpawnerData spawner) {
            super(type, syncId, playerInv, data, 3);
            this.spawner = spawner;
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int index) {
            if (index < 27) {
                return ItemStack.EMPTY;
            }
            return super.quickMove(player, index);
        }

        @Override
        public void onSlotClick(int slotId, int button, SlotActionType clickType, PlayerEntity player) {
            if (slotId >= 0 && slotId < 27 && clickType != SlotActionType.QUICK_CRAFT) {
                if (slotId == 11 && player instanceof ServerPlayerEntity sp) {
                    if (SsPermissions.check(sp, SsPermissions.SELL_ALL, true)) {
                        FabricSpawnerSellService.sellAll(sp, spawner);
                    }
                    sp.closeHandledScreen();
                    return;
                }
                if (slotId == 15 && player instanceof ServerPlayerEntity sp) {
                    sp.closeHandledScreen();
                    return;
                }
                return;
            }
            super.onSlotClick(slotId, button, clickType, player);
        }
    }
}
