package com.palordersoftworks.smartspawner.gui;

import com.palordersoftworks.smartspawner.data.FabricSpawnerData;
import com.palordersoftworks.smartspawner.stack.SpawnerStackerOperations;
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

/**
 * Paper {@code SpawnerStackerUI} / {@code AdminStackerUI} slot layout (27 slots).
 */
public final class SpawnerStackerGui {

    private static final int[] DECREASE_SLOTS = {9, 10, 11};
    private static final int[] INCREASE_SLOTS = {17, 16, 15};
    private static final int SPAWNER_INFO_SLOT = 13;
    private static final int[] STACK_AMOUNTS = {64, 10, 1};
    private static final int REMOVE_ALL_SLOT = 22;
    private static final int ADD_ALL_SLOT = 4;
    private static final int BACK_FROM_MANAGEMENT_SLOT = 22;

    public enum OpenContext {
        /** In-world / main menu: add-all slot 4, remove-all slot 22. */
        PLAYER,
        /** From /ss list management: back at slot 22. */
        FROM_LIST_MANAGEMENT
    }

    private SpawnerStackerGui() {
    }

    public static void open(ServerPlayerEntity player, FabricSpawnerData spawner, OpenContext ctx, int listPage) {
        SimpleInventory inv = buildInventory(spawner, ctx);
        NamedScreenHandlerFactory factory = new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Spawner stacker");
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity p) {
                return new StackerMenu(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inv, spawner, ctx, listPage);
            }
        };
        player.openHandledScreen(factory);
    }

    private static SimpleInventory buildInventory(FabricSpawnerData spawner, OpenContext ctx) {
        SimpleInventory inv = new SimpleInventory(27);
        ItemStack glass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        glass.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < 27; i++) {
            inv.setStack(i, glass.copy());
        }
        for (int i = 0; i < STACK_AMOUNTS.length; i++) {
            inv.setStack(DECREASE_SLOTS[i], amountButton(Items.RED_STAINED_GLASS_PANE, "Remove " + STACK_AMOUNTS[i], STACK_AMOUNTS[i]));
            inv.setStack(INCREASE_SLOTS[i], amountButton(Items.LIME_STAINED_GLASS_PANE, "Add " + STACK_AMOUNTS[i], STACK_AMOUNTS[i]));
        }
        inv.setStack(SPAWNER_INFO_SLOT, spawnerInfo(spawner));
        if (ctx == OpenContext.PLAYER) {
            inv.setStack(ADD_ALL_SLOT, named(Items.LIME_STAINED_GLASS_PANE, "Add all from inventory"));
            inv.setStack(REMOVE_ALL_SLOT, named(Items.RED_STAINED_GLASS_PANE, "Remove all to inventory"));
        } else {
            inv.setStack(BACK_FROM_MANAGEMENT_SLOT, named(Items.RED_STAINED_GLASS_PANE, "Back"));
        }
        return inv;
    }

    private static ItemStack amountButton(net.minecraft.item.Item item, String name, int amount) {
        ItemStack st = new ItemStack(item, Math.min(64, Math.max(1, amount)));
        st.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return st;
    }

    private static ItemStack named(net.minecraft.item.Item item, String name) {
        ItemStack st = new ItemStack(item);
        st.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return st;
    }

    private static ItemStack spawnerInfo(FabricSpawnerData s) {
        ItemStack st = new ItemStack(Items.SPAWNER);
        String t = s.isItemSpawner() ? s.getItemSpawnerMaterial() : s.getEntityTypeName();
        st.set(DataComponentTypes.CUSTOM_NAME, Text.literal(t + " — stack " + s.getStackSize() + " / " + s.getMaxStackSize()));
        return st;
    }

    private static void refresh(ServerPlayerEntity player, FabricSpawnerData spawner, OpenContext ctx, int listPage) {
        player.closeHandledScreen();
        open(player, spawner, ctx, listPage);
    }

    private static final class StackerMenu extends GenericContainerScreenHandler {
        private final FabricSpawnerData spawner;
        private final OpenContext ctx;
        private final int listPage;

        StackerMenu(ScreenHandlerType<?> type, int syncId, PlayerInventory playerInv, Inventory data,
                    FabricSpawnerData spawner, OpenContext ctx, int listPage) {
            super(type, syncId, playerInv, data, 3);
            this.spawner = spawner;
            this.ctx = ctx;
            this.listPage = listPage;
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
            if (!(player instanceof ServerPlayerEntity sp) || slotId < 0 || slotId >= 27 || clickType == SlotActionType.QUICK_CRAFT) {
                super.onSlotClick(slotId, button, clickType, player);
                return;
            }
            if (slotId == SPAWNER_INFO_SLOT) {
                return;
            }
            if (ctx == OpenContext.FROM_LIST_MANAGEMENT && slotId == BACK_FROM_MANAGEMENT_SLOT) {
                sp.closeHandledScreen();
                SpawnerManagementGui.open(sp, spawner, listPage);
                return;
            }
            for (int i = 0; i < DECREASE_SLOTS.length; i++) {
                if (slotId == DECREASE_SLOTS[i]) {
                    SpawnerStackerOperations.tryDecreaseStack(sp, spawner, STACK_AMOUNTS[i]);
                    refresh(sp, spawner, ctx, listPage);
                    return;
                }
            }
            for (int i = 0; i < INCREASE_SLOTS.length; i++) {
                if (slotId == INCREASE_SLOTS[i]) {
                    SpawnerStackerOperations.tryIncreaseStack(sp, spawner, STACK_AMOUNTS[i]);
                    refresh(sp, spawner, ctx, listPage);
                    return;
                }
            }
            if (ctx == OpenContext.PLAYER) {
                if (slotId == ADD_ALL_SLOT) {
                    SpawnerStackerOperations.tryAddAllFromInventory(sp, spawner);
                    refresh(sp, spawner, ctx, listPage);
                    return;
                }
                if (slotId == REMOVE_ALL_SLOT) {
                    SpawnerStackerOperations.tryRemoveAllToInventory(sp, spawner);
                    refresh(sp, spawner, ctx, listPage);
                    return;
                }
            }
            super.onSlotClick(slotId, button, clickType, player);
        }
    }
}
