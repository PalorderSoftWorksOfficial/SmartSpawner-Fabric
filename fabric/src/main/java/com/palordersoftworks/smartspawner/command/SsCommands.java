package com.palordersoftworks.smartspawner.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.palordersoftworks.smartspawner.SmartSpawnerFabric;
import com.palordersoftworks.smartspawner.SmartSpawnerRuntime;
import com.palordersoftworks.smartspawner.data.FabricSpawnerData;
import com.palordersoftworks.smartspawner.gui.ItemPricesGui;
import com.palordersoftworks.smartspawner.gui.SpawnerListGui;
import com.palordersoftworks.smartspawner.item.SpawnerItemHelper;
import com.palordersoftworks.smartspawner.near.NearHighlightManager;
import com.palordersoftworks.smartspawner.permission.SsPermissions;
import com.palordersoftworks.smartspawner.util.EntityCatalog;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SsCommands {

    private static final int GIVE_MAX = 6400;

    private SsCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess ctx) {
        dispatcher.register(buildRoot("smartspawner"));
        dispatcher.register(buildRoot("spawner"));
        dispatcher.register(buildRoot("ss"));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildRoot(String name) {
        return CommandManager.literal(name)
                .requires(src -> SsPermissions.check(src, SsPermissions.COMMAND_USE, SsPermissions.DEFAULT_OP))
                .then(CommandManager.literal("reload")
                        .requires(src -> SsPermissions.check(src, SsPermissions.COMMAND_RELOAD, SsPermissions.DEFAULT_OP))
                        .executes(SsCommands::reload))
                .then(CommandManager.literal("save")
                        .requires(src -> SsPermissions.check(src, SsPermissions.COMMAND_RELOAD, SsPermissions.DEFAULT_OP))
                        .executes(SsCommands::save))
                .then(buildGive())
                .then(CommandManager.literal("list")
                        .requires(src -> SsPermissions.check(src, SsPermissions.COMMAND_LIST, SsPermissions.DEFAULT_OP))
                        .executes(SsCommands::list))
                .then(CommandManager.literal("prices")
                        .requires(src -> SsPermissions.check(src, SsPermissions.COMMAND_PRICES, true))
                        .executes(SsCommands::prices))
                .then(buildHologram())
                .then(buildClear())
                .then(buildNear());
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildGive() {
        return CommandManager.literal("give")
                .requires(src -> SsPermissions.check(src, SsPermissions.COMMAND_GIVE, SsPermissions.DEFAULT_OP))
                .then(CommandManager.literal("spawner")
                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                .then(CommandManager.argument("mobType", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            String rem = b.getRemaining().toLowerCase(Locale.ROOT);
                                            for (String m : EntityCatalog.spawnerMobNamesSorted()) {
                                                if (m.toLowerCase(Locale.ROOT).startsWith(rem)) {
                                                    b.suggest(m.toLowerCase(Locale.ROOT));
                                                }
                                            }
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> giveSmart(ctx, 1))
                                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, GIVE_MAX))
                                                .executes(ctx -> giveSmart(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))))
                .then(CommandManager.literal("vanilla_spawner")
                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                .then(CommandManager.argument("mobType", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            String rem = b.getRemaining().toLowerCase(Locale.ROOT);
                                            for (String m : EntityCatalog.spawnerMobNamesSorted()) {
                                                if (m.toLowerCase(Locale.ROOT).startsWith(rem)) {
                                                    b.suggest(m.toLowerCase(Locale.ROOT));
                                                }
                                            }
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> giveVanilla(ctx, 1))
                                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, GIVE_MAX))
                                                .executes(ctx -> giveVanilla(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))))
                .then(CommandManager.literal("item_spawner")
                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                .then(CommandManager.argument("itemType", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            SmartSpawnerRuntime rt = SmartSpawnerRuntime.getOrNull();
                                            if (rt == null) return b.buildFuture();
                                            String rem = b.getRemaining().toLowerCase(Locale.ROOT);
                                            for (String m : rt.getLootRegistry().itemSpawnerMaterialKeysSorted()) {
                                                if (m.toLowerCase(Locale.ROOT).startsWith(rem)) {
                                                    b.suggest(m.toLowerCase(Locale.ROOT));
                                                }
                                            }
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> giveItemSpawner(ctx, 1))
                                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, GIVE_MAX))
                                                .executes(ctx -> giveItemSpawner(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildHologram() {
        return CommandManager.literal("hologram")
                .requires(src -> SsPermissions.check(src, SsPermissions.COMMAND_HOLOGRAM, SsPermissions.DEFAULT_OP))
                .executes(SsCommands::hologramToggle)
                .then(CommandManager.literal("clear")
                        .executes(SsCommands::hologramClear));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildClear() {
        return CommandManager.literal("clear")
                .requires(src -> SsPermissions.check(src, SsPermissions.COMMAND_CLEAR, SsPermissions.DEFAULT_OP))
                .then(CommandManager.literal("holograms")
                        .executes(SsCommands::hologramClear))
                .then(CommandManager.literal("ghosts")
                        .executes(SsCommands::clearGhosts));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildNear() {
        return CommandManager.literal("near")
                .requires(src -> SsPermissions.check(src, SsPermissions.COMMAND_NEAR, SsPermissions.DEFAULT_OP))
                .executes(ctx -> nearScan(ctx, NearHighlightManager.DEFAULT_RADIUS))
                .then(CommandManager.argument("radius", IntegerArgumentType.integer(1, NearHighlightManager.MAX_RADIUS))
                        .executes(ctx -> nearScan(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))
                .then(CommandManager.literal("cancel")
                        .executes(SsCommands::nearCancel))
                .then(CommandManager.literal("gui")
                        .executes(SsCommands::nearGui));
    }

    private static int reload(CommandContext<ServerCommandSource> ctx) {
        try {
            SmartSpawnerRuntime.get().reloadFromDisk();
            ctx.getSource().sendFeedback(() -> Text.literal("SmartSpawner reloaded configs and loot tables."), true);
        } catch (Exception e) {
            SmartSpawnerFabric.LOGGER.error("Reload failed", e);
            ctx.getSource().sendError(Text.literal("Reload failed: " + e.getMessage()));
        }
        return 1;
    }

    private static int save(CommandContext<ServerCommandSource> ctx) {
        SmartSpawnerRuntime rt = SmartSpawnerRuntime.get();
        if (rt.getYamlStore() != null) {
            rt.getYamlStore().flushBlocking();
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Spawner data saved."), true);
        return 1;
    }

    private static int giveSmart(CommandContext<ServerCommandSource> ctx, int amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
        String mobWord = StringArgumentType.getString(ctx, "mobType");
        EntityType<?> type;
        try {
            type = EntityCatalog.parseMobType(mobWord);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendError(Text.literal("Unknown mob type: " + mobWord));
            return 0;
        }
        ItemStack stack = SpawnerItemHelper.createSmartSpawner(type, amount);
        giveStack(target, stack);
        feedback(ctx, "Gave smart spawner (" + mobWord + ") x" + amount + " to " + target.getName().getString());
        return 1;
    }

    private static int giveVanilla(CommandContext<ServerCommandSource> ctx, int amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
        String mobWord = StringArgumentType.getString(ctx, "mobType");
        EntityType<?> type;
        try {
            type = EntityCatalog.parseMobType(mobWord);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendError(Text.literal("Unknown mob type: " + mobWord));
            return 0;
        }
        ItemStack stack = SpawnerItemHelper.createVanillaSpawner(type, amount);
        giveStack(target, stack);
        feedback(ctx, "Gave vanilla spawner (" + mobWord + ") x" + amount + " to " + target.getName().getString());
        return 1;
    }

    private static int giveItemSpawner(CommandContext<ServerCommandSource> ctx, int amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
        String mat = StringArgumentType.getString(ctx, "itemType").toUpperCase(Locale.ROOT);
        SmartSpawnerRuntime rt = SmartSpawnerRuntime.get();
        if (!rt.getLootRegistry().hasItemSpawnerMaterial(mat)) {
            ctx.getSource().sendError(Text.literal("Invalid item spawner material: " + mat));
            return 0;
        }
        ItemStack stack = SpawnerItemHelper.createItemSpawner(mat, amount);
        giveStack(target, stack);
        feedback(ctx, "Gave item spawner (" + mat + ") x" + amount + " to " + target.getName().getString());
        return 1;
    }

    private static void giveStack(ServerPlayerEntity target, ItemStack stack) {
        target.getInventory().offerOrDrop(stack);
    }

    private static int list(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        PlayerEntity p = src.getPlayer();
        if (!(p instanceof ServerPlayerEntity sp)) {
            src.sendError(Text.literal("Players only."));
            return 0;
        }
        SpawnerListGui.open(sp, 0);
        return 1;
    }

    private static int prices(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        PlayerEntity p = src.getPlayer();
        if (!(p instanceof ServerPlayerEntity sp)) {
            src.sendError(Text.literal("Players only."));
            return 0;
        }
        ItemPricesGui.open(sp, 0);
        return 1;
    }

    private static int hologramToggle(CommandContext<ServerCommandSource> ctx) {
        SmartSpawnerRuntime rt = SmartSpawnerRuntime.get();
        rt.setHologramsEnabled(!rt.isHologramsEnabled());
        rt.getSpawnerHologramManager().refreshAll();
        boolean on = rt.isHologramsEnabled();
        ctx.getSource().sendFeedback(() -> Text.literal(on ? "Spawner holograms enabled." : "Spawner holograms disabled."), true);
        return 1;
    }

    private static int hologramClear(CommandContext<ServerCommandSource> ctx) {
        SmartSpawnerRuntime.get().getSpawnerHologramManager().removeAll();
        ctx.getSource().sendFeedback(() -> Text.literal("Cleared spawner holograms."), true);
        return 1;
    }

    private static int clearGhosts(CommandContext<ServerCommandSource> ctx) {
        SmartSpawnerRuntime rt = SmartSpawnerRuntime.get();
        List<FabricSpawnerData> snap = new ArrayList<>(rt.getSpawnerManager().all());
        int removed = 0;
        for (FabricSpawnerData s : snap) {
            if (!s.getWorld().getBlockState(s.getPos()).isOf(Blocks.SPAWNER)) {
                rt.getSpawnerManager().unregister(s.getSpawnerId());
                if (rt.getYamlStore() != null) {
                    rt.getYamlStore().markDeleted(s.getSpawnerId());
                }
                removed++;
            }
        }
        if (rt.getYamlStore() != null) {
            rt.getYamlStore().flushBlocking();
        }
        int f = removed;
        ctx.getSource().sendFeedback(() -> Text.literal("Removed " + f + " ghost spawner record(s)."), true);
        return 1;
    }

    private static int nearScan(CommandContext<ServerCommandSource> ctx, int radius) {
        ServerCommandSource src = ctx.getSource();
        PlayerEntity p = src.getPlayer();
        if (!(p instanceof ServerPlayerEntity sp)) {
            src.sendError(Text.literal("Players only."));
            return 0;
        }
        SmartSpawnerRuntime.get().getNearHighlightManager().startScan(sp, radius);
        return 1;
    }

    private static int nearCancel(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        PlayerEntity p = src.getPlayer();
        if (!(p instanceof ServerPlayerEntity sp)) {
            src.sendError(Text.literal("Players only."));
            return 0;
        }
        SmartSpawnerRuntime.get().getNearHighlightManager().cancel(sp.getUuid());
        src.sendFeedback(() -> Text.literal("Cancelled spawner highlight."), false);
        return 1;
    }

    private static int nearGui(CommandContext<ServerCommandSource> ctx) {
        return list(ctx);
    }

    private static void feedback(CommandContext<ServerCommandSource> ctx, String msg) {
        ctx.getSource().sendFeedback(() -> Text.literal(msg), true);
    }
}
