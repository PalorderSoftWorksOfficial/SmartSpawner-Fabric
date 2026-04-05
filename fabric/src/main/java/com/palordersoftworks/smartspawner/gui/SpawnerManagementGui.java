package com.palordersoftworks.smartspawner.gui;

import com.palordersoftworks.smartspawner.SmartSpawnerRuntime;
import com.palordersoftworks.smartspawner.data.FabricSpawnerData;
import com.palordersoftworks.smartspawner.permission.SsPermissions;
import net.minecraft.block.Blocks;
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
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.Collections;

/**
 * Paper {@code SpawnerManagementGUI} layout: teleport 10, open 12, stack 14, remove 16, back 26.
 */
public final class SpawnerManagementGui {

    private static final int TELEPORT_SLOT = 10;
    private static final int OPEN_SPAWNER_SLOT = 12;
    private static final int STACK_SLOT = 14;
    private static final int REMOVE_SLOT = 16;
    private static final int BACK_SLOT = 26;

    private SpawnerManagementGui() {
    }

    public static void open(ServerPlayerEntity player, FabricSpawnerData data, int listPage) {
        SimpleInventory inv = new SimpleInventory(27);
        ItemStack glass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        glass.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < 27; i++) {
            inv.setStack(i, glass.copy());
        }
        inv.setStack(TELEPORT_SLOT, named(Items.ENDER_PEARL, "Teleport to spawner"));
        inv.setStack(OPEN_SPAWNER_SLOT, named(Items.ENDER_EYE, "Open spawner menu"));
        inv.setStack(STACK_SLOT, named(Items.SPAWNER, "Adjust stack"));
        inv.setStack(REMOVE_SLOT, named(Items.BARRIER, "Remove spawner"));
        inv.setStack(BACK_SLOT, named(Items.RED_STAINED_GLASS_PANE, "Back to list"));

        NamedScreenHandlerFactory factory = new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("Spawner management");
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity p) {
                return new ManagementMenu(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inv, data, listPage);
            }
        };
        player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
        player.openHandledScreen(factory);
    }

    private static ItemStack named(net.minecraft.item.Item item, String name) {
        ItemStack st = new ItemStack(item);
        st.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return st;
    }

    private static final class ManagementMenu extends GenericContainerScreenHandler {
        private final FabricSpawnerData data;
        private final int listPage;

        ManagementMenu(ScreenHandlerType<?> type, int syncId, PlayerInventory playerInv, Inventory inv,
                       FabricSpawnerData data, int listPage) {
            super(type, syncId, playerInv, inv, 3);
            this.data = data;
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
            if (!SsPermissions.check(sp, SsPermissions.COMMAND_LIST, SsPermissions.DEFAULT_OP)) {
                sp.sendMessage(Text.literal("You don't have permission."), false);
                return;
            }
            SmartSpawnerRuntime rt = SmartSpawnerRuntime.getOrNull();
            FabricSpawnerData live = rt != null ? rt.getSpawnerManager().getById(data.getSpawnerId()) : null;
            if (live == null) {
                sp.sendMessage(Text.literal("Spawner no longer exists."), false);
                sp.closeHandledScreen();
                SpawnerListGui.open(sp, listPage);
                return;
            }
            switch (slotId) {
                case TELEPORT_SLOT -> {
                    sp.closeHandledScreen();
                    sp.teleport(live.getWorld(), live.getPos().getX() + 0.5, live.getPos().getY() + 1.0, live.getPos().getZ() + 0.5,
                            Collections.emptySet(), sp.getYaw(), sp.getPitch(), false);
                    sp.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                }
                case OPEN_SPAWNER_SLOT -> {
                    sp.closeHandledScreen();
                    SpawnerMenus.openMain(sp, live);
                    sp.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
                }
                case STACK_SLOT -> {
                    if (!SsPermissions.check(sp, SsPermissions.STACK, true)) {
                        sp.sendMessage(Text.literal("You don't have permission to adjust stacks."), false);
                        return;
                    }
                    sp.closeHandledScreen();
                    SpawnerStackerGui.open(sp, live, SpawnerStackerGui.OpenContext.FROM_LIST_MANAGEMENT, listPage);
                    sp.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
                }
                case REMOVE_SLOT -> {
                    sp.closeHandledScreen();
                    live.getWorld().setBlockState(live.getPos(), Blocks.AIR.getDefaultState());
                    if (rt != null) {
                        rt.getSpawnerManager().unregister(live.getSpawnerId());
                        if (rt.getYamlStore() != null) {
                            rt.getYamlStore().markDeleted(live.getSpawnerId());
                            rt.getYamlStore().flushBlocking();
                        }
                    }
                    sp.playSound(SoundEvents.ENTITY_ITEM_BREAK.value(), 1.0f, 1.0f);
                    SpawnerListGui.open(sp, listPage);
                }
                case BACK_SLOT -> {
                    sp.closeHandledScreen();
                    SpawnerListGui.open(sp, listPage);
                    sp.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
                }
                default -> super.onSlotClick(slotId, button, clickType, player);
            }
        }
    }
}
