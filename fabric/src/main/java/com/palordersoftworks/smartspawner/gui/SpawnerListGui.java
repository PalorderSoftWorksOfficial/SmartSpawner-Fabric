package com.palordersoftworks.smartspawner.gui;

import com.palordersoftworks.smartspawner.SmartSpawnerRuntime;
import com.palordersoftworks.smartspawner.data.FabricSpawnerData;
import com.palordersoftworks.smartspawner.permission.SsPermissions;
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
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Paginated admin view of all registered spawners (Paper {@code /ss list} subset).
 */
public final class SpawnerListGui {

    private static final int PAGE_SIZE = 45;

    private SpawnerListGui() {
    }

    public static void open(ServerPlayerEntity player, int page) {
        SmartSpawnerRuntime rt = SmartSpawnerRuntime.getOrNull();
        if (rt == null) {
            return;
        }
        List<FabricSpawnerData> all = new ArrayList<>(rt.getSpawnerManager().all());
        all.sort(Comparator.comparing(FabricSpawnerData::getSpawnerId));
        int totalPages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int p = Math.min(Math.max(0, page), totalPages - 1);
        int start = p * PAGE_SIZE;
        List<FabricSpawnerData> slice = all.subList(start, Math.min(start + PAGE_SIZE, all.size()));

        FabricSpawnerData[] rowData = new FabricSpawnerData[PAGE_SIZE];

        SimpleInventory inv = new SimpleInventory(54);
        ItemStack glass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        glass.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 45; i < 54; i++) {
            inv.setStack(i, glass.copy());
        }
        inv.setStack(45, named(Items.ARROW, "Previous page"));
        inv.setStack(49, named(Items.BOOK, "Page " + (p + 1) + " / " + totalPages + " (" + all.size() + " spawners)"));
        inv.setStack(53, named(Items.ARROW, "Next page"));

        int slot = 0;
        for (FabricSpawnerData s : slice) {
            if (slot >= PAGE_SIZE) break;
            rowData[slot] = s;
            inv.setStack(slot++, rowIcon(s));
        }

        final int finalPage = p;
        final int finalTotal = totalPages;
        NamedScreenHandlerFactory factory = new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("SmartSpawner list");
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity px) {
                return new ListMenu(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inv, rowData.clone(), finalPage, finalTotal);
            }
        };
        player.openHandledScreen(factory);
    }

    private static ItemStack rowIcon(FabricSpawnerData s) {
        ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
        String line1 = s.getSpawnerId();
        String line2 = (s.isItemSpawner() ? s.getItemSpawnerMaterial() : s.getEntityTypeName()) + " @ " + formatPos(s.getPos());
        skull.set(DataComponentTypes.CUSTOM_NAME, Text.literal(line1 + " — " + line2));
        return skull;
    }

    private static String formatPos(BlockPos p) {
        return p.getX() + ", " + p.getY() + ", " + p.getZ();
    }

    private static ItemStack named(net.minecraft.item.Item item, String name) {
        ItemStack st = new ItemStack(item);
        st.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return st;
    }

    private static final class ListMenu extends GenericContainerScreenHandler {
        private final FabricSpawnerData[] rowData;
        private final int page;
        private final int totalPages;

        ListMenu(ScreenHandlerType<?> type, int syncId, PlayerInventory playerInv, Inventory data,
                 FabricSpawnerData[] rowData, int page, int totalPages) {
            super(type, syncId, playerInv, data, 6);
            this.rowData = rowData;
            this.page = page;
            this.totalPages = totalPages;
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public void onSlotClick(int slotId, int button, SlotActionType clickType, PlayerEntity player) {
            if (slotId >= 45 && slotId < 54 && player instanceof ServerPlayerEntity sp) {
                if (slotId == 45 && page > 0) {
                    sp.closeHandledScreen();
                    open(sp, page - 1);
                    return;
                }
                if (slotId == 53 && page + 1 < totalPages) {
                    sp.closeHandledScreen();
                    open(sp, page + 1);
                    return;
                }
                return;
            }
            if (slotId >= 0 && slotId < PAGE_SIZE && player instanceof ServerPlayerEntity sp) {
                FabricSpawnerData picked = rowData[slotId];
                if (picked != null) {
                    if (!SsPermissions.check(sp, SsPermissions.COMMAND_LIST, SsPermissions.DEFAULT_OP)) {
                        sp.sendMessage(Text.literal("You don't have permission."), false);
                        return;
                    }
                    sp.closeHandledScreen();
                    SpawnerManagementGui.open(sp, picked, page);
                }
                return;
            }
            super.onSlotClick(slotId, button, clickType, player);
        }
    }
}
