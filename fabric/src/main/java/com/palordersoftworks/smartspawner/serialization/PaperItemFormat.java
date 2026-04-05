package com.palordersoftworks.smartspawner.serialization;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads/writes the same inventory string format as the Paper plugin's {@code ItemStackSerializer}.
 */
public final class PaperItemFormat {

    private PaperItemFormat() {
    }

    public static List<String> serialize(Map<ItemStackKey, Long> items) {
        Map<String, ItemGroup> groups = new HashMap<>();
        for (Map.Entry<ItemStackKey, Long> e : items.entrySet()) {
            ItemStack t = e.getKey().template();
            String mat = Registries.ITEM.getId(t.getItem()).getPath().toUpperCase(Locale.ROOT);
            ItemGroup g = groups.computeIfAbsent(mat, ItemGroup::new);
            if (t.getItem() == Items.TIPPED_ARROW) {
                PotionContentsComponent pc = t.get(DataComponentTypes.POTION_CONTENTS);
                String pot = "WATER";
                if (pc != null && pc.potion().isPresent()) {
                    var unwrap = pc.potion().get().getKey();
                    if (unwrap.isPresent()) {
                        pot = unwrap.get().getValue().getPath().toUpperCase(Locale.ROOT);
                    }
                }
                g.addPotion(pot, e.getValue().intValue());
            } else if (isDestructible(mat)) {
                g.addDamage(t.getDamage(), e.getValue().intValue());
            } else {
                g.addDamage(0, e.getValue().intValue());
            }
        }
        List<String> out = new ArrayList<>();
        for (ItemGroup g : groups.values()) {
            if (!g.potionCounts.isEmpty()) {
                StringBuilder sb = new StringBuilder("TIPPED_ARROW#");
                boolean first = true;
                for (Map.Entry<String, Integer> pe : g.potionCounts.entrySet()) {
                    if (!first) sb.append(',');
                    sb.append(pe.getKey()).append(':').append(pe.getValue());
                    first = false;
                }
                out.add(sb.toString());
            } else if (!g.damageCounts.isEmpty() && isDestructible(g.materialPath)) {
                StringBuilder sb = new StringBuilder(g.materialPath).append(';');
                boolean first = true;
                for (Map.Entry<Integer, Integer> de : g.damageCounts.entrySet()) {
                    if (!first) sb.append(',');
                    sb.append(de.getKey()).append(':').append(de.getValue());
                    first = false;
                }
                out.add(sb.toString());
            } else {
                int sum = g.damageCounts.values().stream().mapToInt(Integer::intValue).sum();
                out.add(g.materialPath + ":" + sum);
            }
        }
        return out;
    }

    public static List<ItemStack> deserialize(List<String> lines) {
        List<ItemStack> stacks = new ArrayList<>();
        if (lines == null) return stacks;
        for (String entry : lines) {
            if (entry.startsWith("TIPPED_ARROW#")) {
                String[] potionEntries = entry.substring("TIPPED_ARROW#".length()).split(",");
                for (String potionEntry : potionEntries) {
                    String[] parts = potionEntry.split(":");
                    if (parts.length < 2) continue;
                    String potionTypeName = parts[0];
                    int count = Integer.parseInt(parts[1]);
                    ItemStack arrow = new ItemStack(Items.TIPPED_ARROW, count);
                    Identifier rl = Identifier.of("minecraft", potionTypeName.toLowerCase(Locale.ROOT));
                    Registries.POTION.getEntry(rl).ifPresent(holder ->
                            arrow.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(holder)));
                    stacks.add(arrow);
                }
            } else if (entry.contains(";")) {
                String[] parts = entry.split(";", 2);
                Item item = Registries.ITEM.get(Identifier.of("minecraft", parts[0].toLowerCase(Locale.ROOT)));
                for (String damageCount : parts[1].split(",")) {
                    String[] dc = damageCount.split(":");
                    int damage = Integer.parseInt(dc[0]);
                    int count = Integer.parseInt(dc[1]);
                    ItemStack st = new ItemStack(item, count);
                    if (st.isDamageable()) {
                        st.setDamage(damage);
                    }
                    stacks.add(st);
                }
            } else {
                String[] parts = entry.split(":");
                Item item = Registries.ITEM.get(Identifier.of("minecraft", parts[0].toLowerCase(Locale.ROOT)));
                int count = Integer.parseInt(parts[1]);
                stacks.add(new ItemStack(item, count));
            }
        }
        return stacks;
    }

    private static boolean isDestructible(String bukkitMaterialName) {
        String n = bukkitMaterialName.toUpperCase(Locale.ROOT);
        if (n.equals("BOW") || n.equals("FISHING_ROD") || n.equals("FLINT_AND_STEEL") || n.equals("SHEARS")
                || n.equals("SHIELD") || n.equals("ELYTRA") || n.equals("TRIDENT") || n.equals("CROSSBOW")
                || n.equals("CARROT_ON_A_STICK") || n.equals("WARPED_FUNGUS_ON_A_STICK") || n.equals("MACE")) {
            return true;
        }
        List<String> armorMats = List.of("LEATHER", "CHAINMAIL", "IRON", "GOLDEN", "DIAMOND", "NETHERITE");
        List<String> pieces = List.of("_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS");
        for (String am : armorMats) {
            for (String p : pieces) {
                if (n.equals(am + p)) return true;
            }
        }
        List<String> tools = List.of("_SWORD", "_PICKAXE", "_AXE", "_SHOVEL", "_HOE");
        for (String t : tools) {
            if (n.endsWith(t)) return true;
        }
        return false;
    }

    private static final class ItemGroup {
        final String materialPath;
        final Map<Integer, Integer> damageCounts = new HashMap<>();
        final Map<String, Integer> potionCounts = new HashMap<>();

        ItemGroup(String materialPath) {
            this.materialPath = materialPath;
        }

        void addDamage(int damage, int count) {
            damageCounts.merge(damage, count, Integer::sum);
        }

        void addPotion(String potionKey, int count) {
            potionCounts.merge(potionKey, count, Integer::sum);
        }
    }

    public record ItemStackKey(ItemStack template) {
        public ItemStackKey {
            template = template.copy();
            template.setCount(1);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemStackKey that)) return false;
            return ItemStack.areItemsAndComponentsEqual(template, that.template);
        }

        @Override
        public int hashCode() {
            return ItemStack.hashCode(template);
        }
    }
}
