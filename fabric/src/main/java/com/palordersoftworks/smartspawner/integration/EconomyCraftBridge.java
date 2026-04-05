package com.palordersoftworks.smartspawner.integration;

import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Optional integration with {@code com.palordersoftworks.economycraft.EconomyCraft} via reflection
 * so SmartSpawner does not require EconomyCraft at compile time.
 */
public final class EconomyCraftBridge {

    private static final String ECONOMY_CLASS = "com.palordersoftworks.economycraft.EconomyCraft";

    private static volatile boolean probed;
    private static volatile boolean available;
    private static volatile Class<?> economyClass;
    private static volatile Method getManager;
    private static volatile Method formatMoneyStatic;
    private static volatile Method methodAddMoney;
    private static volatile Method methodTryRecordDailySell;
    private static volatile Method methodGetDailySellRemaining;

    private EconomyCraftBridge() {
    }

    private static void probe() {
        if (probed) {
            return;
        }
        synchronized (EconomyCraftBridge.class) {
            if (probed) {
                return;
            }
            try {
                economyClass = Class.forName(ECONOMY_CLASS);
                getManager = economyClass.getMethod("getManager", MinecraftServer.class);
                formatMoneyStatic = economyClass.getMethod("formatMoney", long.class);
                Class<?> mgr = getManager.getReturnType();
                methodAddMoney = mgr.getMethod("addMoney", UUID.class, long.class);
                methodTryRecordDailySell = mgr.getMethod("tryRecordDailySell", UUID.class, long.class);
                methodGetDailySellRemaining = mgr.getMethod("getDailySellRemaining", UUID.class);
                available = true;
            } catch (Throwable ignored) {
                available = false;
            }
            probed = true;
        }
    }

    public static boolean isAvailable() {
        probe();
        return available;
    }

    public static String formatMoney(long amount) {
        probe();
        if (!available) {
            return Long.toString(amount);
        }
        try {
            Object r = formatMoneyStatic.invoke(null, amount);
            return r != null ? r.toString() : Long.toString(amount);
        } catch (Throwable e) {
            return Long.toString(amount);
        }
    }

    public static void addMoney(MinecraftServer server, UUID player, long amount) throws ReflectiveOperationException {
        probe();
        if (!available) {
            throw new IllegalStateException("EconomyCraft not present");
        }
        Object manager = getManager.invoke(null, server);
        methodAddMoney.invoke(manager, player, amount);
    }

    /**
     * @return {@code true} if the sale would exceed the daily limit (do not proceed).
     */
    public static boolean tryRecordDailySell(MinecraftServer server, UUID player, long saleAmount) throws ReflectiveOperationException {
        probe();
        if (!available) {
            return false;
        }
        Object manager = getManager.invoke(null, server);
        Object r = methodTryRecordDailySell.invoke(manager, player, saleAmount);
        return r instanceof Boolean b && b;
    }

    public static long getDailySellRemaining(MinecraftServer server, UUID player) throws ReflectiveOperationException {
        probe();
        if (!available) {
            return Long.MAX_VALUE;
        }
        Object manager = getManager.invoke(null, server);
        Object r = methodGetDailySellRemaining.invoke(manager, player);
        return r instanceof Number n ? n.longValue() : Long.MAX_VALUE;
    }
}
