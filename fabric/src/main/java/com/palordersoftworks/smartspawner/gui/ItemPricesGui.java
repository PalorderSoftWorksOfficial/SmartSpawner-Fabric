package com.palordersoftworks.smartspawner.gui;

import com.palordersoftworks.smartspawner.SmartSpawnerRuntime;
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
import java.util.Map;

/**
 * Paginated view of {@code item_prices.yml} (Paper {@code /ss prices} subset).
 */
public final class ItemPricesGui {

    private static final int PAGE_SIZE = 45;

    private ItemPricesGui() {
    }

    public static void open(ServerPlayerEntity player, int page) {
        SmartSpawnerRuntime rt = SmartSpawnerRuntime.getOrNull();
        if (rt == null) {
            return;
        }
        List<Map.Entry<String, Double>> entries = rt.getItemPrices().sortedEntries();
        int totalPages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int p = Math.min(Math.max(0, page), totalPages - 1);
        int start = p * PAGE_SIZE;
        List<Map.Entry<String, Double>> slice = entries.subList(start, Math.min(start + PAGE_SIZE, entries.size()));

        SimpleInventory inv = new SimpleInventory(54);
        ItemStack glass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        glass.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 45; i < 54; i++) {
            inv.setStack(i, glass.copy());
        }
        inv.setStack(45, named(Items.ARROW, "Previous page"));
        inv.setStack(49, named(Items.EMERALD, "Prices " + (p + 1) + "/" + totalPages + " (" + entries.size() + " items)"));
        inv.setStack(53, named(Items.ARROW, "Next page"));

        int slot = 0;
        for (Map.Entry<String, Double> e : slice) {
            if (slot >= PAGE_SIZE) break;
            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponentTypes.CUSTOM_NAME, Text.literal(e.getKey() + ": " + e.getValue()));
            inv.setStack(slot++, paper);
        }

        final int finalPage = p;
        final int finalTotal = totalPages;
        NamedScreenHandlerFactory factory = new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal("SmartSpawner prices");
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity px) {
                return new PricesMenu(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inv, finalPage, finalTotal);
            }
        };
        player.openHandledScreen(factory);
    }

    private static ItemStack named(net.minecraft.item.Item item, String name) {
        ItemStack st = new ItemStack(item);
        st.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return st;
    }

    private static final class PricesMenu extends GenericContainerScreenHandler {
        private final int page;
        private final int totalPages;

        PricesMenu(ScreenHandlerType<?> type, int syncId, PlayerInventory playerInv, Inventory data, int page, int totalPages) {
            super(type, syncId, playerInv, data, 6);
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
            if (slotId >= 0 && slotId < PAGE_SIZE) {
                return;
            }
            super.onSlotClick(slotId, button, clickType, player);
        }
    }
}
