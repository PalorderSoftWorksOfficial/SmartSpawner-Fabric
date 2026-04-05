package com.palordersoftworks.smartspawner.data;

import com.palordersoftworks.smartspawner.serialization.PaperItemFormat.ItemStackKey;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VirtualInventoryMc {

    private final Map<ItemStackKey, Long> consolidated = new ConcurrentHashMap<>();
    private volatile int maxSlots;

    public VirtualInventoryMc(int maxSlots) {
        this.maxSlots = maxSlots;
    }

    public int getMaxSlots() {
        return maxSlots;
    }

    public void resize(int newMaxSlots) {
        this.maxSlots = newMaxSlots;
    }

    public void addItems(List<ItemStack> items) {
        if (items == null || items.isEmpty()) return;
        Map<ItemStackKey, Long> batch = new HashMap<>();
        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) continue;
            ItemStackKey key = new ItemStackKey(item);
            batch.merge(key, (long) item.getCount(), Long::sum);
        }
        for (Map.Entry<ItemStackKey, Long> e : batch.entrySet()) {
            consolidated.merge(e.getKey(), e.getValue(), Long::sum);
        }
    }

    public boolean removeExact(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        ItemStackKey key = new ItemStackKey(stack);
        long have = consolidated.getOrDefault(key, 0L);
        if (have < stack.getCount()) return false;
        long left = have - stack.getCount();
        if (left <= 0) consolidated.remove(key);
        else consolidated.put(key, left);
        return true;
    }

    /** Removes up to {@code amount} items matching template (same item + components). */
    public void removeAmountMatching(ItemStack template, int amount) {
        if (template == null || template.isEmpty() || amount <= 0) return;
        ItemStackKey key = new ItemStackKey(template);
        long have = consolidated.getOrDefault(key, 0L);
        long take = Math.min(have, amount);
        if (take <= 0) return;
        long left = have - take;
        if (left <= 0) consolidated.remove(key);
        else consolidated.put(key, left);
    }

    public Map<ItemStackKey, Long> getConsolidatedItems() {
        return new HashMap<>(consolidated);
    }

    public int getUsedSlots() {
        if (consolidated.isEmpty()) return 0;
        int slots = 0;
        for (Map.Entry<ItemStackKey, Long> e : consolidated.entrySet()) {
            ItemStack t = e.getKey().template();
            int max = t.getMaxCount();
            long amt = e.getValue();
            slots += (int) Math.ceil((double) amt / (double) max);
            if (slots >= maxSlots) return maxSlots;
        }
        return Math.min(slots, maxSlots);
    }

    public List<ItemStack> asDisplayList() {
        List<ItemStack> out = new ArrayList<>();
        List<Map.Entry<ItemStackKey, Long>> entries = new ArrayList<>(consolidated.entrySet());
        entries.sort((a, b) -> Registries.ITEM.getId(a.getKey().template().getItem()).getPath()
                .compareTo(Registries.ITEM.getId(b.getKey().template().getItem()).getPath()));
        int slot = 0;
        for (Map.Entry<ItemStackKey, Long> e : entries) {
            if (slot >= maxSlots) break;
            ItemStack t = e.getKey().template();
            long total = e.getValue();
            int max = t.getMaxCount();
            while (total > 0 && slot < maxSlots) {
                int n = (int) Math.min(total, max);
                ItemStack copy = t.copy();
                copy.setCount(n);
                out.add(copy);
                total -= n;
                slot++;
            }
        }
        return out;
    }

    public void loadFromSerializedLines(List<String> lines) {
        consolidated.clear();
        addItems(com.palordersoftworks.smartspawner.serialization.PaperItemFormat.deserialize(lines));
    }

}
